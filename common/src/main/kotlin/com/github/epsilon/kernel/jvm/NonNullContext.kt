package com.github.epsilon.kernel.jvm

import com.github.epsilon.events.api.AlwaysListening
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.*
import com.github.epsilon.kernel.MinecraftWrapper
import net.minecraft.client.Minecraft
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
    val mc: Minecraft get() = MinecraftWrapper.mc
    abstract val level: ClientLevel?
    abstract val player: LocalPlayer?
    abstract val connection: ClientPacketListener?
    abstract val gameMode: MultiPlayerGameMode?
    abstract val hitResult: HitResult?
    abstract val cameraEntity: Entity?
}

/**
 * 主线程生命周期事件发布的非空快照。玩家、世界、交互管理器和连接必需；
 * 首次进入世界时尚未完成拾取/相机初始化，不以这两个可空字段阻断游戏逻辑。
 */
open class NonNullContext internal constructor(
    override val level: ClientLevel,
    override val player: LocalPlayer,
    override val connection: ClientPacketListener,
    override val gameMode: MultiPlayerGameMode,
    override val hitResult: HitResult?,
    override val cameraEntity: Entity?
) : AbstractContext() {
    inline operator fun <T> invoke(block: NonNullContext.() -> T) = run(block)

    companion object : AlwaysListening {
        @Volatile
        var instance: NonNullContext? = null
            private set

        init {
            handler<GameLeftEvent>(Int.MAX_VALUE, true) { reset() }
            handler<LevelUpdateEvent>(Int.MAX_VALUE, true) { reset() }
            handler<GameJoinedEvent>(Int.MAX_VALUE, true) { update() }
            handler<RespawnEvent>(Int.MAX_VALUE, true) { update() }
            // 必须早于 tick 监听器和主线程队列，不能沿用 CLion 的帧尾 tick 名称。
            handler<ClientTickEvent.Pre>(Int.MAX_VALUE, true) { update() }
        }

        fun update() {
            val level = MinecraftWrapper.level ?: return reset()
            val player = MinecraftWrapper.player ?: return reset()
            val connection = MinecraftWrapper.connection ?: return reset()
            val gameMode = MinecraftWrapper.gameMode ?: return reset()
            // hitResult 和 cameraEntity 在首次进入世界时可以为空，不能阻断世界内逻辑。
            instance = NonNullContext(
                level, player, connection, gameMode,
                MinecraftWrapper.hitResult, MinecraftWrapper.cameraEntity
            )
        }

        fun reset() {
            instance = null
        }
    }
}

context(nn: NonNullContext)
val player: LocalPlayer get() = nn.player

context(nn: NonNullContext)
val level: ClientLevel get() = nn.level

context(nn: NonNullContext)
val gameMode: MultiPlayerGameMode get() = nn.gameMode

context(nn: NonNullContext)
val connection: ClientPacketListener get() = nn.connection

context(nn: NonNullContext)
val mc: Minecraft get() = nn.mc
