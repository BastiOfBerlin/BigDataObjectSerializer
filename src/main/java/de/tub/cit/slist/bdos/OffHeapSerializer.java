package de.tub.cit.slist.bdos;

import java.io.Serializable;

import de.tub.cit.slist.bdos.util.UnsafeHelper;
import sun.misc.Unsafe;

public class OffHeapSerializer<T extends Serializable> implements Serializable {

	private final long		address;
	private final long		memorySize;
	private final long		firstFieldOffset;
	private final long		elementSize;
	private final Class<T>	baseClass;

	public enum SizeType {
		BYTES, ELEMENTS
	}

	public OffHeapSerializer(final Class<T> baseClass, final long size, final SizeType sizeType) {
		this.baseClass = baseClass;
		this.firstFieldOffset = UnsafeHelper.firstFieldOffset(baseClass);
		this.elementSize = UnsafeHelper.sizeOf(baseClass) - this.firstFieldOffset;
		switch (sizeType) {
		case BYTES:
			this.memorySize = size;
			break;
		case ELEMENTS:
		default:
			this.memorySize = size * this.elementSize;
			break;
		}
		this.address = getUnsafe().allocateMemory(this.memorySize);
	}

	public void set(final long idx, final T element) {
		getUnsafe().copyMemory(element, firstFieldOffset, null, offset(idx), elementSize);
	}

	public T get(final long idx) {
		try {
			@SuppressWarnings("unchecked")
			final T obj = (T) getUnsafe().allocateInstance(baseClass);
			return get(obj, idx);
		} catch (final InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public T get(final T dest, final long idx) {
		UnsafeHelper.copyMemory(null, offset(idx), dest, firstFieldOffset, elementSize);
		return dest;
	}

	private long offset(final long idx) {
		return address + (idx * elementSize);
	}

	private static Unsafe getUnsafe() {
		return UnsafeHelper.getUnsafe();
	}

}
