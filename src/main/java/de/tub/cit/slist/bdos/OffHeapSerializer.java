package de.tub.cit.slist.bdos;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.OHSConfig;
import de.tub.cit.slist.bdos.exception.OutOfDynamicMemoryException;
import de.tub.cit.slist.bdos.metadata.ClassMetadata;
import de.tub.cit.slist.bdos.metadata.FieldMetadata;
import de.tub.cit.slist.bdos.metadata.FieldType;
import de.tub.cit.slist.bdos.util.SerializerHelper;
import de.tub.cit.slist.bdos.util.UnsafeHelper;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
/**
 * @param <T> Size of T be computable. That means, that Generics must not be present in the object tree.
 */
public class OffHeapSerializer<T extends Serializable> implements Serializable {

	private static final long	serialVersionUID				= -6348155422688768649L;
	/** size of the status metadata */
	public static final int		METADATA_STATUS_SIZE			= 1;
	/** offset of status byte(s) within metadata */
	public static final int		STATUS_OFFSET					= 0;
	/** size of the dynamic metadata */
	public static final int		DYNAMIC_METADATA_SIZE			= UnsafeHelper.LONG_FIELD_SIZE;
	/** offset of the pointer to the next free block within the free-block list */
	public static final int		FREE_LIST_NEXT_POINTER_OFFSET	= UnsafeHelper.LONG_FIELD_SIZE;

	/** Flag NULL-Status */
	public static final byte	BITMASK_NULL	= 0b00000001;
	/** Status flags reserved for internal purposes; the other flags may be used by the application */
	public static final byte	RESERVED_FLAGS	= 0b00000111;

	/** start address of allocated memory */
	private final long	address;
	/** overall size of allocated memory */
	private final long	memorySize;
	/** max number of elements */
	private final long	maxElementCount;
	/** start address of memory for variable-length elements */
	private final long	dynamicMemoryStart;
	/** size of allocated memory for variable-length elements */
	private final long	dynamicMemorySize;
	/** optional byte array, can be used instead of native (off-heap) memory */
	private byte[]		backingArray	= null;
	/** offset of first field within object */
	private final long	firstFieldOffset;
	/** data size per element; <=> size of object excluding headers */
	private final long	elementSize;
	/** size of metadata */
	private final long	metadataSize;
	/** size of element and meta data */
	private final long	nodeSize;

	/** pointer to first free dynamic block, forms linked list */
	private long firstFreeDynamicBlock;

	/** metadata of persisted classes */
	private final Map<Class<?>, ClassMetadata>	classMetadata	= new HashMap<>();
	/** class to be saved */
	private final Class<T>						baseClass;

	private static final Map<Class<?>, ClassMetadata> wrapperAndPrimitivesMetadata;
	static {
		wrapperAndPrimitivesMetadata = SerializerHelper.acquireWrapperAndPrimitivesMetadata();
	}

	public OffHeapSerializer(final Class<T> baseClass) {
		this(baseClass, (new ConfigFactory()).withDefaults().build(), 0);
	}

