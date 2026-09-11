package com.github.epsilon.managers;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.combat.*;
import com.github.epsilon.modules.impl.movement.*;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.modules.impl.movement.follower.Follower;
import com.github.epsilon.modules.impl.player.*;
import com.github.epsilon.modules.impl.render.*;
import com.github.epsilon.modules.impl.render.maseffects.MasEffects;
import com.github.epsilon.utils.client.KeybindUtils;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class ModuleManager {

    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void initModules() {
        addModule(ClientSetting.INSTANCE);

        // Combat
        addModule(AimBot.INSTANCE);
        addModule(AnchorBlast.INSTANCE);
        addModule(AntiBot.INSTANCE);
        addModule(Backtrack.INSTANCE);
        addModule(AutoClicker.INSTANCE);
        addModule(AutoDtap.INSTANCE);
        addModule(AutoHitCrystal.INSTANCE);
        addModule(AutoMend.INSTANCE);
        addModule(AutoThrow.INSTANCE);
        addModule(AutoTotem.INSTANCE);
        addModule(AutoWeapon.INSTANCE);
        addModule(Criticals.INSTANCE);
        addModule(CrystalAura.INSTANCE);
        addModule(CrystalBlocker.INSTANCE);
        addModule(FeetTrap.INSTANCE);
        addModule(DoubleAnchor.INSTANCE);
        addModule(HoverTotem.INSTANCE);
        addModule(KillAura.INSTANCE);
        addModule(KeyPearl.INSTANCE);
        addModule(MaceAura.INSTANCE);
        addModule(MultiAura.INSTANCE);
        addModule(PacketMine.INSTANCE);
        addModule(SafeAnchor.INSTANCE);
        addModule(SafeCrystal.INSTANCE);
        addModule(SilentAim.INSTANCE);
        addModule(SpearKill.INSTANCE);
        addModule(TriggerBot.INSTANCE);
        addModule(ZealotCrystalPlus.INSTANCE);

        // Player
        addModule(AutoArmor.INSTANCE);
        addModule(AutoCrossbowRelease.INSTANCE);
        addModule(AutoFirework.INSTANCE);
        addModule(AutoKouZi.INSTANCE);
        addModule(AutoBan.INSTANCE);
        addModule(AutoMLG.INSTANCE);
        addModule(AutoTool.INSTANCE);
        addModule(BedNuker.INSTANCE);
        addModule(BreakCooldown.INSTANCE);
        addModule(ChestAura.INSTANCE);
        addModule(Disabler.INSTANCE);
        addModule(ElytraSwap.INSTANCE);
        addModule(FakePlayer.INSTANCE);
        addModule(GhostHand.INSTANCE);
        addModule(InputDisabler.INSTANCE);
        addModule(InvManager.INSTANCE);
        addModule(JumpCooldown.INSTANCE);
        addModule(KeyFriend.INSTANCE);
        addModule(MultiTask.INSTANCE);
        addModule(NoFall.INSTANCE);
        addModule(NoRotate.INSTANCE);
        addModule(PacketEat.INSTANCE);
        addModule(PlayerAlarms.INSTANCE);
        addModule(SoundFX.INSTANCE);
        addModule(Stealer.INSTANCE);
        addModule(Timer.INSTANCE);
        addModule(UseCooldown.INSTANCE);
        addModule(AutoQueue.INSTANCE);

        // Movement
        addModule(ElytraFly.INSTANCE);
        addModule(Follower.INSTANCE);
        addModule(Dolphin.INSTANCE);
        addModule(AutoSprint.INSTANCE);
        addModule(Blink.INSTANCE);
        addModule(Eagle.INSTANCE);
        addModule(AutoMap.INSTANCE);
        addModule(Flight.INSTANCE);
        addModule(GUIMove.INSTANCE);
        addModule(HoleSnap.INSTANCE);
        addModule(JumpReset.INSTANCE);
        addModule(KeepSprint.INSTANCE);
        addModule(MovementFix.INSTANCE);
        addModule(NoSlowdown.INSTANCE);
        addModule(Phase.INSTANCE);
        addModule(ReverseStep.INSTANCE);
        addModule(SafeWalk.INSTANCE);
        addModule(Scaffold.INSTANCE);
        addModule(Speed.INSTANCE);
        addModule(Step.INSTANCE);
        addModule(Strafe.INSTANCE);
        addModule(Stuck.INSTANCE);
        addModule(TargetStrafe.INSTANCE);
        addModule(Velocity.INSTANCE);

        // Render
        addModule(KillEffect.INSTANCE);
        addModule(AntiAlias.INSTANCE);
        addModule(AspectRatio.INSTANCE);
        addModule(BetterChat.INSTANCE);
        addModule(BetterScoreboard.INSTANCE);
        addModule(BlockESP.INSTANCE);
        addModule(BlockHighlight.INSTANCE);
        addModule(CameraClip.INSTANCE);
        addModule(Chams.INSTANCE);
        addModule(CrystalChams.INSTANCE);
        addModule(CustomSky.INSTANCE);
        addModule(ESP2D.INSTANCE);
        addModule(Filter.INSTANCE);
        addModule(FreeCamera.INSTANCE);
        addModule(Fullbright.INSTANCE);
        addModule(GameAnimation.INSTANCE);
        addModule(HandView.INSTANCE);
        addModule(Hat.INSTANCE);
        addModule(Health.INSTANCE);
        addModule(HitParticles.INSTANCE);
        addModule(HoleESP.INSTANCE);
        addModule(ItemPhysics.INSTANCE);
        addModule(JumpCircle.INSTANCE);
        addModule(LogoutSpots.INSTANCE);
        addModule(MasEffects.INSTANCE);
        addModule(MotionBlur.INSTANCE);
        addModule(NameTags.INSTANCE);
        addModule(NoGhostBlocks.INSTANCE);
        addModule(NoRender.INSTANCE);
        addModule(Particles.INSTANCE);
        addModule(PopChams.INSTANCE);
        addModule(Shaders.INSTANCE);
        addModule(SneakTweak.INSTANCE);
        addModule(TitleReplace.INSTANCE);
        addModule(TotemAnimation.INSTANCE);
        addModule(TNTTimer.INSTANCE);
        addModule(Trajectories.INSTANCE);
        addModule(WorldTweaks.INSTANCE);
        addModule(Xray.INSTANCE);

    }

    private void addModule(Module module) {
        modules.add(module);
        module.initI18n(EpsilonTranslateComponent.create("modules", module.getName().toLowerCase()));
    }

    public List<Module> getModules() {
        return modules;
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (mc.level == null || mc.gui.screen() != null || event.getKey() == GLFW.GLFW_KEY_UNKNOWN) return;

        int keyCode = event.getKey();
        int action = event.getAction();

        ClientSetting cs = ClientSetting.INSTANCE;
        if (keyCode == cs.guiKeybind.getValue() && action == InputConstants.PRESS) {
            mc.gui.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
                case Panel -> PanelScreen.INSTANCE;
                case Dropdown -> DropdownScreen.INSTANCE;
            });
        }

        dispatchKeyBind(keyCode, action);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (mc.level != null && mc.gui.screen() == null) {
            dispatchKeyBind(KeybindUtils.encodeMouseButton(event.getButton()), event.getAction());
        }
    }

    private void dispatchKeyBind(int keyCode, int action) {
        boolean isPress = action == InputConstants.PRESS;
        boolean isRelease = action == InputConstants.RELEASE;

        List<Module> affectedModules = new ArrayList<>();
        boolean hasEnabling = false;

        for (Module module : modules) {
            if (module.getKeyBind() != keyCode) continue;

            if (module.getBindMode() == Module.BindMode.Toggle && isPress) {
                if (!module.isEnabled()) {
                    hasEnabling = true;
                }
                affectedModules.add(module);
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    hasEnabling = true;
                    affectedModules.add(module);
                } else if (isRelease && module.isEnabled()) {
                    affectedModules.add(module);
                }
            }
        }

        for (Module module : affectedModules) {
            if (module.getBindMode() == Module.BindMode.Toggle) {
                module.toggle();
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    module.setEnabled(true);
                } else if (isRelease && module.isEnabled()) {
                    module.setEnabled(false);
                }
            }
        }

        if (!affectedModules.isEmpty() && ClientSetting.INSTANCE.soundNotify.getValue()) {
            if (hasEnabling) {
                SoundManager.INSTANCE.playInUi(SoundKey.ENABLE);
            } else {
                SoundManager.INSTANCE.playInUi(SoundKey.DISABLE);
            }
        }
    }

}
