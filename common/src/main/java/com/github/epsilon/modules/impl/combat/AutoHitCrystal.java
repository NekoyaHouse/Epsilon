package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.RightClickEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AutoHitCrystal extends Module {

    public static final AutoHitCrystal INSTANCE = new AutoHitCrystal();

    private AutoHitCrystal() {
        super("Auto Hit Crystal", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                event -> {
                    if (!render.getValue() || renderBoxes.isEmpty()) return;

                    long time = System.currentTimeMillis();
                    long fadeTime = this.fadeTime.getValue().longValue();

                    renderBoxes.removeIf(box -> time - box.startTime() > fadeTime);

                    for (RenderBox box : renderBoxes) {
                        long age = time - box.startTime();
                        float progress = Mth.clamp((float) age / fadeTime, 0.0f, 1.0f);
                        float alphaFactor = Mth.clamp(1.0f - progress, 0.0f, 1.0f);

                        Color sideColor = box.sideColor();
                        Color lineColor = box.lineColor();

                        Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
                        Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

                        Render3DScheduler.INSTANCE.addFilledBox(box.aabb, side);
                        Render3DScheduler.INSTANCE.addOutlineBox(box.aabb, line);
                    }
                }
        ));
    }

    private final KeybindSetting activateKey = keybindSetting("Activate Key", GLFW.GLFW_KEY_UNKNOWN);
    private final BoolSetting checkPlace = boolSetting("Check Place", false);
    private final DoubleSetting switchDelay = doubleSetting("Switch Delay", 0.0, 0.0, 20.0, 1.0);
    private final DoubleSetting switchChance = doubleSetting("Switch Chance", 100.0, 0.0, 100.0, 1.0);
    private final DoubleSetting placeDelay = doubleSetting("Place Delay", 0.0, 0.0, 20.0, 1.0);
    private final DoubleSetting placeChance = doubleSetting("Place Chance", 100.0, 0.0, 100.0, 1.0);
    private final BoolSetting workWithTotem = boolSetting("Work With Totem", false);
    private final BoolSetting workWithCrystal = boolSetting("Work With Crystal", false);
    private final BoolSetting workWithPickaxe = boolSetting("Work With Pickaxe", false);
    private final BoolSetting swordSwap = boolSetting("Sword Swap", true);
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);

    private final BoolSetting render = boolSetting("Render", true);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 183, 197, 100), render::getValue);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 105, 180), render::getValue);

    private int placeClock = 0;
    private int switchClock = 0;
    private boolean active;
    private boolean crystalling;

    private final List<RenderBox> renderBoxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        resetState();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (mc.screen != null) {
            return;
        }

        if (this.switchClock > 0) --this.switchClock;
        if (this.placeClock > 0) --this.placeClock;

        boolean pressed = KeybindUtils.isPressed(activateKey.getValue());
        if (pressed) {
            HitResult hitResult = mc.hitResult;
            if (hitResult instanceof BlockHitResult hitResult2) {
                if (hitResult.getType() == HitResult.Type.BLOCK && !this.active && !mc.level.getBlockState(hitResult2.getBlockPos()).canBeReplaced() && this.checkPlace.getValue()) {
                    return;
                }
            }

            var mainHandStack = mc.player.getMainHandItem();
            if (!(mainHandStack.is(ItemTags.SWORDS) ||
                    (this.workWithTotem.getValue() && mainHandStack.is(Items.TOTEM_OF_UNDYING)) ||
                    (this.workWithCrystal.getValue() && mainHandStack.is(Items.END_CRYSTAL)) ||
                    (this.workWithPickaxe.getValue() && mainHandStack.is(ItemTags.PICKAXES)) ||
                    this.active)) {
                return;

            }

            this.active = true;

            if (!this.crystalling && hitResult instanceof BlockHitResult hit) {
                if (hit.getType() == HitResult.Type.MISS) {
                    return;
                }

                BlockPos renderPos = hit.getBlockPos().relative(hit.getDirection());
                BlockState hitState = mc.level.getBlockState(hit.getBlockPos());

                if (hitState.is(Blocks.BEDROCK) || hitState.is(Blocks.OBSIDIAN)) {
                    this.active = true;
                    this.crystalling = true;
                } else if (!mc.level.getBlockState(renderPos).is(Blocks.OBSIDIAN)) {
                    BlockState state = mc.level.getBlockState(hit.getBlockPos());
                    if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                        return;
                    }

                    mc.options.keyUse.setDown(false);

                    if (!mc.player.isHolding(Items.OBSIDIAN)) {
                        if (this.switchClock > 0) {
                            return;
                        }
                        if (MathUtils.getRandom(1, 100) <= this.switchChance.getValue().intValue()) {
                            this.switchClock = this.switchDelay.getValue().intValue();
                            selectItemFromHotbar(Items.OBSIDIAN);
                        }
                    }

                    if (mc.player.isHolding(Items.OBSIDIAN)) {
                        if (this.placeClock > 0) {
                            return;
                        }

                        if (MathUtils.getRandom(1, 100) <= this.placeChance.getValue().intValue()) {
                            placeBlock(hit, renderPos);
                            this.placeClock = this.placeDelay.getValue().intValue();
                            this.crystalling = true;
                        }
                    }
                }
            }

            if (this.crystalling) {
                if (!mc.player.isHolding(Items.END_CRYSTAL)) {
                    if (this.switchClock > 0) {
                        return;
                    }
                    if (MathUtils.getRandom(1, 100) <= this.switchChance.getValue().intValue()) {
                        if (selectItemFromHotbar(Items.END_CRYSTAL)) {
                            this.switchClock = this.switchDelay.getValue().intValue();
                        }
                    }
                }

                if (mc.player.isHolding(Items.END_CRYSTAL) && !CrystalAura.INSTANCE.isEnabled()) {
                    CrystalAura.INSTANCE.onTick(null);
                }
            }
        } else {
            this.resetState();
        }
    }

    @EventHandler
    private void onClick(RightClickEvent event) {
        if (nullCheck() || !this.active) return;

        var mainHandStack = mc.player.getMainHandItem();
        HitResult crosshairTarget = mc.hitResult;

        if (this.swordSwap.getValue() && mainHandStack.is(ItemTags.SWORDS) && crosshairTarget instanceof BlockHitResult hit) {
            if (mc.level.getBlockState(hit.getBlockPos()).is(Blocks.OBSIDIAN) && !mc.player.isHolding(Items.END_CRYSTAL)) {
                if (selectItemFromHotbar(Items.END_CRYSTAL)) {
                    this.crystalling = true;
                }
            }
        }

        if (this.active && KeybindUtils.isPressed(activateKey.getValue())) {
            if (mainHandStack.is(Items.END_CRYSTAL) || mainHandStack.is(Items.OBSIDIAN)) {
                event.cancel();
            }
        }

        if (mainHandStack.is(Items.END_CRYSTAL) && !mc.mouseHandler.isLeftPressed()) {
            event.cancel();
        }
    }

    private boolean selectItemFromHotbar(net.minecraft.world.item.Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);
        if (result.found()) {
            InvUtils.swap(result.slot(), false);
            return true;
        }
        return false;
    }

    private void placeBlock(BlockHitResult hit, BlockPos renderPos) {
        InteractionHand hand = mc.player.getOffhandItem().is(Items.OBSIDIAN) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (result.consumesAction()) {
            if (swingHand.getValue()) {
                mc.player.swing(hand);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
            renderBoxes.add(new RenderBox(new AABB(renderPos), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis()));
        }
    }

    private void resetState() {
// this.placeClock = this.placeDelay.getValue().intValue();
// this.switchClock = this.switchDelay.getValue().intValue();
        this.switchClock = 0;
        this.placeClock = 0;
        this.active = false;
        this.crystalling = false;
    }

    private record RenderBox(AABB aabb, Color lineColor, Color sideColor, long startTime) {
    }

}
