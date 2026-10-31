package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.assets.ffmpeg.FFmpegNativePlatform
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.screen.AssetDownloadScreen
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.client.ClientPlatform
import com.github.epsilon.utils.network.Http
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.LongSupplier
import java.util.zip.ZipFile

/**
 * 大体积运行时资源（主菜单视频、玲纱立绘、FFmpeg 原生库）的统一管理入口。
 *
 * 这些资源不再随 mod jar 分发，而是在首次使用对应功能时由界面确认后从可配置地址下载到
 * `~/.epsilon/assets/`。管理器负责就绪判定、后台下载与校验、玲纱纹理注册，
 * 以及 JavaCPP 运行时库路径的注入。
 */
object AssetManager {

    const val DEFAULT_RESOURCE_BASE_URL: String =
        "https://github.com/NekoyaHouse/Epsilon-Resources/releases/download/assets-v1/"

    const val VIDEO_FILE_NAME = "columbina.mp4"
    const val LIGHT_TRAILS_FILE_NAME = "lighttrails.png"
    const val REISA_ARCHIVE_NAME = "reisa.zip"

    /**
     * 下载时使用的中立临时名；真正的产物名与原生库清单由 [FFmpegNativePlatform] 按平台决定。
     */
    const val FFMPEG_ARCHIVE_NAME = "ffmpeg-natives.jar"

    private const val MIN_VIDEO_BYTES = 1L shl 20
    private const val MIN_IMAGE_BYTES = 4L shl 10
    private const val MIN_NATIVE_BYTES = 4L shl 10
    private const val DOWNLOAD_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 700L

    private val REISA_SUFFIXES: Set<String> = setOf(
        "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
        "10", "11", "12", "13", "14", "15", "16", "17", "18", "99"
    )

    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /**
     * 可按需下载的资源种类。
     */
    enum class Asset(
        private val fileName: String,
        private val expectedBytes: LongSupplier,
    ) {

        VIDEO(VIDEO_FILE_NAME, LongSupplier { 20_788_534L }),
        LIGHT_TRAILS(LIGHT_TRAILS_FILE_NAME, LongSupplier { 3_681_336L }),
        REISA(REISA_ARCHIVE_NAME, LongSupplier { 8_825_221L }),
        FFMPEG(FFMPEG_ARCHIVE_NAME, LongSupplier { FFmpegNativePlatform.currentOrDefault().archiveBytes() });

        fun fileName(): String {
            return fileName
        }

        /**
         * 预估体积，仅用于下载界面的体积展示与整体进度估算。
         */
        fun expectedBytes(): Long {
            return expectedBytes.asLong
        }

        fun isVideoDependent(): Boolean {
            return this == VIDEO || this == LIGHT_TRAILS || this == FFMPEG
        }

    }

    /**
     * 一次下载任务的状态，供下载界面在渲染线程轮询。
     */
    class DownloadJob internal constructor(assets: Collection<Asset>) {

        enum class Status {
            RUNNING,
            SUCCESS,
            FAILED,
            CANCELLED
        }

        private val assetList: List<Asset> = assets.toList()
        private val totalBytes: Long
        private val cancelled = AtomicBoolean()

        @Volatile
        var status: Status = Status.RUNNING
            private set

        @Volatile
        var current: Asset? = null
            private set

        @Volatile
        var completedBytes: Long = 0L
            internal set

        @Volatile
        var currentBytes: Long = 0L
            internal set

        @Volatile
        var currentTotal: Long = 0L
            internal set

        @Volatile
        var error: String = ""
            internal set

        init {
            var total = 0L
            for (asset in assetList) {
                total += asset.expectedBytes()
            }
            totalBytes = Math.max(1L, total)
        }

        fun assets(): List<Asset> {
            return assetList
        }

        fun status(): Status {
            return status
        }

        fun isRunning(): Boolean {
            return status == Status.RUNNING
        }

        fun currentAsset(): Asset? {
            return current
        }

        fun error(): String {
            return error
        }

        fun progress(): Float {
            val done = completedBytes + currentBytes
            return Math.clamp(done / totalBytes.toFloat(), 0.0f, 1.0f)
        }

