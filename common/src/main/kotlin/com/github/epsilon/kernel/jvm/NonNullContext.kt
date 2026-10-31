package com.github.epsilon.kernel.jvm

import com.github.epsilon.events.api.ListenerOwner
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.GameLeftEvent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import com.github.epsilon.kernel.MinecraftWrapper
import com.github.epsilon.kernel.threads.MainThreadExecutor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.HitResult

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

fun <T> onMainThreadSafe(block: NonNullContext.() -> T) =
    onMainThread { NonNullContext.instance?.block() }

fun <T> onMainThread(block: () -> T) =
    MainThreadExecutor.add(block)

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
    override val hitResult: HitResult,
    override val cameraEntity: Entity
) : AbstractContext() {
    inline operator fun <T> invoke(block: NonNullContext.() -> T) = run(block)

    companion object : ListenerOwner() {
        var instance: NonNullContext? = null; private set

        init {
            handler<GameLeftEvent>(Int.MAX_VALUE, true) {
                reset()
            }

//            handler<WorldEvent.Unload>(Int.MAX_VALUE, true) {
//                reset()
//            }

            handler<ClientTickEvent.Post>(Int.MAX_VALUE, true) {
                update()
            }
        }

        fun update() {
            val level = MinecraftWrapper.level ?: return
            val player = MinecraftWrapper.player ?: return
            val connection = MinecraftWrapper.mc.connection ?: return
            val gameMode = MinecraftWrapper.gameMode ?: return
            val hitResult = MinecraftWrapper.hitResult ?: return
            val cameraEntity = MinecraftWrapper.cameraEntity ?: return

            instance = NonNullContext(
                level,
                player,
                connection,
                gameMode,
                hitResult,
                cameraEntity
            )
        }

        fun reset() {
            instance = null
        }
    }
}
