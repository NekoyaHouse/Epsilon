package me.sofurry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field that holds the OAuth bearer token. The J2C loader reads this field via JNI to authenticate DLL downloads.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NiurenDEOBF {
}
