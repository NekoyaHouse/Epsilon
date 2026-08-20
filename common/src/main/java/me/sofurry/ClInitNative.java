package me.sofurry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that limits native obfuscation to only the {@code <clinit>} method of the annotated class.
 * All other methods in the class will be left untouched.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface ClInitNative {
}
