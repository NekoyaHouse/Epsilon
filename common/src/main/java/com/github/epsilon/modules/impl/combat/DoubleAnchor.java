package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

public class DoubleAnchor extends Module {

    public static final DoubleAnchor INSTANCE = new DoubleAnchor();

    private DoubleAnchor() {
        super("Double Anchor", Category.COMBAT);
    }

    private final KeybindSetting triggerKey = keybindSetting("Trigger Key", -1);
    private final IntSetting detonateSlot = intSetting("Detonate Slot", 1, 1, 9, 1);
    private final IntSetting placeCps = intSetting("Place CPS", 10, 1, 30, 1);
    private final IntSetting chargeCps = intSetting("Charge CPS", 10, 1, 30, 1);

    private enum Phase {
        IDLE,
        PLACE_ANCHOR,
        CHARGE,
        AIRPLACE,
        CHARGE_2,
        DETONATE,
        CLEANUP
    }

    private Phase phase = Phase.IDLE;
    private int originalSlot = -1;
    private boolean wasKeyDown;
    private int cooldown;

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        int key = triggerKey.getValue();
        if (key == -1) return;

        boolean keyDown = isTriggerKeyDown(key);
        boolean newPress = keyDown && !wasKeyDown;
        wasKeyDown = keyDown;

        if (newPress) {
            if (phase != Phase.IDLE && originalSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
            originalSlot = mc.player.getInventory().getSelectedSlot();
            cooldown = 0;
            phase = Phase.PLACE_ANCHOR;
        }

        if (phase == Phase.IDLE) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        switch (phase) {
            case PLACE_ANCHOR -> doPlaceAnchor();
            case CHARGE -> doCharge();
            case AIRPLACE -> doAirplace();
            case CHARGE_2 -> doCharge2();
            case DETONATE -> doDetonate();
            case CLEANUP -> doCleanup();
            default -> {
            }
        }
    }

    private void doPlaceAnchor() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        if (mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
            phase = Phase.CHARGE;
            return;
        }

        int anchorSlot = findHotbarSlot(Items.RESPAWN_ANCHOR);
        if (anchorSlot == -1) {
            doCleanup();
            return;
        }

        mc.player.getInventory().setSelectedSlot(anchorSlot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        cooldown = humanizedCooldownTicks(placeCps.getValue());

        phase = Phase.CHARGE;
    }

    private void doCharge() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;

        int glowstoneSlot = findHotbarSlot(Items.GLOWSTONE);
        if (glowstoneSlot == -1) {
            doCleanup();
            return;
        }

        mc.player.getInventory().setSelectedSlot(glowstoneSlot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        cooldown = humanizedCooldownTicks(chargeCps.getValue());

        phase = Phase.AIRPLACE;
    }

    private void doAirplace() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;

        int anchorSlot = findHotbarSlot(Items.RESPAWN_ANCHOR);
        if (anchorSlot == -1) {
            doCleanup();
            return;
        }

        mc.player.getInventory().setSelectedSlot(anchorSlot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        phase = Phase.CHARGE_2;
    }

    private void doCharge2() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;

        int glowstoneSlot = findHotbarSlot(Items.GLOWSTONE);
        if (glowstoneSlot == -1) {
            doCleanup();
            return;
        }

        mc.player.getInventory().setSelectedSlot(glowstoneSlot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        cooldown = humanizedCooldownTicks(chargeCps.getValue());

        phase = Phase.DETONATE;
    }

    private void doDetonate() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;

        int slot = detonateSlot.getValue() - 1;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        phase = Phase.CLEANUP;
    }

    private void doCleanup() {
        if (originalSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        resetState();
    }

    private boolean isTriggerKeyDown(int key) {
        return KeybindUtils.isPressed(key);
    }

    private int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    /**
     * Humanized click cooldown: base interval derived from CPS plus ±25% Gaussian
     * jitter, clamped to &gt;=50&nbsp;ms so the resulting tick count is always &gt;=1.
     * Mirrors the pattern used by AxeBreaker so anti-cheat sees a non-uniform click
     * rhythm across our combat automation modules.
     */
    private int humanizedCooldownTicks(int cps) {
        int baseTicks = Math.max(1, 20 / cps);
        double baseMs = baseTicks * 50.0;
        double jitter = baseMs * 0.25 * ThreadLocalRandom.current().nextGaussian();
        double adjustedMs = Math.max(50.0, baseMs + jitter);
        return Math.max(1, (int) Math.round(adjustedMs / 50.0));
    }

    private void resetState() {
        phase = Phase.IDLE;
        originalSlot = -1;
        wasKeyDown = false;
        cooldown = 0;
    }

    public boolean isActive() {
        return isEnabled() && phase != Phase.IDLE;
    }


}
