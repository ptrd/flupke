/*
 * Copyright © 2019 Peter Doornbosch
 *
 * This file is part of Flupke, a HTTP3 client Java library
 *
 * Flupke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Flupke is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.http3;

import net.luminis.http3.impl.Http3Connection;
import net.luminis.http3.impl.Http3ConnectionFactory;
import net.luminis.quic.concurrent.DaemonThreadFactory;
import net.luminis.quic.Statistics;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Http3Client extends HttpClient {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final Duration connectTimeout;
    private final Long receiveBufferSize;
    private Http3Connection http3Connection;
    private Http3ConnectionFactory http3ConnectionFactory;
    private final ExecutorService executorService;

    Http3Client(Duration connectTimeout, Long receiveBufferSize) {
        this.connectTimeout = connectTimeout;
        this.receiveBufferSize = receiveBufferSize;
        this.http3ConnectionFactory = new Http3ConnectionFactory(this);

        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory("http3"));
    }

    public static HttpClient newHttpClient() {
        return new Http3ClientBuilder().build();
    }

    public static Builder newBuilder() {
        return new Http3ClientBuilder();
    }

    public Optional<Long> receiveBufferSize() {
        return Optional.ofNullable(receiveBufferSize);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.ofNullable(connectTimeout);
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        return null;
    }

    @Override
    public SSLParameters sslParameters() {
        return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return null;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
        http3Connection = http3ConnectionFactory.getConnection(request);
        http3Connection.connect((int) connectTimeout().orElse(DEFAULT_CONNECT_TIMEOUT).toMillis());
        return http3Connection.send(request, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
        executorService.submit(() -> {
            try {
                future.complete(send(request, responseBodyHandler));
            }
            catch (IOException ex) {
                future.completeExceptionally(ex);
            }
            catch (RuntimeException ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        throw new UnsupportedOperationException("server push is not (yet) supported");
    }

    public Statistics getConnectionStatistics() {
        if (http3Connection != null) {
            return http3Connection.getConnectionStats();
        }
        else {
            return null;
        }
    }
}
