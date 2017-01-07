package de.tub.cit.slist.bdos.metadata;

import java.util.Arrays;

public class ClassMetadata {
	/** total length of object. intended redundancy. */
	private final long				length;
	private final FieldMetadata[]	fields;

	public ClassMetadata(final long length, final FieldMetadata[] fields) {
		super();
		this.length = length;
		this.fields = fields;
	}

	/**
	 * Sets the {@link FieldMetadata#getSerializedOffset() serializedOffset} of each {@link FieldMetadata} child.
	 */
	public void calcSerializedOffsets() {
		long offset = 0;
		for (final FieldMetadata field : fields) {
			field.setSerializedOffset(offset);
			offset += field.getLength();
		}
	}

	/**
	 * @return total length of all fields
	 */
	public long getLength() {
		return length;
	}

	public FieldMetadata[] getFields() {
		return fields;
	}

	@Override
	public String toString() {
		return "ClassMetadata [length=" + length + ", fields=" + Arrays.toString(fields) + "]";
	}

}