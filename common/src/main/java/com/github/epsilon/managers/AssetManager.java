package com.github.epsilon.managers;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.screen.AssetDownloadScreen;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.client.ClientPlatform;
import com.github.epsilon.utils.network.Http;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.github.epsilon.Constants.mc;

/**
 * 大体积运行时资源（主菜单视频、玲纱立绘、FFmpeg 原生库）的统一管理入口。
 * <p>
 * 这些资源不再随 mod jar 分发，而是在首次使用对应功能时由界面确认后从可配置地址下载到
 * {@code ~/.epsilon/assets/}。管理器负责就绪判定、后台下载与校验、玲纱纹理注册，
 * 以及 JavaCPP 运行时库路径的注入。
 */
public class AssetManager {

    public static final AssetManager INSTANCE = new AssetManager();

    public static final String DEFAULT_RESOURCE_BASE_URL =
            "https://github.com/NekoyaHouse/Epsilon-Resources/releases/download/assets-v1/";
    public static final String DEFAULT_FFMPEG_URL =
            "https://maven.aliyun.com/repository/public/org/bytedeco/ffmpeg/6.1.1-1.5.10/ffmpeg-6.1.1-1.5.10-windows-x86_64.jar";

    public static final String VIDEO_FILE_NAME = "columbina.mp4";
    public static final String REISA_ARCHIVE_NAME = "reisa.zip";
    public static final String FFMPEG_ARCHIVE_NAME = "ffmpeg-6.1.1-1.5.10-windows-x86_64.jar";

    private static final long MIN_VIDEO_BYTES = 1L << 20;
    private static final long MIN_IMAGE_BYTES = 4L << 10;
    private static final long MIN_NATIVE_BYTES = 4L << 10;
    private static final int DOWNLOAD_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 700L;

    private static final Set<String> REISA_SUFFIXES = Set.of(
            "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "99"
    );

    private static final List<String> FFMPEG_NATIVE_FILES = List.of(
            "avcodec-60.dll", "avdevice-60.dll", "avfilter-9.dll", "avformat-60.dll", "avutil-58.dll",
            "jniavcodec.dll", "jniavdevice.dll", "jniavfilter.dll", "jniavformat.dll", "jniavutil.dll",
            "jniswresample.dll", "jniswscale.dll", "swresample-4.dll", "swscale-7.dll"
    );

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    /**
     * 可按需下载的资源种类。
     */
    public enum Asset {

        VIDEO(VIDEO_FILE_NAME, 20_788_534L),
        REISA(REISA_ARCHIVE_NAME, 8_825_221L),
        FFMPEG(FFMPEG_ARCHIVE_NAME, 24_461_014L);

        private final String fileName;
        private final long expectedBytes;

        Asset(String fileName, long expectedBytes) {
            this.fileName = fileName;
            this.expectedBytes = expectedBytes;
        }

        public String fileName() {
            return fileName;
        }

        /**
         * 预估体积，仅用于下载界面的体积展示与整体进度估算。
         */
        public long expectedBytes() {
            return expectedBytes;
        }

        public boolean isVideoDependent() {
            return this == VIDEO || this == FFMPEG;
        }

    }

    /**
     * 一次下载任务的状态，供下载界面在渲染线程轮询。
     */
    public static final class DownloadJob {

        public enum Status {
            RUNNING,
            SUCCESS,
            FAILED,
            CANCELLED
        }

        private final List<Asset> assets;
        private final long totalBytes;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private volatile Status status = Status.RUNNING;
        private volatile Asset current;
        private volatile long completedBytes;
        private volatile long currentBytes;
        private volatile long currentTotal;
        private volatile String error = "";

        private DownloadJob(List<Asset> assets) {
            this.assets = List.copyOf(assets);
            long total = 0L;
            for (Asset asset : this.assets) {
                total += asset.expectedBytes();
            }
            this.totalBytes = Math.max(1L, total);
        }

        public List<Asset> assets() {
            return assets;
        }

        public Status status() {
            return status;
        }

        public boolean isRunning() {
            return status == Status.RUNNING;
        }

