package com.github.epsilon.native.winrt

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * Windows Runtime（WinRT）COM 互操作的 Panama FFM 底层封装。
 *
 * 设计要点：
 * - 只使用 JDK 22 起定稿的 java.lang.foreign（JEP 454），不依赖 JNI，也不加载任何原生库；
 * - 手工走 COM vtable：所有槽位号来自 Windows SDK 的 winrt 头文件里的 MIDL 声明顺序，
 *   vtable 第 0/1/2 槽是 QueryInterface/AddRef/Release，第 3..5 槽是 IInspectable，
 *   之后才是接口自己的方法；
 * - 异步操作（IAsyncOperation<T>）通过 QueryInterface 到 IAsyncInfo 轮询 get_Status 等待完成，
 *   再调用 IAsyncOperation<T> 的 GetResults（槽位 8）；
 * - out 参数与 HSTRING 缓冲区由调用方传入的 Arena 管理；接口指针必须显式 [release]。
 */
internal object WinRtNative {

    const val RO_INIT_MULTITHREADED = 1

    private const val S_OK = 0
    private const val S_FALSE = 1
    private val RPC_E_CHANGED_MODE: Int = 0x80010106.toInt()

    private const val ASYNC_STATUS_COMPLETED = 1
    private const val ASYNC_STATUS_CANCELED = 2
    private const val ASYNC_STATUS_ERROR = 3
    private const val ASYNC_TIMEOUT_NANOS = 5_000_000_000L

    private const val FORMAT_MESSAGE_FROM_SYSTEM = 0x00001000
    private const val FORMAT_MESSAGE_IGNORE_INSERTS = 0x00000200

    private val INT = ValueLayout.JAVA_INT
    private val LONG = ValueLayout.JAVA_LONG
    private val SHORT = ValueLayout.JAVA_SHORT
    private val BYTE = ValueLayout.JAVA_BYTE
    private val WCHAR = ValueLayout.JAVA_CHAR
    private val ADDR = ValueLayout.ADDRESS

    private val arena: Arena = Arena.ofShared()
    private val linker: Linker = Linker.nativeLinker()
    private val combase: SymbolLookup = SymbolLookup.libraryLookup("combase.dll", arena)
    private val kernel32: SymbolLookup? = runCatching { SymbolLookup.libraryLookup("kernel32.dll", arena) }.getOrNull()

    private val roInitialize: MethodHandle = handle("RoInitialize", FunctionDescriptor.of(INT, INT))
    private val roGetActivationFactory: MethodHandle = handle(
        "RoGetActivationFactory", FunctionDescriptor.of(INT, ADDR, ADDR, ADDR),
    )
    private val windowsCreateString: MethodHandle = handle(
        "WindowsCreateString", FunctionDescriptor.of(INT, ADDR, INT, ADDR),
    )
    private val windowsDeleteString: MethodHandle = handle("WindowsDeleteString", FunctionDescriptor.of(INT, ADDR))
    private val windowsGetStringRawBuffer: MethodHandle = handle(
        "WindowsGetStringRawBuffer", FunctionDescriptor.of(ADDR, ADDR, ADDR),
    )
    private val formatMessage: MethodHandle? = kernel32?.find("FormatMessageW")?.orElse(null)
        ?.let { linker.downcallHandle(it, FunctionDescriptor.of(INT, INT, ADDR, INT, INT, ADDR, INT, ADDR)) }

    private val apartmentReady = ThreadLocal<Boolean>()

    /** Windows.Media.Control.IGlobalSystemMediaTransportControlsSessionManagerStatics */
    val IID_MANAGER_STATICS: MemorySegment = guid("2050c4ee-11a0-57de-aed7-c97c70338245")

    /** Windows.Storage.Streams.IDataReaderFactory */
    val IID_DATA_READER_FACTORY: MemorySegment = guid("d7527847-57da-4e15-914c-06806699a098")

    /** Windows.Storage.Streams.IRandomAccessStream */
    val IID_RANDOM_ACCESS_STREAM: MemorySegment = guid("905a0fe1-bc53-11df-8c49-001e4fc686da")

