package de.tub.cit.slist.bdos.test;

import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import de.tub.cit.slist.bdos.OffHeapSerializer;
import de.tub.cit.slist.bdos.test.classes.PrimitiveClass;

public class Benchmark {
	private static final int	INSTANCES	= 5000000;
	private static final int	ITERATIONS	= 5000000;
	private static final int	WARMUP		= 5;
	private static final Random	r			= new Random();

	private static OffHeapSerializer<PrimitiveClass>	offHeapSerializer	= null;
	private static OffHeapSerializer<PrimitiveClass>	byteArraySerializer	= null;
	private static final PrimitiveClass[]				ref					= new PrimitiveClass[INSTANCES];
	private static final int[]							getOrder			= new int[ITERATIONS + WARMUP];
	private static final int[]							setOrder			= new int[INSTANCES];

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		long start, end;
		start = System.currentTimeMillis();
		offHeapSerializer = new OffHeapSerializer<>(PrimitiveClass.class, INSTANCES);
		byteArraySerializer = new OffHeapSerializer<>(PrimitiveClass.class, INSTANCES);
		for (int i = 0; i < INSTANCES; i++) {
			ref[i] = new PrimitiveClass(r);
			setOrder[i] = i;
		}
		for (int i = 0; i < ITERATIONS + WARMUP; i++) {
			getOrder[i] = r.nextInt(INSTANCES);
		}
		shuffleArray(setOrder);
		end = System.currentTimeMillis();
		System.out.format("Time for setup:\t\t\t\t%,7dms\n", end - start);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		offHeapSerializer.destroy();
		byteArraySerializer.destroy();
	}

	@Test
	public void randomSetGet() {
		final long fillOffHeapTime = fill(offHeapSerializer);
		System.out.format("Time for fill (off-heap):\t\t%,7dms\n", fillOffHeapTime);
		final long fillOnHeapTime = fill(byteArraySerializer);
		System.out.format("Time for fill  (on-heap):\t\t%,7dms\n", fillOnHeapTime);
		final long readOffHeapTime = read(offHeapSerializer);
		System.out.format("Time for read (off-heap):\t\t%,7dms\n", readOffHeapTime);
		final long readOnHeapTime = read(byteArraySerializer);
		System.out.format("Time for read  (on-heap):\t\t%,7dms\n", readOnHeapTime);
		final long readOffHeapPreallocated = readPreallocated(offHeapSerializer);
		System.out.format("Time for read preallocated (off-heap):\t%,7dms\n", readOffHeapPreallocated);
		final long readOnHeapPreallocated = readPreallocated(byteArraySerializer);
		System.out.format("Time for read preallocated  (on-heap):\t%,7dms\n", readOnHeapPreallocated);
		System.out.println("-------------------------------------------------");
		System.out.format("Fills (off-heap):\t\t%#,14.2f per second\n", (INSTANCES / (double) fillOffHeapTime * 1000));
		System.out.format("Fills  (on-heap):\t\t%#,14.2f per second\n", (INSTANCES / (double) fillOnHeapTime * 1000));
		System.out.format("Reads (off-heap):\t\t%#,14.2f per second\n", (INSTANCES / (double) readOffHeapTime * 1000));
		System.out.format("Reads  (on-heap):\t\t%#,14.2f per second\n", (INSTANCES / (double) readOnHeapTime * 1000));
		System.out.format("Reads preallocated (off-heap):\t%#,14.2f per second\n", (INSTANCES / (double) readOffHeapPreallocated * 1000));
		System.out.format("Reads preallocated  (on-heap):\t%#,14.2f per second\n", (INSTANCES / (double) readOnHeapPreallocated * 1000));
	}

	private long fill(final OffHeapSerializer<PrimitiveClass> serializer) {
		long start, end;
		start = System.currentTimeMillis();
		for (int i = 0; i < INSTANCES; i++) {
			serializer.setRandomAccess(setOrder[i], ref[i]);
		}
		end = System.currentTimeMillis();
		return end - start;
	}

	private long read(final OffHeapSerializer<PrimitiveClass> serializer) {
		long start, end;
		for (int i = 0; i < WARMUP; i++) {
			serializer.getRandomAccess(getOrder[i % INSTANCES]);
		}
		start = System.currentTimeMillis();
		for (int i = WARMUP; i < ITERATIONS + WARMUP; i++) {
			serializer.getRandomAccess(getOrder[i % INSTANCES]);
		}
		end = System.currentTimeMillis();
		return end - start;
	}

	private long readPreallocated(final OffHeapSerializer<PrimitiveClass> serializer) {
		long start, end;
		final PrimitiveClass dest = new PrimitiveClass();
		for (int i = 0; i < WARMUP; i++) {
			serializer.getRandomAccess(dest, getOrder[i % INSTANCES]);
		}
		start = System.currentTimeMillis();
		for (int i = WARMUP; i < ITERATIONS + WARMUP; i++) {
			serializer.getRandomAccess(dest, getOrder[i % INSTANCES]);
		}
		end = System.currentTimeMillis();
		return end - start;
	}

	/**
	 * Shuffles an array implementing the Durstenfeld shuffle
	 *
	 * @param ar
	 */
	static void shuffleArray(final int[] ar) {
		for (int i = ar.length - 1; i > 0; i--) {
			final int index = r.nextInt(i + 1);
			// Simple swap
			final int a = ar[index];
			ar[index] = ar[i];
			ar[i] = a;
		}
	}

}
