package de.tub.cit.slist.bdos.test;

import java.util.Random;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import de.tub.cit.slist.bdos.OffHeapSerializer;
import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.MemoryLocation;
import de.tub.cit.slist.bdos.test.classes.PrimitiveClass;
import de.tub.cit.slist.bdos.util.UnsafeHelper;

public class OffHeapSerializerTest {
	private static final int	INSTANCES	= 2;
	private static final Random	r			= new Random();

	@Ignore
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
			serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
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
			serializer = new OffHeapSerializer<>(PrimitiveClass.class,
					(new ConfigFactory()).withSize(INSTANCES).withLocation(MemoryLocation.BYTE_ARRAY).build(), 0);
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
			// System.out.println(instance);
			serializer.setRandomAccess(i, instance);
		}
		for (int i = 0; i < INSTANCES; i++) {
			// Assert.assertSame(ref[i], serializer.get(i));
			Assert.assertEquals(ref[i], serializer.getRandomAccess(i));
		}
	}

	@Test(expected = AssertionError.class)
	public void testZeroSize() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(0).build(), 0);
		serializer.setRandomAccess(0, new PrimitiveClass(r));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexOutOfBoundsException() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(1).build(), 0);
		serializer.setRandomAccess(1, new PrimitiveClass(r));
	}

	@Test
	public void testGetNull() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(1).build(), 0);
		Assert.assertNull(serializer.getRandomAccess(0));
		Assert.assertTrue(serializer.isNull(0));
		final PrimitiveClass instance = new PrimitiveClass(r);
		serializer.setRandomAccess(0, instance);
		Assert.assertNotNull(serializer.getRandomAccess(0));
		Assert.assertFalse(serializer.isNull(0));
		serializer.setRandomAccess(0, null);
		Assert.assertNull(serializer.getRandomAccess(0));
		Assert.assertTrue(serializer.isNull(0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetReservedFlag() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(1).build(), 0);
		serializer.setFlag(0, (byte) 0b00000001);
	}

	@Test
	public void testSetFlag() {
		final OffHeapSerializer<PrimitiveClass> serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(1).build(), 0);
		final byte mask = (byte) 0b01000000;
		serializer.setFlag(0, mask);
		Assert.assertTrue(serializer.checkFlag(0, mask));
		serializer.unsetFlag(0, mask);
		Assert.assertFalse(serializer.checkFlag(0, mask));
	}

}
