package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.ModuleDeclaration;
import com.github.epsilon.modules.orchestration.ModuleDispatchMode;
import com.github.epsilon.modules.orchestration.ModulePart;
import com.github.epsilon.modules.orchestration.NodeKey;
import com.github.epsilon.modules.orchestration.Phase;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.PlayerUtils;
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
        setDispatchMode(ModuleDispatchMode.MANAGED);
        part(new CommitPart());
    }

    /**
     * COMMIT：整个状态机都会切换物品栏并调用 {@code useItemOn}，属于外部副作用。
     */
    private final class CommitPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("commit.anchor_cycle"))
                    .phase(Phase.COMMIT)
                    .handler(DoubleAnchor.this::onTick);
        }
    }

    private final KeybindSetting triggerKey = keybindSetting("Trigger Key", -1);
    private final IntSetting detonateSlot = intSetting("Detonate Slot", 1, 1, 9, 1);
    private final IntSetting placeCps = intSetting("Place CPS", 10, 1, 30, 1);
    private final IntSetting chargeCps = intSetting("Charge CPS", 10, 1, 30, 1);

    /** 起爆状态机的内部阶段；与编排层的 {@link com.github.epsilon.modules.orchestration.Phase} 无关。 */
    private enum AnchorPhase {
        IDLE,
        PLACE_ANCHOR,
        CHARGE,
        AIRPLACE,
        CHARGE_2,
        DETONATE,
        CLEANUP
    }

    private AnchorPhase phase = AnchorPhase.IDLE;
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
    private void onTick(PlayerTickEvent.Pre event) {
        int key = triggerKey.getValue();
        if (key == -1) return;

        boolean keyDown = isTriggerKeyDown(key);
        boolean newPress = keyDown && !wasKeyDown;
        wasKeyDown = keyDown;

        if (newPress) {
            if (phase != AnchorPhase.IDLE && originalSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
            originalSlot = mc.player.getInventory().getSelectedSlot();
            cooldown = 0;
            phase = AnchorPhase.PLACE_ANCHOR;
        }

        if (phase == AnchorPhase.IDLE) return;

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
            phase = AnchorPhase.CHARGE;
            return;
        }

        int anchorSlot = findHotbarSlot(Items.RESPAWN_ANCHOR);
        if (anchorSlot == -1) {
            doCleanup();
            return;
        }

        mc.player.getInventory().setSelectedSlot(anchorSlot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
        cooldown = humanizedCooldownTicks(placeCps.getValue());

        phase = AnchorPhase.CHARGE;
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
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
        cooldown = humanizedCooldownTicks(chargeCps.getValue());

        phase = AnchorPhase.AIRPLACE;
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
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);

        phase = AnchorPhase.CHARGE_2;
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
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
        cooldown = humanizedCooldownTicks(chargeCps.getValue());

        phase = AnchorPhase.DETONATE;
    }

    private void doDetonate() {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;

        int slot = detonateSlot.getValue() - 1;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);

        phase = AnchorPhase.CLEANUP;
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
        phase = AnchorPhase.IDLE;
        originalSlot = -1;
        wasKeyDown = false;
        cooldown = 0;
    }

    public boolean isActive() {
        return isEnabled() && phase != AnchorPhase.IDLE;
    }


}