	public OffHeapSerializer(final Class<T> baseClass, final OHSConfig config, final long metadataSize) {
		super();
		assert (Unsafe.ADDRESS_SIZE == UnsafeHelper.LONG_FIELD_SIZE);
		assert (config != null);
		final long size = config.getSize();
		assert (size > 0);
		this.baseClass = baseClass;

		classMetadata.putAll(wrapperAndPrimitivesMetadata);
		acquireClassMetadata(baseClass);

		this.firstFieldOffset = UnsafeHelper.firstFieldOffset(baseClass);
		// this.elementSize = UnsafeHelper.sizeOf(baseClass) - this.firstFieldOffset;
		this.elementSize = classMetadata.get(baseClass).getLength();
		this.metadataSize = METADATA_STATUS_SIZE + metadataSize;
		this.nodeSize = this.elementSize + this.metadataSize;

		long staticMemorySize;
		switch (config.getSizeType()) {
		case BYTES:
			this.memorySize = size;
			staticMemorySize = (long) (this.memorySize * (1 - config.getDynamicRatio()));
			this.maxElementCount = staticMemorySize / this.nodeSize;
			this.dynamicMemorySize = this.memorySize - (this.maxElementCount * this.nodeSize); // adjust to use every byte (rounding effects)
			break;
		case ELEMENTS:
		default:
			this.maxElementCount = size;
			staticMemorySize = this.maxElementCount * this.nodeSize;
			this.memorySize = (long) (staticMemorySize / (1 - config.getDynamicRatio()));
			this.dynamicMemorySize = this.memorySize - staticMemorySize;
			break;
		}

		switch (config.getLocation()) {
		case NATIVE_MEMORY:
		default:
			this.address = getUnsafe().allocateMemory(this.memorySize);
			getUnsafe().setMemory(address, this.memorySize, (byte) 0);
			this.dynamicMemoryStart = this.address + staticMemorySize;
			break;
		case BYTE_ARRAY:
			if (this.memorySize > Integer.MAX_VALUE) throw new IllegalArgumentException(
					"When using BYTE_ARRAY location, max. memory size is " + Integer.MAX_VALUE + ", tried to allocate " + this.memorySize);
			this.backingArray = new byte[(int) this.memorySize];
			this.address = UnsafeHelper.toAddress(this.backingArray) + Unsafe.ARRAY_BYTE_BASE_OFFSET;
			this.dynamicMemoryStart = staticMemorySize;
			break;
		}

		// init free block list
		this.firstFreeDynamicBlock = this.dynamicMemoryStart;
		getUnsafe().putLong(this.backingArray, this.firstFreeDynamicBlock, this.dynamicMemorySize);
	}

	/**
	 * get metadata for <code>baseclazz</code> and store it in {@link #classMetadata}
	 *
	 * @param baseclazz
	 * @return added class metadata
	 */
	private Map<Class<?>, ClassMetadata> acquireClassMetadata(final Class<?> baseclazz) {
		return SerializerHelper.acquireClassMetadata(baseclazz, classMetadata);
	}

	/**
	 * Clears the entire allocated memory by overriding it with NULs.
	 */
	public void clear() {
		if (backingArray != null) {
			java.util.Arrays.fill(backingArray, (byte) 0);
		} else {
			getUnsafe().setMemory(address, this.memorySize, (byte) 0);
		}
	}

