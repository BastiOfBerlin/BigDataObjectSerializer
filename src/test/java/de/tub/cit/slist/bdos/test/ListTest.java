package de.tub.cit.slist.bdos.test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.tub.cit.slist.bdos.BigDataLinkedList;
import de.tub.cit.slist.bdos.conf.MemoryLocation;
import de.tub.cit.slist.bdos.conf.SizeType;
import de.tub.cit.slist.bdos.test.classes.PrimitiveClass;

public class ListTest {
	private static final int LIST_SIZE = 10;

	private static BigDataLinkedList<PrimitiveClass>	linkedList;
	private static List<PrimitiveClass>					list;
	private static final Random							r	= new Random();
	private static List<PrimitiveClass>					ref;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		linkedList = new BigDataLinkedList<>(PrimitiveClass.class, LIST_SIZE, SizeType.ELEMENTS, MemoryLocation.NATIVE_MEMORY);
		list = (linkedList);
		ref = new ArrayList<>(LIST_SIZE);
		for (int i = 0; i < LIST_SIZE; i++) {
			final PrimitiveClass instance = new PrimitiveClass(r);
			ref.add(instance);
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		list.clear();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAdd() {
		for (final PrimitiveClass instance : ref) {
			list.add(instance);
		}
		assertListsEqual();
	}

	@Test
	public void testAddIndex() {
		list.addAll(ref);
		final List<PrimitiveClass> localRef = new ArrayList<>();
		localRef.addAll(ref);
		list.remove(0);
		localRef.remove(0);
		final int idx = r.nextInt(LIST_SIZE);
		final PrimitiveClass instance = new PrimitiveClass(r);
		list.add(idx, instance);
		localRef.add(idx, instance);
		assertListsEqual(localRef);
	}

	@Test
	public void testAddAll() {
		list.addAll(ref);
		assertListsEqual();
	}

	@Test
	public void testSet() {
		list.addAll(ref);
		final List<PrimitiveClass> localRef = new ArrayList<>();
		localRef.addAll(ref);
		for (int i = 0; i < (r.nextInt(LIST_SIZE - 1) + 1); i++) {
			final PrimitiveClass newInstance = new PrimitiveClass(r);
			final int idx = r.nextInt(localRef.size());
			localRef.set(idx, newInstance);
			list.set(idx, newInstance);
		}
		assertListsEqual(localRef);
	}

	@Test
	public void testRemove() {
		list.addAll(ref);
		final List<PrimitiveClass> localRef = new ArrayList<>();
		localRef.addAll(ref);
		for (int i = 0; i < (r.nextInt(LIST_SIZE - 1) + 1); i++) {
			final int idx = r.nextInt(localRef.size());
			localRef.remove(idx);
			list.remove(idx);
		}
		assertListsEqual(localRef);
	}

	@Test
	public void testRemoveObject() {
		list.addAll(ref);
		final List<PrimitiveClass> localRef = new ArrayList<>();
		localRef.addAll(ref);
		for (int i = 0; i < (r.nextInt(LIST_SIZE - 1) + 1); i++) {
			final int idx = r.nextInt(localRef.size());
			localRef.remove(localRef.get(idx));
			list.remove(list.get(idx));
		}
		assertListsEqual(localRef);
	}

	@Test
	public void testIndexOf() {
		list.addAll(ref);
		final int idx = r.nextInt(ref.size());
		Assert.assertEquals(idx, list.indexOf(ref.get(idx)));
	}

	@Test
	public void testContains() {
		list.addAll(ref);
		for (final PrimitiveClass instance : ref) {
			Assert.assertTrue(list.contains(instance));
		}
		Assert.assertTrue(list.containsAll(ref));
	}

	@Test
	public void testToArray() {
		list.addAll(ref);
		final Object[] array = list.toArray();
		Assert.assertEquals(ref.size(), array.length);
		for (int i = 0; i < ref.size(); i++) {
			Assert.assertEquals(ref.get(i), array[i]);
		}
		final PrimitiveClass[] typedArray = new PrimitiveClass[list.size()];
		Assert.assertEquals(ref.size(), typedArray.length);
		list.toArray(typedArray);
		for (int i = 0; i < ref.size(); i++) {
			Assert.assertEquals(ref.get(i), typedArray[i]);
		}
	}

	@Test
	public void testIterator() {
		list.addAll(ref);
		final Iterator<PrimitiveClass> it = list.iterator();
		int i = 0;
		while (it.hasNext()) {
			Assert.assertEquals(ref.get(i++), it.next());
		}
	}

	private void assertListsEqual() {
		assertListsEqual(ref);
	}

	private void assertListsEqual(final List<PrimitiveClass> ref) {
		for (int i = 0; i < ref.size(); i++) {
			Assert.assertEquals(ref.get(i), list.get(i));
		}
	}

}