        fun currentProgress(): Float {
            if (currentTotal <= 0L) {
                return 0.0f
            }
            return Math.clamp(currentBytes / currentTotal.toFloat(), 0.0f, 1.0f)
        }

        fun cancel() {
            cancelled.set(true)
        }

        fun isCancelled(): Boolean {
            return cancelled.get()
        }

        internal fun setCurrent(asset: Asset?) {
            current = asset
        }

        internal fun setStatus(status: Status) {
            this.status = status
        }

        internal fun updateError(error: String) {
            this.error = error
        }

    }

    private val rootDir: Path = ConfigManager.configDir.resolve("assets")
    private val videoDir: Path = rootDir.resolve("video")
    private val videoFile: Path = videoDir.resolve(VIDEO_FILE_NAME)
    private val lightTrailsFile: Path = videoDir.resolve(LIGHT_TRAILS_FILE_NAME)
    private val reisaDir: Path = rootDir.resolve("reisa")
    private val ffmpegDir: Path = rootDir.resolve("ffmpeg")
    private val ffmpegNativeDir: Path = ffmpegDir.resolve("natives")
    private val tempDir: Path = rootDir.resolve(".tmp")

    private val declinedThisSession: MutableSet<Asset> = EnumSet.noneOf(Asset::class.java)
    private val registeredTextures = LinkedHashMap<String, Identifier>()

    @Volatile
    private var activeJob: DownloadJob? = null
    private var legacyVideoChecked = false

    @Volatile
    private var reisaReadyCache: Boolean? = null

    fun rootDirectory(): Path {
        return rootDir
    }

    fun videoFile(): Path {
        return videoFile
    }

    // ------------------------------------------------------------------
    // 平台与就绪判定
    // ------------------------------------------------------------------

    /**
     * 主菜单视频依赖 Windows x86_64 或 macOS arm64 的 FFmpeg 原生库。
     */
    fun isVideoSupported(): Boolean {
        return FFmpegNativePlatform.current() != null
    }

    fun isAssetSupported(asset: Asset): Boolean {
        return !asset.isVideoDependent() || isVideoSupported()
    }

    fun supportedAssets(): Set<Asset> {
        val supported = EnumSet.allOf(Asset::class.java)
        if (!isVideoSupported()) {
            supported.remove(Asset.VIDEO)
            supported.remove(Asset.FFMPEG)
        }
        return supported
    }

    fun isReady(asset: Asset): Boolean {
        return when (asset) {
            Asset.VIDEO -> isVideoReady()
            Asset.LIGHT_TRAILS -> isLightTrailsReady()
            Asset.REISA -> isReisaReady()
            Asset.FFMPEG -> isFfmpegReady()
        }
    }

    fun isVideoReady(): Boolean {
        migrateLegacyVideo()
        return isFile(videoFile, MIN_VIDEO_BYTES)
    }

    fun isLightTrailsReady(): Boolean {
        return isFile(lightTrailsFile, MIN_IMAGE_BYTES)
    }

    fun isReisaReady(): Boolean {
        val cached = reisaReadyCache
        if (cached != null) {
            return cached
        }
        val ready = checkReisaReady()
        reisaReadyCache = ready
        return ready
    }

    private fun checkReisaReady(): Boolean {
        for (suffix in REISA_SUFFIXES) {
            if (!isFile(reisaFile(suffix), MIN_IMAGE_BYTES)) {
                return false
            }
        }
        return true
    }

    fun isFfmpegReady(): Boolean {
        val platform = FFmpegNativePlatform.current() ?: return false
        for (name in platform.nativeFiles()) {
            if (!isFile(ffmpegNativeDir.resolve(name), MIN_NATIVE_BYTES)) {
                return false
            }
        }
        return true
    }

    fun missingAssets(assets: Collection<Asset>): Set<Asset> {
        val missing = EnumSet.noneOf(Asset::class.java)
        for (asset in assets) {
            if (isAssetSupported(asset) && !isReady(asset)) {
                missing.add(asset)
            }
        }
        return missing
    }

