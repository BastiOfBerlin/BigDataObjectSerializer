package de.tub.cit.slist.bdos.test.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import de.tub.cit.slist.bdos.annotation.FixedLength;

public class FixedLengthClass implements RandomlyInitializable, java.io.Serializable {
	private static final long serialVersionUID = -3926475491171031058L;

	private static final int ELEMENT_COUNT = 10;

	@FixedLength(ELEMENT_COUNT)
	private String fixedString;

	@FixedLength(ELEMENT_COUNT)
	private int[] fixedIntArray = new int[ELEMENT_COUNT];

	@FixedLength(ELEMENT_COUNT)
	private Integer[] fixedIntegerArray = new Integer[ELEMENT_COUNT];

	@FixedLength(ELEMENT_COUNT)
	private List<Integer> fixedIntegerList = new ArrayList<>(ELEMENT_COUNT);

	public FixedLengthClass() {

	}

	public FixedLengthClass(final Random r) {
		super();
		randomInit(r);
	}

	public FixedLengthClass(final String fixedString, final int[] fixedIntArray, final Integer[] fixedIntegerArray, final ArrayList<Integer> fixedIntegerList) {
		super();
		this.fixedString = fixedString;
		this.fixedIntArray = fixedIntArray;
		this.fixedIntegerArray = fixedIntegerArray;
		this.fixedIntegerList = fixedIntegerList;
	}

	@Override
	public void randomInit(final Random r) {
		fixedString = r.ints(48, 122) //
				.filter(i -> (i < 57 || i > 65) && (i < 90 || i > 97)) //
				.limit(ELEMENT_COUNT) //
				.mapToObj(i -> (char) i) //
				.collect(StringBuilder::new, StringBuilder::append, StringBuilder::append) //
				.toString();
		for (int i = 0; i < ELEMENT_COUNT; i++) {
			fixedIntArray[i] = r.nextInt();
		}
		for (int i = 0; i < ELEMENT_COUNT; i++) {
			fixedIntegerArray[i] = r.nextInt();
		}
		for (int i = 0; i < ELEMENT_COUNT; i++) {
			fixedIntegerList.add(r.nextInt());
		}
	}

	public String getFixedString() {
		return fixedString;
	}

	public void setFixedString(final String fixedString) {
		this.fixedString = fixedString;
	}

	public int[] getFixedIntArray() {
		return fixedIntArray;
	}

	public void setFixedIntArray(final int[] fixedIntArray) {
		this.fixedIntArray = fixedIntArray;
	}

	public Integer[] getFixedIntegerArray() {
		return fixedIntegerArray;
	}

	public void setFixedIntegerArray(final Integer[] fixedIntegerArray) {
		this.fixedIntegerArray = fixedIntegerArray;
	}

	public List<Integer> getFixedIntegerList() {
		return fixedIntegerList;
	}

	public void setFixedIntegerList(final List<Integer> fixedIntegerList) {
		this.fixedIntegerList = fixedIntegerList;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(fixedIntArray);
		result = prime * result + Arrays.hashCode(fixedIntegerArray);
		result = prime * result + ((fixedIntegerList == null) ? 0 : fixedIntegerList.hashCode());
		result = prime * result + ((fixedString == null) ? 0 : fixedString.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final FixedLengthClass other = (FixedLengthClass) obj;
		if (!Arrays.equals(fixedIntArray, other.fixedIntArray)) return false;
		if (!Arrays.equals(fixedIntegerArray, other.fixedIntegerArray)) return false;
		if (fixedIntegerList == null) {
			if (other.fixedIntegerList != null) return false;
		} else if (!fixedIntegerList.equals(other.fixedIntegerList)) return false;
		if (fixedString == null) {
			if (other.fixedString != null) return false;
		} else if (!fixedString.equals(other.fixedString)) return false;
		return true;
	}

	@Override
	public String toString() {
		return "FixedLengthClass [fixedString=" + fixedString + ", fixedIntArray=" + Arrays.toString(fixedIntArray) + ", fixedIntegerArray="
				+ Arrays.toString(fixedIntegerArray) + ", fixedIntegerList=" + fixedIntegerList + "]";
	}

}
