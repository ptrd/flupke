package net.luminis.http3;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

public class Http3ClientBuilder implements HttpClient.Builder {

    @Override
    public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
        return this;
    }

    @Override
    public HttpClient.Builder connectTimeout(Duration duration) {
        return this;
    }

    @Override
    public HttpClient.Builder sslContext(SSLContext sslContext) {
        return this;
    }

    @Override
    public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
        return this;
    }

    @Override
    public HttpClient.Builder executor(Executor executor) {
        return this;
    }

    @Override
    public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
        return this;
    }

    @Override
    public HttpClient.Builder version(HttpClient.Version version) {
        return this;
    }

    @Override
    public HttpClient.Builder priority(int priority) {
        return this;
    }

    @Override
    public HttpClient.Builder proxy(ProxySelector proxySelector) {
        return this;
    }

    @Override
    public HttpClient.Builder authenticator(Authenticator authenticator) {
        return this;
    }

    @Override
    public HttpClient build() {
        return new Http3Client();
    }
}
