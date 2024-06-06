package org.nimdaved.util.yaolog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to suppress AOP logging for the method parameters and return values
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface HideLogElements {
  /**
   * Disables AOP logging of method's return value
   * @return true if disabled otherwise false
   */
  boolean hideReturnValue() default true;

  /**
   * Disables AOP logging of method's parameters
   * @return true if disabled otherwise false
   */
  boolean hideParameters() default false;
}
