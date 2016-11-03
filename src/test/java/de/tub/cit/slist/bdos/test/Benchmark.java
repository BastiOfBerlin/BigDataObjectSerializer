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
		System.out.println("Time for setup:\t\t" + (end - start) + "ms");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		offHeapSerializer.destroy();
		byteArraySerializer.destroy();
	}

	@Test
	public void randomSetGet() {
		final long fillOffHeapTime = fill(offHeapSerializer);
		System.out.println("Time for fill (off-heap):\t" + fillOffHeapTime + "ms");
		final long fillOnHeapTime = fill(byteArraySerializer);
		System.out.println("Time for fill (on-heap):\t" + fillOnHeapTime + "ms");
		final long readOffHeapTime = read(offHeapSerializer);
		System.out.println("Time for read (off-heap):\t" + readOffHeapTime + "ms");
		final long readOnHeapTime = read(byteArraySerializer);
		System.out.println("Time for read (on-heap):\t" + readOnHeapTime + "ms");
		System.out.println("--------------------------------------");
		System.out.println("Fill per element (off-heap):\t" + ((double) fillOffHeapTime / INSTANCES) + "ms");
		System.out.println("Fill per element (on-heap):\t" + ((double) fillOnHeapTime / INSTANCES) + "ms");
		System.out.println("Read per element (off-heap):\t" + ((double) readOffHeapTime / ITERATIONS) + "ms");
		System.out.println("Read per element (on-heap):\t" + ((double) readOnHeapTime / ITERATIONS) + "ms");
	}

	private long fill(final OffHeapSerializer<PrimitiveClass> serializer) {
		long start, end;
		start = System.currentTimeMillis();
		for (int i = 0; i < INSTANCES; i++) {
			serializer.set(setOrder[i], ref[i]);
		}
		end = System.currentTimeMillis();
		return end - start;
	}

	private long read(final OffHeapSerializer<PrimitiveClass> serializer) {
		long start, end;
		for (int i = 0; i < WARMUP; i++) {
			serializer.get(getOrder[i]);
		}
		start = System.currentTimeMillis();
		for (int i = WARMUP; i < ITERATIONS + WARMUP; i++) {
			serializer.get(getOrder[i]);
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
