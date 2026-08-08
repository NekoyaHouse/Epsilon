package com.github.epsilon.modules.impl.movement.noslow;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import love.slmpc.ClInitNative;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Map;

@ClInitNative
public class NoSlow extends Module {

    public static final NoSlow INSTANCE = new NoSlow();

    private enum Mode {
        Vanilla,
        Jump,
        Grim1_2,
        Grim1_3,
        GrimBlink,
        GrimFull
    }

    private final Map<Mode, NoSlowMode> specialModes = new EnumMap<>(Mode.class);

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla, this::onModeChanged);
    private final BoolSetting food = boolSetting("Food", true);
    private final BoolSetting bow = boolSetting("Bow", true, this::supportsConfigurableRangedItems);
    private final BoolSetting crossbow = boolSetting("Crossbow", true, this::supportsConfigurableRangedItems);
    private final IntSetting releaseTicks = intSetting("Release Ticks", 32, 0, 150, 1, () -> mode.is(Mode.GrimFull));

    private Mode activeModeType = Mode.Vanilla;
    private int onGroundTick;

    private NoSlow() {
        super("No Slow", Category.MOVEMENT);
        specialModes.put(Mode.GrimBlink, new GrimBlinkMode(this));
        specialModes.put(Mode.GrimFull, new GrimFullMode(this));
    }

    public boolean isWorking() {
        NoSlowMode activeMode = getActiveSpecialMode();
        return isEnabled() && activeMode != null && activeMode.isWorking();
    }

    public void stopIfWorking() {
        NoSlowMode activeMode = getActiveSpecialMode();
        if (isEnabled() && activeMode != null && activeMode.isWorking()) {
            activeMode.stopIfWorking();
        }
    }

    int getReleaseTicks() {
        return releaseTicks.getValue();
    }

    @Override
    protected void onEnable() {
        activeModeType = mode.getValue();
        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onEnable();
        }
    }

    @Override
    protected void onDisable() {
        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onDisable();
        }
        onGroundTick = 0;
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (mc.player.onGround()) {
            onGroundTick++;
        } else {
            onGroundTick = 0;
        }

        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onSendPosition(event);
        }
    }

    @EventHandler
    private void onAfterSendPosition(AfterSendPositionEvent event) {
        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onAfterSendPosition(event);
        }
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onClientTick();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onHighPriorityPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || activeModeType != Mode.GrimFull) return;

        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onPacketReceive(event);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || activeModeType != Mode.GrimBlink) return;

        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onPacketReceive(event);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;

        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onPacketSend(event);
        }
    }

    @EventHandler
    private void onSlowdown(SlowdownEvent event) {
        if (!food.getValue() && mc.player.getUseItem().has(DataComponents.FOOD)) return;
        if ((!bow.getValue() || mode.is(Mode.GrimBlink)) && mc.player.getUseItem().is(Items.BOW)) return;
        if ((!crossbow.getValue() || mode.is(Mode.GrimBlink)) && mc.player.getUseItem().is(Items.CROSSBOW)) return;

        NoSlowMode activeMode = getActiveSpecialMode();
        if (activeMode != null) {
            activeMode.onSlowdown(event);
            return;
        }

        switch (activeModeType) {
            case Vanilla -> event.setSlowdown(false);
            case Jump -> handleJumpSlowdown(event);
            case Grim1_2 -> handlePeriodicSlowdown(event, 2);
            case Grim1_3 -> handlePeriodicSlowdown(event, 3);
            default -> {
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (activeModeType == Mode.Jump
                && mc.player.onGround()
                && mc.player.isUsingItem()
                && (event.getForward() != 0 || event.getStrafe() != 0)) {
            event.setJump(true);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        for (NoSlowMode specialMode : specialModes.values()) {
            specialMode.onGameLeft();
        }
        onGroundTick = 0;
    }

    private void handleJumpSlowdown(SlowdownEvent event) {
        if (onGroundTick == 1 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private void handlePeriodicSlowdown(SlowdownEvent event, int interval) {
        if (mc.player.getUseItemRemainingTicks() % interval == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private boolean supportsConfigurableRangedItems() {
        return !mode.is(Mode.GrimBlink) && !mode.is(Mode.GrimFull);
    }

    private NoSlowMode getActiveSpecialMode() {
        return specialModes.get(activeModeType);
    }

    private void onModeChanged(Mode newMode) {
        if (!isEnabled()) {
            activeModeType = newMode;
            return;
        }

        NoSlowMode previousMode = getActiveSpecialMode();
        if (previousMode != null) {
            previousMode.onDisable();
        }

        activeModeType = newMode;
        NoSlowMode nextMode = getActiveSpecialMode();
        if (nextMode != null) {
            nextMode.onEnable();
        }
    }

}
