package de.tub.cit.slist.bdos.metadata;

import java.util.Collection;

import de.tub.cit.slist.bdos.annotation.FixedLength;

/**
 * Enumeration identifying different field types of objects.
 */
public enum FieldType {
	// Primitives
	BOOL, //
	BYTE, //
	CHAR, //
	DOUBLE, //
	FLOAT, //
	INT, //
	LONG, //
	SHORT, //
	// Objects
	STRING, //
	STRING_FIXED, //
	ARRAY, //
	ARRAY_FIXED, //
	COLLECTION, //
	COLLECTION_FIXED, //
	OBJECT, //
	;

	/**
	 * Converts the Class parameter into an enumeration value with the assumption of no {@link FixedLength} annotation present.
	 * 
	 * @param clazz
	 * @return {@link FieldType}
	 *
	 * @see #fromClass(Class, boolean)
	 */
	public static FieldType fromClass(final Class<?> clazz) {
		return fromClass(clazz, false);
	}

	/**
	 * Converts the Class parameter into an enumeration value.
	 * 
	 * @param clazz
	 * @param fixedLength is {@link FixedLength} annotation present on field
	 * @return {@link FieldType}
	 */
	public static FieldType fromClass(final Class<?> clazz, final boolean fixedLength) {
		if (clazz == boolean.class)
			return FieldType.BOOL;
		else if (clazz == byte.class)
			return FieldType.BYTE;
		else if (clazz == char.class)
			return FieldType.CHAR;
		else if (clazz == double.class)
			return FieldType.DOUBLE;
		else if (clazz == float.class)
			return FieldType.FLOAT;
		else if (clazz == int.class)
			return FieldType.INT;
		else if (clazz == long.class)
			return FieldType.LONG;
		else if (clazz == short.class)
			return FieldType.SHORT;
		else if (clazz == String.class)
			return fixedLength ? FieldType.STRING_FIXED : FieldType.STRING;
		else if (clazz.isArray())
			return fixedLength ? FieldType.ARRAY_FIXED : FieldType.ARRAY;
		else if (Collection.class.isAssignableFrom(clazz))
			return fixedLength ? FieldType.COLLECTION_FIXED : FieldType.COLLECTION;
		else
			return FieldType.OBJECT;
	}
}