	private void copyObject(final Class<?> baseclass, final Direction direction, final Object src, final long srcOffset, final Object dest,
			final long destOffset) throws InstantiationException, IllegalAccessException {
		if (baseclass.isPrimitive()) {
			copyPrimitive(FieldType.fromClass(baseclass), src, srcOffset, dest, destOffset);
			return;
		}

		final ClassMetadata classMetadata = this.classMetadata.get(baseclass);

		long sOff, dOff;
		for (final FieldMetadata field : classMetadata.getFields()) {
			long subTypeLength;
			// add corresponding field offsets offsets
			if (direction == Direction.SERIALIZE) {
				sOff = srcOffset + field.getOffset();
				dOff = destOffset + field.getSerializedOffset();
			} else {
				sOff = srcOffset + field.getSerializedOffset();
				dOff = destOffset + field.getOffset();
			}
			switch (field.getType()) {
			case BOOL:
			case BYTE:
			case CHAR:
			case DOUBLE:
			case FLOAT:
			case INT:
			case LONG:
			case SHORT:
				copyPrimitive(field.getType(), src, sOff, dest, dOff);
				break;
			case STRING_FIXED:
				if (direction == Direction.SERIALIZE) {
					final String s = (String) getUnsafe().getObject(src, sOff);
					final int stringLength = Math.min(s != null ? s.length() : 0, field.getElements());
					writeFixedLength(s != null ? stringLength : null, dest, dOff);
					writeString(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE, s, stringLength);
					// pad with NULs
					if (stringLength < field.getElements()) {
						getUnsafe().setMemory(dest,
								dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + (stringLength * UnsafeHelper.CHAR_FIELD_SIZE),
								((long) field.getElements() * UnsafeHelper.CHAR_FIELD_SIZE) - ((long) stringLength * UnsafeHelper.CHAR_FIELD_SIZE), (byte) 0);
					}
				} else {
					final Integer stringLength = readFixedLength(src, sOff);
					getUnsafe().putObject(dest, dOff, readString(src, sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE, stringLength));
				}
				break;
			case ARRAY_FIXED:
				subTypeLength = this.classMetadata.get(field.getClazz()).getLength();
				if (direction == Direction.SERIALIZE) {
					final Object arr = getUnsafe().getObject(src, sOff);
					final int arrLength = arr != null ? Math.min(Array.getLength(arr), field.getElements()) : 0;
					writeFixedLength(arr != null ? arrLength : null, dest, dOff);
					for (int i = 0; i < arrLength; i++) {
						Object copySrc;
						long copySrcOffset = 0;
						long copyDestOffset = 0;
						if (field.getClazz().isPrimitive()) {
							copySrc = arr;
							copySrcOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * (long) UnsafeHelper.getPrimitiveLengths().get(field.getClazz());
						} else {
							copySrc = Array.get(arr, i);
							getUnsafe().putBoolean(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength,
									copySrc == null);
							copyDestOffset = UnsafeHelper.BOOLEAN_FIELD_SIZE;
						}
						if (copySrc != null) {
							copyObject(field.getClazz(), direction, copySrc, copySrcOffset, dest,
									dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength + copyDestOffset);
						} else {
							getUnsafe().setMemory(dest,
									dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength + copyDestOffset,
									subTypeLength - 1, (byte) 0);
						}
					}
					// pad with NULs
					if (arrLength < field.getElements()) {
						getUnsafe().setMemory(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + (arrLength * subTypeLength),
								(field.getElements() * subTypeLength) - (arrLength * subTypeLength), (byte) 0);
					}
				} else {
					final Integer arrLength = readFixedLength(src, sOff);
					if (arrLength != null) {
						final Object arr = Array.newInstance(field.getClazz(), arrLength);
						for (int i = 0; i < arrLength; i++) {
							Object copyDest;
							long copySrcOffset = 0, copyDestOffset = 0;
							if (field.getClazz().isPrimitive()) {
								copyDest = arr;
								copyDestOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * (long) UnsafeHelper.getPrimitiveLengths().get(field.getClazz());
							} else {
								Object newElement = null;
								if (!getUnsafe().getBoolean(src, sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength)) {
									newElement = getUnsafe().allocateInstance(field.getClazz());
								}
								Array.set(arr, i, newElement);
								copyDest = newElement;
								copySrcOffset = UnsafeHelper.BOOLEAN_FIELD_SIZE;
							}
							// arr[i]=field.getClazz().newInstance();
							if (copyDest != null) {
								copyObject(field.getClazz(), direction, src,
										sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength + copySrcOffset, copyDest,
										copyDestOffset);
							}
						}
						getUnsafe().putObject(dest, dOff, arr);
					} else {
						getUnsafe().putObject(dest, dOff, null);
					}
				}
				break;
			case COLLECTION_FIXED:
				subTypeLength = this.classMetadata.get(field.getClazz()).getLength();
				if (direction == Direction.SERIALIZE) {
					final Collection<?> coll = (Collection<?>) getUnsafe().getObject(src, sOff);
					final int collLength = coll != null ? coll.size() : 0;
					writeFixedLength(coll != null ? collLength : null, dest, dOff);
					if (coll != null) {
						int i = 0;
						for (final Object copySrc : coll) {
							getUnsafe().putBoolean(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength,
									copySrc == null);
							if (copySrc != null) {
								copyObject(field.getClazz(), direction, copySrc, 0, dest,
										dOff + 2 * UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength);
							} else {
								getUnsafe().setMemory(dest, dOff + 2 * UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength,
										subTypeLength - 1, (byte) 0);
							}
							i++;
							if (i >= field.getElements()) {
								break;
							}
						}
					}
					// pad with NULs
					if (collLength < field.getElements()) {
						getUnsafe().setMemory(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + (collLength * subTypeLength),
								(field.getElements() * subTypeLength) - (collLength * subTypeLength), (byte) 0);
					}
				} else {
					// TODO new Instance with number of elements?
					// final Collection<Object> coll = (Collection<Object>) getUnsafe().allocateInstance(field.getCollectionClass());
					final Integer collLength = readFixedLength(src, sOff);
					if (collLength != null) {
						@SuppressWarnings("unchecked")
						final Collection<Object> coll = (Collection<Object>) field.getCollectionClass().newInstance();
						for (int i = 0; i < collLength; i++) {
							if (!getUnsafe().getBoolean(src, sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength)) {
								final Object copyDest = getUnsafe().allocateInstance(field.getClazz());
								copyObject(field.getClazz(), direction, src,
										sOff + 2 * UnsafeHelper.BOOLEAN_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE + i * subTypeLength, copyDest, 0);
								coll.add(copyDest);
							} else {
								coll.add(null);
							}
						}
						getUnsafe().putObject(dest, dOff, coll);
					} else {
						getUnsafe().putObject(dest, dOff, null);
					}
				}
				break;
			case OBJECT:
				if (direction == Direction.SERIALIZE) {
					final Object copySrc = getUnsafe().getObject(src, sOff);
					getUnsafe().putBoolean(dest, dOff, copySrc == null);
					if (copySrc != null) {
						copyObject(field.getClazz(), direction, copySrc, 0, dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE);
					} else {
						getUnsafe().setMemory(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE, this.classMetadata.get(field.getClazz()).getLength(), (byte) 0);
					}
				} else {
					if (!getUnsafe().getBoolean(src, sOff)) {
						final Object copyDest = getUnsafe().allocateInstance(field.getClazz());
						copyObject(field.getClazz(), direction, src, sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE, copyDest, 0);
						getUnsafe().putObject(dest, dOff, copyDest);
					} else {
						getUnsafe().putObject(dest, dOff, null);
					}
				}
				break;
			case ARRAY:
			case STRING:
			case COLLECTION:
				if (direction == Direction.SERIALIZE) {
					try {
						writeDynamicField(field, src, sOff, dest, dOff);
					} catch (final OutOfDynamicMemoryException e) {
						// TODO reset
						throw e;
					}
				} else {
					readDynamicField(field, src, sOff, dest, dOff);
				}
				break;
			default:
				throw new UnsupportedOperationException();

			}
		}
	}