    /** Windows.Foundation.IClosable */
    val IID_CLOSABLE: MemorySegment = guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e")

    /** Windows.Foundation.IAsyncInfo */
    val IID_ASYNC_INFO: MemorySegment = guid("00000036-0000-0000-c000-000000000046")

    /**
     * 在当前线程初始化 WinRT 公寓（MTA）。
     *
     * 每个线程只初始化一次；`RoInitialize` 对已初始化的线程返回 S_FALSE，
     * 线程已处于 STA 时返回 RPC_E_CHANGED_MODE，两种情况都不影响后续调用。
     */
    fun initializeApartment() {
        if (apartmentReady.get() == true) return
        val result = callInt(roInitialize, RO_INIT_MULTITHREADED)
        if (result != S_OK && result != S_FALSE && result != RPC_E_CHANGED_MODE) {
            throw WinRtException(result, "RoInitialize")
        }
        apartmentReady.set(true)
    }

    /** 通过 RoGetActivationFactory 获取激活工厂上的指定接口。 */
    fun activationFactory(className: String, iid: MemorySegment, arena: Arena): MemorySegment {
        val classId = createHString(className, arena)
        try {
            val out = arena.allocate(8, 8)
            checkHr(callInt(roGetActivationFactory, classId, iid, out), "RoGetActivationFactory($className)")
            val factory = out.get(ADDR, 0)
            if (factory.address() == 0L) throw WinRtException(S_OK, "RoGetActivationFactory($className)")
            return factory
        } finally {
            deleteHString(classId)
        }
    }

    fun createHString(value: String, arena: Arena): MemorySegment {
        val length = value.length
        val buffer = arena.allocate(maxOf(2L, length.toLong() * 2), 2)
        for (index in 0 until length) {
            buffer.set(WCHAR, index.toLong() * 2, value[index])
        }
        val out = arena.allocate(8, 8)
        checkHr(callInt(windowsCreateString, buffer, length, out), "WindowsCreateString")
        return out.get(ADDR, 0)
    }

    fun deleteHString(hstring: MemorySegment?) {
        if (hstring == null || hstring.address() == 0L) return
        runCatching { callInt(windowsDeleteString, hstring) }
    }

    fun readHString(hstring: MemorySegment, arena: Arena): String {
        if (hstring.address() == 0L) return ""
        val lengthOut = arena.allocate(4, 4)
        val raw = callAddress(windowsGetStringRawBuffer, hstring, lengthOut)
        val length = lengthOut.get(INT, 0)
        if (length <= 0) return ""
        val chars = CharArray(length)
        val text = raw.reinterpret(length.toLong() * 2)
        for (index in 0 until length) {
            chars[index] = text.get(WCHAR, index.toLong() * 2)
        }
        return String(chars)
    }

    /** 释放一个 COM 接口指针（vtable 第 2 槽 Release）。 */
    fun release(reference: MemorySegment?) {
        if (reference == null || reference.address() == 0L) return
        runCatching {
            val function = vtableSlot(reference, 2)
            val method = linker.downcallHandle(MemorySegment.ofAddress(function.address()), FunctionDescriptor.of(INT, ADDR))
            invokeMethod(method, arrayOf(reference))
        }
    }

    /** QueryInterface；接口不支持时返回 null。 */
    fun queryInterface(reference: MemorySegment, iid: MemorySegment, arena: Arena): MemorySegment? {
        val function = vtableSlot(reference, 0)
        val method = linker.downcallHandle(
            MemorySegment.ofAddress(function.address()),
            FunctionDescriptor.of(INT, ADDR, ADDR, ADDR),
        )
        val out = arena.allocate(8, 8)
        val result = (invokeMethod(method, arrayOf(reference, iid, out)) as Number).toInt()
        if (result < 0) return null
        val queried = out.get(ADDR, 0)
        return if (queried.address() == 0L) null else queried
    }

    /** 尽力调用 IClosable.Close，失败忽略。 */
    fun tryClose(reference: MemorySegment?, arena: Arena) {
        if (reference == null || reference.address() == 0L) return
        val closable = queryInterface(reference, IID_CLOSABLE, arena) ?: return
        try {
            callHr(closable, 6)
        } catch (_: Throwable) {
            // 关闭失败不影响后续 Release，忽略。
        } finally {
            release(closable)
        }
    }

