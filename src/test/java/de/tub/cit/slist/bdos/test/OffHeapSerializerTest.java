package de.tub.cit.slist.bdos.test;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import de.tub.cit.slist.bdos.OffHeapSerializer;
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
	public void testGetSet() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, 2, SizeType.ELEMENTS);
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

}
