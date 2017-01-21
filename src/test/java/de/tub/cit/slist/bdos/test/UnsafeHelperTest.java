package de.tub.cit.slist.bdos.test;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.tub.cit.slist.bdos.util.UnsafeHelper;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class UnsafeHelperTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSerializeDeserializeString() {
		final byte[] dest = new byte[16];
		final String longString = "\u0442\u044D\u0441\u0442 \u0422\u0401\u0405\u0422";
		UnsafeHelper.serializeString(dest, Unsafe.ARRAY_BYTE_BASE_OFFSET, longString, dest.length);
		Assert.assertEquals(longString.substring(0, longString.length() - 1), UnsafeHelper.deserializeString(dest, Unsafe.ARRAY_BYTE_BASE_OFFSET, dest.length));
	}

	@Test
	public void testSerializeString() {
		final byte[] dest = new byte[16];
		// test padding
		Assert.assertEquals(false, UnsafeHelper.serializeString(dest, Unsafe.ARRAY_BYTE_BASE_OFFSET, "test 123", dest.length));
		Assert.assertArrayEquals(
				new byte[] { 't', 'e', 's', 't', ' ', '1', '2', '3', (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0 }, dest);
		// test null String
		Assert.assertEquals(false, UnsafeHelper.serializeString(dest, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, dest.length));
		Assert.assertArrayEquals(new byte[] { (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
				(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0 }, dest);
		// test cut
		Assert.assertEquals(true, UnsafeHelper.serializeString(dest, Unsafe.ARRAY_BYTE_BASE_OFFSET, "Lorem ipsum dolor sit amet", dest.length));
		Assert.assertArrayEquals(new byte[] { 'L', 'o', 'r', 'e', 'm', ' ', 'i', 'p', 's', 'u', 'm', ' ', 'd', 'o', 'l', 'o' }, dest);
		// test unicode
		Assert.assertEquals(true,
				UnsafeHelper.serializeString(dest, Unsafe.ARRAY_BYTE_BASE_OFFSET, "\u0442\u044D\u0441\u0442 \u0422\u0401\u0405\u0422", dest.length));
		// System.out.println(Arrays.toString(dest));
		Assert.assertArrayEquals(new byte[] { (byte) -47, (byte) -126, (byte) -47, (byte) -115, (byte) -47, (byte) -127, (byte) -47, (byte) -126, (byte) 32,
				(byte) -48, (byte) -94, (byte) -48, (byte) -127, (byte) -48, (byte) -123, (byte) 0 }, dest);
	}

	@Test
	public void testDeserializeString() {
		final byte[] arr = { 't', 'e', 's', 't', ' ', '1', '2', '3', (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0 };
		Assert.assertEquals("test 123", UnsafeHelper.deserializeString(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET, arr.length));
		Assert.assertEquals("", UnsafeHelper.deserializeString(new byte[] { (byte) 0 }, Unsafe.ARRAY_BYTE_BASE_OFFSET, 1));
		Assert.assertEquals("", UnsafeHelper.deserializeString(new byte[] {}, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0));
		Assert.assertEquals("", UnsafeHelper.deserializeString(null, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0));
		// unicode
		Assert.assertEquals("\u0442\u044D\u0441\u0442 \u0422\u0401\u0405",
				UnsafeHelper
						.deserializeString(
								new byte[] { (byte) -47, (byte) -126, (byte) -47, (byte) -115, (byte) -47, (byte) -127, (byte) -47, (byte) -126, (byte) 32,
										(byte) -48, (byte) -94, (byte) -48, (byte) -127, (byte) -48, (byte) -123, (byte) 0 },
								Unsafe.ARRAY_BYTE_BASE_OFFSET, 16));
	}

}