	/**
	 * Writes <code>stringLength</code> characters of a String.
	 *
	 * @param dest
	 * @param dOff
	 * @param s
	 * @param stringLength
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private void writeString(final Object dest, final long dOff, final String s, final int stringLength) throws InstantiationException, IllegalAccessException {
		if (s != null) {
			final char[] arr = new char[stringLength];
			s.getChars(0, stringLength, arr, 0);
			for (int i = 0; i < stringLength; i++) {
				copyObject(char.class, Direction.SERIALIZE, arr, Unsafe.ARRAY_CHAR_BASE_OFFSET + (long) i * UnsafeHelper.CHAR_FIELD_SIZE, dest,
						dOff + i * UnsafeHelper.CHAR_FIELD_SIZE);
			}
		}
	}

	/**
	 * Reads a String with a length of <code>stringLength</code>.
	 *
	 * @param src
	 * @param sOff
	 * @param stringLength
	 * @return The String or <code>NULL</code> if the length is <code>NULL</code>
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private String readString(final Object src, final long sOff, final Integer stringLength) throws InstantiationException, IllegalAccessException {
		if (stringLength == null) return null;
		final char[] arr = new char[stringLength];
		for (int i = 0; i < stringLength; i++) {
			copyObject(char.class, Direction.DESERIALIZE, src, sOff + i * UnsafeHelper.CHAR_FIELD_SIZE, arr,
					Unsafe.ARRAY_CHAR_BASE_OFFSET + (long) i * UnsafeHelper.CHAR_FIELD_SIZE);
		}
		return String.valueOf(arr);
	}

	/**
	 * Writes the length of a {@link de.tub.cit.slist.bdos.annotation.FixedLength FixedLength} String|Array|Collection or {@link Integer#MIN_VALUE} iff length
	 * is <code>NULL</code>.
	 *
	 * @param length
	 * @param dest
	 * @param dOff
	 */
	private void writeFixedLength(final Integer length, final Object dest, final long dOff) {
		getUnsafe().putInt(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE, length != null ? length : Integer.MIN_VALUE);
	}

