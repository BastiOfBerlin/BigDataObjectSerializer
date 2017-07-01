package de.tub.cit.slist.bdos.test;

import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import de.tub.cit.slist.bdos.OffHeapSerializer;
import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.MemoryLocation;
import de.tub.cit.slist.bdos.test.classes.FixedLengthClass;
import de.tub.cit.slist.bdos.test.classes.GenericClass;
import de.tub.cit.slist.bdos.test.classes.PrimitiveClass;
import de.tub.cit.slist.bdos.test.classes.RandomlyInitializable;
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
	public void testSetGetNative() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<PrimitiveClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(PrimitiveClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
			setAndGet(serializer, PrimitiveClass.class);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	@Test
	public void testSetGetByteArray() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<PrimitiveClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(PrimitiveClass.class,
					(new ConfigFactory()).withSize(INSTANCES).withLocation(MemoryLocation.BYTE_ARRAY).build(), 0);
			setAndGet(serializer, PrimitiveClass.class);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	private <T extends RandomlyInitializable> void setAndGet(final OffHeapSerializer<T> serializer, final Class<T> clazz)
			throws InstantiationException, IllegalAccessException {
		final RandomlyInitializable[] ref = new RandomlyInitializable[INSTANCES];
		for (int i = 0; i < INSTANCES; i++) {
			final T instance = clazz.newInstance();
			instance.randomInit(r);
			ref[i] = instance;
			serializer.setRandomAccess(i, instance);
		}
		assertEquals(serializer, ref);
	}

	private <T extends RandomlyInitializable> void assertEquals(final OffHeapSerializer<T> serializer, final RandomlyInitializable[] ref) {
		for (int i = 0; i < INSTANCES; i++) {
			Assert.assertEquals(ref[i], serializer.getRandomAccess(i));
		}
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

	@Test
	public void testFixedLength() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<FixedLengthClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(FixedLengthClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
			setAndGet(serializer, FixedLengthClass.class);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	@Test
	public void testFixedLengthWithLessElements() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<FixedLengthClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(FixedLengthClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
			final FixedLengthClass[] ref = new FixedLengthClass[INSTANCES];
			for (int i = 0; i < INSTANCES; i++) {
				final FixedLengthClass instance = FixedLengthClass.class.newInstance();
				instance.randomInit(r);
				// cut out one element per field
				instance.setFixedString(instance.getFixedString().substring(1));
				instance.setFixedIntArray(Arrays.copyOfRange(instance.getFixedIntArray(), 0, instance.getFixedIntArray().length - 1));
				instance.setFixedIntegerArray(Arrays.copyOfRange(instance.getFixedIntegerArray(), 0, instance.getFixedIntegerArray().length - 1));
				instance.getFixedIntegerList().remove(0);
				ref[i] = instance;
				serializer.setRandomAccess(i, instance);
			}
			assertEquals(serializer, ref);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	@Test
	@Ignore
	public void testFixedLengthEmpty() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<FixedLengthClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(FixedLengthClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
			final FixedLengthClass[] ref = new FixedLengthClass[INSTANCES];
			for (int i = 0; i < INSTANCES; i++) {
				final FixedLengthClass instance = new FixedLengthClass();
				instance.setFixedString("");
				ref[i] = instance;
				serializer.setRandomAccess(i, instance);
			}
			assertEquals(serializer, ref);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	@Test
	public void testFixedLengthNull() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<FixedLengthClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(FixedLengthClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
			final FixedLengthClass[] ref = new FixedLengthClass[INSTANCES];
			for (int i = 0; i < INSTANCES; i++) {
				final FixedLengthClass instance = new FixedLengthClass();
				instance.setFixedString(null);
				instance.setFixedIntArray(null);
				instance.setFixedIntegerArray(null);
				instance.setFixedIntegerList(null);
				ref[i] = instance;
				serializer.setRandomAccess(i, instance);
			}
			assertEquals(serializer, ref);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

	@Test
	public void testGenericClass() throws InstantiationException, IllegalAccessException {
		OffHeapSerializer<GenericClass> serializer = null;
		try {
			serializer = new OffHeapSerializer<>(GenericClass.class, (new ConfigFactory()).withSize(INSTANCES).build(), 0);
			setAndGet(serializer, GenericClass.class);
		} finally {
			if (serializer != null) {
				serializer.destroy();
			}
		}
	}

}
