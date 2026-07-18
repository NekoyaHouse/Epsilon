package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.interfaces.WalkAnimationStateAccessor;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.WireframeEntityRenderer;
import com.github.epsilon.utils.render.animation.Easing;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PopChams extends Module {

    public static final PopChams INSTANCE = new PopChams();

    private PopChams() {
        super("Pop Chams", Category.RENDER);
    }

    private final BoolSetting ignoreSelf = boolSetting("Ignore Self", true);
    private final DoubleSetting renderTime = doubleSetting("Render Time", 1.0, 0.1, 6.0, 0.1);
    private final DoubleSetting yModifier = doubleSetting("Y Modifier", 0.75, -4.0, 4.0, 0.05);
    private final EnumSetting<Easing> yEasing = enumSetting("Y Easing", Easing.EASE_IN_OUT_EXPO);
    private final DoubleSetting scaleModifier = doubleSetting("Scale Modifier", -0.25, -4.0, 4.0, 0.05);
    private final BoolSetting fadeOut = boolSetting("Fade Out", true);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 255, 255, 25));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 127));

    private final List<GhostPlayer> ghosts = new ArrayList<>();

    @Override
    protected void onDisable() {
        synchronized (ghosts) {
            ghosts.clear();
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH)
            return;

        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player player)) return;
        if (ignoreSelf.getValue() && player == mc.player) return;

        synchronized (ghosts) {
            ghosts.add(new GhostPlayer(player));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        synchronized (ghosts) {
            if (ghosts.isEmpty()) {
                return;
            }

            WireframeEntityRenderer.beginBatch(event.getPoseStack());
            ghosts.removeIf(ghostPlayer -> ghostPlayer.render(event));
            WireframeEntityRenderer.endBatch();
        }
    }

    private final class GhostPlayer extends RemotePlayer {
        private double timer;
        private final float walkPosition;
        private final float walkSpeed;
        private final float attackAnimation;
        private final double startY;

        private GhostPlayer(Player player) {
            super(mc.level, new GameProfile(player.getGameProfile().id(), player.getGameProfile().name()));
            float tickDelta = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            walkPosition = player.walkAnimation.position(tickDelta);
            walkSpeed = player.walkAnimation.speed(tickDelta);
            attackAnimation = player.getAttackAnim(tickDelta);
            startY = player.getY();

            copyPosition(player);
            setOldPosAndRot();
            yHeadRot = player.yHeadRot;
            yHeadRotO = yHeadRot;
            yBodyRot = player.yBodyRot;
            yBodyRotO = yBodyRot;
            getAttributes().assignAllValues(player.getAttributes());
            setPose(player.getPose());
            swingingArm = player.swingingArm;
        }

        private boolean render(Render3DEvent event) {
            float frameTime = mc.getDeltaTracker().getGameTimeDeltaTicks() / 20.0f;
            timer += frameTime;
            if (timer > renderTime.getValue()) return true;

            float tickDelta = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            tickCount = (int) (timer * 20.0);

            float progress = Mth.clamp((float) (timer / renderTime.getValue()), 0.0f, 1.0f);
            setPos(getX(), startY + yModifier.getValue() * renderTime.getValue() * yEasing.getValue().getFunction().apply(progress), getZ());
            setOldPosAndRot();
            yHeadRotO = yHeadRot;
            yBodyRotO = yBodyRot;
            oAttackAnim = attackAnimation;
            attackAnim = attackAnimation;

            double scale = 1.0 + scaleModifier.getValue() * timer;
            if (scale <= 0.0) return true;

            int alphaSide = sideColor.getValue().getAlpha();
            int alphaLine = lineColor.getValue().getAlpha();
            float fadeFactor = fadeOut.getValue() ? (float) Math.max(0.0, 1.0 - timer / renderTime.getValue()) : 1.0f;

            ((WalkAnimationStateAccessor) walkAnimation).epsilon$freeze(walkPosition, walkSpeed, tickDelta);

            Color side = withAlpha(sideColor.getValue(), Math.round(alphaSide * fadeFactor));
            Color line = withAlpha(lineColor.getValue(), Math.round(alphaLine * fadeFactor));

            WireframeEntityRenderer.render(event.getPoseStack(), this, scale, side, line, 2.0f);
            return false;
        }

        private Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), Mth.clamp(alpha, 0, 255));
        }

        @Override
        public boolean shouldShowName() {
            return false;
        }

        @Override
        public @Nullable Component belowNameDisplay() {
            return null;
        }
    }

}
