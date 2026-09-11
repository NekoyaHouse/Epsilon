package me.sofurry.smtc;

import com.github.epsilon.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public class SmtcNativeBridge {

    private static final String RESOURCE_PATH = "/natives/windows-x86_64/epsilon_smtc.dll";
    private static final boolean AVAILABLE = loadLibrary();

    private SmtcNativeBridge() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static SmtcNativeResult poll() {
        if (!AVAILABLE) return null;
        return pollNative();
    }

    public static void reset() {
        if (AVAILABLE) {
            resetNative();
        }
    }

    private static native SmtcNativeResult pollNative();

    private static native void resetNative();

    private static boolean loadLibrary() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("windows") || !(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            return false;
        }

        try (InputStream stream = SmtcNativeBridge.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                Constants.LOGGER.warn("Windows SMTC bridge resource is missing: {}", RESOURCE_PATH);
                return false;
            }

            byte[] libraryBytes = stream.readAllBytes();
            String digest = sha256(libraryBytes).substring(0, 16);
            Path nativeDirectory = Path.of(System.getProperty("java.io.tmpdir"), "epsilon", "native");
            Files.createDirectories(nativeDirectory);
            Path libraryPath = nativeDirectory.resolve("epsilon_smtc-" + digest + ".dll");
            extractLibrary(nativeDirectory, libraryPath, libraryBytes);

            System.load(libraryPath.toAbsolutePath().toString());
            libraryPath.toFile().deleteOnExit();
            Constants.LOGGER.info("Loaded Windows SMTC bridge {}", libraryPath.getFileName());
            return true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException e) {
            Constants.LOGGER.warn("Failed to load Windows SMTC bridge from {}", RESOURCE_PATH, e);
            return false;
        }
    }

    private static void extractLibrary(Path nativeDirectory, Path libraryPath, byte[] libraryBytes) throws IOException {
        if (Files.isRegularFile(libraryPath)) return;

        Path temporary = Files.createTempFile(nativeDirectory, "epsilon-smtc-", ".tmp");
        try {
            Files.write(temporary, libraryBytes);
            try {
                Files.move(temporary, libraryPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException ignored) {
                // 另一个游戏实例已经完成了相同哈希原生库的解压。
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

}
