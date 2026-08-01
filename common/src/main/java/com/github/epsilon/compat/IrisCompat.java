package com.github.epsilon.compat;

import java.util.function.BooleanSupplier;

public final class IrisCompat {

    private static boolean irisPresent;
    private static BooleanSupplier handActiveChecker;
    private static Runnable handOutlineDrainer;

    private IrisCompat() {
    }

    public static void register(boolean present, BooleanSupplier activeChecker, Runnable drainer) {
        irisPresent = present;
        handActiveChecker = activeChecker;
        handOutlineDrainer = drainer;
    }

    public static boolean isHandActive() {
        return irisPresent && handActiveChecker != null && handActiveChecker.getAsBoolean();
    }

    public static void drainHandOutline() {
        if (irisPresent && handOutlineDrainer != null) {
            handOutlineDrainer.run();
        }
    }

}
