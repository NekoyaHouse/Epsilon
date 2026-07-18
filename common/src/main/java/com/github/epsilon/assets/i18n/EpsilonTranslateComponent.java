package com.github.epsilon.assets.i18n;

/**
 * Static factory that creates {@link TranslateComponent} instances
 * with the "epsilon" prefix. Used for Epsilon's own i18n keys.
 */
public class EpsilonTranslateComponent {

    private static final String PREFIX = "epsilon";

    public static TranslateComponent create(String prefix, String suffix) {
        return DefaultTranslateComponent.create(PREFIX + "." + prefix + "." + suffix);
    }

}

