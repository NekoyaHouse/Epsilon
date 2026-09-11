package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.managers.TimerManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Timer extends Module {

    public static final Timer INSTANCE = new Timer();

    private Timer() {
        super("Timer", Category.MOVEMENT);
    }

    private enum Mode {
        Always,
        Balance,
        SlowBalance
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Always);
    private final KeybindSetting activateKey = keybindSetting("Activate Key", GLFW.GLFW_KEY_X, () -> !mode.is(Mode.Always));
    public final DoubleSetting multiplier = doubleSetting("Multiplier", 1.8, 0.1, 10.0, 0.1);
    private final IntSetting maxBalance = intSetting("Max Balance", 1000, 0, 10000, 1, () -> mode.is(Mode.Balance));
    public final IntSetting ticks = intSetting("Ticks", 13, 1, 20, 1, () -> mode.is(Mode.SlowBalance));

    private float balance;
    private long currentTime;
    private long balanceTime;
    private boolean onGround;
    private boolean attack;
    private boolean releasing;
    private boolean shouldRelease;
    public boolean lagging;

    private final TimerUtils timer = new TimerUtils();
    public static final Queue<Packet<ClientGamePacketListener>> packets = new ConcurrentLinkedQueue<>();

    @Override
    public String getInfo() {
        return switch (mode.getValue()) {
            case Always -> String.valueOf(multiplier.getValue());
            case Balance -> balanceTime + "ms";
            case SlowBalance -> String.format("%.1f", balance) + " Ticks";
        };
    }

    @Override
    protected void onEnable() {
        resetState();
        if (mode.is(Mode.Balance)) {
            timer.reset();
            currentTime = System.currentTimeMillis();
            updateTimerSpeed();
        }
        releaseAll();
    }

    @Override
    protected void onDisable() {
        lagging = false;
        releasing = false;
        shouldRelease = false;
        TimerManager.INSTANCE.reset();
        releaseAll();
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        // 先收回其他模块留下的临时倍率，再应用当前 Timer 模式。
        TimerManager.INSTANCE.tryReset();

        switch (mode.getValue()) {
            case Always -> TimerManager.INSTANCE.set(multiplier.getValue().floatValue());
            case SlowBalance -> {
                boolean moving = mc.player.isMoving();
                if (shouldRelease && balance > 0.1F) {
                    float speed = multiplier.getValue().floatValue();
                    if (balance >= speed - 1.0F) {
                        TimerManager.INSTANCE.set(speed);
                        balance -= speed - 1.0F;
                    } else {
                        TimerManager.INSTANCE.set(1.0F);
                    }
                } else if (KillAura.INSTANCE.target == null && balance < ticks.getValue()) {
                    float speed = !moving && mc.player.onGround() ? 0.8F : 0.96F;
                    TimerManager.INSTANCE.set(speed);
                    balance += 1.0F - speed;
                } else {
                    TimerManager.INSTANCE.set(1.0F);
                }
                balance = Mth.clamp(balance, 0.0F, ticks.getValue());
            }
            case Balance -> {
                if (releasing && balanceTime <= 10L) {
                    updateTimerSpeed();
                }

                if (!lagging) releaseAll();

                KillAura killAura = KillAura.INSTANCE;
                if (killAura.isEnabled() && killAura.target != null && mc.player.distanceTo(killAura.target) <= killAura.aimRange.getValue().floatValue()) {
                    stopBalance();
                    return;
                }

                if (!mc.player.isAlive() || mc.player.getHealth() <= 0.0f) {
                    setEnabled(false);
                    return;
                }

                if (!shouldRelease && !lagging && !releasing && !mc.player.isMoving()) {
                    timer.reset();
                    balanceTime = 0L;
                    currentTime = System.currentTimeMillis();
                    startBalance();
                } else if (lagging && !shouldRelease && !releasing && !mc.player.isMoving() && balanceTime < 0L) {
                    balanceTime = 0L;
                    currentTime = System.currentTimeMillis();
                }

                updateTimerSpeed();
            }
        }
    }

    @EventHandler
    private void onClientTickPost(ClientTickEvent.Post event) {
        if (mode.is(Mode.Balance) && releasing && balanceTime <= 10L) {
            stopBalance();
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (mode.is(Mode.Balance) && attack) {
            if (lagging) stopBalance();
            attack = false;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!mode.is(Mode.Balance) || nullCheck()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            stopBalance();
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId()) {
            stopBalance();
            return;
        }
        if (packet instanceof ClientboundPingPacket && lagging) {
            packets.add((Packet<ClientGamePacketListener>) packet);
            event.cancel();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!mode.is(Mode.Balance)) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket movePacket) {
            if (lagging) {
                if (!movePacket.hasPosition() && !movePacket.hasRotation() && movePacket.isOnGround() == onGround) {
                    event.cancel();
                } else {
                    balanceTime -= releasing ? 100L : 50L;
                    balanceTime = Math.max(-3000L, balanceTime);
                }

                long now = System.currentTimeMillis();
                if (balanceTime <= maxBalance.getValue()) {
                    balanceTime += now - currentTime;
                }
                currentTime = now;
            }
            onGround = movePacket.isOnGround();
        } else if (packet instanceof ServerboundInteractPacket && lagging && !packets.isEmpty()) {
            attack = true;
            event.cancel();
        }
    }

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        if (mode.is(Mode.Balance)) {
            packets.clear();
            stopBalance();
        }
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (event.getKey() != activateKey.getValue()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            shouldRelease = true;
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            shouldRelease = false;
        }
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (event.getButton() != activateKey.getValue()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            shouldRelease = true;
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            shouldRelease = false;
        }
    }

    public long getBalanceTime() {
        return mode.is(Mode.SlowBalance) ? (long) (balance * 50.0F) : balanceTime;
    }

    public long getMaxBalance() {
        return mode.is(Mode.SlowBalance) ? (long) ticks.getValue() * 50L : maxBalance.getValue();
    }

    public boolean isReleasing() {
        return mode.is(Mode.SlowBalance) ? shouldRelease : releasing;
    }

    private void startBalance() {
        releaseAll();
        lagging = true;
        releasing = false;
    }

    private void stopBalance() {
        updateTimerSpeed();
        timer.reset();
        balanceTime = 0L;
        currentTime = System.currentTimeMillis();
        if (lagging) {
            lagging = false;
            releaseAll();
        }
        releasing = false;
    }

    private void updateTimerSpeed() {
        boolean shouldSpeedUp = shouldRelease && balanceTime > 10L;
        releasing = shouldSpeedUp;
        TimerManager.INSTANCE.set(shouldSpeedUp ? multiplier.getValue().floatValue() : 1.0F);
    }

    private void releaseAll() {
        if (!lagging && !packets.isEmpty()) {
            Packet<ClientGamePacketListener> packet;
            while ((packet = packets.poll()) != null && mc.getConnection() != null) {
                packet.handle(mc.getConnection());
            }
        }
    }

    private void resetState() {
        balance = 0.0F;
        balanceTime = 0L;
        currentTime = System.currentTimeMillis();
        onGround = false;
        attack = false;
        lagging = false;
        releasing = false;
        shouldRelease = false;
        packets.clear();
    }

}
