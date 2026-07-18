package com.github.epsilon.holders;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
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

public class ModuleHolder {

    public static final ModuleHolder INSTANCE = new ModuleHolder();

    public ModuleHolder() {
        EventBus.INSTANCE.subscribe(this);
    }

    private final List<Module> modules = new ArrayList<>();

    public void initModules() {
        addModule(ClientSetting.INSTANCE);

        // Combat
        addModule(AimBot.INSTANCE);
        addModule(AnchorBlast.INSTANCE);
        addModule(AntiBot.INSTANCE);
        addModule(AutoClicker.INSTANCE);
        addModule(AutoDtap.INSTANCE);
        addModule(AutoHitCrystal.INSTANCE);
        addModule(AutoMend.INSTANCE);
        addModule(AutoTotem.INSTANCE);
        addModule(AutoWeapon.INSTANCE);
        addModule(Criticals.INSTANCE);
        addModule(ZealotCrystalPlus.INSTANCE);
        addModule(CrystalAura.INSTANCE);
        addModule(CrystalBlocker.INSTANCE);
        addModule(FeetTrap.INSTANCE);
        addModule(DoubleAnchor.INSTANCE);
        addModule(HoverTotem.INSTANCE);
        addModule(KillAura.INSTANCE);
        addModule(KeyPearl.INSTANCE);
        addModule(MaceAura.INSTANCE);
        addModule(PacketMine.INSTANCE);
        addModule(SafeAnchor.INSTANCE);
        addModule(SafeCrystal.INSTANCE);
        addModule(SilentAim.INSTANCE);
        addModule(SpearKill.INSTANCE);
        addModule(TriggerBot.INSTANCE);

        // Player
        addModule(AutoArmor.INSTANCE);
        addModule(AutoFirework.INSTANCE);
        addModule(AutoKouZi.INSTANCE);
        addModule(AutoTool.INSTANCE);
        addModule(BreakCooldown.INSTANCE);
        addModule(Disabler.INSTANCE);
        addModule(ElytraSwap.INSTANCE);
        addModule(FakePlayer.INSTANCE);
        addModule(GhostHand.INSTANCE);
        addModule(InvManager.INSTANCE);
        addModule(JumpCooldown.INSTANCE);
        addModule(MultiTask.INSTANCE);
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
        addModule(AutoSprint.INSTANCE);
        addModule(Blink.INSTANCE);
        addModule(Eagle.INSTANCE);
        addModule(AutoMap.INSTANCE);
        addModule(FastWeb.INSTANCE);
        addModule(Flight.INSTANCE);
        addModule(GUIMove.INSTANCE);
        addModule(HoleSnap.INSTANCE);
        addModule(JumpReset.INSTANCE);
        addModule(KeepSprint.INSTANCE);
        addModule(MovementFix.INSTANCE);
        addModule(NoFall.INSTANCE);
        addModule(NoSlow.INSTANCE);
        addModule(Phase.INSTANCE);
        addModule(SafeWalk.INSTANCE);
        addModule(Scaffold.INSTANCE);
        addModule(Speed.INSTANCE);
        addModule(Step.INSTANCE);
        addModule(Strafe.INSTANCE);
        addModule(Stuck.INSTANCE);
        addModule(Velocity.INSTANCE);

        // Render
        addModule(AntiAlias.INSTANCE);
        addModule(AspectRatio.INSTANCE);
        addModule(BlockESP.INSTANCE);
        addModule(BlockHighlight.INSTANCE);
        addModule(CameraClip.INSTANCE);
        addModule(Chams.INSTANCE);
        addModule(CrystalChams.INSTANCE);
        addModule(ESP2D.INSTANCE);
        addModule(Filter.INSTANCE);
        addModule(FreeCamera.INSTANCE);
        addModule(Fullbright.INSTANCE);
        addModule(GameAnimation.INSTANCE);
        addModule(HandsView.INSTANCE);
        addModule(Hat.INSTANCE);
        addModule(HitParticles.INSTANCE);
        addModule(HoleESP.INSTANCE);
        addModule(JumpCircle.INSTANCE);
        addModule(MasEffects.INSTANCE);
        addModule(NameTags.INSTANCE);
        addModule(NoRender.INSTANCE);
        addModule(Particles.INSTANCE);
        addModule(PopChams.INSTANCE);
        addModule(Shaders.INSTANCE);
        addModule(SneakTweak.INSTANCE);
        addModule(Xray.INSTANCE);

    }

    private void addModule(Module module) {
        modules.add(module);
        module.setAddonId("epsilon");
        module.initI18n(EpsilonTranslateComponent.create("modules", module.getName().toLowerCase()));
    }

    public void registerAddonModule(String addonId, Module module, TranslateComponent moduleComponent) {
        module.setAddonId(addonId);
        module.initI18n(moduleComponent);
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (mc.level == null || mc.screen != null || event.getKey() == GLFW.GLFW_KEY_UNKNOWN) return;

        int keyCode = event.getKey();
        int action = event.getAction();

        ClientSetting cs = ClientSetting.INSTANCE;
        if (keyCode == cs.guiKeybind.getValue() && action == InputConstants.PRESS) {
            mc.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
                case Panel -> PanelScreen.INSTANCE;
                case Dropdown -> DropdownScreen.INSTANCE;
            });
        }

        dispatchKeyBind(keyCode, action);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (mc.level != null && mc.screen == null) {
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
                Managers.SOUND.playInUi(SoundKey.ENABLE);
            } else {
                Managers.SOUND.playInUi(SoundKey.DISABLE);
            }
        }
    }

}
