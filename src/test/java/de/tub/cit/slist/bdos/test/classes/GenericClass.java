package de.tub.cit.slist.bdos.test.classes;

import java.util.Random;

public class GenericClass implements RandomlyInitializable, java.io.Serializable {
	private static final long serialVersionUID = 6610820233895673837L;

	private Integer				integer;
	private PrimitiveClass		primitives;
	private FixedLengthClass	fixedLength;

	public GenericClass() {
		super();
	}

	public GenericClass(final Random r) {
		this();
		randomInit(r);
	}

	public GenericClass(final Integer integer, final PrimitiveClass primitives, final FixedLengthClass fixedLength) {
		super();
		this.integer = integer;
		this.primitives = primitives;
		this.fixedLength = fixedLength;
	}

	@Override
	public void randomInit(final Random r) {
		this.integer = r.nextInt();
		this.primitives = new PrimitiveClass(r);
		this.fixedLength = new FixedLengthClass(r);
	}

	public Integer getInteger() {
		return integer;
	}

	public void setInteger(final Integer integer) {
		this.integer = integer;
	}

	public PrimitiveClass getPrimitives() {
		return primitives;
	}

	public void setPrimitives(final PrimitiveClass primitives) {
		this.primitives = primitives;
	}

	public FixedLengthClass getFixedLength() {
		return fixedLength;
	}

	public void setFixedLength(final FixedLengthClass fixedLength) {
		this.fixedLength = fixedLength;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fixedLength == null) ? 0 : fixedLength.hashCode());
		result = prime * result + ((integer == null) ? 0 : integer.hashCode());
		result = prime * result + ((primitives == null) ? 0 : primitives.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final GenericClass other = (GenericClass) obj;
		if (fixedLength == null) {
			if (other.fixedLength != null) return false;
		} else if (!fixedLength.equals(other.fixedLength)) return false;
		if (integer == null) {
			if (other.integer != null) return false;
		} else if (!integer.equals(other.integer)) return false;
		if (primitives == null) {
			if (other.primitives != null) return false;
		} else if (!primitives.equals(other.primitives)) return false;
		return true;
	}

	@Override
	public String toString() {
		return "GenericClass [integer=" + integer + ", primitives=" + primitives + ", fixedLength=" + fixedLength + "]";
	}

}