	/**
	 * Gets the length of a {@link de.tub.cit.slist.bdos.annotation.FixedLength FixedLength} String|Array|Collection.
	 *
	 * @param src
	 * @param sOff
	 * @return lenth or <code>NULL</code> iff value is {@link Integer#MIN_VALUE}.
	 */
	private Integer readFixedLength(final Object src, final long sOff) {
		final int ret = getUnsafe().getInt(src, sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE);
		return ret == Integer.MIN_VALUE ? null : ret;
	}

	private void writeDynamicField(final FieldMetadata field, final Object src, final long sOff, final Object dest, final long dOff)
			throws OutOfDynamicMemoryException, InstantiationException, IllegalAccessException {
		final Object copySrc = getUnsafe().getObject(src, sOff);
		final boolean isNull = copySrc == null;
		getUnsafe().putBoolean(dest, dOff, isNull);
		if (isNull) {
			getUnsafe().putLong(dest, dOff, Long.MIN_VALUE);
		}
		long addr = getUnsafe().getLong(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE);
		if (isNull && addr > 0) {
			deallocateDynamicMemory(addr);
			return;
		}

		switch (field.getType()) {
		case STRING:
			final String s = (String) copySrc;
			final int payloadLength = s.length();
			if (addr > 0 && getUnsafe().getLong(backingArray, addr) != payloadLength) {
				deallocateDynamicMemory(addr);
				addr = Long.MIN_VALUE;
			}
			if (addr <= 0) {
				addr = allocateDynamicMemory((long) payloadLength * UnsafeHelper.CHAR_FIELD_SIZE);
			}
			writeString(backingArray, addr + UnsafeHelper.LONG_FIELD_SIZE, s, payloadLength);
			getUnsafe().putLong(dest, dOff + UnsafeHelper.BOOLEAN_FIELD_SIZE, addr);
			break;
		case ARRAY:
		case COLLECTION:
		default:
			throw new UnsupportedOperationException();
		}
		/*
		 * TODO
		 * source is null && destination hat adresse? -> löschen und return
		 * länge ermitteln
		 * destination hat adresse:
		 * länge identisch? -> adresse übernehmen
		 * sonst löschen
		 * keine adresse? -> speicher allozieren
		 * nicht genug platz? -> exception
		 * feld an adresse schreiben
		 * adresse in destination schreiben
		 */
	}

