package de.tub.cit.slist.bdos.test;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import de.tub.cit.slist.bdos.OffHeapSerializer;
import de.tub.cit.slist.bdos.OffHeapSerializer.MemoryLocation;
import de.tub.cit.slist.bdos.OffHeapSerializer.SizeType;
import de.tub.cit.slist.bdos.test.classes.PrimitiveClass;
import de.tub.cit.slist.bdos.util.UnsafeHelper;

public class OffHeapSerializerTest {
	private static final int	INSTANCES	= 2;
	private static final Random	r			= new Random();

	@Test
	public void testSizeof() {
		System.out.println("Header size:      " + UnsafeHelper.headerSize(PrimitiveClass.class));
		System.out.println("firstFieldOffset: " + UnsafeHelper.firstFieldOffset(PrimitiveClass.class));
		System.out.println("Size of fields:   " + UnsafeHelper.sizeOfFields(PrimitiveClass.class));
		System.out.println("Size Of:          " + UnsafeHelper.sizeOf(PrimitiveClass.class));
	}

	@Test
	public void testSetGetNative() {
		OffHeapSerializer<PrimitiveClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(PrimitiveClass.class, INSTANCES);
			setAndGet(serializer);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	@Test
	public void testSetGetByteArray() {
		OffHeapSerializer<PrimitiveClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(PrimitiveClass.class, INSTANCES, SizeType.ELEMENTS, MemoryLocation.BYTE_ARRAY);
			setAndGet(serializer);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	private void setAndGet(final OffHeapSerializer<PrimitiveClass> serializer) {
		final PrimitiveClass[] ref = new PrimitiveClass[INSTANCES];
		for (int i = 0; i < INSTANCES; i++) {
			final PrimitiveClass instance = new PrimitiveClass(r);
			ref[i] = instance;
			System.out.println(instance);
			serializer.set(i, instance);
		}
		for (int i = 0; i < INSTANCES; i++) {
			// Assert.assertSame(ref[i], serializer.get(i));
			Assert.assertEquals(ref[i], serializer.get(i));
		}
	}

	@Test(expected = AssertionError.class)
	public void testZeroSize() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, 0);
		serializer.set(0, new PrimitiveClass(r));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexOutOfBoundsException() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, 1);
		serializer.set(1, new PrimitiveClass(r));
	}

}
