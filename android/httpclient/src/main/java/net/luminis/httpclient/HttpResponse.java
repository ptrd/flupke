package net.luminis.httpclient;

import javax.net.ssl.SSLSession;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

public interface HttpResponse<T> {

    Optional<HttpResponse<T>> previousResponse();

    HttpHeaders headers();

    T body();

    int statusCode();

    HttpClient.Version version();

    HttpRequest request();

    Optional<SSLSession> sslSession();

    URI uri();

    interface BodyHandler<T> {
        HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo);
    }

    interface PushPromiseHandler<T> {

        void applyPushPromise(HttpRequest initiatingRequest, HttpRequest pushPromiseRequest,
                              Function<BodyHandler<T>,CompletableFuture<HttpResponse<T>>> acceptor);
    }

    interface ResponseInfo {
        int statusCode();

        HttpHeaders headers();

        HttpClient.Version version();
    }

    interface BodySubscriber<T> {

        void onSubscribe(Flow.Subscription subscription);

        void onNext(List<ByteBuffer> wrap);

        void onComplete();

        CompletionStage<T> getBody();
    }

    class BodyHandlers {

        public static HttpResponse.BodyHandler<String> ofString() {
            return new StringBodyHandlerImpl();
        }

        public static HttpResponse.BodyHandler<Path> ofFile(Path file) {
            return new FileBodyHandlerImpl(file);
        }
    }
}