    /** 调用只带 out 指针参数的方法，返回 out 指针（可能为 null）。 */
    fun callOutPtr(owner: MemorySegment, slot: Int, arena: Arena): MemorySegment? {
        return callOutPtrWithDescriptor(owner, slot, FunctionDescriptor.of(INT, ADDR, ADDR), arena, emptyArray())
    }

    /** 调用带一个指针入参和 out 指针的方法，例如 IDataReaderFactory.CreateDataReader。 */
    fun callOutPtrWithPtrArg(owner: MemorySegment, slot: Int, argument: MemorySegment, arena: Arena): MemorySegment? {
        return callOutPtrWithDescriptor(
            owner, slot, FunctionDescriptor.of(INT, ADDR, ADDR, ADDR), arena, arrayOf<Any?>(argument),
        )
    }

    /** 调用带一个 UInt32 入参和 out 指针的方法，例如 IDataReader.LoadAsync。 */
    fun callOutPtrWithIntArg(owner: MemorySegment, slot: Int, argument: Int, arena: Arena): MemorySegment? {
        return callOutPtrWithDescriptor(
            owner, slot, FunctionDescriptor.of(INT, ADDR, INT, ADDR), arena, arrayOf<Any?>(argument),
        )
    }

    /** 调用带一个 UInt64 入参和 out 指针的方法，例如 IRandomAccessStream.GetInputStreamAt。 */
    fun callOutPtrWithLongArg(owner: MemorySegment, slot: Int, argument: Long, arena: Arena): MemorySegment? {
        return callOutPtrWithDescriptor(
            owner, slot, FunctionDescriptor.of(INT, ADDR, LONG, ADDR), arena, arrayOf<Any?>(argument),
        )
    }

    private fun callOutPtrWithDescriptor(
        owner: MemorySegment,
        slot: Int,
        descriptor: FunctionDescriptor,
        arena: Arena,
        arguments: Array<out Any?>,
    ): MemorySegment? {
        val function = vtableSlot(owner, slot)
        val method = linker.downcallHandle(MemorySegment.ofAddress(function.address()), descriptor)
        val out = arena.allocate(8, 8)
        val callArguments = arrayOfNulls<Any?>(arguments.size + 2)
        callArguments[0] = owner
        for (index in arguments.indices) {
            callArguments[index + 1] = arguments[index]
        }
        callArguments[callArguments.size - 1] = out
        val result = (invokeMethod(method, callArguments) as Number).toInt()
        checkHr(result, "vtable slot $slot")
        val pointer = out.get(ADDR, 0)
        return if (pointer.address() == 0L) null else pointer
    }

    /** 调用返回 Int32（out 参数）的属性，例如 get_PlaybackStatus。 */
    fun callOutInt(owner: MemorySegment, slot: Int, arena: Arena): Int {
        val function = vtableSlot(owner, slot)
        val method = linker.downcallHandle(
            MemorySegment.ofAddress(function.address()),
            FunctionDescriptor.of(INT, ADDR, ADDR),
        )
        val out = arena.allocate(4, 4)
        val result = (invokeMethod(method, arrayOf(owner, out)) as Number).toInt()
        checkHr(result, "vtable slot $slot")
        return out.get(INT, 0)
    }

    /** 调用返回 UInt64（out 参数）的属性，例如 IRandomAccessStream.get_Size。 */
    fun callOutLong(owner: MemorySegment, slot: Int, arena: Arena): Long {
        val function = vtableSlot(owner, slot)
        val method = linker.downcallHandle(
            MemorySegment.ofAddress(function.address()),
            FunctionDescriptor.of(INT, ADDR, ADDR),
        )
        val out = arena.allocate(8, 8)
        val result = (invokeMethod(method, arrayOf(owner, out)) as Number).toInt()
        checkHr(result, "vtable slot $slot")
        return out.get(LONG, 0)
    }

