package com.github.epsilon.kernel

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.HitResult

object MinecraftWrapper {

    @JvmField
    val mc: Minecraft = Minecraft.getInstance()

    @JvmStatic
    val player: LocalPlayer? get() = mc.player

    @JvmStatic
    val level: ClientLevel? get() = mc.level

    @JvmStatic
    val connection: ClientPacketListener? get() = mc.connection

    @JvmStatic
    val gameMode: MultiPlayerGameMode? get() = mc.gameMode

    @JvmStatic
    val hitResult: HitResult? get() = mc.hitResult

    @JvmStatic
    val cameraEntity: Entity? get() = mc.cameraEntity

}