        public Asset currentAsset() {
            return current;
        }

        public String error() {
            return error;
        }

        public float progress() {
            long done = completedBytes + currentBytes;
            return Math.clamp(done / (float) totalBytes, 0.0f, 1.0f);
        }

        public float currentProgress() {
            if (currentTotal <= 0L) {
                return 0.0f;
            }
            return Math.clamp(currentBytes / (float) currentTotal, 0.0f, 1.0f);
        }

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private final Path rootDir = ConfigManager.INSTANCE.getConfigDir().resolve("assets");
    private final Path videoDir = rootDir.resolve("video");
    private final Path videoFile = videoDir.resolve(VIDEO_FILE_NAME);
    private final Path reisaDir = rootDir.resolve("reisa");
    private final Path ffmpegDir = rootDir.resolve("ffmpeg");
    private final Path ffmpegNativeDir = ffmpegDir.resolve("natives");
    private final Path tempDir = rootDir.resolve(".tmp");

    private final Set<Asset> declinedThisSession = EnumSet.noneOf(Asset.class);
    private final Map<String, Identifier> reisaTextures = new HashMap<>();

    private volatile DownloadJob activeJob;
    private boolean legacyVideoChecked;
    private volatile Boolean reisaReadyCache;

    private AssetManager() {
    }

    public Path rootDirectory() {
        return rootDir;
    }

    public Path videoFile() {
        return videoFile;
    }

    // ------------------------------------------------------------------
    // 平台与就绪判定
    // ------------------------------------------------------------------

    /**
     * 主菜单视频依赖 Windows x86_64 的 FFmpeg 原生库。
     */
    public boolean isVideoSupported() {
        return ClientPlatform.isWindowsX64();
    }

    public boolean isAssetSupported(Asset asset) {
        return !asset.isVideoDependent() || isVideoSupported();
    }

    public Set<Asset> supportedAssets() {
        EnumSet<Asset> supported = EnumSet.allOf(Asset.class);
        if (!isVideoSupported()) {
            supported.remove(Asset.VIDEO);
            supported.remove(Asset.FFMPEG);
        }
        return supported;
    }

    public boolean isReady(Asset asset) {
        return switch (asset) {
            case VIDEO -> isVideoReady();
            case REISA -> isReisaReady();
            case FFMPEG -> isFfmpegReady();
        };
    }

    public boolean isVideoReady() {
        migrateLegacyVideo();
        return isFile(videoFile, MIN_VIDEO_BYTES);
    }

    public boolean isReisaReady() {
        Boolean cached = reisaReadyCache;
        if (cached != null) {
            return cached;
        }
        boolean ready = checkReisaReady();
        reisaReadyCache = ready;
        return ready;
    }

    private boolean checkReisaReady() {
        for (String suffix : REISA_SUFFIXES) {
            if (!isFile(reisaFile(suffix), MIN_IMAGE_BYTES)) {
                return false;
            }
        }
        return true;
    }

    public boolean isFfmpegReady() {
        if (!isVideoSupported()) {
            return false;
        }
        for (String name : FFMPEG_NATIVE_FILES) {
            if (!isFile(ffmpegNativeDir.resolve(name), MIN_NATIVE_BYTES)) {
                return false;
            }
        }
        return true;
    }

    public Set<Asset> missingAssets(Collection<Asset> assets) {
        EnumSet<Asset> missing = EnumSet.noneOf(Asset.class);
        for (Asset asset : assets) {
            if (isAssetSupported(asset) && !isReady(asset)) {
                missing.add(asset);
            }
        }
        return missing;
    }

    // ------------------------------------------------------------------
    // 下载入口
    // ------------------------------------------------------------------

    /**
     * 在功能即将使用但资源缺失时弹出下载确认界面。
     * <p>
     * 已被用户在本会话拒绝、已经在下载、或界面已经打开时都会直接返回。
     */
    public void requestDownload(Collection<Asset> assets) {
        Set<Asset> missing = missingAssets(assets);
        missing.removeAll(declinedThisSession);
        if (missing.isEmpty()) {
            return;
        }
        DownloadJob job = activeJob;
        if (job != null && job.isRunning()) {
            return;
        }
        openDownloadScreen(missing, true);
    }

