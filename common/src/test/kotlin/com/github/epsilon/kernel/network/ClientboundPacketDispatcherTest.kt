package com.github.epsilon.kernel.network

import com.github.epsilon.events.api.Handler
import com.github.epsilon.events.impl.PacketEvent
import net.minecraft.network.PacketProcessor
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientboundPacketDispatcherTest {
    private val listener = Proxy.newProxyInstance(
        ClientGamePacketListener::class.java.classLoader,
        arrayOf(ClientGamePacketListener::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "shouldHandleMessage", "isAcceptingMessages" -> true
            else -> null
        }
    } as ClientGamePacketListener

    private class TestPacket(val name: String, val trace: MutableList<String>) : Packet<ClientGamePacketListener> {
        override fun type(): PacketType<out Packet<ClientGamePacketListener>> = error("Not serialized")
        override fun handle(listener: ClientGamePacketListener) { trace.add("handle:$name") }
    }

    @Test
    fun queuedPacketsAndBundleAreObservedBeforeHandlingInOrder() {
        val mainThread = Thread.currentThread()
        val processor = PacketProcessor(mainThread)
        val trace = mutableListOf<String>()
        val observed = Handler(this, PacketEvent.ReceiveMain.busID, 0) {
            assertEquals(mainThread, Thread.currentThread())
            trace.add("observe:${((it as PacketEvent.ReceiveMain).packet as TestPacket).name}")
        }
        PacketEvent.ReceiveMain.subscribe(observed)
        try {
            val failure = AtomicReference<Throwable>()
            val worker = Thread {
                try {
                    ClientboundPacketDispatcher.replay(TestPacket("a", trace), listener, processor) { true }
                    val bundle = ClientboundBundlePacket(listOf(TestPacket("b", trace), TestPacket("c", trace)))
                    ClientboundPacketDispatcher.replay(bundle, listener, processor) { true }
                } catch (e: Throwable) { failure.set(e) }
            }
            worker.start()
            worker.join()
            failure.get()?.let { throw it }
            assertTrue(trace.isEmpty())
            processor.processQueuedPackets()
            assertEquals(listOf("observe:a", "handle:a", "observe:b", "handle:b", "observe:c", "handle:c"), trace)
        } finally {
            PacketEvent.ReceiveMain.unsubscribe(observed)
            processor.close()
        }
    }

    @Test
    fun oldConnectionIsDiscardedAndReplayDoesNotRepeatInterception() {
        val processor = PacketProcessor(Thread.currentThread())
        val current = AtomicBoolean(true)
        val trace = mutableListOf<String>()
        var intercepts = 0
        val interceptor = Handler(this, PacketEvent.ReceiveNetty.busID, 0) { intercepts++ }
        PacketEvent.ReceiveNetty.subscribe(interceptor)
        try {
            val worker = Thread {
                ClientboundPacketDispatcher.replay(TestPacket("old", trace), listener, processor, current::get)
            }
            worker.start()
            worker.join()
            current.set(false)
            processor.processQueuedPackets()
            assertTrue(trace.isEmpty())
            current.set(true)
            ClientboundPacketDispatcher.replay(TestPacket("cached", trace), listener, processor, current::get)
            assertEquals(listOf("handle:cached"), trace)
            assertEquals(0, intercepts)
        } finally {
            PacketEvent.ReceiveNetty.unsubscribe(interceptor)
            processor.close()
        }
    }

    @Test
    fun interceptionCanReplaceOrCancelWithoutPostingMainEvent() {
        val trace = mutableListOf<String>()
        val original = TestPacket("original", trace)
        val replacement = TestPacket("replacement", trace)
        var mainEvents = 0
        val observer = Handler(this, PacketEvent.ReceiveMain.busID, 0) { mainEvents++ }
        val interceptor = Handler(this, PacketEvent.ReceiveNetty.busID, 0) {
            (it as PacketEvent.ReceiveNetty).packet = replacement
            it.cancel()
        }
        PacketEvent.ReceiveMain.subscribe(observer)
        PacketEvent.ReceiveNetty.subscribe(interceptor)
        try {
            val event = PacketEvent.ReceiveNetty(original)
            assertFalse(event.isCancelled)
            event.post()
            assertTrue(event.isCancelled)
            assertEquals(replacement, event.packet)
            assertEquals(0, mainEvents)
            assertTrue(trace.isEmpty())
            // Cancellation exits the actual synthetic receive path before accessing Minecraft.
            ClientboundPacketDispatcher.receive(original, listener)
            assertEquals(0, mainEvents)
            assertTrue(trace.isEmpty())
        } finally {
            PacketEvent.ReceiveNetty.unsubscribe(interceptor)
            PacketEvent.ReceiveMain.unsubscribe(observer)
        }
    }
}
