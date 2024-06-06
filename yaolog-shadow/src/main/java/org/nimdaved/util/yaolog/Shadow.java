package org.nimdaved.util.yaolog;

/**
 * This class to be excluded and shadowed in client projects.
 * It is placed in separate gradle module to ease exclusion
 */
public final class Shadow {
    /** Shadow value for logging AOP pointcut. Replace this constant with "com....acme" */
    public static final String ROOT_PACKAGE = "com";

    private Shadow() {
    }
}
