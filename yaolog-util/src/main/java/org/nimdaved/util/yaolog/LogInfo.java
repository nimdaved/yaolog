package org.nimdaved.util.yaolog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation marker to indicate info log level for AOP method logging
 */

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface LogInfo {
	/**
	 * Interface marker for future use cases. Currently unused
	 * @return specified value
	 */
	String value() default "";
}
