package de.tub.cit.slist.bdos;

import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.OHSConfig;
import de.tub.cit.slist.bdos.conf.SizeType;
import de.tub.cit.slist.bdos.util.BigDataCollectionHelper;
import de.tub.cit.slist.bdos.util.UnsafeHelper;

public class BigDataLinkedList<T extends Serializable> extends AbstractSequentialList<T> implements List<T>, Deque<T>, java.io.Serializable {

	private static final long serialVersionUID = 6295395861024765218L;

	/** 1 Byte Status already allocated + 2x4 Bytes pointer next/prev */
	private static final int METADATA_BYTES = 8;

	private final OffHeapSerializer<T>	serializer;
	/** class to be saved */
	private final Class<T>				baseClass;

	/** index offset to first free element */
	private int	firstFree	= 0;
	/** actual size of the list, i.e. number of elements */
	private int	size		= 0;
	/** index offset to first element */
	private int	first		= -1;

	/** index offset to last element */
	private int last = -1;

	/**
	 * Creates a new linked list with default configuration.
	 *
	 * @param baseClass
	 *
	 * @see ConfigFactory#withDefaults()
	 */
	public BigDataLinkedList(final Class<T> baseClass) {
		this(baseClass, null, null);
	}

	/**
	 * Creates a new linked list with the given configuration.
	 *
	 * @param baseClass
	 * @param config {@link OHSConfig}
	 *
	 * @see ConfigFactory
	 */
	public BigDataLinkedList(final Class<T> baseClass, final OHSConfig config) {
		this(baseClass, config, null);
	}

	/**
	 * Creates a new linked list with default configuration and adds all elements from c if not null.<br />
	 * If c contains more elements than the defaults provide, size is set to <code>c.size()</code>.
	 *
	 * @param baseClass
	 * @param c Collection&lt;T&gt;
	 *
	 * @see ConfigFactory#withDefaults()
	 * @see #addAll(Collection)
	 */
	public BigDataLinkedList(final Class<T> baseClass, final Collection<T> c) {
		final ConfigFactory f = (new ConfigFactory()).withDefaults();
		final OHSConfig config = f.build();
		// try to avoid out of bounds exceptions
		if (config.getSizeType() == SizeType.ELEMENTS && config.getSize() < c.size()) {
			f.withSize(c.size());
		}
		this.baseClass = baseClass;
		serializer = new OffHeapSerializer<>(baseClass, config, METADATA_BYTES);
		if (c != null) {
			this.addAll(c);
		}
	}

	/**
	 * Creates a new linked list with a configuration and adds all elements from c if not null.<br />
	 * <strong>Note:</strong> It must be ensured that <code>config</code> declares enough size to hold all elements from c, otherwise an exception is thrown.
	 *
	 * @param baseClass
	 * @param config {@link OHSConfig}, defaults to default settings if null
	 * @param c Collection&lt;T&gt;
	 *
	 * @see ConfigFactory#withDefaults()
	 * @see #addAll(Collection)
	 */
	public BigDataLinkedList(final Class<T> baseClass, final OHSConfig config, final Collection<T> c) {
		OHSConfig conf = config;
		if (conf == null) {
			conf = (new ConfigFactory()).withDefaults().build();
		}
		this.baseClass = baseClass;
		serializer = new OffHeapSerializer<>(baseClass, conf, METADATA_BYTES);
		if (c != null) {
			this.addAll(c);
		}
	}

	private int getNextPointer(final int idx) {
		return BigDataCollectionHelper.getNextPointer(serializer, idx);
	}

	private void setNextPointer(final int idx, final int pointer) {
		BigDataCollectionHelper.setNextPointer(serializer, idx, pointer);
	}

	private int getPrevPointer(final int idx) {
		return BigDataCollectionHelper.getPrevPointer(serializer, idx);
	}

	private void setPrevPointer(final int idx, final int pointer) {
		BigDataCollectionHelper.setPrevPointer(serializer, idx, pointer);
	}

