package com.github.epsilon.kernel.jvm

import com.github.epsilon.kernel.MinecraftWrapper
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.HitResult
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <R> runSafeOrElse(defaultValue: R, block: NonNullContext.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val instance = NonNullContext.instance
    return if (instance != null) {
        block.invoke(instance)
    } else {
        defaultValue
    }
}

@OptIn(ExperimentalContracts::class)
inline fun runSafeOrFalse(block: NonNullContext.() -> Boolean): Boolean {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val instance = NonNullContext.instance
    return if (instance != null) {
        block.invoke(instance)
    } else {
        false
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R> runSafe(block: NonNullContext.() -> R): R? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val instance = NonNullContext.instance
    return if (instance != null) {
        block.invoke(instance)
    } else {
        null
    }
}

suspend fun <R> runSafeSuspend(block: suspend NonNullContext.() -> R): R? {
    return NonNullContext.instance?.let { block(it) }
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any, R> T.runSynchronized(block: T.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return synchronized(this@runSynchronized) {
        block.invoke(this@runSynchronized)
    }
}

abstract class AbstractContext {
    val mc = MinecraftWrapper.mc
    abstract val level: ClientLevel?
    abstract val player: LocalPlayer?
    abstract val connection: ClientPacketListener?
    abstract val gameMode: MultiPlayerGameMode?
    abstract val hitResult: HitResult?
    abstract val cameraEntity: Entity?
}

open class NonNullContext internal constructor(
    override val level: ClientLevel,
    override val player: LocalPlayer,
    override val connection: ClientPacketListener,
    override val gameMode: MultiPlayerGameMode,
    override val hitResult: HitResult?,
    override val cameraEntity: Entity?
) : AbstractContext() {
    inline operator fun <T> invoke(block: NonNullContext.() -> T) = run(block)

    companion object {
        @Volatile
        var instance: NonNullContext? = null; private set

        @JvmStatic
        fun update() {
            val level = MinecraftWrapper.level ?: return reset()
            val player = MinecraftWrapper.player ?: return reset()
            val connection = MinecraftWrapper.connection ?: return reset()
            val gameMode = MinecraftWrapper.gameMode ?: return reset()
            instance = NonNullContext(
                level,
                player,
                connection,
                gameMode,
                MinecraftWrapper.hitResult,
                MinecraftWrapper.cameraEntity
            )
        }

        @JvmStatic
        fun reset() {
            instance = null
        }
    }
}