	private void readDynamicField(final FieldMetadata field, final Object src, final long sOff, final Object dest, final long dOff)
			throws InstantiationException, IllegalAccessException {
		if (!getUnsafe().getBoolean(src, sOff)) {
			Object copyDest = null;
			switch (field.getType()) {
			case STRING:
				final long addr = getUnsafe().getLong(src, sOff + UnsafeHelper.BOOLEAN_FIELD_SIZE);
				if (addr >= 0) {
					final long size = getUnsafe().getLong(backingArray, addr);
					final Integer payloadLength = Long.MIN_VALUE == size ? null : (int) size / UnsafeHelper.CHAR_FIELD_SIZE;
					copyDest = readString(backingArray, addr + UnsafeHelper.LONG_FIELD_SIZE, payloadLength);
				}
				break;
			case ARRAY:
				// final Object copyDest = getUnsafe().allocateInstance(field.getClazz());
			case COLLECTION:
			default:
				throw new UnsupportedOperationException();
			}
			/*
			 * TODO
			 * adresse ermitteln
			 * länge ermitteln
			 * feld lesen
			 */
			getUnsafe().putObject(dest, dOff, copyDest);
		} else {
			getUnsafe().putObject(dest, dOff, null);
		}
	}

	/**
	 * ersten freien block entsprechender groesse finden. fehler, falls kein platz.
	 *
	 * @param objectSize
	 * @return
	 * @throws OutOfDynamicMemoryException
	 */
	private FreeBlockAddress findFirstFreeDynamicBlock(final long objectSize) throws OutOfDynamicMemoryException {
		if (firstFreeDynamicBlock == 0)
			throw new OutOfDynamicMemoryException(String.format("Cannot allocate %d bytes of dynamic memory.", objectSize + UnsafeHelper.LONG_FIELD_SIZE));
		return findFreeDynamicBlock(objectSize, firstFreeDynamicBlock);
	}

	private FreeBlockAddress findFreeDynamicBlock(final long objectSize, final long blockAddr) throws OutOfDynamicMemoryException {
		return findFreeDynamicBlock(objectSize, new FreeBlockAddress(blockAddr));
	}

	private FreeBlockAddress findFreeDynamicBlock(final long objectSize, final FreeBlockAddress blockAddr) throws OutOfDynamicMemoryException {
		if (getUnsafe().getLong(backingArray, blockAddr.address) > objectSize + UnsafeHelper.LONG_FIELD_SIZE)
			return blockAddr;
		else {
			// look for next block
			final long nextBlockAddr = blockAddr.address + FREE_LIST_NEXT_POINTER_OFFSET;
			if (nextBlockAddr > dynamicMemorySize)
				throw new OutOfDynamicMemoryException(String.format("Cannot allocate %d bytes of dynamic memory.", objectSize + UnsafeHelper.LONG_FIELD_SIZE));
			final long nextBlock = getUnsafe().getLong(backingArray, nextBlockAddr);
			if (nextBlock == 0) // last free block reached
				throw new OutOfDynamicMemoryException(String.format("Cannot allocate %d bytes of dynamic memory.", objectSize + UnsafeHelper.LONG_FIELD_SIZE));
			blockAddr.previousAddress = blockAddr.address;
			blockAddr.address = nextBlock;
			return findFreeDynamicBlock(objectSize, blockAddr);
		}
	}

