package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.FeetTrap;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Step extends Module {

    public static final Step INSTANCE = new Step();

    private Step() {
        super("Step", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        OldNCP,
        NCP
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final DoubleSetting height = doubleSetting("Height", 1.0, 0.0, 5.0, 0.5);
    private final BoolSetting useTimer = boolSetting("Timer", true, () -> mode.is(Mode.OldNCP) || mode.is(Mode.NCP));
    private final BoolSetting fast = boolSetting("Fast", true, () -> mode.is(Mode.NCP) && useTimer.getValue());
    private final BoolSetting feetTrapPause = boolSetting("FeetTrap Pause", true);
    private final BoolSetting inWebPause = boolSetting("In Web Pause", true);
    private final BoolSetting insideBlockPause = boolSetting("Inside Block Pause", true);
    private final BoolSetting sneakingPause = boolSetting("Sneaking Pause", true);

    private boolean timer;
    private int packets = 0;

    @Override
    public String getInfo() {
        return mode.getTranslatedValue();
    }

    @Override
    protected void onDisable() {
        Managers.TIMER.reset();
        timer = false;
        packets = 0;
        if (!nullCheck()) setStepHeight(0.6f);
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        if (sneakingPause.getValue() && mc.player.isCrouching() || insideBlockPause.getValue() && PlayerUtils.isInBlock() || mc.player.isInLava() || mc.player.isInWater() || inWebPause.getValue() && PlayerUtils.isInWeb() || !mc.player.onGround() || feetTrapPause.getValue() && FeetTrap.INSTANCE.isEnabled()) {
            setStepHeight(0.6f);
            return;
        }

        setStepHeight(height.getValue().floatValue());
    }

    @EventHandler
    private void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        if (timer && packets <= 0) {
            Managers.TIMER.reset();
            timer = false;
        }
        boolean strict = mode.getValue() == Mode.NCP;
        if (mode.getValue().equals(Mode.OldNCP) || strict) {
            double stepHeight = mc.player.getY() - mc.player.yo;
            if (stepHeight <= 0.75 || stepHeight > height.getValue()) {
                return;
            }

            double[] offsets = getOffset(stepHeight);
            if (offsets != null && offsets.length > 1) {
                if (useTimer.getValue()) {
                    Managers.TIMER.set((float) getTimer(stepHeight));
                    timer = true;
                    packets = 2;
                }
                for (double offset : offsets) {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.xo, mc.player.yo + offset, mc.player.zo, false, mc.player.horizontalCollision));
                }
            }
        }
    }

    @EventHandler
    private void onPlayerTickPost(PlayerTickEvent.Post event) {
        packets--;
    }

    private double getTimer(double height) {
        if (height > 0.6 && height <= 1) {
            if (!fast.getValue() && mode.getValue() == Mode.NCP) {
                return 1d / 3d;
            }
            return 0.5;
        }
        double[] offsets = getOffset(height);
        if (offsets == null) {
            return 1;
        }
        return 1d / offsets.length;
    }

    private double[] getOffset(double height) {
        boolean strict = mode.getValue() == Mode.NCP;
        if (height == 0.75) {
            if (strict) {
                return new double[]{0.42, 0.753, 0.75};
            } else {
                return new double[]{0.42, 0.753};
            }
        } else if (height == 0.8125) {
            if (strict) {
                return new double[]{0.39, 0.7, 0.8125};
            } else {
                return new double[]{0.39, 0.7};
            }
        } else if (height == 0.875) {
            if (strict) {
                return new double[]{0.39, 0.7, 0.875};
            } else {
                return new double[]{0.39, 0.7};
            }
        } else if (height == 1) {
            if (strict) {
                return new double[]{0.42, 0.753, 1};
            } else {
                return new double[]{0.42, 0.753};
            }
        } else if (height == 1.5) {
            return new double[]{0.42, 0.75, 1.0, 1.16, 1.23, 1.2};
        } else if (height == 2) {
            return new double[]{0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43};
        } else if (height == 2.5) {
            return new double[]{0.425, 0.821, 0.699, 0.599, 1.022, 1.372, 1.652, 1.869, 2.019, 1.907};
        }

        return null;
    }

    private void setStepHeight(float v) {
        mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(v);
    }

}
