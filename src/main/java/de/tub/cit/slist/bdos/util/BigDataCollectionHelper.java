package de.tub.cit.slist.bdos.util;

import de.tub.cit.slist.bdos.OffHeapSerializer;

public class BigDataCollectionHelper {

	/** offset of next pointer within metadata */
	public static final int	NEXT_OFFSET	= 1;
	/** offset of prev pointer within metadata */
	public static final int	PREV_OFFSET	= 5;

	public static <T extends java.io.Serializable> int getNextPointer(final OffHeapSerializer<T> serializer, final int idx) {
		return getPointer(serializer, idx, NEXT_OFFSET);
	}

	public static <T extends java.io.Serializable> int getPrevPointer(final OffHeapSerializer<T> serializer, final int idx) {
		return getPointer(serializer, idx, PREV_OFFSET);
	}

	public static <T extends java.io.Serializable> int getPointer(final OffHeapSerializer<T> serializer, final int idx, final int pointerOffset) {
		return serializer.getInt(idx, pointerOffset);
	}

	public static <T extends java.io.Serializable> void setNextPointer(final OffHeapSerializer<T> serializer, final int idx, final int pointer) {
		setPointer(serializer, idx, NEXT_OFFSET, pointer);
	}

	public static <T extends java.io.Serializable> void setPrevPointer(final OffHeapSerializer<T> serializer, final int idx, final int pointer) {
		setPointer(serializer, idx, PREV_OFFSET, pointer);
	}

	public static <T extends java.io.Serializable> void setPointer(final OffHeapSerializer<T> serializer, final int idx, final int pointerOffset,
			final int pointer) {
		serializer.putInt(idx, pointerOffset, pointer);
	}
}