	/**
	 * Allocate first free block with sufficient size and update free-block management info.
	 *
	 * @param objectSize
	 * @return start address of the new block
	 * @throws OutOfDynamicMemoryException no block of sufficient size left
	 */
	private long allocateDynamicMemory(final long objectSize) throws OutOfDynamicMemoryException {
		final FreeBlockAddress addr = findFirstFreeDynamicBlock(objectSize);
		final long requiredSize = objectSize + DYNAMIC_METADATA_SIZE;
		final long blockSize = getUnsafe().getLong(backingArray, addr.address);
		final long nextBlock = getUnsafe().getLong(backingArray, addr.address + FREE_LIST_NEXT_POINTER_OFFSET); // is 0 if last free
		final long nextBlockToSet;
		if (blockSize > requiredSize) {
			// split block
			nextBlockToSet = addr.address + requiredSize;
			// update size of free block
			getUnsafe().putLong(backingArray, nextBlockToSet, blockSize - requiredSize);
			// set pointer to next block
			getUnsafe().putLong(backingArray, nextBlockToSet + FREE_LIST_NEXT_POINTER_OFFSET, nextBlock);
		} else {
			nextBlockToSet = nextBlock;
		}

		// update previous block to point to new next free block
		if (addr.previousAddress < 0) {
			// first free block
			firstFreeDynamicBlock = nextBlockToSet;
		} else {
			getUnsafe().putLong(backingArray, addr.previousAddress + FREE_LIST_NEXT_POINTER_OFFSET, nextBlockToSet);
			// NULify pointer of this block
			getUnsafe().putLong(backingArray, addr.address + FREE_LIST_NEXT_POINTER_OFFSET, 0);
		}
		// set size
		getUnsafe().putLong(backingArray, addr.address, objectSize);
		return addr.address;
	}

	/**
	 * Deallocates the block at the given address.
	 *
	 * This means, memory is being NULified and free-block management info updated.
	 *
	 * @param address
	 * @return size of deallocated memory
	 */
	private long deallocateDynamicMemory(final long address) {
		if (address < dynamicMemoryStart || address > dynamicMemoryStart + dynamicMemorySize)
			throw new IndexOutOfBoundsException(String.format("Address not in dynamic memory (0x%x)", address));

		final long blockSize = getUnsafe().getLong(backingArray, address) + DYNAMIC_METADATA_SIZE;
		// clear block contents
		getUnsafe().setMemory(backingArray, address, blockSize, (byte) 0);

		if (firstFreeDynamicBlock > 0) {
			long previousFreeBlock = firstFreeDynamicBlock;
			long nextFreeBlock = getUnsafe().getLong(backingArray, previousFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET);
			while (nextFreeBlock < address && nextFreeBlock != 0) {
				previousFreeBlock = nextFreeBlock;
				nextFreeBlock = getUnsafe().getLong(backingArray, previousFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET);
			}
			if (address < firstFreeDynamicBlock) {
				// this will be the first free block
				if (address + blockSize < firstFreeDynamicBlock) {
					getUnsafe().putLong(backingArray, address + FREE_LIST_NEXT_POINTER_OFFSET, firstFreeDynamicBlock);
					getUnsafe().putLong(backingArray, address, blockSize);
				} else {
					// adjacent blocks
					final long sizeAdjacentBlock = getUnsafe().getLong(backingArray, firstFreeDynamicBlock);
					final long nextPointerAdjacentBlock = getUnsafe().getLong(backingArray, firstFreeDynamicBlock + FREE_LIST_NEXT_POINTER_OFFSET);
					getUnsafe().putLong(backingArray, address + FREE_LIST_NEXT_POINTER_OFFSET, nextPointerAdjacentBlock);
					getUnsafe().putLong(backingArray, address, blockSize + sizeAdjacentBlock);
					getUnsafe().putLong(backingArray, firstFreeDynamicBlock, 0);
					getUnsafe().putLong(backingArray, firstFreeDynamicBlock + FREE_LIST_NEXT_POINTER_OFFSET, 0);
				}
				firstFreeDynamicBlock = address;
			} else if (nextFreeBlock == 0) {
				// this will be the last free block
				final long sizePreviousFreeBlock = getUnsafe().getLong(backingArray, previousFreeBlock);
				if (previousFreeBlock + sizePreviousFreeBlock < address) {
					getUnsafe().putLong(backingArray, previousFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET, address);
					getUnsafe().putLong(backingArray, address, blockSize);
				} else {
					// adjacent blocks
					getUnsafe().putLong(backingArray, previousFreeBlock, blockSize + sizePreviousFreeBlock);
				}
			} else {
				// free blocks ahead and behind
				long totalBlockSize = blockSize;
				boolean nextFreeBlockAdjacent = false;
				if (address + blockSize >= nextFreeBlock) {
					// next free block is adjacent
					nextFreeBlockAdjacent = true;
					totalBlockSize += getUnsafe().getLong(backingArray, nextFreeBlock);
					nextFreeBlock = getUnsafe().getLong(backingArray, nextFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET);
					getUnsafe().putLong(backingArray, nextFreeBlock, 0);
					getUnsafe().putLong(backingArray, nextFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET, 0);
				}
				final long sizePreviousFreeBlock = getUnsafe().getLong(backingArray, previousFreeBlock);
				if (previousFreeBlock + sizePreviousFreeBlock < address) {
					// previous free block is adjacent
					totalBlockSize += sizePreviousFreeBlock;
					getUnsafe().putLong(backingArray, previousFreeBlock, totalBlockSize);
					if (nextFreeBlockAdjacent) {
						getUnsafe().putLong(backingArray, previousFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET, nextFreeBlock);
					}
				} else {
					// set metadata in current block
					getUnsafe().putLong(backingArray, address, totalBlockSize);
					getUnsafe().putLong(backingArray, address + FREE_LIST_NEXT_POINTER_OFFSET, nextFreeBlock);
					getUnsafe().putLong(backingArray, previousFreeBlock + FREE_LIST_NEXT_POINTER_OFFSET, address);
				}
			}
		} else {
			// no free blocks
			firstFreeDynamicBlock = address;
			getUnsafe().putLong(backingArray, address, blockSize);
		}
		return blockSize;
	}

