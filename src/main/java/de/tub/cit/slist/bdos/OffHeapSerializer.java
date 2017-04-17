package de.tub.cit.slist.bdos;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tub.cit.slist.bdos.annotation.FixedLength;
import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.OHSConfig;
import de.tub.cit.slist.bdos.exception.OutOfDynamicMemoryException;
import de.tub.cit.slist.bdos.metadata.ClassMetadata;
import de.tub.cit.slist.bdos.metadata.FieldMetadata;
import de.tub.cit.slist.bdos.metadata.FieldType;
import de.tub.cit.slist.bdos.util.UnsafeHelper;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
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
		wrapperAndPrimitivesMetadata = new HashMap<>();
		acquireClassMetadata(Boolean.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Byte.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Character.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Double.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Float.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Integer.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Long.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Short.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Boolean.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Byte.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Character.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Double.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Float.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Integer.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Long.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Short.TYPE, wrapperAndPrimitivesMetadata);
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
		return acquireClassMetadata(baseclazz, classMetadata);
	}

	/**
	 * get metadata for <code>baseclazz</code> and store it in <code>target</code>
	 *
	 * @param baseclazz
	 * @param classMetadataMap
	 * @return added class metadata
	 */
	private static Map<Class<?>, ClassMetadata> acquireClassMetadata(final Class<?> baseclazz, final Map<Class<?>, ClassMetadata> classMetadataMap) {
		final Map<Class<?>, ClassMetadata> addedClasses = new HashMap<>();
		if (classMetadataMap.containsKey(baseclazz)) return addedClasses;

		Class<?> clazz = baseclazz;
		final List<FieldMetadata> fields = new ArrayList<>();
		long fieldLength, totalLength = 0;
		if (clazz.isPrimitive()) {
			totalLength = UnsafeHelper.PRIMITIVE_LENGTHS.get(clazz);
		} else {
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
							fieldLength = UnsafeHelper.BOOLEAN_FIELD_SIZE;
						} else if (classType == byte.class) {
							fieldMetadata.setType(FieldType.BYTE);
							fieldLength = UnsafeHelper.BYTE_FIELD_SIZE;
						} else if (classType == char.class) {
							fieldMetadata.setType(FieldType.CHAR);
							fieldLength = UnsafeHelper.CHAR_FIELD_SIZE;
						} else if (classType == double.class) {
							fieldMetadata.setType(FieldType.DOUBLE);
							fieldLength = UnsafeHelper.DOUBLE_FIELD_SIZE;
						} else if (classType == float.class) {
							fieldMetadata.setType(FieldType.FLOAT);
							fieldLength = UnsafeHelper.FLOAT_FIELD_SIZE;
						} else if (classType == int.class) {
							fieldMetadata.setType(FieldType.INT);
							fieldLength = UnsafeHelper.INT_FIELD_SIZE;
						} else if (classType == long.class) {
							fieldMetadata.setType(FieldType.LONG);
							fieldLength = UnsafeHelper.LONG_FIELD_SIZE;
						} else if (classType == short.class) {
							fieldMetadata.setType(FieldType.SHORT);
							fieldLength = UnsafeHelper.SHORT_FIELD_SIZE;
						} else if (classType == String.class) {
							final FixedLength fixedLength = f.getAnnotation(FixedLength.class);
							if (fixedLength != null) {
								fieldMetadata.setType(FieldType.STRING_FIXED);
								fieldLength = fixedLength.value() * UnsafeHelper.CHAR_FIELD_SIZE;
								fieldMetadata.setElements(fixedLength.value());
							} else
								// TODO: variable-length String
								throw new UnsupportedOperationException();
						} else if (classType.isArray()) {
							final FixedLength fixedLength = f.getAnnotation(FixedLength.class);
							if (fixedLength != null) {
								fieldMetadata.setType(FieldType.ARRAY_FIXED);
								final Class<?> subtype = classType.getComponentType();
								if (subtype.isInterface()) throw new UnsupportedOperationException("No Interface Array allowed.");
								fieldMetadata.setClazz(subtype);
								fieldMetadata.setElements(fixedLength.value());
								final Map<Class<?>, ClassMetadata> subtypeMetadata = acquireClassMetadata(subtype, classMetadataMap);
								fieldLength = fixedLength.value() * classMetadataMap.get(subtype).getLength();
								addedClasses.putAll(subtypeMetadata);
							} else
								// TODO: variable-length Array
								throw new UnsupportedOperationException();
						} else if (Collection.class.isAssignableFrom(classType)) {
							final FixedLength fixedLength = f.getAnnotation(FixedLength.class);
							if (fixedLength != null) {
								fieldMetadata.setType(FieldType.COLLECTION_FIXED);
								if (classType.isInterface()) {
									if (classType == List.class) {
										// default implementation for Lists
										fieldMetadata.setCollectionClass(ArrayList.class);
									} else if (classType == Map.class) {
										// default implementation for Maps
										fieldMetadata.setCollectionClass(HashMap.class);
									} else if (classType == Set.class) {
										// default implementation for Sets
										fieldMetadata.setCollectionClass(HashSet.class);
									} else
										throw new UnsupportedOperationException("Collection types must either be non-Interfaces, or of type List|Map|Set.");
								} else {
									fieldMetadata.setCollectionClass(classType);
								}
								final Class<?> subtype = (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
								fieldMetadata.setClazz(subtype);
								fieldMetadata.setElements(fixedLength.value());
								final Map<Class<?>, ClassMetadata> subtypeMetadata = acquireClassMetadata(subtype, classMetadataMap);
								fieldLength = fixedLength.value() * classMetadataMap.get(subtype).getLength();
								addedClasses.putAll(subtypeMetadata);
							} else
								// TODO: variable-length Collection
								throw new UnsupportedOperationException();
						} else {
							fieldMetadata.setType(FieldType.OBJECT);
							final Map<Class<?>, ClassMetadata> subtypeMetadata = acquireClassMetadata(classType, classMetadataMap);
							fieldLength = classMetadataMap.get(classType).getLength();
							addedClasses.putAll(subtypeMetadata);
						}

						totalLength += fieldLength;
						fieldMetadata.setLength(fieldLength);
						fields.add(fieldMetadata);
					}
				}
			} while ((clazz = clazz.getSuperclass()) != null);
			Collections.sort(fields); // order by offset
		}
		final ClassMetadata classMeta = new ClassMetadata(totalLength, fields.toArray(new FieldMetadata[fields.size()]));
		classMeta.calcSerializedOffsets();
		addedClasses.put(baseclazz, classMeta);
		classMetadataMap.putAll(addedClasses);
		return addedClasses;
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
					final char[] arr = new char[Math.min(s.length(), field.getElements())];
					s.getChars(0, arr.length, arr, 0);
					for (int i = 0; i < arr.length; i++) {
						// copyPrimitive(FieldType.CHAR, arr, Unsafe.ARRAY_CHAR_BASE_OFFSET + i * UnsafeHelper.CHAR_FIELD_SIZE, dest,
						// dOff + i * UnsafeHelper.CHAR_FIELD_SIZE);
						copyObject(char.class, direction, arr, Unsafe.ARRAY_CHAR_BASE_OFFSET + i * UnsafeHelper.CHAR_FIELD_SIZE, dest,
								dOff + i * UnsafeHelper.CHAR_FIELD_SIZE);
					}
					// pad with NULs
					if (arr.length < field.getElements()) {
						getUnsafe().setMemory(dest, dOff + (arr.length * UnsafeHelper.CHAR_FIELD_SIZE),
								(field.getElements() * UnsafeHelper.CHAR_FIELD_SIZE) - (arr.length * UnsafeHelper.CHAR_FIELD_SIZE), (byte) 0);
					}
				} else {
					final char[] arr = new char[field.getElements()];
					for (int i = 0; i < arr.length; i++) {
						if (getUnsafe().getChar(sOff + i * UnsafeHelper.CHAR_FIELD_SIZE) == (byte) 0) {
							break;
						}
						// copyPrimitive(FieldType.CHAR, src, sOff + i * UnsafeHelper.CHAR_FIELD_SIZE, arr,
						// Unsafe.ARRAY_CHAR_BASE_OFFSET + i * UnsafeHelper.CHAR_FIELD_SIZE);
						copyObject(char.class, direction, src, sOff + i * UnsafeHelper.CHAR_FIELD_SIZE, arr,
								Unsafe.ARRAY_CHAR_BASE_OFFSET + i * UnsafeHelper.CHAR_FIELD_SIZE);
					}
					getUnsafe().putObject(dest, dOff, String.valueOf(arr));
				}
				break;
			case ARRAY_FIXED:
				subTypeLength = this.classMetadata.get(field.getClazz()).getLength();
				if (direction == Direction.SERIALIZE) {
					final Object arr = getUnsafe().getObject(src, sOff);
					final int arrLength = Array.getLength(arr);
					for (int i = 0; i < Math.min(arrLength, field.getElements()); i++) {
						Object copySrc;
						long copySrcOffset;
						if (field.getClazz().isPrimitive()) {
							copySrc = arr;
							copySrcOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * UnsafeHelper.PRIMITIVE_LENGTHS.get(field.getClazz());
						} else {
							copySrc = Array.get(arr, i);
							copySrcOffset = 0;
						}
						copyObject(field.getClazz(), direction, copySrc, copySrcOffset, dest, dOff + i * subTypeLength);
					}
					// pad with NULs
					if (arrLength < field.getElements()) {
						getUnsafe().setMemory(dest, dOff + (arrLength * subTypeLength), (field.getElements() * subTypeLength) - (arrLength * subTypeLength),
								(byte) 0);
					}
				} else {
					final Object arr = Array.newInstance(field.getClazz(), field.getElements());
					for (int i = 0; i < Array.getLength(arr); i++) {
						Object copyDest;
						long copyDestOffset;
						if (field.getClazz().isPrimitive()) {
							copyDest = arr;
							copyDestOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * UnsafeHelper.PRIMITIVE_LENGTHS.get(field.getClazz());
						} else {
							final Object newElement = getUnsafe().allocateInstance(field.getClazz());
							Array.set(arr, i, newElement);
							copyDest = newElement;
							copyDestOffset = 0;
						}
						// arr[i]=field.getClazz().newInstance();
						copyObject(field.getClazz(), direction, src, sOff + i * subTypeLength, copyDest, copyDestOffset);
					}
					getUnsafe().putObject(dest, dOff, arr);
				}
				break;
			case COLLECTION_FIXED:
				subTypeLength = this.classMetadata.get(field.getClazz()).getLength();
				if (direction == Direction.SERIALIZE) {
					final Collection<?> coll = (Collection<?>) getUnsafe().getObject(src, sOff);
					final int collLength = coll.size();
					int i = 0;
					for (final Object copySrc : coll) {
						copyObject(field.getClazz(), direction, copySrc, 0, dest, dOff + i * subTypeLength);
						i++;
						if (i >= field.getElements()) {
							break;
						}
					}
					// pad with NULs
					if (collLength < field.getElements()) {
						getUnsafe().setMemory(dest, dOff + (collLength * subTypeLength), (field.getElements() * subTypeLength) - (collLength * subTypeLength),
								(byte) 0);
					}
				} else {
					// TODO new Instance with number of elements?
					// final Collection<Object> coll = (Collection<Object>) getUnsafe().allocateInstance(field.getCollectionClass());
					@SuppressWarnings("unchecked")
					final Collection<Object> coll = (Collection<Object>) field.getCollectionClass().newInstance();
					for (int i = 0; i < field.getElements(); i++) {
						final Object copyDest = getUnsafe().allocateInstance(field.getClazz());
						copyObject(field.getClazz(), direction, src, sOff + i * subTypeLength, copyDest, 0);
						coll.add(copyDest);
					}
					getUnsafe().putObject(dest, dOff, coll);
				}
				break;
			case OBJECT:
				if (direction == Direction.SERIALIZE) {
					final Object copySrc = getUnsafe().getObject(src, sOff);
					copyObject(field.getClazz(), direction, copySrc, 0, dest, dOff);
				} else {
					final Object copyDest = getUnsafe().allocateInstance(field.getClazz());
					copyObject(field.getClazz(), direction, src, sOff, copyDest, 0);
					getUnsafe().putObject(dest, dOff, copyDest);
				}
				break;
			case ARRAY:
			case STRING:
			case COLLECTION:
			default:
				throw new UnsupportedOperationException();

			}
		}
	}

	private void copyObjectDynamic(final Class<?> baseclass, final Direction direction, final Object src, final long srcOffset, final Object dest,
			final long destOffset) {
		// TODO
		if (direction == Direction.SERIALIZE) {

		} else {

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
			// getUnsafe().copyMemory(element, firstFieldOffset, backingArray, offset(idx), elementSize);
			try {
				copyObject(baseClass, Direction.SERIALIZE, element, 0, backingArray, offset(idx));
				setNull(idx, false);
			} catch (final InstantiationException | IllegalAccessException e) {
				throw new UndeclaredThrowableException(e);
			}
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
		// UnsafeHelper.copyMemory(backingArray, offset(idx), dest, firstFieldOffset, elementSize);
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

	private class FreeBlockAddress {
		long	address;
		long	previousAddress	= -1;

		FreeBlockAddress() {
		}

		FreeBlockAddress(final long address) {
			this.address = address;
		}
	}

}
