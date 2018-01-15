package de.tub.cit.slist.bdos.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tub.cit.slist.bdos.annotation.FixedLength;
import de.tub.cit.slist.bdos.metadata.ClassMetadata;
import de.tub.cit.slist.bdos.metadata.FieldMetadata;
import de.tub.cit.slist.bdos.metadata.FieldType;

public class SerializerHelper {

	private SerializerHelper() {
	}

	/**
	 * get metadata for <code>baseclazz</code> and store it in <code>target</code>
	 *
	 * @param baseclazz
	 * @param classMetadataMap
	 * @return added class metadata
	 */
	@SuppressWarnings("restriction")
	public static Map<Class<?>, ClassMetadata> acquireClassMetadata(final Class<?> baseclazz, final Map<Class<?>, ClassMetadata> classMetadataMap) {
		final Map<Class<?>, ClassMetadata> addedClasses = new HashMap<>();
		if (classMetadataMap.containsKey(baseclazz)) return addedClasses;

		Class<?> clazz = baseclazz;
		final List<FieldMetadata> fields = new ArrayList<>();
		long fieldLength = 0;
		long totalLength = 0;
		if (clazz.isPrimitive()) {
			totalLength = UnsafeHelper.PRIMITIVE_LENGTHS.get(clazz);
		} else {
			do {
				for (final Field f : clazz.getDeclaredFields()) {
					if (!Modifier.isStatic(f.getModifiers())) {
						final Class<?> classType = f.getType();
						final FieldMetadata fieldMetadata = new FieldMetadata();
						fieldMetadata.setFieldName(f.getName());
						fieldMetadata.setOffset(UnsafeHelper.getUnsafe().objectFieldOffset(f));
						fieldMetadata.setClazz(classType);

						if (classType == boolean.class) {
							fieldMetadata.setType(FieldType.BOOL);
							fieldLength = UnsafeHelper.BOOLEAN_FIELD_SIZE;
						} else if (classType == byte.class) {
							fieldMetadata.setType(FieldType.BYTE);
							fieldLength = UnsafeHelper.BYTE_FIELD_SIZE;
						} else if (classType == char.class) {
							fieldMetadata.setType(FieldType.CHAR);
							fieldLength = UnsafeHelper.CHAR_FIELD_SIZE;
						} else if (classType == double.class) {
							fieldMetadata.setType(FieldType.DOUBLE);
							fieldLength = UnsafeHelper.DOUBLE_FIELD_SIZE;
						} else if (classType == float.class) {
							fieldMetadata.setType(FieldType.FLOAT);
							fieldLength = UnsafeHelper.FLOAT_FIELD_SIZE;
						} else if (classType == int.class) {
							fieldMetadata.setType(FieldType.INT);
							fieldLength = UnsafeHelper.INT_FIELD_SIZE;
						} else if (classType == long.class) {
							fieldMetadata.setType(FieldType.LONG);
							fieldLength = UnsafeHelper.LONG_FIELD_SIZE;
						} else if (classType == short.class) {
							fieldMetadata.setType(FieldType.SHORT);
							fieldLength = UnsafeHelper.SHORT_FIELD_SIZE;
						} else if (classType == String.class) {
							final FixedLength fixedLength = f.getAnnotation(FixedLength.class);
							if (fixedLength != null) {
								fieldMetadata.setType(FieldType.STRING_FIXED);
								fieldLength = (long) fixedLength.value() * UnsafeHelper.CHAR_FIELD_SIZE + UnsafeHelper.INT_FIELD_SIZE;
								fieldMetadata.setElements(fixedLength.value());
							} else {
								fieldMetadata.setType(FieldType.STRING);
								fieldLength = UnsafeHelper.LONG_FIELD_SIZE;
							}
						} else if (classType.isArray()) {
							final Class<?> subtype = classType.getComponentType();
							if (subtype.isInterface()) throw new UnsupportedOperationException("No Interface Array allowed.");
							fieldMetadata.setClazz(subtype);
							final Map<Class<?>, ClassMetadata> subtypeMetadata = acquireClassMetadata(subtype, classMetadataMap);
							addedClasses.putAll(subtypeMetadata);

							final FixedLength fixedLength = f.getAnnotation(FixedLength.class);
							if (fixedLength != null) {
								fieldMetadata.setType(FieldType.ARRAY_FIXED);
								fieldMetadata.setElements(fixedLength.value());
								fieldLength = fixedLength.value() * classMetadataMap.get(subtype).getLength() + UnsafeHelper.INT_FIELD_SIZE;
							} else {
								fieldMetadata.setType(FieldType.ARRAY);
								fieldLength = UnsafeHelper.LONG_FIELD_SIZE;
							}
						} else if (Collection.class.isAssignableFrom(classType)) {
							if (classType.isInterface()) {
								if (classType == List.class) {
									// default implementation for Lists
									fieldMetadata.setCollectionClass(ArrayList.class);
								} else if (classType == Map.class) {
									// default implementation for Maps
									fieldMetadata.setCollectionClass(HashMap.class);
								} else if (classType == Set.class) {
									// default implementation for Sets
									fieldMetadata.setCollectionClass(HashSet.class);
								} else
									throw new UnsupportedOperationException("Collection types must either be non-Interfaces, or of type List|Map|Set.");
							} else {
								fieldMetadata.setCollectionClass(classType);
							}
							final Class<?> subtype = (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
							fieldMetadata.setClazz(subtype);
							final Map<Class<?>, ClassMetadata> subtypeMetadata = acquireClassMetadata(subtype, classMetadataMap);
							addedClasses.putAll(subtypeMetadata);

							final FixedLength fixedLength = f.getAnnotation(FixedLength.class);
							if (fixedLength != null) {
								fieldMetadata.setType(FieldType.COLLECTION_FIXED);
								fieldMetadata.setElements(fixedLength.value());
								fieldLength = fixedLength.value() * classMetadataMap.get(subtype).getLength() + UnsafeHelper.INT_FIELD_SIZE;
							} else {
								fieldMetadata.setType(FieldType.COLLECTION_FIXED);
								fieldLength = UnsafeHelper.LONG_FIELD_SIZE;
							}
						} else {
							fieldMetadata.setType(FieldType.OBJECT);
							final Map<Class<?>, ClassMetadata> subtypeMetadata = acquireClassMetadata(classType, classMetadataMap);
							fieldLength = classMetadataMap.get(classType).getLength();
							addedClasses.putAll(subtypeMetadata);
						}
						fieldLength += UnsafeHelper.BOOLEAN_FIELD_SIZE; // isNull indicator
						totalLength += fieldLength;
						fieldMetadata.setLength(fieldLength);
						fields.add(fieldMetadata);
					}
				}
			} while ((clazz = clazz.getSuperclass()) != null);
			Collections.sort(fields); // order by offset
		}
		final ClassMetadata classMeta = new ClassMetadata(totalLength, fields.toArray(new FieldMetadata[fields.size()]));
		classMeta.calcSerializedOffsets();
		addedClasses.put(baseclazz, classMeta);
		classMetadataMap.putAll(addedClasses);
		return addedClasses;
	}

	public static Map<Class<?>, ClassMetadata> acquireWrapperAndPrimitivesMetadata() {
		final Map<Class<?>, ClassMetadata> wrapperAndPrimitivesMetadata = new HashMap<>(29);
		acquireClassMetadata(Boolean.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Byte.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Character.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Double.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Float.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Integer.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Long.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Short.class, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Boolean.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Byte.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Character.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Double.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Float.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Integer.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Long.TYPE, wrapperAndPrimitivesMetadata);
		acquireClassMetadata(Short.TYPE, wrapperAndPrimitivesMetadata);
		return wrapperAndPrimitivesMetadata;
	}

}