	/**
	 * Tells if the argument is the index of an existing element.
	 */
	private boolean isElementIndex(final long index) {
		return index >= 0 && index < size;
	}

	/**
	 * Tells if the argument is the index of a valid position for an iterator or an add operation.
	 */
	private boolean isPositionIndex(final long index) {
		return index >= 0 && index <= size;
	}

	/**
	 * Constructs an IndexOutOfBoundsException detail message. Of the many possible refactorings of the error handling code, this "outlining" performs best with
	 * both server and client VMs.
	 */
	private String outOfBoundsMsg(final long index) {
		return "Index: " + index + ", Size: " + size;
	}

	private void checkElementIndex(final long index) {
		if (!isElementIndex(index)) throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	private void checkPositionIndex(final long index) {
		if (!isPositionIndex(index)) throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	/**
	 * Sets the first free block and manages the linked list of free blocks
	 *
	 * @param element
	 * @return index of the new block
	 */
	private int setFirstFree(final T element) {
		final int idx = firstFree;
		serializer.setRandomAccess(idx, element);
		firstFree = getNextPointer(idx);
		// the pointer will be 0 if it was not unlinked before
		if (firstFree == 0) {
			firstFree = idx + 1;
		}
		return idx;
	}

	/**
	 * Deletes a block (nullify) and updates the linked list of free blocks
	 *
	 * @param idx of the to-be-deleted block
	 */
	private void deleteElement(final int idx) {
		serializer.setRandomAccess(idx, null);
		if (idx < firstFree) {
			setNextPointer(idx, firstFree);
			setPrevPointer(idx, 0);
			firstFree = idx;
		} else {
			int free = firstFree;
			int lastFree = 0;
			while (free < idx) {
				lastFree = free;
				free = getNextPointer(free);
			}
			setNextPointer(lastFree, idx);
			setNextPointer(idx, free);
		}
	}

	/**
	 * Links element as first element.
	 */
	private void linkFirst(final T element) {
		final int f = first;
		final int newNode = setFirstFree(element);

		setNextPointer(newNode, f);
		setPrevPointer(newNode, -1); // undefined
		first = newNode;

		if (f == -1) {
			last = newNode;
		} else {
			setPrevPointer(f, newNode);
		}
		size++;
		modCount++;
	}

	/**
	 * Links element as last element.
	 */
	private void linkLast(final T element) {
		final int l = last;
		final int newNode = setFirstFree(element);

		setNextPointer(newNode, -1); // undefined
		setPrevPointer(newNode, l);
		last = newNode;

		if (l == -1) {
			first = newNode;
		} else {
			setNextPointer(l, newNode);
		}
		size++;
		modCount++;
	}

	/**
	 * Inserts element e before non-null Node succ.
	 */
	void linkBefore(final T e, final int succ) {
		assert (succ > 0);
		final int pred = getPrevPointer(succ);
		final int newNode = setFirstFree(e);

		setNextPointer(newNode, succ);
		setPrevPointer(newNode, pred);
		setPrevPointer(succ, newNode);

		if (pred == -1) {
			first = newNode;
		} else {
			setNextPointer(pred, newNode);
		}
		size++;
		modCount++;
	}

	/**
	 * Unlinks first node idx.
	 */
	private T unlinkFirst(final int idx) {
		final T element = serializer.getRandomAccess(idx);
		final int next = getNextPointer(idx);
		first = next;
		if (next == -1) {
			last = -1;
		} else {
			setPrevPointer(next, -1);
		}
		deleteElement(idx);
		size--;
		modCount++;
		return element;
	}

	/**
	 * Unlinks last node idx.
	 */
	private T unlinkLast(final int idx) {
		final T element = serializer.getRandomAccess(idx);
		final int prev = getPrevPointer(idx);
		last = prev;
		if (prev == -1) {
			first = -1;
		} else {
			setNextPointer(prev, -1);
		}
		deleteElement(idx);
		size--;
		modCount++;
		return element;
	}

	/**
	 * Unlinks non-null node x.
	 */
	private T unlink(final int idx) {
		final T element = serializer.getRandomAccess(idx);
		final int next = getNextPointer(idx);
		final int prev = getPrevPointer(idx);

		if (prev == -1) {
			first = next;
		} else {
			setNextPointer(prev, next);
		}

		if (next == -1) {
			last = prev;
		} else {
			setPrevPointer(next, prev);
		}

		deleteElement(idx);
		size--;
		modCount++;
		return element;
	}

	@Override
	public void addFirst(final T e) {
		linkFirst(e);

	}

	@Override
	public void addLast(final T e) {
		linkLast(e);

	}

	/**
	 * Appends the specified element to the end of this list.
	 *
	 * <p>
	 * This method is equivalent to {@link #addLast}.
	 *
	 * @param e element to be appended to this list
	 * @return {@code true} (as specified by {@link Collection#add})
	 */
	@Override
	public boolean add(final T e) {
		linkLast(e);
		return true;
	}

	/**
	 * Inserts the specified element at the specified position in this list.
	 * Shifts the element currently at that position (if any) and any
	 * subsequent elements to the right (adds one to their indices).
	 *
	 * @param index index at which the specified element is to be inserted
	 * @param element element to be inserted
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 */
	@Override
	public void add(final int index, final T element) {
		checkPositionIndex(index);

		if (index == size) {
			linkLast(element);
		} else {
			linkBefore(element, node(index));
		}
	}

	@Override
	public boolean addAll(final Collection<? extends T> c) {
		return addAll(size, c);
	}

	/**
	 * Inserts all of the elements in the specified collection into this
	 * list, starting at the specified position. Shifts the element
	 * currently at that position (if any) and any subsequent elements to
	 * the right (increases their indices). The new elements will appear
	 * in the list in the order that they are returned by the
	 * specified collection's iterator.
	 *
	 * @param index index at which to insert the first element
	 *            from the specified collection
	 * @param c collection containing elements to be added to this list
	 * @return {@code true} if this list changed as a result of the call
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 * @throws NullPointerException if the specified collection is null
	 */
	@Override
	public boolean addAll(final int index, final Collection<? extends T> c) {
		checkPositionIndex(index);

		final Object[] a = c.toArray();
		final int numNew = a.length;
		if (numNew == 0) return false;

		int pred;
		int succ;
		if (index == size) {
			succ = -1;
			pred = last;
		} else {
			succ = index;
			pred = getPrevPointer(succ);
		}

		for (final Object o : a) {
			@SuppressWarnings("unchecked")
			final T e = (T) o;
			final int newNode = setFirstFree(e);
			setPrevPointer(newNode, pred);
			setNextPointer(newNode, -1);
			if (pred == -1) {
				first = newNode;
			} else {
				setNextPointer(pred, newNode);
			}
			pred = newNode;
		}

		if (succ == -1) {
			last = pred;
		} else {
			setNextPointer(pred, succ);
			setPrevPointer(succ, pred);
		}

		size += numNew;
		modCount++;
		return true;
	}

	@Override
	public boolean offerFirst(final T e) {
		if (isFull()) return false;
		addFirst(e);
		return true;
	}

	@Override
	public boolean offerLast(final T e) {
		if (isFull()) return false;
		addLast(e);
		return true;
	}

	private boolean isFull() {
		return size >= serializer.getMaxElementCount();
	}

	@Override
	public boolean remove(final Object o) {
		try {
			@SuppressWarnings({ "unchecked", "restriction" })
			final T compare = (T) UnsafeHelper.getUnsafe().allocateInstance(baseClass);
			if (o == null) {
				for (int x = first; x != -1; x = getNextPointer(x)) {
					if (serializer.getRandomAccess(compare, x) == null) {
						unlink(x);
						return true;
					}
				}
			} else {
				for (int x = first; x != -1; x = getNextPointer(x)) {
					if (o.equals(serializer.getRandomAccess(compare, x))) {
						unlink(x);
						return true;
					}
				}
			}
		} catch (final InstantiationException e) {
			throw new UndeclaredThrowableException(e);
		}
		return false;
	}

	/**
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 */
	@Override
	public T remove(final int index) {
		checkElementIndex(index);
		return unlink(node(index));
	}

	@Override
	public void clear() {
		serializer.clear();
		firstFree = 0;
		first = last = -1;
		size = 0;
		modCount++;
	}

	/**
	 * @throws NoSuchElementException if this list is empty
	 */
	@Override
	public T removeFirst() {
		final int f = first;
		if (f == -1) throw new NoSuchElementException();
		return unlinkFirst(f);
	}

	/**
	 * @throws NoSuchElementException if this list is empty
	 */
	@Override
	public T removeLast() {
		final int l = last;
		if (l == -1) throw new NoSuchElementException();
		return unlinkLast(l);
	}

	@Override
	public T pollFirst() {
		final int f = first;
		return (f == -1) ? null : unlinkFirst(f);
	}

	@Override
	public T pollLast() {
		final int l = last;
		return (l == -1) ? null : unlinkLast(l);
	}

	/**
	 * Returns the element at the specified position in this list.
	 *
	 * @param index index of the element to return
	 * @return the element at the specified position in this list
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 */
	@Override
	public T get(final int index) {
		checkElementIndex(index);
		return serializer.getRandomAccess(node(index));
	}

	/**
	 * @throws NoSuchElementException if this list is empty
	 */
	@Override
	public T getFirst() {
		final int f = first;
		if (f == -1) throw new NoSuchElementException();
		return serializer.getRandomAccess(f);
	}

	/**
	 * @throws NoSuchElementException if this list is empty
	 */
	@Override
	public T getLast() {
		final int l = last;
		if (l == -1) throw new NoSuchElementException();
		return serializer.getRandomAccess(l);
	}

	/**
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 */
	@Override
	public T set(final int index, final T element) {
		checkElementIndex(index);
		final int x = node(index);
		final T oldVal = serializer.getRandomAccess(x);
		serializer.setRandomAccess(x, element);
		return oldVal;
	}

	@Override
	public T peekFirst() {
		final int f = first;
		return (f == -1) ? null : serializer.getRandomAccess(f);
	}

	@Override
	public T peekLast() {
		final int l = last;
		return (l == -1) ? null : serializer.getRandomAccess(l);
	}

	@Override
	public boolean removeFirstOccurrence(final Object o) {
		return remove(o);
	}

	@Override
	public boolean removeLastOccurrence(final Object o) {
		try {
			@SuppressWarnings({ "unchecked", "restriction" })
			final T compare = (T) UnsafeHelper.getUnsafe().allocateInstance(baseClass);
			if (o == null) {
				for (int x = last; x != -1; x = getPrevPointer(x)) {
					if (serializer.getRandomAccess(compare, x) == null) {
						unlink(x);
						return true;
					}
				}
			} else {
				for (int x = last; x != -1; x = getPrevPointer(x)) {
					if (o.equals(serializer.getRandomAccess(compare, x))) {
						unlink(x);
						return true;
					}
				}
			}
		} catch (final InstantiationException e) {
			throw new UndeclaredThrowableException(e);
		}
		return false;
	}

	@Override
	public boolean offer(final T e) {
		if (isFull()) return false;
		return add(e);
	}

	@Override
	public T remove() {
		return removeFirst();
	}

	@Override
	public T poll() {
		final int f = first;
		return (f == -1) ? null : unlinkFirst(f);
	}

	/**
	 * @throws NoSuchElementException if this list is empty
	 */
	@Override
	public T element() {
		return getFirst();
	}

	@Override
	public T peek() {
		final int f = first;
		return (f == -1) ? null : serializer.getRandomAccess(f);
	}

	@Override
	public void push(final T e) {
		addFirst(e);
	}

	@Override
	public T pop() {
		return removeFirst();
	}

	/**
	 * Returns the (non-null) Node at the specified element index.
	 *
	 * @param index
	 * @return
	 */
	int node(final int index) {
		if (index < (size >> 1)) {
			int x = first;
			for (long l = 0; l < index; l++) {
				x = getNextPointer(x);
			}
			return x;
		} else {
			int x = last;
			for (int i = size - 1; i > index; i--) {
				x = getPrevPointer(x);
			}
			return x;
		}
	}

	@Override
	public int indexOf(final Object o) {
		try {
			@SuppressWarnings({ "unchecked", "restriction" })
			final T compare = (T) UnsafeHelper.getUnsafe().allocateInstance(baseClass);
			int index = 0;
			if (o == null) {
				for (int x = first; x != -1; x = getNextPointer(x)) {
					if (serializer.getRandomAccess(compare, x) == null) return index;
					index++;
				}
			} else {
				for (int x = first; x != -1; x = getNextPointer(x)) {
					if (o.equals(serializer.getRandomAccess(compare, x))) return index;
					index++;
				}
			}
		} catch (final InstantiationException e) {
			throw new UndeclaredThrowableException(e);
		}
		return -1;
	}

	@Override
	public boolean contains(final Object o) {
		return indexOf(o) != -1;
	}

	@Override
	public int lastIndexOf(final Object o) {
		try {
			@SuppressWarnings({ "unchecked", "restriction" })
			final T compare = (T) UnsafeHelper.getUnsafe().allocateInstance(baseClass);
			int index = size;
			if (o == null) {
				for (int x = last; x != -1; x = getPrevPointer(x)) {
					index--;
					if (serializer.getRandomAccess(compare, x) == null) return index;
				}
			} else {
				for (int x = last; x != -1; x = getPrevPointer(x)) {
					index--;
					if (o.equals(serializer.getRandomAccess(compare, x))) return index;
				}
			}
		} catch (final InstantiationException e) {
			throw new UndeclaredThrowableException(e);
		}
		return -1;
	}

	@Override
	public Iterator<T> descendingIterator() {
		return new DescendingIterator();
	}

	/**
	 * @throws IndexOutOfBoundsException {@inheritDoc}
	 */
	@Override
	public ListIterator<T> listIterator(final int index) {
		checkPositionIndex(index);
		return new ListItr(index);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public Object[] toArray() {
		final Object[] result = new Object[size];
		int i = 0;
		for (int x = first; x != -1; x = getNextPointer(x)) {
			result[i++] = serializer.getRandomAccess(x);
		}
		return result;
	}

	/**
	 * Returns an array containing all of the elements in this list in
	 * proper sequence (from first to last element); the runtime type of
	 * the returned array is that of the specified array. If the list fits
	 * in the specified array, it is returned therein. Otherwise, a new
	 * array is allocated with the runtime type of the specified array and
	 * the size of this list.
	 *
	 * <p>
	 * If the list fits in the specified array with room to spare (i.e.,
	 * the array has more elements than the list), the element in the array
	 * immediately following the end of the list is set to {@code null}.
	 * (This is useful in determining the length of the list <i>only</i> if
	 * the caller knows that the list does not contain any null elements.)
	 *
	 * <p>
	 * Like the {@link #toArray()} method, this method acts as bridge between
	 * array-based and collection-based APIs. Further, this method allows
	 * precise control over the runtime type of the output array, and may,
	 * under certain circumstances, be used to save allocation costs.
	 *
	 * <p>
	 * Suppose {@code x} is a list known to contain only strings.
	 * The following code can be used to dump the list into a newly
	 * allocated array of {@code String}:
	 *
	 * <pre>
	 * String[] y = x.toArray(new String[0]);
	 * </pre>
	 *
	 * Note that {@code toArray(new Object[0])} is identical in function to
	 * {@code toArray()}.
	 *
	 * @param a the array into which the elements of the list are to
	 *            be stored, if it is big enough; otherwise, a new array of the
	 *            same runtime type is allocated for this purpose.
	 * @return an array containing the elements of the list
	 * @throws ArrayStoreException if the runtime type of the specified array
	 *             is not a supertype of the runtime type of every element in
	 *             this list
	 * @throws NullPointerException if the specified array is null
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <X> X[] toArray(X[] a) {
		if (a.length < size) {
			a = (X[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
		}
		int i = 0;
		final Object[] result = a;
		for (int x = first; x != -1; x = getNextPointer(x)) {
			result[i++] = serializer.getRandomAccess(x);
		}

		if (a.length > size) {
			a[size] = null;
		}

		return a;
	}

	private class ListItr implements ListIterator<T> {
		private int	lastReturned		= -1;
		private int	next				= -1;
		private int	nextIndex;
		private int	expectedModCount	= modCount;

		ListItr(final int index) {
			// assert isPositionIndex(index);
			next = (index == size) ? null : node(index);
			nextIndex = index;
		}

		@Override
		public boolean hasNext() {
			return nextIndex < size;
		}

		@Override
		public T next() {
			checkForComodification();
			if (!hasNext()) throw new NoSuchElementException();

			lastReturned = next;
			next = getNextPointer(next);
			nextIndex++;
			return serializer.getRandomAccess(lastReturned);
		}

		@Override
		public boolean hasPrevious() {
			return nextIndex > 0;
		}

		@Override
		public T previous() {
			checkForComodification();
			if (!hasPrevious()) throw new NoSuchElementException();

			lastReturned = next = (next == -1) ? last : getPrevPointer(next);
			nextIndex--;
			return serializer.getRandomAccess(lastReturned);
		}

		@Override
		public int nextIndex() {
			return nextIndex;
		}

		@Override
		public int previousIndex() {
			return nextIndex - 1;
		}

		@Override
		public void remove() {
			checkForComodification();
			if (lastReturned == -1) throw new IllegalStateException();

			final int lastNext = getNextPointer(lastReturned);
			unlink(lastReturned);
			if (next == lastReturned) {
				next = lastNext;
			} else {
				nextIndex--;
			}
			expectedModCount++;
		}

		@Override
		public void set(final T e) {
			if (lastReturned == -1) throw new IllegalStateException();
			checkForComodification();
			serializer.setRandomAccess(lastReturned, e);
		}

		@Override
		public void add(final T e) {
			checkForComodification();
			lastReturned = -1;
			if (next == -1) {
				linkLast(e);
			} else {
				linkBefore(e, next);
			}
			nextIndex++;
			expectedModCount++;
		}

		@Override
		public void forEachRemaining(final Consumer<? super T> action) {
			Objects.requireNonNull(action);
			while (modCount == expectedModCount && nextIndex < size) {
				action.accept(serializer.getRandomAccess(next));
				lastReturned = next;
				next = getNextPointer(next);
				nextIndex++;
			}
			checkForComodification();
		}

		final void checkForComodification() {
			if (modCount != expectedModCount) throw new ConcurrentModificationException();
		}
	}

	/**
	 * Adapter to provide descending iterators via ListItr.previous
	 */
	private class DescendingIterator implements Iterator<T> {
		private final ListItr itr = new ListItr(size());

		@Override
		public boolean hasNext() {
			return itr.hasPrevious();
		}

		@Override
		public T next() {
			if (!itr.hasNext()) throw new NoSuchElementException();
			return itr.previous();
		}

		@Override
		public void remove() {
			itr.remove();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		serializer.destroy();
		super.finalize();
	}
}