	/**
	 * Copies a primitive of type <code>type</code> from <code>src</code> to <code>dest</code>, using double-register mode.
	 *
	 * @param type
	 * @param src
	 * @param srcOffset
	 * @param dest
	 * @param destOffset
	 */
	private void copyPrimitive(final FieldType type, final Object src, final long srcOffset, final Object dest, final long destOffset) {
		switch (type) {
		case BOOL:
			getUnsafe().putBoolean(dest, destOffset, getUnsafe().getBoolean(src, srcOffset));
			break;
		case BYTE:
			getUnsafe().putByte(dest, destOffset, getUnsafe().getByte(src, srcOffset));
			break;
		case CHAR:
			getUnsafe().putChar(dest, destOffset, getUnsafe().getChar(src, srcOffset));
			break;
		case DOUBLE:
			getUnsafe().putDouble(dest, destOffset, getUnsafe().getDouble(src, srcOffset));
			break;
		case FLOAT:
			getUnsafe().putFloat(dest, destOffset, getUnsafe().getFloat(src, srcOffset));
			break;
		case INT:
			getUnsafe().putInt(dest, destOffset, getUnsafe().getInt(src, srcOffset));
			break;
		case LONG:
			getUnsafe().putLong(dest, destOffset, getUnsafe().getLong(src, srcOffset));
			break;
		case SHORT:
			getUnsafe().putShort(dest, destOffset, getUnsafe().getShort(src, srcOffset));
			break;
		default:
			break;
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
			try {
				copyObject(baseClass, Direction.SERIALIZE, element, 0, backingArray, offset(idx));
				setNull(idx, false);
			} catch (final InstantiationException | IllegalAccessException e) {
				throw new UndeclaredThrowableException(e);
			}
		} else {
			// TODO: delete dynamic length fields
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
		// checkIndexBounds(idx);
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
		try {
			copyObject(baseClass, Direction.DESERIALIZE, backingArray, offset(idx), dest, 0);
		} catch (final InstantiationException | IllegalAccessException e) {
			throw new UndeclaredThrowableException(e);
		}
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
	 * Returns the offset to the <code>idx</code><sup>th</sup> element.
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
	public static Unsafe getUnsafe() {
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

	private class FreeBlockAddress {
		long	address;
		long	previousAddress	= -1;

		FreeBlockAddress(final long address) {
			this.address = address;
		}
	}

}
