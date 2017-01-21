package de.tub.cit.slist.bdos.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>
 * Annotation used to mark Strings, Arrays, Collections as fixed-length.<br />
 * Fixed length fields are stored "inline" within the main memory section. In contrast, variable-length fields are stored in the dynamic section. Accesses to
 * the latter are considered performing worse.
 * </p>
 * <p>
 * During serialization, the given number of elements (for Strings, that means characters) will be stored, no matter how many are present at runtime. As a
 * result, oversized data will be cut and memory footprint is always exactly the value of the annotation.
 * </p>
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface FixedLength {
	/** number of elements to be stored */
	long value();
}
