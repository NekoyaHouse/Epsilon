package com.github.epsilon.utils.network;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.github.epsilon.Constants.mc;

public class Http {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private static final Gson GSON = new Gson();

    /**
     * 流式下载进度回调。
     */
    @FunctionalInterface
    public interface ProgressListener {

        /**
         * @param downloaded 已下载字节数
         * @param total      服务端声明的总字节数，未知时为 {@code -1}
         */
        void onProgress(long downloaded, long total);

    }

    /**
     * 把远端文件流式写入本地路径。
     * <p>
     * 调用方负责传入临时文件路径、校验内容以及移动到最终位置；本方法只处理 HTTP 语义：
     * 跟随重定向、设定超时、复用游戏代理，并在取消标记被置位时中断传输。
     *
     * @param url         下载地址
     * @param destination 目标文件，父目录会自动创建
     * @param listener    进度回调，可为空
     * @param cancelled   取消标记，可为空
     * @throws IOException 网络错误、HTTP 状态异常或下载被取消
     */
    public static void download(String url, Path destination, ProgressListener listener, BooleanSupplier cancelled) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .proxy(resolveProxySelector())
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                throw new IOException("HTTP " + status + " for " + url);
            }

            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }

            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(destination,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                long downloaded = 0L;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        throw new IOException("Download cancelled");
                    }
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (listener != null) {
                        listener.onProgress(downloaded, total);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    /**
     * Java HttpClient 只支持 HTTP 代理，这里把游戏内代理设置转换成选择器；SOCKS 代理回退直连。
     */
    private static ProxySelector resolveProxySelector() {
        try {
            if (mc != null) {
                Proxy proxy = mc.getProxy();
                if (proxy != null && proxy.type() == Proxy.Type.HTTP && proxy.address() instanceof InetSocketAddress address) {
                    return ProxySelector.of(address);
                }
            }
        } catch (Throwable ignored) {
            // 客户端尚未初始化时退回直连。
        }
        return ProxySelector.getDefault();
    }

    public static class Request {
        private final HttpRequest.Builder builder;
        private boolean hasBody;
        private Consumer<Exception> exceptionHandler = Exception::printStackTrace;

        private Request(String url) {
            this.builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT);
        }

        public Request bearer(String token) {
            builder.header("Authorization", "Bearer " + token);
            return this;
        }

        public Request bodyForm(String string) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            return body(string);
        }

        public Request bodyJson(String string) {
            builder.header("Content-Type", "application/json");
            return body(string);
        }

        public Request bodyJson(Object object) {
            builder.header("Content-Type", "application/json");
            return body(GSON.toJson(object));
        }

        private Request body(String string) {
            builder.method("POST", HttpRequest.BodyPublishers.ofString(string));
            hasBody = true;
            return this;
        }

        public Request exceptionHandler(Consumer<Exception> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public <T> T sendJson(Type type) {
            try {
                HttpResponse<String> res = send(HttpResponse.BodyHandlers.ofString(), "application/json");
                return res != null ? GSON.fromJson(res.body(), type) : null;
            } catch (IOException | InterruptedException e) {
                exceptionHandler.accept(e);
                return null;
            }
        }

        public InputStream sendInputStream() {
            try {
                HttpResponse<InputStream> res = send(HttpResponse.BodyHandlers.ofInputStream(), "*/*");
                return res != null ? res.body() : null;
            } catch (IOException | InterruptedException e) {
                exceptionHandler.accept(e);
                return null;
            }
        }

        private <T> HttpResponse<T> send(HttpResponse.BodyHandler<T> bodyHandler, String accept) throws IOException, InterruptedException {
            builder.header("Accept", accept);
            if (!hasBody) builder.method("GET", HttpRequest.BodyPublishers.noBody());
            HttpResponse<T> res = CLIENT.send(builder.build(), bodyHandler);
            return res.statusCode() == 200 ? res : null;
        }
    }

    public static Request get(String url) {
        return new Request(url);
    }

    public static Request post(String url) {
        return new Request(url);
    }

}
