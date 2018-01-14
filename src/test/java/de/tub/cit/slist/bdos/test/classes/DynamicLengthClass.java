package de.tub.cit.slist.bdos.test.classes;

import java.util.Random;

public class DynamicLengthClass implements RandomlyInitializable, java.io.Serializable {
	private static final long serialVersionUID = -3926475491171031058L;

	private static final int	MIN_ELEMENT_COUNT	= 8;
	private static final int	MAX_ELEMENT_COUNT	= 16;

	private String string;

	public DynamicLengthClass() {

	}

	public DynamicLengthClass(final Random r) {
		super();
		randomInit(r);
	}

	public DynamicLengthClass(final String string) {
		super();
		this.string = string;
	}

	@Override
	public void randomInit(final Random r) {
		string = r.ints(48, 122) //
				.filter(i -> (i < 57 || i > 65) && (i < 90 || i > 97)) //
				.limit(r.nextInt(MAX_ELEMENT_COUNT - MIN_ELEMENT_COUNT) + MIN_ELEMENT_COUNT) //
				.mapToObj(i -> (char) i) //
				.collect(StringBuilder::new, StringBuilder::append, StringBuilder::append) //
				.toString();
	}

	public String getFixedString() {
		return string;
	}

	public void setFixedString(final String fixedString) {
		this.string = fixedString;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((string == null) ? 0 : string.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final DynamicLengthClass other = (DynamicLengthClass) obj;
		if (string == null) {
			if (other.string != null) return false;
		} else if (!string.equals(other.string)) return false;
		return true;
	}

	@Override
	public String toString() {
		return "DynamicLengthClass [string=" + string + "]";
	}

}
