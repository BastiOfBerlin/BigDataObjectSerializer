package de.tub.cit.slist.bdos;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.OHSConfig;
import de.tub.cit.slist.bdos.metadata.ClassMetadata;
import de.tub.cit.slist.bdos.metadata.FieldMetadata;
import de.tub.cit.slist.bdos.metadata.FieldType;
import de.tub.cit.slist.bdos.util.UnsafeHelper;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class OffHeapSerializer<T extends Serializable> implements Serializable {

	private static final long	serialVersionUID		= -6348155422688768649L;
	/** size of the status metadata */
	public static final int		METADATA_STATUS_SIZE	= 1;
	/** offset of status byte(s) within metadata */
	public static final int		STATUS_OFFSET			= 0;

	/** Flag NULL-Status */
	public static final byte	BITMASK_NULL	= 0b00000001;
	/** Status flags reserved for internal purposes; the other flags may be used by the application */
	public static final byte	RESERVED_FLAGS	= 0b00000111;

	/** start address of allocated memory */
	private final long							address;
	/** overall size of allocated memory */
	private final long							memorySize;
	/** max number of elements */
	private final long							maxElementCount;
	/** optional byte array, can be used instead of native (off-heap) memory */
	private byte[]								backingArray	= null;
	/** offset of first field within object */
	private final long							firstFieldOffset;
	/** data size per element; <=> size of object excluding headers */
	private final long							elementSize;
	/** size of metadata */
	private final long							metadataSize;
	/** size of element and meta data */
	private final long							nodeSize;
	/** metadata of persisted classes */
	private final Map<Class<?>, ClassMetadata>	classMetadata	= new HashMap<>();
	/** class to be saved */
	private final Class<T>						baseClass;

	public OffHeapSerializer(final Class<T> baseClass) {
		this(baseClass, (new ConfigFactory()).withDefaults().build(), 0);
	}

	public OffHeapSerializer(final Class<T> baseClass, final OHSConfig config, final long metadataSize) {
		super();
		assert (config != null);
		final long size = config.getSize();
		assert (size > 0);
		this.baseClass = baseClass;

		acquireClassMetadata(baseClass);

		this.firstFieldOffset = UnsafeHelper.firstFieldOffset(baseClass);
		// this.elementSize = UnsafeHelper.sizeOf(baseClass) - this.firstFieldOffset;
		this.elementSize = classMetadata.get(baseClass).getLength();
		this.metadataSize = METADATA_STATUS_SIZE + metadataSize;
		this.nodeSize = this.elementSize + this.metadataSize;

		switch (config.getSizeType()) {
		case BYTES:
			this.memorySize = size;
			this.maxElementCount = size / this.nodeSize;
			break;
		case ELEMENTS:
		default:
			this.memorySize = size * this.nodeSize;
			this.maxElementCount = size;
			break;
		}

		switch (config.getLocation()) {
		case NATIVE_MEMORY:
		default:
			this.address = getUnsafe().allocateMemory(this.memorySize);
			getUnsafe().setMemory(address, this.memorySize, (byte) 0);
			break;
		case BYTE_ARRAY:
			if (this.memorySize > Integer.MAX_VALUE) throw new IllegalArgumentException(
					"When using BYTE_ARRAY location, max. memory size is " + Integer.MAX_VALUE + ", tried to allocate " + this.memorySize);
			this.backingArray = new byte[(int) this.memorySize];
			this.address = UnsafeHelper.toAddress(this.backingArray) + Unsafe.ARRAY_BYTE_BASE_OFFSET;
			break;
		}
	}

	/**
	 * get metadata for <code>baseclazz</code> and store it in {@link #classMetadata}
	 *
	 * @param baseclazz
	 */
	private void acquireClassMetadata(final Class<?> baseclazz) {
		if (classMetadata.containsKey(baseclazz)) return;

		Class<?> clazz = baseclazz;
		final List<FieldMetadata> fields = new ArrayList<>();
		long fieldLength, totalLength = 0;
		do {
			for (final Field f : clazz.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers())) {
					final Class<?> classType = f.getType();
					final FieldMetadata fieldMetadata = new FieldMetadata();
					fieldMetadata.setFieldName(f.getName());
					fieldMetadata.setOffset(getUnsafe().objectFieldOffset(f));
					fieldMetadata.setClazz(classType);

					if (classType == boolean.class) {
						fieldMetadata.setType(FieldType.BOOL);
						fieldLength = 1;
					} else if (classType == byte.class) {
						fieldMetadata.setType(FieldType.BYTE);
						fieldLength = 1;
					} else if (classType == char.class) {
						fieldMetadata.setType(FieldType.CHAR);
						fieldLength = 2;
					} else if (classType == double.class) {
						fieldMetadata.setType(FieldType.DOUBLE);
						fieldLength = 8;
					} else if (classType == float.class) {
						fieldMetadata.setType(FieldType.FLOAT);
						fieldLength = 4;
					} else if (classType == int.class) {
						fieldMetadata.setType(FieldType.INT);
						fieldLength = 4;
					} else if (classType == long.class) {
						fieldMetadata.setType(FieldType.LONG);
						fieldLength = 8;
					} else if (classType == short.class) {
						fieldMetadata.setType(FieldType.SHORT);
						fieldLength = 2;
					} else
						throw new UnsupportedOperationException();

					totalLength += fieldLength;
					fieldMetadata.setLength(fieldLength);
					fields.add(fieldMetadata);
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);
		Collections.sort(fields); // order by offset
		final ClassMetadata classMeta = new ClassMetadata(totalLength, fields.toArray(new FieldMetadata[fields.size()]));
		classMeta.calcSerializedOffsets();
		classMetadata.put(baseclazz, classMeta);
	}

	public void clear() {
		if (backingArray != null) {
			java.util.Arrays.fill(backingArray, (byte) 0);
		} else {
			getUnsafe().setMemory(address, this.memorySize, (byte) 0);
		}
	}

	private void copyObject(final Class<?> baseclass, final Direction direction, final Object src, final long srcOffset, final Object dest,
			final long destOffset) {
		final ClassMetadata classMetadata = this.classMetadata.get(baseclass);
		long sOff, dOff;
		for (final FieldMetadata field : classMetadata.getFields()) {
			if (direction == Direction.SERIALIZE) {
				sOff = srcOffset + field.getOffset();
				dOff = destOffset + field.getSerializedOffset();
			} else {
				sOff = srcOffset + field.getSerializedOffset();
				dOff = destOffset + field.getOffset();
			}
			switch (field.getType()) {
			case BOOL:
				getUnsafe().putBoolean(dest, dOff, getUnsafe().getBoolean(src, sOff));
				break;
			case BYTE:
				getUnsafe().putByte(dest, dOff, getUnsafe().getByte(src, sOff));
				break;
			case CHAR:
				getUnsafe().putChar(dest, dOff, getUnsafe().getChar(src, sOff));
				break;
			case DOUBLE:
				getUnsafe().putDouble(dest, dOff, getUnsafe().getDouble(src, sOff));
				break;
			case FLOAT:
				getUnsafe().putFloat(dest, dOff, getUnsafe().getFloat(src, sOff));
				break;
			case INT:
				getUnsafe().putInt(dest, dOff, getUnsafe().getInt(src, sOff));
				break;
			case LONG:
				getUnsafe().putLong(dest, dOff, getUnsafe().getLong(src, sOff));
				break;
			case SHORT:
				getUnsafe().putShort(dest, dOff, getUnsafe().getShort(src, sOff));
				break;
			case ARRAY:
			case ARRAY_CONST:
			case STRING:
			case STRING_CONST:
			case COLLECTION:
			case COLLECTION_CONST:
			case OBJECT:
			default:
				throw new UnsupportedOperationException();

			}
		}
	}

	/**
	 * Sets a block in a random access manner (Array-like).<br />
	 * <strong>WARNING!</strong> This is not compatible with List-access. Only one mode must be used!
	 *
	 * @param idx
	 * @param element
	 */
	public void setRandomAccess(final long idx, final T element) {
		checkIndexBounds(idx);
		if (element != null) {
			// getUnsafe().copyMemory(element, firstFieldOffset, backingArray, offset(idx), elementSize);
			copyObject(baseClass, Direction.SERIALIZE, element, 0, backingArray, offset(idx));
			setNull(idx, false);
		} else {
			getUnsafe().setMemory(offset(idx), elementSize, (byte) 0);
			setNull(idx, true);
		}
	}

	/**
	 * Gets a block in a random access manner (Array-like). in contrast to {@link #getRandomAccess(Serializable, long)}, this method allocates a new Object
	 * and therefore is slightly slower. So if you can reuse objects, you should use that method.<br />
	 * <strong>WARNING!</strong> This is not compatible with List-access. Only one mode must be used!
	 *
	 * @param idx
	 * @return the element
	 */
	public T getRandomAccess(final long idx) {
		checkIndexBounds(idx);
		try {
			@SuppressWarnings("unchecked")
			final T obj = (T) getUnsafe().allocateInstance(baseClass);
			return getRandomAccess(obj, idx);
		} catch (final InstantiationException e) {
			throw new UndeclaredThrowableException(e);
		}
	}

	/**
	 * Gets a block in a random access manner (Array-like). This method is faster than {@link #getRandomAccess(long)} because no Object allocation needs to
	 * be done.<br />
	 * <strong>WARNING!</strong> This is not compatible with List-access. Only one mode must be used!
	 *
	 * @param dest pre-allocated destination object
	 * @param idx
	 * @return the element
	 */
	public T getRandomAccess(final T dest, final long idx) {
		checkIndexBounds(idx);
		if (isNull(idx)) return null;
		// UnsafeHelper.copyMemory(backingArray, offset(idx), dest, firstFieldOffset, elementSize);
		copyObject(baseClass, Direction.DESERIALIZE, backingArray, offset(idx), dest, 0);
		return dest;
	}

	public long getLong(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getLong(backingArray, offset(idx, false) + offset);
	}

	public void putLong(final long idx, final int offset, final long l) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putLong(backingArray, offset(idx, false) + offset, l);
	}

	public byte getByte(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getByte(backingArray, offset(idx, false) + offset);
	}

	public void putByte(final long idx, final int offset, final byte b) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putByte(backingArray, offset(idx, false) + offset, b);
	}

	public boolean getBoolean(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getBoolean(backingArray, offset(idx, false) + offset);
	}

	public void putBoolean(final long idx, final int offset, final boolean b) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putBoolean(backingArray, offset(idx, false) + offset, b);
	}

	public char getChar(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getChar(backingArray, offset(idx, false) + offset);
	}

	public void putChar(final long idx, final int offset, final char c) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putChar(backingArray, offset(idx, false) + offset, c);
	}

	public double getDouble(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getDouble(backingArray, offset(idx, false) + offset);
	}

	public void putDouble(final long idx, final int offset, final double d) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putDouble(backingArray, offset(idx, false) + offset, d);
	}

	public float getFloat(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getFloat(backingArray, offset(idx, false) + offset);
	}

	public void putFloat(final long idx, final int offset, final float f) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putFloat(backingArray, offset(idx, false) + offset, f);
	}

	public int getInt(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getInt(backingArray, offset(idx, false) + offset);
	}

	public void putInt(final long idx, final int offset, final int i) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putInt(backingArray, offset(idx, false) + offset, i);
	}

	public short getShort(final long idx, final int offset) {
		checkIndexBounds(idx);
		return getUnsafe().getShort(backingArray, offset(idx, false) + offset);
	}

	public void putShort(final long idx, final int offset, final short s) {
		checkIndexBounds(idx);
		checkStatusField(offset);
		getUnsafe().putShort(backingArray, offset(idx, false) + offset, s);
	}

	public byte getStatus(final long idx) {
		checkIndexBounds(idx);
		return getByte(idx, STATUS_OFFSET);
	}

	void setStatus(final long idx, final byte status) {
		checkIndexBounds(idx);
		getUnsafe().putByte(backingArray, offset(idx, false) + STATUS_OFFSET, status);
	}

	public boolean checkFlag(final long idx, final byte mask) {
		return (getStatus(idx) & mask) == mask;
	}

	public void setFlag(final long idx, final byte mask) {
		checkReservedFlags(mask);
		setStatus(idx, (byte) (getStatus(idx) | mask));
	}

	public void unsetFlag(final long idx, final byte mask) {
		checkReservedFlags(mask);
		setStatus(idx, (byte) (getStatus(idx) & ~mask));
	}

	/**
	 * check for illegal (write) access to status field
	 *
	 * @param offset
	 * @throws IllegalArgumentException
	 */
	private void checkStatusField(final long offset) {
		// only works if status is the first byte of metadata
		if (offset < 0 || offset == STATUS_OFFSET) throw new IllegalArgumentException("Illegal access to status field.");
	}

	/**
	 * check for illegal (write) access to reserved (internal) flags
	 *
	 * @param mask
	 * @throws IllegalArgumentException
	 */
	private void checkReservedFlags(final byte mask) {
		if ((mask & RESERVED_FLAGS) != 0b00000000)
			throw new IllegalArgumentException(String.format("Illegally tried to set an internal flag. Reserved flags are: 0x%02x", RESERVED_FLAGS));
	}

	/**
	 * Checks bounds for <code>idx</code>
	 *
	 * @param idx
	 */
	private void checkIndexBounds(final long idx) {
		if (idx < 0 || idx >= maxElementCount)
			throw new IndexOutOfBoundsException(String.format("Tried to access index '%d', max index is '%d'.", idx, maxElementCount));
	}

	/**
	 * Checks the <code>NULL</code> flag.
	 *
	 * @param idx
	 * @return <code>true</code> iff the <code>NULL</code> flag is set
	 */
	public boolean isNull(final long idx) {
		return !checkFlag(idx, BITMASK_NULL);
	}

	/**
	 * sets the <code>NULL</code> flag to <code>nil</code>
	 *
	 * @param idx
	 * @param nil
	 */
	void setNull(final long idx, final boolean nil) {
		final byte oldStatus = getStatus(idx);
		if (nil) {
			setStatus(idx, (byte) (oldStatus & ~BITMASK_NULL));
		} else {
			setStatus(idx, (byte) (oldStatus | BITMASK_NULL));
		}
	}

	/**
	 * frees the memory
	 */
	public void destroy() {
		if (backingArray != null) {
			backingArray = null;
		} else {
			getUnsafe().freeMemory(address);
		}
	}

	/**
	 * Returns the offset to the <code>idx</code>th element.
	 *
	 * @param idx
	 * @return
	 * @see #offset(long, boolean)
	 */
	private long offset(final long idx) {
		return offset(idx, true);
	}

	/**
	 * Returns either relative or absolute memory offset of the element with index idx, depending on the presence of the backing array.<br />
	 * This is necessary because access to on-heap memory has to be done using relative offset within the array.
	 *
	 * @param idx
	 * @param includeMetadataOffset if <code>false</code>, the offset to the metadata is returned
	 * @return
	 */
	private long offset(final long idx, final boolean includeMetadataOffset) {
		return (idx * nodeSize) + (includeMetadataOffset ? metadataSize : 0) + (backingArray == null ? address : Unsafe.ARRAY_BYTE_BASE_OFFSET);
	}

	/**
	 * @return the {@link Unsafe}
	 * @see {@link UnsafeHelper#getUnsafe()}
	 */
	private static Unsafe getUnsafe() {
		return UnsafeHelper.getUnsafe();
	}

	public long getMaxElementCount() {
		return maxElementCount;
	}

	public long getElementSize() {
		return elementSize;
	}

	@Override
	protected void finalize() throws Throwable {
		destroy();
		super.finalize();
	}

	private enum Direction {
		SERIALIZE, DESERIALIZE
	}

}
