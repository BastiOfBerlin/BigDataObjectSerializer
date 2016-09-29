package de.tub.cit.slist.bdos.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import sun.misc.Unsafe;

public class UnsafeHelper {
	public static final int	OBJECT_SHELL_SIZE	= 8;
	public static final int	OBJREF_SIZE			= 4;
	public static final int	LONG_FIELD_SIZE		= 8;
	public static final int	INT_FIELD_SIZE		= 4;
	public static final int	SHORT_FIELD_SIZE	= 2;
	public static final int	CHAR_FIELD_SIZE		= 2;
	public static final int	BYTE_FIELD_SIZE		= 1;
	public static final int	BOOLEAN_FIELD_SIZE	= 1;
	public static final int	DOUBLE_FIELD_SIZE	= 8;
	public static final int	FLOAT_FIELD_SIZE	= 4;

	/** the unsafe */
	private static Unsafe unsafe;

	/**
	 * Gets the unsafe.
	 *
	 * @return {@link Unsafe}
	 */
	public static Unsafe getUnsafe() {
		if (unsafe == null) {
			Field field;
			try {
				field = Unsafe.class.getDeclaredField("theUnsafe");
				field.setAccessible(true);
				return (Unsafe) field.get(null);
			} catch (SecurityException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return unsafe;
	}

	/**
	 * Returns the object located at the address.
	 *
	 * @param address The address
	 * @return the object at this address
	 */
	public static Object fromAddress(final long address) {
		final Object[] array = new Object[] { null };
		final long baseOffset = getUnsafe().arrayBaseOffset(Object[].class);
		getUnsafe().putLong(array, baseOffset, address);
		return array[0];
	}

	/**
	 * Copies the memory from srcAddress into dest
	 *
	 * <p>
	 * This is our own implementation because Unsafe.copyMemory(Object src, .. Object dest, ...)
	 * only works if <a href="https://goo.gl/pBVlJv">dest in an array</a>.
	 */
	public static void copyMemory(final Object src, long srcOffset, final Object dest, final long destOffset, final long len) {
		int stepSize = 0;
		if (len % LONG_FIELD_SIZE == 0) {
			stepSize = LONG_FIELD_SIZE;
		} else if (len % INT_FIELD_SIZE == 0) {
			stepSize = INT_FIELD_SIZE;
		} else if (len % SHORT_FIELD_SIZE == 0) {
			stepSize = SHORT_FIELD_SIZE;
		} else {
			stepSize = BYTE_FIELD_SIZE;
		}

		final long end = destOffset + len;
		for (long offset = destOffset; offset < end;) {
			switch (stepSize) {
			case LONG_FIELD_SIZE:
				getUnsafe().putLong(dest, offset, getUnsafe().getLong(src, srcOffset));
				break;
			case INT_FIELD_SIZE:
				getUnsafe().putInt(dest, offset, getUnsafe().getInt(src, srcOffset));
				break;
			case SHORT_FIELD_SIZE:
				getUnsafe().putShort(dest, offset, getUnsafe().getShort(src, srcOffset));
				break;
			case BYTE_FIELD_SIZE:
				getUnsafe().putByte(dest, offset, getUnsafe().getByte(src, srcOffset));
				break;
			default:
				break;
			}
			offset += stepSize;
			srcOffset += stepSize;
		}
	}

	/**
	 * Returns the size of an object out of the class header (offset 12 (0x0C)).<br>
	 * <strong>Works only on 32-Bit Java 7 VMs!</strong>
	 *
	 * @param object Object to measure
	 * @return size of the object
	 */
	public static long jvm7_32_sizeOf(final Object object) {
		// This is getting the size out of the class header (at offset 12)
		return getUnsafe().getAddress(normalize(getUnsafe().getInt(object, 4L)) + 12L);
	}

	private static long roundUpTo8(final long number) {
		return ((number / 8) + 1) * 8;
	}

	/**
	 * Returns the size of the header for an instance of this class (in bytes).
	 *
	 * <p>
	 * More information <a href=
	 * "http://www.codeinstructions.com/2008/12/java-objects-memory-structure.html">http://www.codeinstructions.com/2008/12/java-objects-memory-structure.html</a>
	 * and <a href="http://stackoverflow.com/a/17348396/88646">http://stackoverflow.com/a/17348396/88646</a>
	 *
	 * <p>
	 *
	 * <pre>
	 * +------------------+------------------+------------------ +---------------+
	 * |    mark word(8)  | klass pointer(4) |  array size (opt) |    padding    |
	 * +------------------+------------------+-------------------+---------------+
	 * </pre>
	 *
	 * @param clazz
	 * @return
	 */
	public static long headerSize(final Class<?> clazz) {
		if (clazz == null) throw new NullPointerException();
		// TODO Should be calculated based on the platform
		// TODO maybe unsafe.addressSize() would help? but returns 8 for compressed pointers off..
		// JVM_64 has a 12 byte header 8 + 4 (with compressed pointers on)
		long len = OBJECT_SHELL_SIZE + OBJREF_SIZE;
		if (clazz.isArray()) {
			len += INT_FIELD_SIZE;
		}
		return len;
	}

	/**
	 * Returns the offset of the first field in the range [headerSize, sizeOf].
	 *
	 * @param clazz
	 * @return
	 */
	public static long firstFieldOffset(Class<?> clazz) {
		long minSize = roundUpTo8(headerSize(clazz));

		// Find the min offset for all the classes, up the class hierarchy.
		do {
			for (final Field f : clazz.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers())) {
					minSize = Math.min(minSize, getUnsafe().objectFieldOffset(f));
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);

		return minSize;
	}

	/**
	 * Returns the size of an instance of this class (in bytes).
	 * Instances include a header + all fields + padded to 8 bytes.
	 * If this is an array, it does not include the size of the elements.
	 *
	 * @param clazz
	 * @return
	 */
	public static long sizeOf(Class<?> clazz) {
		long maxSize = headerSize(clazz);

		do {
			for (final Field f : clazz.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers())) {
					maxSize = Math.max(maxSize, getUnsafe().objectFieldOffset(f));
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);

		return roundUpTo8(maxSize);
	}

	/**
	 * Size of all the fields
	 *
	 * @param clazz
	 * @return
	 */
	public static long sizeOfFields(final Class<?> clazz) {
		return sizeOf(clazz) - firstFieldOffset(clazz);
	}

	/**
	 * Returns the object as a byte array, including header, padding and all fields.
	 *
	 * @param obj
	 * @return
	 */
	public static byte[] toByteArray(final Object obj) {
		final int len = (int) sizeOf(obj.getClass());
		final byte[] bytes = new byte[len];
		unsafe.copyMemory(obj, 0, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, bytes.length);
		return bytes;
	}

	private static long normalize(final int value) {
		if (value >= 0) return value;
		return (~0L >>> 32) & value;
	}
}
