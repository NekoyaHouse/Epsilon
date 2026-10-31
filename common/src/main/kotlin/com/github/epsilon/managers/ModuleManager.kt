package com.github.epsilon.managers

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent
import com.github.epsilon.events.api.AlwaysListening
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.KeyPressEvent
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.modules.impl.GuiSetting
import com.github.epsilon.modules.impl.combat.*
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.modules.impl.movement.*
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.modules.impl.player.*
import com.github.epsilon.modules.impl.render.*
import com.github.epsilon.modules.impl.render.maseffects.MasEffects
import com.github.epsilon.utils.client.KeybindUtils
import com.mojang.blaze3d.platform.InputConstants

object ModuleManager : AlwaysListening {

    private val modules = ArrayList<Module>()

    init {
        handler<KeyPressEvent>(alwaysListening = true) { event -> onKeyPress(event) }
        handler<MousePressEvent>(alwaysListening = true) { event -> onMousePress(event) }
    }

    fun initModules() {
        addModule(ClientSetting)
        addModule(GuiSetting)

        // Combat
        addModule(AimBot)
        addModule(AnchorBlast)
        addModule(AntiBot)
        addModule(Backtrack)
        addModule(AutoClicker)
        addModule(AutoDtap)
        addModule(AutoHitCrystal)
        addModule(AutoMend)
        addModule(AutoMine)
        addModule(AutoThrow)
        addModule(AutoTotem)
        addModule(AutoWeapon)
        addModule(Criticals)
        addModule(CrystalAura)
        addModule(CrystalBlocker)
        addModule(FeetTrap)
        addModule(DoubleAnchor)
        addModule(ElytraCombat)
        addModule(HoverTotem)
        addModule(KillAura)
        addModule(SpearAura)
        addModule(KeyPearl)
        addModule(MaceAura)
        addModule(MultiAura)
        addModule(PacketMine)
        addModule(SafeAnchor)
        addModule(SafeCrystal)
        addModule(SilentAim)
        addModule(SpearKill)
        addModule(TriggerBot)
        addModule(ZealotCrystalPlus)

        // Player
        addModule(AutoArmor)
        addModule(AutoFirework)
        addModule(AutoKouZi)
        addModule(AutoBan)
        addModule(AutoMLG)
        addModule(AutoTool)
        addModule(BedNuker)
        addModule(BreakCooldown)
        addModule(ChestAura)
        addModule(Disabler)
        addModule(ElytraSwap)
        addModule(FakePlayer)
        addModule(GhostHand)
        addModule(InputDisabler)
        addModule(InvManager)
        addModule(JumpCooldown)
        addModule(KeyFriend)
        addModule(MultiTask)
        addModule(NoFall)
        addModule(NoRotate)
        addModule(PacketEat)
        addModule(PlayerAlarms)
        addModule(SoundFX)
        addModule(Stealer)
        addModule(Timer)
        addModule(UseCooldown)
        addModule(AutoQueue)

        // Movement
        addModule(ElytraFly)
        addModule(ElytraGrimAccelerate)
        addModule(Dolphin)
        addModule(AutoSprint)
        addModule(Blink)
        addModule(Eagle)
        addModule(AutoMap)
        addModule(Flight)
        addModule(GUIMove)
        addModule(HoleSnap)
        addModule(JumpReset)
        addModule(KeepSprint)
        addModule(MovementFix)
        addModule(NoSlowdown)
        addModule(Phase)
        addModule(ReverseStep)
        addModule(SafeWalk)
        addModule(Scaffold)
        addModule(Speed)
        addModule(Step)
        addModule(Strafe)
        addModule(Stuck)
        addModule(TargetStrafe)
        addModule(Velocity)

        // Render
        addModule(ACSpoof)
        addModule(KillEffect)
        addModule(AntiAlias)
        addModule(AspectRatio)
        addModule(BetterChat)
        addModule(BetterScoreboard)
        addModule(BlockESP)
        addModule(BlockHighlight)
        addModule(CameraClip)
        addModule(Chams)
        addModule(CrystalChams)
        addModule(CustomSky)
        addModule(ESP2D)
        addModule(Filter)
        addModule(FreeCamera)
        addModule(Fullbright)
        addModule(GameAnimation)
        addModule(HandView)
        addModule(Hat)
        addModule(Health)
        addModule(HitParticles)
        addModule(HoleESP)
        addModule(ItemPhysics)
        addModule(JumpCircle)
        addModule(LogoutSpots)
        addModule(MasEffects)
        addModule(MotionBlur)
        addModule(NameTags)
        addModule(NoGhostBlocks)
        addModule(NoRender)
        addModule(Particles)
        addModule(PopChams)
        addModule(Shaders)
        addModule(Skeleton)
        addModule(SneakTweak)
        addModule(TitleReplace)
        addModule(TotemAnimation)
        addModule(TNTTimer)
        addModule(Trajectories)
        addModule(WorldTweaks)
        addModule(Xray)
    }

    private fun addModule(module: Module) {
        modules.add(module)
        module.initI18n(EpsilonTranslateComponent.create("modules", module.name.lowercase()))
    }

    fun getModules(): List<Module> {
        return modules
    }

    private fun onKeyPress(event: KeyPressEvent) {
        if (mc.level == null || mc.gui.screen() != null ||
            event.getKey() == InputConstants.UNKNOWN.value
        ) return

        val keyCode = event.getKey()
        val action = event.action

        if (keyCode == ClientSetting.guiKeybind && action == InputConstants.PRESS) {
            mc.gui.setScreen(ClientSetting.getGuiScreen())
        }

        dispatchKeyBind(keyCode, action)
    }

    private fun onMousePress(event: MousePressEvent) {
        if (mc.level != null && mc.gui.screen() == null) {
            dispatchKeyBind(KeybindUtils.encodeMouseButton(event.button), event.action)
        }
    }

    private fun dispatchKeyBind(keyCode: Int, action: Int) {
        val isPress = action == InputConstants.PRESS
        val isRelease = action == InputConstants.RELEASE

        val affectedModules = ArrayList<Module>()
        var hasEnabling = false

        for (module in modules) {
            if (module.keyBind != keyCode) continue

            if (module.bindMode == Module.BindMode.Toggle && isPress) {
                if (!module.isEnabled) {
                    hasEnabling = true
                }
                affectedModules.add(module)
            } else if (module.bindMode == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled) {
                    hasEnabling = true
                    affectedModules.add(module)
                } else if (isRelease && module.isEnabled) {
                    affectedModules.add(module)
                }
            }
        }

        for (module in affectedModules) {
            if (module.bindMode == Module.BindMode.Toggle) {
                module.toggle()
            } else if (module.bindMode == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled) {
                    module.isEnabled = true
                } else if (isRelease && module.isEnabled) {
                    module.isEnabled = false
                }
            }
        }

        if (affectedModules.isNotEmpty() && ClientSetting.soundNotify) {
            if (hasEnabling) {
                SoundManager.playInUi(SoundKey.ENABLE)
            } else {
                SoundManager.playInUi(SoundKey.DISABLE)
            }
        }
    }

}