    // ------------------------------------------------------------------
    // 下载入口
    // ------------------------------------------------------------------

    /**
     * 在功能即将使用但资源缺失时弹出下载确认界面。
     *
     * 已被用户在本会话拒绝、已经在下载、或界面已经打开时都会直接返回。
     */
    fun requestDownload(assets: Collection<Asset>) {
        val missing = missingAssets(assets).filterNot { declinedThisSession.contains(it) }.toMutableSet()
        if (missing.isEmpty()) {
            return
        }
        val job = activeJob
        if (job != null && job.isRunning()) {
            return
        }
        openDownloadScreen(missing, true)
    }

    /**
     * 打开资源下载界面，缺失项全部预选。
     */
    fun openDownloadScreen() {
        openDownloadScreen(supportedAssets(), false)
    }

    fun openDownloadScreen(assets: Collection<Asset>) {
        openDownloadScreen(assets, false)
    }

    private fun openDownloadScreen(assets: Collection<Asset>, declineOnClose: Boolean) {
        val selected = EnumSet.noneOf(Asset::class.java)
        for (asset in assets) {
            if (isAssetSupported(asset)) {
                selected.add(asset)
            }
        }
        Constants.client.execute {
            if (Constants.client.gui.screen() is AssetDownloadScreen) {
                return@execute
            }
            normalizeFfmpegDownloadUrl()
            Constants.client.gui.setScreen(AssetDownloadScreen(Constants.client.gui.screen(), selected, declineOnClose))
        }
    }

    fun markDeclined(assets: Collection<Asset>) {
        declinedThisSession.addAll(assets)
    }

    fun clearDeclined(assets: Collection<Asset>) {
        declinedThisSession.removeAll(assets)
    }

    fun isDeclinedThisSession(asset: Asset): Boolean {
        return declinedThisSession.contains(asset)
    }

    fun activeJob(): DownloadJob? {
        return activeJob
    }

    fun startDownload(assets: Collection<Asset>): DownloadJob? {
        val running = activeJob
        if (running != null && running.isRunning()) {
            return running
        }

        val requested = EnumSet.noneOf(Asset::class.java)
        for (asset in assets) {
            if (isAssetSupported(asset)) {
                requested.add(asset)
            }
        }
        if (requested.isEmpty()) {
            return null
        }

        val ordered = ArrayList<Asset>(Asset.entries)
        ordered.retainAll(requested)

        val job = DownloadJob(ordered)
        activeJob = job
        ExecutorManager.execute { runJob(job) }
        return job
    }

    private fun runJob(job: DownloadJob) {
        try {
            for (asset in job.assets()) {
                if (job.isCancelled()) {
                    job.setStatus(DownloadJob.Status.CANCELLED)
                    return
                }
                job.setCurrent(asset)
                job.currentBytes = 0L
                job.currentTotal = 0L
                downloadAsset(asset, job)
                job.completedBytes += asset.expectedBytes()
                job.currentBytes = 0L
            }
            job.setStatus(DownloadJob.Status.SUCCESS)
            job.setCurrent(null)
            notifyResult(true, null)
        } catch (e: DownloadCancelledException) {
            job.setStatus(DownloadJob.Status.CANCELLED)
            job.setCurrent(null)
        } catch (e: Throwable) {
            Constants.LOGGER.error("[AssetManager] Asset download failed", e)
            job.setStatus(DownloadJob.Status.FAILED)
            job.updateError(if (e.message == null) e.javaClass.simpleName else e.message!!)
            notifyResult(false, job.error)
        }
    }

