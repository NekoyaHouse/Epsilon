package com.github.epsilon.utils.network;

import com.github.epsilon.Constants;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Http {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private static final Gson GSON = new Gson();

    public static class Request {
        private final HttpRequest.Builder builder;
        private final String url;
        private boolean hasBody;
        private Consumer<Exception> exceptionHandler;

        private Request(String url) {
            this.url = url;
            this.builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
        }

        public Request timeout(Duration timeout) {
            builder.timeout(timeout);
            return this;
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

        private void handleException(Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.accept(e);
                return;
            }

            if (e instanceof HttpTimeoutException) {
                Constants.LOGGER.error("请求超时: {}", url);
            } else {
                Constants.LOGGER.error("请求失败: {}", url, e);
            }
        }

        public <T> T sendJson(Type type) {
            try {
                HttpResponse<String> res = send(HttpResponse.BodyHandlers.ofString(), "application/json");
                return res != null ? GSON.fromJson(res.body(), type) : null;
            } catch (IOException e) {
                handleException(e);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleException(e);
                return null;
            }
        }

        public InputStream sendInputStream() {
            try {
                HttpResponse<InputStream> res = send(HttpResponse.BodyHandlers.ofInputStream(), "*/*");
                return res != null ? res.body() : null;
            } catch (IOException e) {
                handleException(e);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleException(e);
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
