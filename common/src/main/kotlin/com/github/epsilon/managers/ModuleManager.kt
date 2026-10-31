package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslateComponent
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.impl.KeyPressEvent
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.modules.impl.GuiSetting
import com.github.epsilon.modules.impl.combat.AimBot
import com.github.epsilon.modules.impl.combat.AnchorBlast
import com.github.epsilon.modules.impl.combat.AntiBot
import com.github.epsilon.modules.impl.combat.AutoClicker
import com.github.epsilon.modules.impl.combat.AutoDtap
import com.github.epsilon.modules.impl.combat.AutoHitCrystal
import com.github.epsilon.modules.impl.combat.AutoMend
import com.github.epsilon.modules.impl.combat.AutoMine
import com.github.epsilon.modules.impl.combat.AutoThrow
import com.github.epsilon.modules.impl.combat.AutoTotem
import com.github.epsilon.modules.impl.combat.AutoWeapon
import com.github.epsilon.modules.impl.combat.Backtrack
import com.github.epsilon.modules.impl.combat.Criticals
import com.github.epsilon.modules.impl.combat.CrystalAura
import com.github.epsilon.modules.impl.combat.CrystalBlocker
import com.github.epsilon.modules.impl.combat.DoubleAnchor
import com.github.epsilon.modules.impl.combat.FeetTrap
import com.github.epsilon.modules.impl.combat.HoverTotem
import com.github.epsilon.modules.impl.combat.KeyPearl
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.modules.impl.combat.MaceAura
import com.github.epsilon.modules.impl.combat.MultiAura
import com.github.epsilon.modules.impl.combat.PacketMine
import com.github.epsilon.modules.impl.combat.SafeAnchor
import com.github.epsilon.modules.impl.combat.SafeCrystal
import com.github.epsilon.modules.impl.combat.SilentAim
import com.github.epsilon.modules.impl.combat.SpearAura
import com.github.epsilon.modules.impl.combat.SpearKill
import com.github.epsilon.modules.impl.combat.TriggerBot
import com.github.epsilon.modules.impl.combat.ZealotCrystalPlus
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.modules.impl.movement.AutoMap
import com.github.epsilon.modules.impl.movement.AutoSprint
import com.github.epsilon.modules.impl.movement.Blink
import com.github.epsilon.modules.impl.movement.Dolphin
import com.github.epsilon.modules.impl.movement.Eagle
import com.github.epsilon.modules.impl.movement.Flight
import com.github.epsilon.modules.impl.movement.GUIMove
import com.github.epsilon.modules.impl.movement.HoleSnap
import com.github.epsilon.modules.impl.movement.JumpReset
import com.github.epsilon.modules.impl.movement.KeepSprint
import com.github.epsilon.modules.impl.movement.MovementFix
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.modules.impl.movement.Phase
import com.github.epsilon.modules.impl.movement.ReverseStep
import com.github.epsilon.modules.impl.movement.SafeWalk
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.modules.impl.movement.Speed
import com.github.epsilon.modules.impl.movement.Step
import com.github.epsilon.modules.impl.movement.Strafe
import com.github.epsilon.modules.impl.movement.Stuck
import com.github.epsilon.modules.impl.movement.TargetStrafe
import com.github.epsilon.modules.impl.movement.Velocity
import com.github.epsilon.modules.impl.movement.ElytraGrimAccelerate
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.modules.impl.player.AutoArmor
import com.github.epsilon.modules.impl.player.AutoBan
import com.github.epsilon.modules.impl.player.AutoFirework
import com.github.epsilon.modules.impl.player.AutoKouZi
import com.github.epsilon.modules.impl.player.AutoMLG
import com.github.epsilon.modules.impl.player.AutoQueue
import com.github.epsilon.modules.impl.player.AutoTool
import com.github.epsilon.modules.impl.player.BedNuker
import com.github.epsilon.modules.impl.player.BreakCooldown
import com.github.epsilon.modules.impl.player.ChestAura
import com.github.epsilon.modules.impl.player.Disabler
import com.github.epsilon.modules.impl.player.ElytraSwap
import com.github.epsilon.modules.impl.player.FakePlayer
import com.github.epsilon.modules.impl.player.GhostHand
import com.github.epsilon.modules.impl.player.InputDisabler
import com.github.epsilon.modules.impl.player.InvManager
import com.github.epsilon.modules.impl.player.JumpCooldown
import com.github.epsilon.modules.impl.player.KeyFriend
import com.github.epsilon.modules.impl.player.MultiTask
import com.github.epsilon.modules.impl.player.NoFall
import com.github.epsilon.modules.impl.player.NoRotate
import com.github.epsilon.modules.impl.player.PacketEat
import com.github.epsilon.modules.impl.player.PlayerAlarms
import com.github.epsilon.modules.impl.player.SoundFX
import com.github.epsilon.modules.impl.player.Stealer
import com.github.epsilon.modules.impl.player.Timer
import com.github.epsilon.modules.impl.player.UseCooldown
import com.github.epsilon.modules.impl.render.ACSpoof
import com.github.epsilon.modules.impl.render.AntiAlias
import com.github.epsilon.modules.impl.render.AspectRatio
import com.github.epsilon.modules.impl.render.BetterChat
import com.github.epsilon.modules.impl.render.BetterScoreboard
import com.github.epsilon.modules.impl.render.BlockESP
import com.github.epsilon.modules.impl.render.BlockHighlight
import com.github.epsilon.modules.impl.render.CameraClip
import com.github.epsilon.modules.impl.render.Chams
import com.github.epsilon.modules.impl.render.CrystalChams
import com.github.epsilon.modules.impl.render.CustomSky
import com.github.epsilon.modules.impl.render.ESP2D
import com.github.epsilon.modules.impl.render.Filter
import com.github.epsilon.modules.impl.render.FreeCamera
import com.github.epsilon.modules.impl.render.Fullbright
import com.github.epsilon.modules.impl.render.GameAnimation
import com.github.epsilon.modules.impl.render.HandView
import com.github.epsilon.modules.impl.render.Hat
import com.github.epsilon.modules.impl.render.Health
import com.github.epsilon.modules.impl.render.HitParticles
import com.github.epsilon.modules.impl.render.HoleESP
import com.github.epsilon.modules.impl.render.ItemPhysics
import com.github.epsilon.modules.impl.render.JumpCircle
import com.github.epsilon.modules.impl.render.KillEffect
import com.github.epsilon.modules.impl.render.LogoutSpots
import com.github.epsilon.modules.impl.render.MotionBlur
import com.github.epsilon.modules.impl.render.NameTags
import com.github.epsilon.modules.impl.render.NoGhostBlocks
import com.github.epsilon.modules.impl.render.NoRender
import com.github.epsilon.modules.impl.render.Particles
import com.github.epsilon.modules.impl.render.PopChams
import com.github.epsilon.modules.impl.render.Shaders
import com.github.epsilon.modules.impl.render.Skeleton
import com.github.epsilon.modules.impl.render.SneakTweak
import com.github.epsilon.modules.impl.render.TNTTimer
import com.github.epsilon.modules.impl.render.TitleReplace
import com.github.epsilon.modules.impl.render.TotemAnimation
import com.github.epsilon.modules.impl.render.Trajectories
import com.github.epsilon.modules.impl.render.WorldTweaks
import com.github.epsilon.modules.impl.render.Xray
import com.github.epsilon.modules.impl.render.maseffects.MasEffects
import com.github.epsilon.utils.client.KeybindUtils
import com.mojang.blaze3d.platform.InputConstants
import java.util.ArrayList

object ModuleManager {

    private val modules = ArrayList<Module>()

    init {
        EventBus.handler<KeyPressEvent> { event -> onKeyPress(event) }
        EventBus.handler<MousePressEvent> { event -> onMousePress(event) }
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
        if (Constants.client.level == null || Constants.client.gui.screen() != null ||
            event.getKey() == InputConstants.UNKNOWN.value
        ) return

        val keyCode = event.getKey()
        val action = event.action

        if (keyCode == ClientSetting.guiKeybind && action == InputConstants.PRESS) {
            Constants.client.gui.setScreen(ClientSetting.getGuiScreen())
        }

        dispatchKeyBind(keyCode, action)
    }

    private fun onMousePress(event: MousePressEvent) {
        if (Constants.client.level != null && Constants.client.gui.screen() == null) {
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
