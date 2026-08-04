package com.github.epsilon.utils.network;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private static final Gson GSON = new Gson();

    public static class Request {
        private final HttpRequest.Builder builder;
        private boolean hasBody;
        private Consumer<Exception> exceptionHandler = Exception::printStackTrace;

        private Request(String url) {
            this.builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
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
