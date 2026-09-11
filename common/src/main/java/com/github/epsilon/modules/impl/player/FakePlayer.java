package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FakePlayer extends Module {

    public static final FakePlayer INSTANCE = new FakePlayer();

    private FakePlayer() {
        super("Fake Player", Category.PLAYER);
    }

    private final StringSetting name = stringSetting("Name", "BC_zxy");
    private final BoolSetting copyInventory = boolSetting("Copy Inventory", true);
    private final BoolSetting record = boolSetting("Record", false);
    private final BoolSetting playback = boolSetting("Playback", false);

    public static RemotePlayer fakePlayer;

    private int movementTick, deathTime;
    private final List<PlayerState> positions = new ArrayList<>();

    @Override
    protected void onEnable() {
        if (nullCheck()) {
            toggle();
            return;
        }

        movementTick = 0;
        deathTime = 0;
        if (!record.getValue()) {
            positions.clear();
        }

        fakePlayer = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes(("fake-player:" + name.getValue()).getBytes(StandardCharsets.UTF_8)), name.getValue()));
        fakePlayer.setId(-66123666);
        fakePlayer.copyPosition(mc.player);
        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.setYHeadRot(mc.player.getYHeadRot());
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());

        if (copyInventory.getValue()) {
            fakePlayer.getInventory().replaceWith(mc.player.getInventory());
        }

        mc.level.addEntity(fakePlayer);
    }

    @Override
    protected void onDisable() {
        if (mc.level != null && fakePlayer != null) {
            mc.level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer = null;
        positions.clear();
        movementTick = 0;
        deathTime = 0;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (record.getValue()) {
            positions.add(new PlayerState(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()));
            return;
        }

        if (fakePlayer != null) {
            if (playback.getValue() && !positions.isEmpty()) {
                if (movementTick >= positions.size()) {
                    movementTick = 0;
                }

                PlayerState state = positions.get(movementTick);
                movementTick++;

                fakePlayer.snapTo(state.x, state.y, state.z, state.yaw, state.pitch); // 啥阴，懒得修了
                fakePlayer.setYRot(state.yaw);
                fakePlayer.setXRot(state.pitch);
                fakePlayer.setYHeadRot(state.yaw);
            } else {
                movementTick = 0;
            }

            if (fakePlayer.isDeadOrDying()) {
                deathTime++;
                if (deathTime > 10) {
                    toggle();
                }
            } else {
                deathTime = 0;
            }
        }
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {
    }

}