    /**
     * 打开资源下载界面，缺失项全部预选。
     */
    public void openDownloadScreen() {
        openDownloadScreen(supportedAssets(), false);
    }

    public void openDownloadScreen(Collection<Asset> assets) {
        openDownloadScreen(assets, false);
    }

    private void openDownloadScreen(Collection<Asset> assets, boolean declineOnClose) {
        EnumSet<Asset> selected = EnumSet.noneOf(Asset.class);
        for (Asset asset : assets) {
            if (isAssetSupported(asset)) {
                selected.add(asset);
            }
        }
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (mc.gui.screen() instanceof AssetDownloadScreen) {
                return;
            }
            mc.gui.setScreen(new AssetDownloadScreen(mc.gui.screen(), selected, declineOnClose));
        });
    }

    public void markDeclined(Collection<Asset> assets) {
        declinedThisSession.addAll(assets);
    }

    public boolean isDeclinedThisSession(Asset asset) {
        return declinedThisSession.contains(asset);
    }

    public DownloadJob activeJob() {
        return activeJob;
    }

    public DownloadJob startDownload(Collection<Asset> assets) {
        DownloadJob running = activeJob;
        if (running != null && running.isRunning()) {
            return running;
        }

        EnumSet<Asset> requested = EnumSet.noneOf(Asset.class);
        for (Asset asset : assets) {
            if (isAssetSupported(asset)) {
                requested.add(asset);
            }
        }
        if (requested.isEmpty()) {
            return null;
        }

        List<Asset> ordered = new ArrayList<>(List.of(Asset.values()));
        ordered.retainAll(requested);

        DownloadJob job = new DownloadJob(ordered);
        activeJob = job;
        ExecutorManager.INSTANCE.execute(() -> runJob(job));
        return job;
    }

    private void runJob(DownloadJob job) {
        try {
            for (Asset asset : job.assets()) {
                if (job.isCancelled()) {
                    job.status = DownloadJob.Status.CANCELLED;
                    return;
                }
                job.current = asset;
                job.currentBytes = 0L;
                job.currentTotal = 0L;
                downloadAsset(asset, job);
                job.completedBytes += asset.expectedBytes();
                job.currentBytes = 0L;
            }
            job.status = DownloadJob.Status.SUCCESS;
            job.current = null;
            notifyResult(true, null);
        } catch (DownloadCancelledException e) {
            job.status = DownloadJob.Status.CANCELLED;
            job.current = null;
        } catch (Throwable e) {
            Constants.LOGGER.error("[AssetManager] Asset download failed", e);
            job.status = DownloadJob.Status.FAILED;
            job.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            notifyResult(false, job.error);
        }
    }

    private void notifyResult(boolean success, String error) {
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (success) {
                NotificationManager.INSTANCE.success(
                        EpsilonTranslations.Resources.DOWNLOAD_SUCCESS_TITLE.getTranslatedName(),
                        EpsilonTranslations.Resources.DOWNLOAD_SUCCESS_MESSAGE.getTranslatedName());
            } else {
                NotificationManager.INSTANCE.error(
                        EpsilonTranslations.Resources.DOWNLOAD_FAILED_TITLE.getTranslatedName(),
                        error == null ? "" : error);
            }
        });
    }

    // ------------------------------------------------------------------
    // 单个资源的下载与安装
    // ------------------------------------------------------------------

    private void downloadAsset(Asset asset, DownloadJob job) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            if (job.isCancelled()) {
                throw new DownloadCancelledException();
            }
            Path part = tempDir.resolve(asset.fileName() + ".part");
            try {
                Files.createDirectories(tempDir);
                Files.deleteIfExists(part);
                Http.download(urlFor(asset), part, (downloaded, total) -> {
                    job.currentBytes = downloaded;
                    job.currentTotal = total;
                }, job::isCancelled);
                install(asset, part);
                Files.deleteIfExists(part);
                return;
            } catch (DownloadCancelledException e) {
                throw e;
            } catch (IOException e) {
                if (job.isCancelled()) {
                    throw new DownloadCancelledException();
                }
                lastError = e;
                Files.deleteIfExists(part);
                Constants.LOGGER.warn("[AssetManager] Attempt {}/{} for {} failed: {}",
                        attempt, DOWNLOAD_ATTEMPTS, asset.fileName(), e.toString());
                if (attempt < DOWNLOAD_ATTEMPTS) {
                    sleep(RETRY_DELAY_MS * attempt);
                }
            }
        }
        throw lastError != null ? lastError : new IOException("Download failed");
    }

    private void install(Asset asset, Path part) throws IOException {
        switch (asset) {
            case VIDEO -> installVideo(part);
            case REISA -> installReisa(part);
            case FFMPEG -> installFfmpeg(part);
        }
    }

    private void installVideo(Path part) throws IOException {
        if (!isMp4(part)) {
            throw new IOException("Downloaded video is not a valid MP4 file");
        }
        Files.createDirectories(videoDir);
        moveReplacing(part, videoFile);
    }

    private void installReisa(Path archive) throws IOException {
        Files.createDirectories(reisaDir);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Map<String, ZipEntry> entries = new LinkedHashMap<>();
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                if (entry.isDirectory()) {
                    continue;
                }
                String suffix = reisaSuffix(baseName(entry.getName()));
                if (suffix != null) {
                    entries.put(suffix, entry);
                }
            }
            for (String suffix : REISA_SUFFIXES) {
                if (!entries.containsKey(suffix)) {
                    throw new IOException("reisa archive is missing reisa_" + suffix + ".png");
                }
            }
            for (String suffix : REISA_SUFFIXES) {
                Path target = reisaDir.resolve("reisa_" + suffix + ".png");
                Files.deleteIfExists(target);
                try (InputStream in = zip.getInputStream(entries.get(suffix))) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!isPng(target)) {
                    Files.deleteIfExists(target);
                    throw new IOException("reisa_" + suffix + ".png is not a valid PNG file");
                }
            }
        }
        releaseReisaTextures();
        reisaReadyCache = true;
    }

    private void installFfmpeg(Path archive) throws IOException {
        Files.createDirectories(ffmpegNativeDir);
        Set<String> required = Set.copyOf(FFMPEG_NATIVE_FILES);
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = baseName(entry.getName());
                if (required.contains(name)) {
                    entries.put(name, entry);
                }
            }
            for (String name : FFMPEG_NATIVE_FILES) {
                ZipEntry entry = entries.get(name);
                if (entry == null) {
                    throw new IOException("ffmpeg archive is missing " + name);
                }
                Path target = ffmpegNativeDir.resolve(name);
                Files.deleteIfExists(target);
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // FFmpeg 与玲纱纹理
    // ------------------------------------------------------------------

    /**
     * 把已解压的 FFmpeg 原生库目录注册为 JavaCPP 的搜索路径。
     * <p>
     * 必须在任何 {@code org.bytedeco.ffmpeg} 类初始化之前调用，否则 JavaCPP 会缓存不带
     * 该路径的平台属性并直接抛出 {@link UnsatisfiedLinkError}。
     */
    public void ensureFfmpegLoaded() {
        if (!isFfmpegReady()) {
            return;
        }
        System.setProperty("org.bytedeco.javacpp.platform.linkpath", ffmpegNativeDir.toAbsolutePath().toString());
    }

    /**
     * 返回玲纱立绘的纹理标识，未下载或不在渲染线程时返回 {@code null}。
     */
    public Identifier reisaTexture(String suffix) {
        if (!isReisaReady()) {
            return null;
        }
        Identifier existing = reisaTextures.get(suffix);
        if (existing != null) {
            return existing;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return null;
        }

        Path file = reisaDir.resolve("reisa_" + suffix + ".png");
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(() -> "Epsilon Reisa " + suffix, image);
            Identifier identifier = Identifier.fromNamespaceAndPath(
                    "epsilon_assets", "textures/gui/galgame/reisa_" + suffix + ".png");
            mc.getTextureManager().register(identifier, texture);
            reisaTextures.put(suffix, identifier);
            return identifier;
        } catch (IOException e) {
            Constants.LOGGER.warn("[AssetManager] Failed to load reisa_{} texture", suffix, e);
            return null;
        }
    }

    public void releaseReisaTextures() {
        Map<String, Identifier> registered = Map.copyOf(reisaTextures);
        reisaTextures.clear();
        if (registered.isEmpty() || mc == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        registered.values().forEach(identifier -> mc.getTextureManager().release(identifier));
    }

    /**
     * 清空 {@code ~/.epsilon/assets}，下次使用时会重新走下载流程。
     */
    public void clearCache() {
        releaseReisaTextures();
        declinedThisSession.clear();
        reisaReadyCache = null;
        if (!Files.exists(rootDir)) {
            return;
        }
        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Constants.LOGGER.warn("[AssetManager] Failed to clear asset cache", e);
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private void migrateLegacyVideo() {
        if (legacyVideoChecked || mc == null) {
            return;
        }
        synchronized (this) {
            if (legacyVideoChecked) {
                return;
            }
            legacyVideoChecked = true;
            try {
                if (Files.exists(videoFile) || mc.gameDirectory == null) {
                    return;
                }
                Path legacy = mc.gameDirectory.toPath().resolve("epsilon-video").resolve(VIDEO_FILE_NAME);
                if (Files.notExists(legacy) || Files.size(legacy) < MIN_VIDEO_BYTES) {
                    return;
                }
                Files.createDirectories(videoDir);
                moveReplacing(legacy, videoFile);
                Constants.LOGGER.info("[AssetManager] Migrated legacy main menu video to {}", videoFile);
            } catch (IOException e) {
                Constants.LOGGER.warn("[AssetManager] Failed to migrate legacy main menu video", e);
            }
        }
    }

    private String urlFor(Asset asset) {
        if (asset == Asset.FFMPEG) {
            String url = ClientSettingUrl.ffmpegUrl();
            return url.isBlank() ? DEFAULT_FFMPEG_URL : url;
        }
        return ClientSettingUrl.resourceBaseUrl() + asset.fileName();
    }

    private Path reisaFile(String suffix) {
        return reisaDir.resolve("reisa_" + suffix + ".png");
    }

    private static boolean isFile(Path path, long minBytes) {
        try {
            return Files.isRegularFile(path) && Files.size(path) >= minBytes;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isPng(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(PNG_MAGIC.length);
            if (header.length < PNG_MAGIC.length) {
                return false;
            }
            for (int i = 0; i < PNG_MAGIC.length; i++) {
                if (header[i] != PNG_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isMp4(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(64);
            for (int i = 4; i + 4 <= header.length; i++) {
                if (header[i] == 'f' && header[i + 1] == 't' && header[i + 2] == 'y' && header[i + 3] == 'p') {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String baseName(String entryName) {
        int index = entryName.lastIndexOf('/');
        return index >= 0 ? entryName.substring(index + 1) : entryName;
    }

    private static String reisaSuffix(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("reisa_") || !lower.endsWith(".png")) {
            return null;
        }
        String suffix = lower.substring("reisa_".length(), lower.length() - ".png".length());
        return REISA_SUFFIXES.contains(suffix) ? suffix : null;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class DownloadCancelledException extends IOException {
    }

    /**
     * 读取下载地址设置，隔离设置模块与资源管理器之间的初始化顺序。
     */
    private static final class ClientSettingUrl {

        private static String resourceBaseUrl() {
            String base = ClientSetting.INSTANCE.resourceBaseUrl.getValue();
            base = base == null ? "" : base.trim();
            if (base.isEmpty()) {
                return DEFAULT_RESOURCE_BASE_URL;
            }
            return base.endsWith("/") ? base : base + "/";
        }

        private static String ffmpegUrl() {
            String url = ClientSetting.INSTANCE.ffmpegDownloadUrl.getValue();
            return url == null ? "" : url.trim();
        }

    }

}
