package com.github.epsilon.utils.network

import com.github.epsilon.Constants
import com.google.gson.Gson
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.Executors
import java.util.function.BooleanSupplier
import java.util.function.Consumer

object Http {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"

    private val CLIENT: HttpClient = HttpClient.newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build()

    private val GSON = Gson()

    /**
     * 流式下载进度回调。
     */
    fun interface ProgressListener {

        /**
         * @param downloaded 已下载字节数
         * @param total      服务端声明的总字节数，未知时为 {@code -1}
         */
        fun onProgress(downloaded: Long, total: Long)

    }

    /**
     * 把远端文件流式写入本地路径。
     *
     * 调用方负责传入临时文件路径、校验内容以及移动到最终位置；本方法只处理 HTTP 语义：
     * 跟随重定向、设定超时、复用游戏代理，并在取消标记被置位时中断传输。
     *
     * @param url         下载地址
     * @param destination 目标文件，父目录会自动创建
     * @param listener    进度回调，可为空
     * @param cancelled   取消标记，可为空
     * @throws IOException 网络错误、HTTP 状态异常或下载被取消
     */
    fun download(url: String, destination: Path, listener: ProgressListener?, cancelled: BooleanSupplier?) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .proxy(resolveProxySelector())
            .build()

        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(30))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val status = response.statusCode()
            if (status != 200) {
                throw IOException("HTTP $status for $url")
            }

            val total = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            val parent = destination.getParent()
            if (parent != null) {
                Files.createDirectories(parent)
            }

            response.body().use { input ->
                Files.newOutputStream(
                    destination,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
                ).use { output: OutputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read = input.read(buffer)
                    while (read != -1) {
                        if (cancelled != null && cancelled.getAsBoolean()) {
                            throw IOException("Download cancelled")
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (listener != null) {
                            listener.onProgress(downloaded, total)
                        }
                        read = input.read(buffer)
                    }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Download interrupted", e)
        }
    }

    /**
     * Java HttpClient 只支持 HTTP 代理，这里把游戏内代理设置转换成选择器；SOCKS 代理回退直连。
     */
    private fun resolveProxySelector(): ProxySelector {
        try {
            val client = Constants.mc
            if (client != null) {
                val proxy = client.getProxy()
                if (proxy != null && proxy.type() == Proxy.Type.HTTP && proxy.address() is InetSocketAddress) {
                    return ProxySelector.of(proxy.address() as InetSocketAddress)
                }
            }
        } catch (ignored: Throwable) {
            // 客户端尚未初始化时退回直连。
        }
        return ProxySelector.getDefault()
    }

    class Request internal constructor(url: String) {

        private val builder: HttpRequest.Builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)

        private var hasBody = false
        private var exceptionHandler: Consumer<Exception> = Consumer { it.printStackTrace() }

        fun bearer(token: String): Request {
            builder.header("Authorization", "Bearer $token")
            return this
        }

        fun bodyForm(string: String): Request {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
            return body(string)
        }

        fun bodyJson(string: String): Request {
            builder.header("Content-Type", "application/json")
            return body(string)
        }

        fun bodyJson(objectValue: Any): Request {
            builder.header("Content-Type", "application/json")
            return body(GSON.toJson(objectValue))
        }

        private fun body(string: String): Request {
            builder.method("POST", HttpRequest.BodyPublishers.ofString(string))
            hasBody = true
            return this
        }

        fun exceptionHandler(exceptionHandler: Consumer<Exception>): Request {
            this.exceptionHandler = exceptionHandler
            return this
        }

        fun <T> sendJson(type: Type): T? {
            try {
                val res = send(HttpResponse.BodyHandlers.ofString(), "application/json")
                return if (res != null) GSON.fromJson<T>(res.body(), type) else null
            } catch (e: IOException) {
                exceptionHandler.accept(e)
                return null
            } catch (e: InterruptedException) {
                exceptionHandler.accept(e)
                return null
            }
        }

        fun sendInputStream(): InputStream? {
            try {
                val res = send(HttpResponse.BodyHandlers.ofInputStream(), "*/*")
                return if (res != null) res.body() else null
            } catch (e: IOException) {
                exceptionHandler.accept(e)
                return null
            } catch (e: InterruptedException) {
                exceptionHandler.accept(e)
                return null
            }
        }

        private fun <T> send(bodyHandler: HttpResponse.BodyHandler<T>, accept: String): HttpResponse<T>? {
            builder.header("Accept", accept)
            if (!hasBody) {
                builder.method("GET", HttpRequest.BodyPublishers.noBody())
            }
            val res = CLIENT.send(builder.build(), bodyHandler)
            return if (res.statusCode() == 200) res else null
        }

    }

    fun get(url: String): Request {
        return Request(url)
    }

    fun post(url: String): Request {
        return Request(url)
    }

}
