package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.interfaces.WalkAnimationStateAccessor;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.player.FakePlayer;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.render.WireframeEntityRenderer;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class LogoutSpots extends Module {

    public static final LogoutSpots INSTANCE = new LogoutSpots();

    private LogoutSpots() {
        super("Logout Spots", Category.RENDER);
    }

    private enum RenderMode {
        Model,
        Box
    }

    private final BoolSetting notifications = boolSetting("Notifications", true);
    private final BoolSetting ignoreBots = boolSetting("Ignore Bots", true);
    private final EnumSetting<RenderMode> renderMode = enumSetting("Render Mode", RenderMode.Model);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 255, 255, 25));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 127));

    private final Map<UUID, Player> playerCache = Maps.newConcurrentMap();
    private final Map<UUID, LogoutPlayer> logoutCache = Maps.newConcurrentMap();

    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    protected void onEnable() {
        playerCache.clear();
        logoutCache.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            if (packet.actions().contains(Action.ADD_PLAYER)) {
                for (Entry entry : packet.entries()) {
                    Player player = logoutCache.get(entry.profileId());
                    if (player == null) continue;
                    if (ignoreBots.getValue() && isABot(player)) continue;
                    if (notifications.getValue()) {
                        ChatUtils.addChatMessage(player.getName().getString() + " logged back at  X: " + (int) player.getX() + " Y: " + (int) player.getY() + " Z: " + (int) player.getZ());
                    }
                    logoutCache.remove(entry.profileId());
                }
            }
            playerCache.clear();
        }

        if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket(List<UUID> profileIds)) {
            for (UUID uuid : profileIds) {
                Player player = playerCache.get(uuid);
                if (player == null) continue;
                if (ignoreBots.getValue() && isABot(player)) continue;
                if (notifications.getValue()) {
                    ChatUtils.addChatMessage(player.getName().getString() + " logged out at  X: " + (int) player.getX() + " Y: " + (int) player.getY() + " Z: " + (int) player.getZ());
                }
                logoutCache.computeIfAbsent(uuid, ignored -> new LogoutPlayer(player));
            }
            playerCache.clear();
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        for (Player player : mc.level.players()) {
            if (player.equals(mc.player)) continue;
            playerCache.put(player.getGameProfile().id(), player);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderMode.is(RenderMode.Box)) {
            for (LogoutPlayer player : logoutCache.values()) {
                Render3DScheduler.INSTANCE.addFilledBox(player.getBoundingBox(), sideColor.getValue());
                Render3DScheduler.INSTANCE.addOutlineBox(event.getPoseStack(), player.getBoundingBox(), lineColor.getValue());
            }
        } else {
            boolean batching = false;
            for (LogoutPlayer player : logoutCache.values()) {
                if (!batching) {
                    WireframeEntityRenderer.beginBatch(event.getPoseStack());
                    batching = true;
                }
                player.render(event.getPoseStack());
            }

            if (batching) {
                WireframeEntityRenderer.endBatch();
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        RectRenderer rectRenderer = rectRendererSupplier.get();
        TextRenderer textRenderer = textRendererSupplier.get();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();

        for (LogoutPlayer player : logoutCache.values()) {
            Vec3 anchor = player.getPosition(partialTick)
                    .add(0.0, player.getBbHeight() + (player.isCrouching() ? 0.1 : 0.2), 0.0);
            Vector3f projectedAnchor = WorldToScreen.calcWorld2Screen(anchor);
            if (projectedAnchor == null) continue;

            float textScale = WorldToScreen.calcScale(anchor) * 0.4f;
            float padding = 2.0f * textScale;
            String text = player.getName().getString() + " " + String.format(Locale.ROOT, "%.1f", player.getHealth() + player.getAbsorptionAmount())
                    + " X: " + (int) player.getX() + " Z: " + (int) player.getZ();

            float textWidth = textRenderer.getWidth(text, textScale);
            float textHeight = textRenderer.getHeight(textScale);
            float boxWidth = textWidth + padding * 2.0f;
            float boxHeight = textHeight + padding * 2.0f;
            if (!Float.isFinite(projectedAnchor.x) || !Float.isFinite(projectedAnchor.y)) {
                continue;
            }

            float centerX = projectedAnchor.x;
            float boxX = centerX - boxWidth * 0.5f;
            float boxY = projectedAnchor.y - boxHeight - 2.0f * textScale;

            if (boxX + boxWidth < 0.0f || boxY + boxHeight < 0.0f || boxX > screenWidth || boxY > screenHeight) {
                continue;
            }

            rectRenderer.addRect(boxX, boxY, boxWidth, boxHeight, new Color(0, 0, 0, 153));
            textRenderer.addText(text, boxX + padding, boxY + padding, textScale, Color.WHITE);
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();
    }

    private boolean isABot(Player ent) {
        return !ent.getUUID().equals(UUID.nameUUIDFromBytes(("OfflinePlayer:" + ent.getName().getString()).getBytes(StandardCharsets.UTF_8))) && ent instanceof RemotePlayer
                && (FakePlayer.fakePlayer == null || ent.getId() != FakePlayer.fakePlayer.getId())
                && !ent.getName().getString().contains("-");
    }

    private final class LogoutPlayer extends RemotePlayer {
        private final float walkPosition;
        private final float walkSpeed;
        private final float attackAnimation;

        private LogoutPlayer(Player player) {
            super(mc.level, new GameProfile(player.getGameProfile().id(), player.getGameProfile().name()));

            float tickDelta = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            walkPosition = player.walkAnimation.position(tickDelta);
            walkSpeed = player.walkAnimation.speed(tickDelta);
            attackAnimation = player.getAttackAnim(tickDelta);

            copyPosition(player);
            setOldPosAndRot();
            yHeadRot = player.yHeadRot;
            yHeadRotO = yHeadRot;
            yBodyRot = player.yBodyRot;
            yBodyRotO = yBodyRot;
            getAttributes().assignAllValues(player.getAttributes());
            setPose(player.getPose());
            setHealth(player.getHealth());
            setAbsorptionAmount(player.getAbsorptionAmount());
            swingingArm = player.swingingArm;
        }

        private void render(PoseStack poseStack) {
            float tickDelta = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            oAttackAnim = attackAnimation;
            attackAnim = attackAnimation;
            ((WalkAnimationStateAccessor) walkAnimation).epsilon$freeze(walkPosition, walkSpeed, tickDelta);

            WireframeEntityRenderer.render(poseStack, this, 1.0, sideColor.getValue(), lineColor.getValue(), 2.0f);
        }

        @Override
        public boolean shouldShowName() {
            return false;
        }

        @Override
        public Component belowNameDisplay() {
            return null;
        }
    }

}
