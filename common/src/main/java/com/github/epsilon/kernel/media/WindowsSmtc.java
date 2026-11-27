package com.github.epsilon.kernel.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.*;

public class WindowsSmtc implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final Linker linker = Linker.nativeLinker();
    private final Map<Integer, MethodHandle> getters = new HashMap<>();
    private final MethodHandle initialize, uninitialize, createString, deleteString, rawString, activation;
    private final MethodHandle release, query, noArgs;
    private final MethodHandle streamBridge, streamRead;
    private byte[] cachedEncoded;
    private MediaArtwork cachedArtwork;
    private String artworkTrack;
    private long artworkCheckedNanos;
    private boolean initialized;
    private MemorySegment manager = MemorySegment.NULL;

    static WindowsSmtc open() throws Exception {
        WindowsSmtc instance = new WindowsSmtc();
        try {
            instance.start();
            return instance;
        } catch (Exception | Error failure) {
            instance.close();
            throw failure;
        }
    }

    private WindowsSmtc() {
        try {
            SymbolLookup combase = SymbolLookup.libraryLookup("combase.dll", arena);
            initialize = symbol(combase, "RoInitialize", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
            uninitialize = symbol(combase, "RoUninitialize", FunctionDescriptor.ofVoid());
            createString = symbol(combase, "WindowsCreateString", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
            deleteString = symbol(combase, "WindowsDeleteString", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            rawString = symbol(combase, "WindowsGetStringRawBuffer", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            activation = symbol(combase, "RoGetActivationFactory", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            SymbolLookup shcore = SymbolLookup.libraryLookup("shcore.dll", arena);
            streamBridge = symbol(shcore, "CreateStreamOverRandomAccessStream", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            streamRead = linker.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
            release = linker.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));
            query = linker.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            noArgs = linker.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));
        } catch (RuntimeException | Error failure) {
            arena.close();
            throw failure;
        }
    }

    private void start() throws Exception {
        check((int) invoke(initialize, 1)); // RO_INIT_MULTITHREADED; S_FALSE also requires RoUninitialize.
        initialized = true;
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment className = hstring(scratch, "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager");
            try {
                MemorySegment output = scratch.allocate(ADDRESS);
                check((int) invoke(activation, className, guid(scratch, "2050c4ee-11a0-57de-aed7-c97c70338245"), output));
                MemorySegment factory = output.get(ADDRESS, 0);
                try {
                    manager = await(object(factory, 6));
                } finally {
                    release(factory);
                }
            } finally {
                invoke(deleteString, className);
            }
        }
    }

    MediaSessionSnapshot read() throws Exception {
        MemorySegment session = object(manager, 6); // GetCurrentSession: follow Windows' active session.
        if (session.address() == 0) {
            clearArtwork();
            return null;
        }
        try {
            String source = string(session, 6);
            MemorySegment playback = object(session, 9);
            int status;
            try {
                status = integer(playback, 7);
            } finally {
                release(playback);
            }
            // Closed=0, Opened=1, Changing=2, Stopped=3, Playing=4, Paused=5.
            if (status != 4 && status != 5) {
                clearArtwork();
                return null;
            }
            MemorySegment properties = await(object(session, 7));
            String title, artist;
            MediaArtwork artwork;
            try {
                title = string(properties, 6);
                artist = string(properties, 9);
                artwork = artwork(properties, source + "\0" + title + "\0" + artist);
            } finally {
                release(properties);
            }
            if (title.isBlank()) title = source;
            if (title.isBlank()) return null;
            MemorySegment timeline = object(session, 8);
            long start, end, position;
            try {
                start = number(timeline, 6);
                end = number(timeline, 7);
                position = number(timeline, 10);
            } finally {
                release(timeline);
            }
            // WinRT TimeSpan ticks are 100 ns. Some apps supply no timeline (duration=0).
            long duration = Math.max(0L, (end - start) / 10_000L);
            long relative = Math.clamp((position - start) / 10_000L, 0L, duration);
            return new MediaSessionSnapshot(source, title, artist, status == 4, relative, duration, System.nanoTime(), artwork);
        } finally {
            release(session);
        }
    }

    private void clearArtwork() {
        cachedEncoded = null;
        cachedArtwork = null;
        artworkTrack = null;
        artworkCheckedNanos = 0L;
    }

    private MediaArtwork artwork(MemorySegment properties, String track) throws Exception {
        long now = System.nanoTime();
        if (!track.equals(artworkTrack)) {
            clearArtwork();
            artworkTrack = track;
        }
        // Retry delayed artwork and detect updates even when title/artist stay unchanged.
        if (artworkCheckedNanos != 0L && now - artworkCheckedNanos < 2_000_000_000L) return cachedArtwork;
        artworkCheckedNanos = now;
        try {
            byte[] encoded = thumbnail(properties);
            if (!Arrays.equals(encoded, cachedEncoded)) {
                cachedArtwork = encoded == null ? null : MediaArtwork.decode(encoded);
                cachedEncoded = encoded;
            }
        } catch (InterruptedException interrupted) {
            throw interrupted;
        } catch (Exception failure) {
            // Optional artwork must not suppress otherwise valid media metadata.
            cachedEncoded = null;
            cachedArtwork = null;
        }
        return cachedArtwork;
    }

    private byte[] thumbnail(MemorySegment properties) throws Exception {
        MemorySegment reference = object(properties, 15); // get_Thumbnail
        if (reference.address() == 0) return null;
        MemorySegment randomStream = MemorySegment.NULL, stream = MemorySegment.NULL;
        try (Arena scratch = Arena.ofConfined()) {
            randomStream = await(object(reference, 6)); // IRandomAccessStreamReference.OpenReadAsync
            MemorySegment output = scratch.allocate(ADDRESS);
            check((int) invoke(streamBridge, randomStream,
                    guid(scratch, "0000000c-0000-0000-c000-000000000046"), output)); // IID_IStream
            stream = output.get(ADDRESS, 0);
            MemorySegment buffer = scratch.allocate(64 * 1024), count = scratch.allocate(JAVA_INT);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("SMTC artwork interrupted");
                if (System.nanoTime() >= deadline) throw new IOException("SMTC artwork read timed out");
                check((int) invoke(streamRead, slot(stream, 3), stream, buffer, (int) buffer.byteSize(), count));
                int read = count.get(JAVA_INT, 0);
                if (read == 0) break;
                if (read < 0 || read > buffer.byteSize() || bytes.size() + read > 4 * 1024 * 1024)
                    throw new IOException("SMTC artwork exceeds 4 MiB");
                bytes.write(buffer.asSlice(0, read).toArray(JAVA_BYTE));
            }
            return bytes.size() == 0 ? null : bytes.toByteArray();
        } finally {
            try {
                release(stream);
            } finally {
                try {
                    release(randomStream);
                } finally {
                    release(reference);
                }
            }
        }
    }

    private MemorySegment await(MemorySegment operation) throws Exception {
        MemorySegment info = MemorySegment.NULL;
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment output = scratch.allocate(ADDRESS);
            check((int) invoke(query, slot(operation, 0), operation,
                    guid(scratch, "00000036-0000-0000-c000-000000000046"), output));
            info = output.get(ADDRESS, 0);
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (integer(info, 7) == 0) { // AsyncStatus.Started
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("SMTC interrupted");
                if (System.nanoTime() >= deadline) throw new IllegalStateException("SMTC async operation timed out");
                Thread.sleep(20L);
            }
            // GetResults propagates canceled/error HRESULTs and hands us an owned interface.
            return object(operation, 8);
        } finally {
            if (info.address() != 0) {
                // Cancel is harmless for completed operations, and handles sleep interruption too.
                invoke(noArgs, slot(info, 9), info);
                // Close is best-effort; a still-canceling operation may reject it.
                invoke(noArgs, slot(info, 10), info);
                release(info);
            }
            release(operation);
        }
    }

    private MethodHandle symbol(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor);
    }

    private static Object invoke(MethodHandle handle, Object... args) throws Exception {
        try {
            return handle.invokeWithArguments(args);
        } catch (Exception | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        }
    }

    private MemorySegment slot(MemorySegment object, int index) {
        if (object.address() == 0) throw new IllegalStateException("Null WinRT interface");
        MemorySegment vtable = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        return vtable.reinterpret((index + 1L) * ADDRESS.byteSize()).get(ADDRESS, index * ADDRESS.byteSize());
    }

    private void get(MemorySegment object, int slot, MemorySegment output) throws Exception {
        MethodHandle handle = getters.computeIfAbsent(slot, ignored ->
                linker.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)));
        check((int) invoke(handle, slot(object, slot), object, output));
    }

    private MemorySegment object(MemorySegment object, int slot) throws Exception {
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment output = scratch.allocate(ADDRESS);
            get(object, slot, output);
            return output.get(ADDRESS, 0);
        }
    }

    private int integer(MemorySegment object, int slot) throws Exception {
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment output = scratch.allocate(JAVA_INT);
            get(object, slot, output);
            return output.get(JAVA_INT, 0);
        }
    }

    private long number(MemorySegment object, int slot) throws Exception {
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment output = scratch.allocate(JAVA_LONG);
            get(object, slot, output);
            return output.get(JAVA_LONG, 0);
        }
    }

    private String string(MemorySegment object, int slot) throws Exception {
        MemorySegment value = object(object, slot);
        try (Arena scratch = Arena.ofConfined()) {
            MemorySegment length = scratch.allocate(JAVA_INT);
            MemorySegment buffer = (MemorySegment) invoke(rawString, value, length);
            int count = length.get(JAVA_INT, 0);
            if (count == 0) return "";
            if (count < 0 || count > 1_048_576) throw new IllegalStateException("Invalid HSTRING length");
            return new String(buffer.reinterpret(count * 2L).toArray(JAVA_CHAR));
        } finally {
            invoke(deleteString, value);
        }
    }

    private MemorySegment hstring(Arena scratch, String text) throws Exception {
        MemorySegment chars = scratch.allocateFrom(JAVA_CHAR, text.toCharArray());
        MemorySegment output = scratch.allocate(ADDRESS);
        check((int) invoke(createString, chars, text.length(), output));
        return output.get(ADDRESS, 0);
    }

    private MemorySegment guid(Arena scratch, String text) {
        UUID uuid = UUID.fromString(text);
        MemorySegment result = scratch.allocate(16, 4);
        result.set(JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), 0, (int) (uuid.getMostSignificantBits() >>> 32));
        result.set(JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 4, (short) (uuid.getMostSignificantBits() >>> 16));
        result.set(JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 6, (short) uuid.getMostSignificantBits());
        result.set(JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), 8, uuid.getLeastSignificantBits());
        return result;
    }

    private static void check(int result) {
        if (result < 0) throw new IllegalStateException("WinRT HRESULT 0x" + Integer.toHexString(result));
    }

    private void release(MemorySegment object) throws Exception {
        if (object.address() != 0) invoke(release, slot(object, 2), object);
    }

    @Override
    public void close() throws Exception {
        try {
            release(manager);
        } finally {
            try {
                if (initialized) invoke(uninitialize);
            } finally {
                arena.close();
            }
        }
    }
}
