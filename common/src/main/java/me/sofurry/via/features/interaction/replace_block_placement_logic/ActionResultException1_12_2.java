package me.sofurry.via.features.interaction.replace_block_placement_logic;

import net.minecraft.world.InteractionResult;

public final class ActionResultException1_12_2 extends RuntimeException {

    private final InteractionResult actionResult;

    public ActionResultException1_12_2(final InteractionResult actionResult) {
        this.actionResult = actionResult;
    }

    public InteractionResult getActionResult() {
        return this.actionResult;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

}