    /** 调用返回 HSTRING 的属性；读取后立即删除 HSTRING 引用。 */
    fun callOutHString(owner: MemorySegment, slot: Int, arena: Arena): String {
        val hstring = callOutPtr(owner, slot, arena) ?: return ""
        try {
            return readHString(hstring, arena)
        } finally {
            deleteHString(hstring)
        }
    }

    /** 调用只返回 HRESULT 的无参方法，例如 IClosable.Close。 */
    fun callHr(owner: MemorySegment, slot: Int): Int {
        val function = vtableSlot(owner, slot)
        val method = linker.downcallHandle(MemorySegment.ofAddress(function.address()), FunctionDescriptor.of(INT, ADDR))
        return (invokeMethod(method, arrayOf<Any?>(owner)) as Number).toInt()
    }

    /** 读取 IDataReader.ReadBytes（槽位 14）的结果。 */
    fun readBytes(reader: MemorySegment, count: Int, arena: Arena): ByteArray {
        val function = vtableSlot(reader, 14)
        val method = linker.downcallHandle(
            MemorySegment.ofAddress(function.address()),
            FunctionDescriptor.of(INT, ADDR, INT, ADDR),
        )
        val buffer = arena.allocate(count.toLong().coerceAtLeast(1L), 8)
        val result = (invokeMethod(method, arrayOf<Any?>(reader, count, buffer)) as Number).toInt()
        checkHr(result, "IDataReader.ReadBytes")
        return buffer.toArray(ValueLayout.JAVA_BYTE).copyOf(count)
    }

