/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke;

import tech.kwik.flupke.core.Http3ClientConnection;
import tech.kwik.flupke.core.HttpError;
import tech.kwik.flupke.core.HttpStream;
import tech.kwik.flupke.impl.Http3ConnectionFactory;
import tech.kwik.flupke.impl.InterfaceBoundDatagramSocketFactory;
import tech.kwik.core.DatagramSocketFactory;
import tech.kwik.core.Statistics;
import tech.kwik.core.concurrent.DaemonThreadFactory;
import tech.kwik.core.log.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
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


public class Http3Client extends HttpClient implements Http3ConnectionSettings {

    private final Duration connectTimeout;
    private final Long receiveBufferSize;
    private final boolean disableCertificateCheck;
    private final int maxAdditionalPeerInitiatedUnidirectionalStreams;
    private final int maxAdditionalPeerInitiatedBidirectionalStreams;
    private final DatagramSocketFactory datagramSocketFactory;
    private final Logger logger;
    private Http3ClientConnection http3Connection;
    protected Http3ConnectionFactory http3ConnectionFactory;
    private final ExecutorService executorService;

    Http3Client(Duration connectTimeout, Long receiveBufferSize, boolean disableCertificateCheck,
                int maxAdditionalPeerInitiatedUnidirectionalStreams, int maxAdditionalPeerInitiatedBidirectionalStreams,
                InetAddress inetAddress, Logger logger) {
        this.connectTimeout = connectTimeout;
        this.receiveBufferSize = receiveBufferSize;
        this.disableCertificateCheck = disableCertificateCheck;
        this.maxAdditionalPeerInitiatedUnidirectionalStreams = maxAdditionalPeerInitiatedUnidirectionalStreams;
        this.maxAdditionalPeerInitiatedBidirectionalStreams = maxAdditionalPeerInitiatedBidirectionalStreams;
        this.logger = logger;
        this.http3ConnectionFactory = new Http3ConnectionFactory(this);
        this.datagramSocketFactory = new InterfaceBoundDatagramSocketFactory(inetAddress);

        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory("http3"));
    }

    public static HttpClient newHttpClient() {
        return new Http3ClientBuilder().build();
    }

    public static Http3ClientBuilder newBuilder() {
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

    @Deprecated
    public boolean isDisableCertificateCheck() {
        return disableCertificateCheck;
    }

    @Override
    public boolean disableCertificateCheck() {
        return disableCertificateCheck;
    }

    @Override
    public int maxAdditionalPeerInitiatedUnidirectionalStreams() {
        return maxAdditionalPeerInitiatedUnidirectionalStreams;
    }

    @Override
    public int maxAdditionalPeerInitiatedBidirectionalStreams() {
        return maxAdditionalPeerInitiatedBidirectionalStreams;
    }

    public DatagramSocketFactory getDatagramSocketFactory() {
        return datagramSocketFactory;
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
        http3Connection = http3ConnectionFactory.getConnection(request);
        http3Connection.connect();
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

    /**
     * Sends a CONNECT request (that creates a tunnel to a remote host) and returns a HttpStream object that can be used
     * to send/receive data to/from remote host.
     *
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method:
     * "In HTTP/2 and HTTP/3, the CONNECT method is used to establish a tunnel over a single stream."
     *
     * @param request
     * @return
     * @throws IOException
     * @throws HttpError
     */
    public HttpStream sendConnect(HttpRequest request) throws IOException, HttpError {
        http3Connection = http3ConnectionFactory.getConnection(request);
        http3Connection.connect();
        return http3Connection.sendConnect(request);
    }

    /**
     * Sends an Extended CONNECT request (that can be used for tunneling other protocols like websocket and webtransport).
     * See https://www.rfc-editor.org/rfc/rfc9220.html and  https://www.rfc-editor.org/rfc/rfc8441.html.
     *
     * @param request
     * @param protocol
     * @param scheme
     * @return
     * @throws IOException
     * @throws HttpError
     * @throws InterruptedException
     */
    public HttpStream sendExtendedConnect(HttpRequest request, String protocol, String scheme) throws IOException, HttpError, InterruptedException {
        http3Connection = http3ConnectionFactory.getConnection(request);
        http3Connection.connect();
        return http3Connection.sendExtendedConnect(request, protocol, scheme, Duration.ofSeconds(10));
    }

    /**
     * Creates a new Http3ClientConnection object, even if there is already a connection to the same host.
     * The returned Http3ClientConnection object is not yet connected, enabling the caller to set additional settings
     * before connecting.
     * @param request  the request for which the connection is to be used (used to determine the host and port)
     * @return
     * @throws IOException
     */
    public Http3ClientConnection createConnection(HttpRequest request) throws IOException {
        return http3ConnectionFactory.getConnection(request, true, true);
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
