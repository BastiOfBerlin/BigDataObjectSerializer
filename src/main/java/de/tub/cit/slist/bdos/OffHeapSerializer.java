package de.tub.cit.slist.bdos;

import java.io.Serializable;

import de.tub.cit.slist.bdos.util.UnsafeHelper;
import sun.misc.Unsafe;

public class OffHeapSerializer<T extends Serializable> implements Serializable {

	private static final long serialVersionUID = -6348155422688768649L;

	/** start address of allocated memory */
	private final long		address;
	/** overall size of allocated memory */
	private final long		memorySize;
	/** max number of elements */
	private final long		maxElementCount;
	/** optional byte array, can be used instead of native (off-heap) memory */
	private byte[]			backingArray	= null;
	/** offset of first field within object */
	private final long		firstFieldOffset;
	/** data size per element; <=> size of object excluding headers */
	private final long		elementSize;
	/** class to be saved */
	private final Class<T>	baseClass;

	/**
	 * Size unit
	 */
	public enum SizeType {
		BYTES, ELEMENTS
	}

	/**
	 * Defines where the data are stored
	 */
	public enum MemoryLocation {
		NATIVE_MEMORY, BYTE_ARRAY
	}

	public OffHeapSerializer(final Class<T> baseClass, final long size) {
		this(baseClass, size, SizeType.ELEMENTS, MemoryLocation.NATIVE_MEMORY);
	}

	public OffHeapSerializer(final Class<T> baseClass, final long size, final SizeType sizeType, final MemoryLocation location) {
		super();
		assert (size > 0);
		this.baseClass = baseClass;
		this.firstFieldOffset = UnsafeHelper.firstFieldOffset(baseClass);
		this.elementSize = UnsafeHelper.sizeOf(baseClass) - this.firstFieldOffset;

		switch (sizeType) {
		case BYTES:
			this.memorySize = size;
			this.maxElementCount = size / this.elementSize;
			break;
		case ELEMENTS:
		default:
			this.memorySize = size * this.elementSize;
			this.maxElementCount = size;
			break;
		}

		switch (location) {
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
	 * Sets an element in a random access manner (Array-like).<br />
	 * <strong>WARNING!</strong> This is not compatible with List-access. Only one mode must be used!
	 * 
	 * @param idx
	 * @param element
	 */
	public void setRandomAccess(final long idx, final T element) {
		checkIndexBounds(idx);
		getUnsafe().copyMemory(element, firstFieldOffset, backingArray, offset(idx), elementSize);
	}

	/**
	 * Gets an element in a random access manner (Array-like). in contrast to {@link #getRandomAccess(Serializable, long)}, this method allocates a new Object
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Gets an element in a random access manner (Array-like). This method is faster than {@link #getRandomAccess(long)} because no Object allocation needs to
	 * be done.<br />
	 * <strong>WARNING!</strong> This is not compatible with List-access. Only one mode must be used!
	 * 
	 * @param dest pre-allocated destination object
	 * @param idx
	 * @return the element
	 */
	public T getRandomAccess(final T dest, final long idx) {
		checkIndexBounds(idx);
		UnsafeHelper.copyMemory(backingArray, offset(idx), dest, firstFieldOffset, elementSize);
		return dest;
	}

	private void checkIndexBounds(final long idx) {
		if (idx >= maxElementCount) throw new IndexOutOfBoundsException(String.format("Tried to access index '%d', max index is '%d'.", idx, maxElementCount));
	}

	public void destroy() {
		if (backingArray != null) {
			backingArray = null;
		} else {
			getUnsafe().freeMemory(address);
		}
	}

	/**
	 * Returns either relative or absolute memory offset of the element with index idx, depending on the presence of the backing array.<br />
	 * This is necessary because access to on-heap memory has to be done using relative offset within the array.
	 *
	 * @param idx
	 * @return
	 */
	private long offset(final long idx) {
		return (idx * elementSize) + (backingArray == null ? address : Unsafe.ARRAY_BYTE_BASE_OFFSET);
	}

	private static Unsafe getUnsafe() {
		return UnsafeHelper.getUnsafe();
	}

}