    /** 轮询 IAsyncInfo 状态直到异步操作完成；取消、失败、超时都抛出 [WinRtException]。 */
    fun awaitAsync(operation: MemorySegment, arena: Arena) {
        val asyncInfo = queryInterface(operation, IID_ASYNC_INFO, arena)
            ?: throw WinRtException(S_OK, "QueryInterface(IAsyncInfo)")
        try {
            val statusOut = arena.allocate(4, 4)
            val errorOut = arena.allocate(4, 4)
            val deadline = System.nanoTime() + ASYNC_TIMEOUT_NANOS
            while (true) {
                checkHr(callIntAt(asyncInfo, 7, statusOut), "IAsyncInfo.get_Status")
                when (statusOut.get(INT, 0)) {
                    ASYNC_STATUS_COMPLETED -> return
                    ASYNC_STATUS_CANCELED -> throw WinRtException(S_OK, "IAsyncOperation canceled")
                    ASYNC_STATUS_ERROR -> {
                        val errorCode = callIntAt(asyncInfo, 8, errorOut)
                        checkHr(errorCode, "IAsyncInfo.get_ErrorCode")
                        throw WinRtException(errorOut.get(INT, 0), "IAsyncOperation failed")
                    }
                    else -> Unit
                }
                if (System.nanoTime() > deadline) {
                    runCatching { callHrAt(asyncInfo, 9) } // Cancel
                    throw WinRtException(S_OK, "IAsyncOperation timeout")
                }
                try {
                    Thread.sleep(1L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw WinRtException(S_OK, "IAsyncOperation interrupted")
                }
            }
        } finally {
            release(asyncInfo)
        }
    }

    /** 等待异步操作完成并读取结果的接口指针。 */
    fun asyncInterface(operation: MemorySegment, arena: Arena): MemorySegment? {
        awaitAsync(operation, arena)
        val out = arena.allocate(8, 8)
        val result = (invokeMethod(getResultsHandle(operation), arrayOf<Any?>(operation, out)) as Number).toInt()
        checkHr(result, "IAsyncOperation.GetResults")
        val pointer = out.get(ADDR, 0)
        return if (pointer.address() == 0L) null else pointer
    }

    /** 等待异步操作完成并读取 UInt32 结果。 */
    fun asyncUInt(operation: MemorySegment, arena: Arena): Int {
        awaitAsync(operation, arena)
        val out = arena.allocate(4, 4)
        val result = (invokeMethod(getResultsHandle(operation), arrayOf<Any?>(operation, out)) as Number).toInt()
        checkHr(result, "IAsyncOperation.GetResults")
        return out.get(INT, 0)
    }

    fun checkHr(hresult: Int, step: String) {
        if (hresult < 0) throw WinRtException(hresult, step)
    }

    /** HRESULT 的可读描述，含系统消息（FormatMessageW）。 */
    fun describeHresult(hresult: Int): String {
        val hex = String.format("0x%08X", hresult)
        val text = runCatching { systemMessage(hresult) }.getOrNull()
        return if (text.isNullOrBlank()) hex else "$hex $text"
    }

    private fun systemMessage(hresult: Int): String {
        val formatter = formatMessage ?: return ""
        val temp = Arena.ofConfined()
        try {
            val buffer = temp.allocate(1024, 2)
            val length = (invokeMethod(
                formatter,
                arrayOf<Any?>(
                    FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS,
                    MemorySegment.NULL,
                    hresult,
                    0,
                    buffer,
                    512,
                    MemorySegment.NULL,
                ),
            ) as Number).toInt()
            if (length <= 0) return ""
            val chars = CharArray(length)
            for (index in 0 until length) {
                chars[index] = buffer.get(WCHAR, index.toLong() * 2)
            }
            return String(chars).trim()
        } finally {
            temp.close()
        }
    }

    private fun getResultsHandle(operation: MemorySegment): MethodHandle {
        val function = vtableSlot(operation, 8)
        return linker.downcallHandle(
            MemorySegment.ofAddress(function.address()),
            FunctionDescriptor.of(INT, ADDR, ADDR),
        )
    }

    private fun callIntAt(owner: MemorySegment, slot: Int, out: MemorySegment): Int {
        val function = vtableSlot(owner, slot)
        val method = linker.downcallHandle(
            MemorySegment.ofAddress(function.address()),
            FunctionDescriptor.of(INT, ADDR, ADDR),
        )
        return (invokeMethod(method, arrayOf<Any?>(owner, out)) as Number).toInt()
    }

    private fun callHrAt(owner: MemorySegment, slot: Int): Int {
        val function = vtableSlot(owner, slot)
        val method = linker.downcallHandle(MemorySegment.ofAddress(function.address()), FunctionDescriptor.of(INT, ADDR))
        return (invokeMethod(method, arrayOf<Any?>(owner)) as Number).toInt()
    }

    private fun vtableSlot(owner: MemorySegment, slot: Int): MemorySegment {
        val table = owner.reinterpret(8).get(ADDR, 0)
        return table.reinterpret((slot + 1).toLong() * 8).get(ADDR, slot.toLong() * 8)
    }

    private fun handle(name: String, descriptor: FunctionDescriptor): MethodHandle {
        val symbol = combase.find(name).orElseThrow { IllegalStateException("Missing combase export: $name") }
        return linker.downcallHandle(symbol, descriptor)
    }

    private fun callInt(method: MethodHandle, vararg arguments: Any?): Int {
        return (invokeMethod(method, arrayOf(*arguments)) as Number).toInt()
    }

    private fun callAddress(method: MethodHandle, vararg arguments: Any?): MemorySegment {
        return invokeMethod(method, arrayOf(*arguments)) as MemorySegment
    }

    private fun invokeMethod(method: MethodHandle, arguments: Array<out Any?>): Any? {
        return method.invokeWithArguments(*arguments)
    }

    private fun guid(text: String): MemorySegment {
        val parts = text.split('-')
        val segment = arena.allocate(16, 4)
        segment.set(INT, 0, parts[0].toLong(16).toInt())
        segment.set(SHORT, 4, parts[1].toInt(16).toShort())
        segment.set(SHORT, 6, parts[2].toInt(16).toShort())
        val tail = parts[3] + parts[4]
        for (index in 0 until 8) {
            segment.set(BYTE, 8L + index, tail.substring(index * 2, index * 2 + 2).toInt(16).toByte())
        }
        return segment
    }
}

/** WinRT/COM 调用失败。 */
internal class WinRtException(
    val hresult: Int,
    val step: String? = null,
) : RuntimeException(
    buildString {
        append(step ?: "WinRT call")
        if (hresult != 0) {
            append(" failed: ")
            append(WinRtNative.describeHresult(hresult))
        }
    },
)