    private fun notifyResult(success: Boolean, error: String?) {
        Constants.client.execute {
            if (success) {
                NotificationManager.success(
                    EpsilonTranslations.Resources.DOWNLOAD_SUCCESS_TITLE.getTranslatedName(),
                    EpsilonTranslations.Resources.DOWNLOAD_SUCCESS_MESSAGE.getTranslatedName()
                )
            } else {
                NotificationManager.error(
                    EpsilonTranslations.Resources.DOWNLOAD_FAILED_TITLE.getTranslatedName(),
                    error ?: ""
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // 单个资源的下载与安装
    // ------------------------------------------------------------------

    private fun downloadAsset(asset: Asset, job: DownloadJob) {
        var lastError: IOException? = null
        for (attempt in 1..DOWNLOAD_ATTEMPTS) {
            if (job.isCancelled()) {
                throw DownloadCancelledException()
            }
            val part = tempDir.resolve(asset.fileName() + ".part")
            try {
                Files.createDirectories(tempDir)
                Files.deleteIfExists(part)
                Http.download(
                    urlFor(asset), part,
                    { downloaded, total ->
                        job.currentBytes = downloaded
                        job.currentTotal = total
                    },
                    { job.isCancelled() }
                )
                install(asset, part)
                Files.deleteIfExists(part)
                return
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: IOException) {
                if (job.isCancelled()) {
                    throw DownloadCancelledException()
                }
                lastError = e
                Files.deleteIfExists(part)
                Constants.LOGGER.warn(
                    "[AssetManager] Attempt {}/{} for {} failed: {}",
                    attempt, DOWNLOAD_ATTEMPTS, asset.fileName(), e.toString()
                )
                if (attempt < DOWNLOAD_ATTEMPTS) {
                    sleep(RETRY_DELAY_MS * attempt)
                }
            }
        }
        throw lastError ?: IOException("Download failed")
    }

    private fun install(asset: Asset, part: Path) {
        when (asset) {
            Asset.VIDEO -> installVideo(part)
            Asset.LIGHT_TRAILS -> installLightTrails(part)
            Asset.REISA -> installReisa(part)
            Asset.FFMPEG -> installFfmpeg(part)
        }
    }

    private fun installVideo(part: Path) {
        if (!isMp4(part)) {
            throw IOException("Downloaded video is not a valid MP4 file")
        }
        Files.createDirectories(videoDir)
        moveReplacing(part, videoFile)
    }

    private fun installLightTrails(part: Path) {
        if (!isPng(part)) {
            throw IOException("Downloaded light trails overlay is not a valid PNG file")
        }
        Files.createDirectories(videoDir)
        moveReplacing(part, lightTrailsFile)
        releaseRegisteredTextures()
    }

    private fun installReisa(archive: Path) {
        Files.createDirectories(reisaDir)
        ZipFile(archive.toFile()).use { zip ->
            val entries = LinkedHashMap<String, java.util.zip.ZipEntry>()
            val iterator = zip.entries().asIterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.isDirectory) {
                    continue
                }
                val suffix = reisaSuffix(baseName(entry.name))
                if (suffix != null) {
                    entries[suffix] = entry
                }
            }
            for (suffix in REISA_SUFFIXES) {
                if (!entries.containsKey(suffix)) {
                    throw IOException("reisa archive is missing reisa_$suffix.png")
                }
            }
            for (suffix in REISA_SUFFIXES) {
                val target = reisaDir.resolve("reisa_$suffix.png")
                Files.deleteIfExists(target)
                zip.getInputStream(entries[suffix]!!).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
                if (!isPng(target)) {
                    Files.deleteIfExists(target)
                    throw IOException("reisa_$suffix.png is not a valid PNG file")
                }
            }
        }
        releaseRegisteredTextures()
        reisaReadyCache = true
    }

    private fun installFfmpeg(archive: Path) {
        val platform = FFmpegNativePlatform.current()
            ?: throw IOException("FFmpeg natives are unsupported on " + ClientPlatform.displayName())
        Files.createDirectories(ffmpegNativeDir)
        val nativeFiles = platform.nativeFiles()
        val required = nativeFiles.toSet()
        val entries = LinkedHashMap<String, java.util.zip.ZipEntry>()
        ZipFile(archive.toFile()).use { zip ->
            val iterator = zip.entries().asIterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.isDirectory) {
                    continue
                }
                val name = baseName(entry.name)
                if (required.contains(name)) {
                    entries[name] = entry
                }
            }
            for (name in nativeFiles) {
                val entry = entries[name] ?: throw IOException("ffmpeg archive is missing $name")
                val target = ffmpegNativeDir.resolve(name)
                Files.deleteIfExists(target)
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // FFmpeg、玲纱纹理与背景光效
    // ------------------------------------------------------------------

    /**
     * 把已解压的 FFmpeg 原生库目录注册为 JavaCPP 的搜索路径。
     *
     * 必须在任何 `org.bytedeco.ffmpeg` 类初始化之前调用，否则 JavaCPP 会缓存不带
     * 该路径的平台属性并直接抛出 [UnsatisfiedLinkError]。
     */
    fun ensureFfmpegLoaded() {
        if (!isFfmpegReady()) {
            return
        }
        System.setProperty("org.bytedeco.javacpp.platform.linkpath", ffmpegNativeDir.toAbsolutePath().toString())
    }

    /**
     * 返回玲纱立绘的纹理标识，未下载或不在渲染线程时返回 `null`。
     */
    fun reisaTexture(suffix: String): Identifier? {
        if (!isReisaReady()) {
            return null
        }
        return pngTexture(
            "reisa_$suffix",
            reisaDir.resolve("reisa_$suffix.png"),
            "textures/gui/galgame/reisa_$suffix.png",
            "Reisa $suffix"
        )
    }

    /**
     * 返回主菜单视频叠层光效的纹理标识，未下载或不在渲染线程时返回 `null`。
     */
    fun videoOverlayTexture(): Identifier? {
        if (!isLightTrailsReady()) {
            return null
        }
        return pngTexture("lighttrails", lightTrailsFile, "textures/lighttrails.png", "Main menu light trails")
    }

    /**
     * 把磁盘上的 PNG 惰性注册为动态纹理；纹理管理必须在渲染线程上进行。
     */
    private fun pngTexture(key: String, file: Path, resourcePath: String, label: String): Identifier? {
        val existing = registeredTextures[key]
        if (existing != null) {
            return existing
        }
        if (!RenderSystem.isOnRenderThread()) {
            return null
        }

        try {
            Files.newInputStream(file).use { input ->
                val image = NativeImage.read(input)
                val texture = DynamicTexture({ "Epsilon $label" }, image)
                val identifier = Identifier.fromNamespaceAndPath("epsilon_assets", resourcePath)
                Constants.client.textureManager.register(identifier, texture)
                registeredTextures[key] = identifier
                return identifier
            }
        } catch (e: IOException) {
            Constants.LOGGER.warn("[AssetManager] Failed to load {} texture from {}", label, file, e)
            return null
        }
    }

    fun releaseRegisteredTextures() {
        val registered = LinkedHashMap(registeredTextures)
        registeredTextures.clear()
        if (registered.isEmpty() || !RenderSystem.isOnRenderThread()) {
            return
        }
        registered.values.forEach { identifier -> Constants.client.textureManager.release(identifier) }
    }

    /**
     * 清空 `~/.epsilon/assets`，下次使用时会重新走下载流程。
     */
    fun clearCache() {
        releaseRegisteredTextures()
        declinedThisSession.clear()
        reisaReadyCache = null
        if (!Files.exists(rootDir)) {
            return
        }
        try {
            Files.walkFileTree(rootDir, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            Constants.LOGGER.warn("[AssetManager] Failed to clear asset cache", e)
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private fun migrateLegacyVideo() {
        if (legacyVideoChecked) {
            return
        }
        synchronized(this) {
            if (legacyVideoChecked) {
                return
            }
            legacyVideoChecked = true
            try {
                if (Files.exists(videoFile)) {
                    return
                }
                val legacy = Constants.client.gameDirectory.toPath().resolve("epsilon-video").resolve(VIDEO_FILE_NAME)
                if (Files.notExists(legacy) || Files.size(legacy) < MIN_VIDEO_BYTES) {
                    return
                }
                Files.createDirectories(videoDir)
                moveReplacing(legacy, videoFile)
                Constants.LOGGER.info("[AssetManager] Migrated legacy main menu video to {}", videoFile)
            } catch (e: IOException) {
                Constants.LOGGER.warn("[AssetManager] Failed to migrate legacy main menu video", e)
            }
        }
    }

    private fun urlFor(asset: Asset): String {
        if (asset == Asset.FFMPEG) {
            return ffmpegUrl()
        }
        return ClientSettingUrl.resourceBaseUrl() + asset.fileName()
    }

    /**
     * 解析 FFmpeg 原生库下载地址。
     *
     * 只有指向当前平台产物的 http(s) 地址才会覆盖默认值；空值、被截断的值以及旧版本或其它平台残留的
     * 地址都会回退到当前平台的默认地址，避免下到与平台不匹配的原生库。
     */
    private fun ffmpegUrl(): String {
        val url = ClientSettingUrl.ffmpegUrl()
        return if (FFmpegNativePlatform.isUrlForCurrentPlatform(url)) {
            url
        } else {
            FFmpegNativePlatform.currentOrDefault().defaultUrl()
        }
    }

    /**
     * 把设置里不可用的 FFmpeg 下载地址修正为当前平台的默认地址。
     *
     * 旧版本的输入框会把长地址截断后写回配置，切换平台也会残留另一平台的地址；这些值本来就会被
     * [ffmpegUrl] 忽略，这里同步回设置项，避免界面显示与实际下载行为不一致。必须在客户端线程调用。
     */
    private fun normalizeFfmpegDownloadUrl() {
        val configured = ClientSettingUrl.ffmpegUrl()
        val resolved = ffmpegUrl()
        if (Objects.equals(configured, resolved)) {
            return
        }
        ClientSetting.ffmpegDownloadUrl = resolved
        ConfigManager.saveNow()
        Constants.LOGGER.info("[AssetManager] FFmpeg download URL reset to {}", resolved)
    }

    private fun reisaFile(suffix: String): Path {
        return reisaDir.resolve("reisa_$suffix.png")
    }

    private fun isFile(path: Path, minBytes: Long): Boolean {
        try {
            return Files.isRegularFile(path) && Files.size(path) >= minBytes
        } catch (e: IOException) {
            return false
        }
    }

    private fun isPng(path: Path): Boolean {
        try {
            Files.newInputStream(path).use { input ->
                val header = input.readNBytes(PNG_MAGIC.size)
                if (header.size < PNG_MAGIC.size) {
                    return false
                }
                for (i in PNG_MAGIC.indices) {
                    if (header[i] != PNG_MAGIC[i]) {
                        return false
                    }
                }
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }

    private fun isMp4(path: Path): Boolean {
        try {
            Files.newInputStream(path).use { input ->
                val header = input.readNBytes(64)
                var i = 4
                while (i + 4 <= header.size) {
                    if (header[i] == 'f'.code.toByte() && header[i + 1] == 't'.code.toByte() &&
                        header[i + 2] == 'y'.code.toByte() && header[i + 3] == 'p'.code.toByte()
                    ) {
                        return true
                    }
                    i++
                }
                return false
            }
        } catch (e: IOException) {
            return false
        }
    }

    private fun baseName(entryName: String): String {
        val index = entryName.lastIndexOf('/')
        return if (index >= 0) entryName.substring(index + 1) else entryName
    }

    private fun reisaSuffix(fileName: String?): String? {
        if (fileName == null) {
            return null
        }
        val lower = fileName.lowercase()
        if (!lower.startsWith("reisa_") || !lower.endsWith(".png")) {
            return null
        }
        val suffix = lower.substring("reisa_".length, lower.length - ".png".length)
        return if (REISA_SUFFIXES.contains(suffix)) suffix else null
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private class DownloadCancelledException : IOException()

    /**
     * 读取下载地址设置，隔离设置模块与资源管理器之间的初始化顺序。
     */
    private object ClientSettingUrl {

        fun resourceBaseUrl(): String {
            var base = ClientSetting.resourceBaseUrl.trim()
            if (base.isEmpty()) {
                return DEFAULT_RESOURCE_BASE_URL
            }
            return if (base.endsWith("/")) base else "$base/"
        }

        fun ffmpegUrl(): String {
            return ClientSetting.ffmpegDownloadUrl.trim()
        }

    }

}
