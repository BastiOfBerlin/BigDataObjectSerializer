package de.tub.cit.slist.bdos.test;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.tub.cit.slist.bdos.BigDataLinkedList;
import de.tub.cit.slist.bdos.OffHeapSerializer.MemoryLocation;
import de.tub.cit.slist.bdos.OffHeapSerializer.SizeType;
import de.tub.cit.slist.bdos.test.classes.PrimitiveClass;

public class DequeTest {
	private static final int	LIST_SIZE	= 10;
	private static final int	FREE_SLOTS	= 2;

	private static BigDataLinkedList<PrimitiveClass>	linkedList;
	private static Deque<PrimitiveClass>				deque;
	private static final Random							r	= new Random();
	private static Deque<PrimitiveClass>				ref;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		linkedList = new BigDataLinkedList<>(PrimitiveClass.class, LIST_SIZE, SizeType.ELEMENTS, MemoryLocation.NATIVE_MEMORY);
		deque = (linkedList);
		ref = new LinkedList<>();
		for (int i = 0; i < LIST_SIZE - FREE_SLOTS; i++) {
			final PrimitiveClass instance = new PrimitiveClass(r);
			ref.add(instance);
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		deque.clear();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testPushAndPop() {
		deque.addAll(ref);
		for (int i = 0; i < FREE_SLOTS; i++) {
			final PrimitiveClass instance = new PrimitiveClass(r);
			ref.push(instance);
			deque.push(instance);
		}
		for (int i = 0; i < FREE_SLOTS; i++) {
			Assert.assertEquals(ref.pop(), deque.pop());
		}
	}

	@Test
	public void testGetAndPeek() {
		deque.addAll(ref);
		Assert.assertEquals(ref.getFirst(), deque.getFirst());
		Assert.assertEquals(ref.getLast(), deque.getLast());
		Assert.assertEquals(ref.peekFirst(), deque.peekFirst());
		Assert.assertEquals(ref.peekLast(), deque.peekLast());
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testFull() {
		deque.addAll(ref);
		for (int i = 0; i < FREE_SLOTS; i++) {
			final PrimitiveClass instance = new PrimitiveClass(r);
			deque.push(instance);
		}
		final PrimitiveClass instance = new PrimitiveClass(r);
		// this one is too much
		Assert.assertFalse(deque.offer(instance));
		Assert.assertFalse(deque.offerFirst(instance));
		Assert.assertFalse(deque.offerLast(instance));
		deque.push(instance);
	}

	@Test
	public void testOfferAndPoll() {
		deque.addAll(ref);
		for (int i = 0; i < FREE_SLOTS; i++) {
			final PrimitiveClass instance = new PrimitiveClass(r);
			Assert.assertTrue(ref.offerFirst(instance));
			Assert.assertTrue(deque.offerFirst(instance));
		}
		for (int i = 0; i < FREE_SLOTS; i++) {
			Assert.assertEquals(ref.poll(), deque.poll());
		}
	}

	@Test(expected = NoSuchElementException.class)
	public void testRemoveAndPollLast() {
		deque.addAll(ref);
		final Deque<PrimitiveClass> localRef = new LinkedList<>();
		localRef.addAll(ref);
		for (int i = 0; i < ref.size(); i++) {
			Assert.assertEquals(localRef.removeLast(), deque.removeLast());
		}
		Assert.assertNull(deque.pollLast());
		deque.removeLast();
	}

	@Test
	public void testRemoveLastOccurance() {
		deque.addAll(ref);
		final Deque<PrimitiveClass> localRef = new LinkedList<>();
		localRef.addAll(ref);
		for (int i = 0; i < (r.nextInt(LIST_SIZE - 1) + 1); i++) {
			final int idx = r.nextInt(localRef.size());
			@SuppressWarnings("unchecked")
			final PrimitiveClass refObj = ((List<PrimitiveClass>) localRef).get(idx);
			@SuppressWarnings("unchecked")
			final PrimitiveClass dequeObj = ((List<PrimitiveClass>) deque).get(idx);
			localRef.removeLastOccurrence(refObj);
			deque.removeLastOccurrence(dequeObj);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testLastIndexOf() {
		deque.addAll(ref);
		final int idx = r.nextInt(ref.size());
		Assert.assertEquals(idx, linkedList.lastIndexOf(((List<PrimitiveClass>) ref).get(idx)));
	}

}
