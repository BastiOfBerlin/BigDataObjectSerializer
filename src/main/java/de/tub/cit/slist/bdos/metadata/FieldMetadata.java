package de.tub.cit.slist.bdos.metadata;

public class FieldMetadata implements Comparable<FieldMetadata> {
	private String		fieldName;
	/** offset within the object */
	private long		offset;
	/** offset in serialized form */
	private long		serializedOffset;
	private long		length;
	/** number of elements */
	private int			elements;
	private FieldType	type;
	/** not-null only for arrays, collections and objects */
	private Class<?>	clazz;

	public FieldMetadata() {

	};

	public FieldMetadata(final String fieldName, final long offset, final long serializedOffset, final long length, final int elements, final FieldType type,
			final Class<?> clazz) {
		super();
		this.fieldName = fieldName;
		this.offset = offset;
		this.serializedOffset = serializedOffset;
		this.elements = elements;
		this.length = length;
		this.type = type;
		this.clazz = clazz;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(final String fieldName) {
		this.fieldName = fieldName;
	}

	public long getOffset() {
		return offset;
	}

	public void setOffset(final long offset) {
		this.offset = offset;
	}

	public long getSerializedOffset() {
		return serializedOffset;
	}

	public void setSerializedOffset(final long serializedOffset) {
		this.serializedOffset = serializedOffset;
	}

	public long getLength() {
		return length;
	}

	public void setLength(final long length) {
		this.length = length;
	}

	public int getElements() {
		return elements;
	}

	public void setElements(final int elements) {
		this.elements = elements;
	}

	public FieldType getType() {
		return type;
	}

	public void setType(final FieldType type) {
		this.type = type;
	}

	public Class<?> getClazz() {
		return clazz;
	}

	public void setClazz(final Class<?> clazz) {
		this.clazz = clazz;
	}

	@Override
	public String toString() {
		return "FieldMetadata [fieldName=" + fieldName + ", offset=" + offset + ", serializedOffset=" + serializedOffset + ", length=" + length + ", type="
				+ type + ", clazz=" + clazz + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final FieldMetadata other = (FieldMetadata) obj;
		if (fieldName == null) {
			if (other.fieldName != null) return false;
		} else if (!fieldName.equals(other.fieldName)) return false;
		return true;
	}

	@Override
	public int compareTo(final FieldMetadata o) {
		return (offset < o.offset ? -1 : (offset == o.offset ? 0 : 1));
	}

}