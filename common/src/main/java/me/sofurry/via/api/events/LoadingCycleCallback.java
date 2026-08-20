package me.sofurry.via.api.events;

/**
 * This event is fired in various loading stages of ViaVersion. See {@link LoadingCycle} for more information.
 */
public interface LoadingCycleCallback {

    enum LoadingCycle {

        /**
         * Before ViaVersion is loaded.
         */
        PRE_VIAVERSION_LOAD,

        /**
         * After ViaVersion is loaded.
         */
        POST_VIAVERSION_LOAD,

        /**
         * Final stage after everything is loaded.
         */
        FINAL_LOAD,

        /**
         * After the game is loaded.
         */
        POST_GAME_LOAD

    }

    void onLoadCycle(final LoadingCycle cycle);

}
