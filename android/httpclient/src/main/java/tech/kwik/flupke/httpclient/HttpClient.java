package tech.kwik.flupke.httpclient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class HttpClient {

    public enum Version {
        HTTP_1_1,
        HTTP_2,
        HTTP_3
    }

    public enum Redirect {
        ALWAYS,
        NEVER,
        NORMAL
    }

    public abstract Optional<CookieHandler> cookieHandler();

    public abstract Optional<Duration> connectTimeout();

    public abstract HttpClient.Redirect followRedirects();

    public abstract Optional<ProxySelector> proxy();

    public abstract SSLContext sslContext();

    public abstract SSLParameters sslParameters();

    public abstract Optional<Authenticator> authenticator();

    public abstract HttpClient.Version version();

    public abstract Optional<Executor> executor();

    public abstract <T> HttpResponse<T> send(HttpRequest request,  HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException;

    public abstract <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler);

    public abstract <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                                                     HttpResponse.PushPromiseHandler<T> pushPromiseHandler);


    public interface Builder {

        Builder authenticator(Authenticator authenticator);

        Builder cookieHandler(CookieHandler cookieHandler);

        Builder connectTimeout(Duration duration);

        Builder executor(Executor executor);

        Builder followRedirects(HttpClient.Redirect policy);

        Builder priority(int priority);

        Builder proxy(ProxySelector proxySelector);

        Builder receiveBufferSize(long bufferSize);

        Builder sslContext(SSLContext sslContext);

        Builder sslParameters(SSLParameters sslParameters);

        Builder version(Version version);

        HttpClient build();
    }
}
