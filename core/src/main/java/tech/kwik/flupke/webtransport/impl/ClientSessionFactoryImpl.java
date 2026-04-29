/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.webtransport.impl;

import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.Http3ClientConnection;
import tech.kwik.flupke.HttpError;
import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.core.CapsuleProtocolStream;
import tech.kwik.flupke.impl.CapsuleProtocolStreamImpl;
import tech.kwik.flupke.impl.StructuredFields;
import tech.kwik.flupke.impl.StructuredFieldsException;
import tech.kwik.flupke.webtransport.ClientSessionFactory;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static tech.kwik.flupke.webtransport.Constants.STREAM_TYPE_WEBTRANSPORT;


/**
 * A factory for creating WebTransport sessions for a given server.
 * All sessions created by this factory are associated with a single HTTP/3 connection, that is created by this factory.
 */
public class ClientSessionFactoryImpl extends AbstractSessionFactoryImpl implements ClientSessionFactory {

    private final String server;
    private final int serverPort;
    private final Http3ClientConnection httpClientConnection;
    private final long maxSessions;

    /**
     * Creates a new WebTransport session factory for a given server.
     *
     * @param serverUri       server URI, only the host and port are used (i.e. path etc. is ignored)
     * @param httpClient      the client to use for creating the HTTP/3 connection
     * @param executor
     * @throws IOException if the connection to the server cannot be established
     */
    public ClientSessionFactoryImpl(URI serverUri, Http3Client httpClient, ExecutorService executor) throws IOException {
        this(serverUri, httpClient, executor, 3, 3);
    }

    /**
     * Creates a new WebTransport session factory for a given server.
     *
     * @param serverUri           server URI, only the host and port are used (i.e. path etc. is ignored)
     * @param httpClient          the client to use for creating the HTTP/3 connection
     * @param executor
     * @param maxStreamsQueued    the maximum number of streams that can be queued per session
     * @param maxDatagramsQueued  the maximum number of datagrams that can be buffered before a session is established
     * @throws IOException if the connection to the server cannot be established
     */
    public ClientSessionFactoryImpl(URI serverUri, Http3Client httpClient, ExecutorService executor, int maxStreamsQueued, int maxDatagramsQueued) throws IOException {
        super(executor, maxStreamsQueued, maxDatagramsQueued);
        this.server = serverUri.getHost();
        this.serverPort = serverUri.getPort();

        try {
            HttpRequest request = HttpRequest.newBuilder(new URI("https://" + server + ":" + serverPort)).build();
            httpClientConnection = httpClient.createConnection(request);

            // Send all WebTransport setting variants for maximum cross-implementation compatibility.
            // Current (draft-06+)
            httpClientConnection.addSettingsParameter(SETTINGS_WT_MAX_SESSIONS_DRAFT_07_12, 1);
            // Draft-13 (used by moqtail.dev / older Flupke servers)
            httpClientConnection.addSettingsParameter(SETTINGS_WT_MAX_SESSIONS_DRAFT_13_14, 1);
            // Deprecated variants (used by some servers)
            httpClientConnection.addSettingsParameter(SETTINGS_WT_ENABLE_DRAFT_06, 1);
            httpClientConnection.addSettingsParameter(SETTINGS_WT_MAX_SESSIONS_DRAFT_04_05, 1);
            httpClientConnection.connect();

            // Check all WT_MAX_SESSIONS variants the server might advertise.
            // Default to unlimited (Long.MAX_VALUE) since many servers omit this setting.
            maxSessions = httpClientConnection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS_DRAFT_07_12)
                .or(() -> httpClientConnection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS_DRAFT_13_14))
                .or(() -> httpClientConnection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS_DRAFT_04_05))
                .orElse(Long.MAX_VALUE);

            httpClientConnection.registerUnidirectionalStreamType(STREAM_TYPE_WEBTRANSPORT, this::handleUnidirectionalStream);
            httpClientConnection.registerBidirectionalStreamHandler(this::handleBidirectionalStream);
            httpClientConnection.registerDefaultDatagramHandler((streamId, data) -> handleEarlyDatagram(streamId, data)); // TODO: moet dit ook niet voor server?
        }
        catch (URISyntaxException e) {
            throw new IOException("Invalid server URI: " + server);
        }
    }

    @Override
    public Session createSession(URI serverUri) throws IOException, HttpError {
        return createSession(serverUri, s -> {}, s -> {});
    }

    @Override
    public Session createSession(HttpRequest request) throws IOException, HttpError {
        return createSession(request, s -> {}, s -> {});
    }

    @Override
    public Session createSession(URI webTransportUri, Consumer<WebTransportStream> unidirectionalStreamHandler,
                                 Consumer<WebTransportStream> bidirectionalStreamHandler) throws IOException, HttpError {
        HttpRequest request = HttpRequest.newBuilder(webTransportUri).build();
        return createSession(request, unidirectionalStreamHandler, bidirectionalStreamHandler);
    }

    @Override
    public Session createSession(URI serverUri, List<String> availableProtocols) throws IOException, HttpError {
        return createSession(serverUri, availableProtocols, s -> {}, s -> {});
    }

    @Override
    public Session createSession(URI serverUri, List<String> availableProtocols,
                                 Consumer<WebTransportStream> unidirectionalStreamHandler,
                                 Consumer<WebTransportStream> bidirectionalStreamHandler) throws IOException, HttpError {
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.html#section-3.3
        // "The client MAY include a WT-Available-Protocols header field in the CONNECT request."
        // "Both WT-Available-Protocols and WT-Protocol are Structured Fields [FIELDS]. WT-Available-Protocols is a List.
        // (...) In both cases, the only valid value type is a String."
        HttpRequest request = HttpRequest.newBuilder(serverUri)
                .header("wt-available-protocols", StructuredFields.serializeStringList(availableProtocols))
                .build();
        return createSession(request, unidirectionalStreamHandler, bidirectionalStreamHandler);
    }

    @Override
    public Session createSession(HttpRequest request, Consumer<WebTransportStream> unidirectionalStreamHandler,
                                 Consumer<WebTransportStream> bidirectionalStreamHandler) throws IOException, HttpError {
        if (!server.equals(request.uri().getHost()) || serverPort != request.uri().getPort()) {
            throw new IllegalArgumentException("WebTransport URI must have the same host and port as the server URI used with the constructor");
        }
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-limiting-the-number-of-simu
        // "The client MUST NOT open more sessions than indicated in the server SETTINGS parameters. "
        if (sessionRegistry.size() >= maxSessions) {
            throw new IllegalStateException("Maximum number of sessions (" + maxSessions + ") reached");
        }

        try {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-creating-a-new-session
            // "In order to create a new WebTransport session, a client can send an HTTP CONNECT request.
            //  The :protocol pseudo-header field ([RFC8441]) MUST be set to webtransport.
            //  The :scheme field MUST be https. "
            String protocol = "webtransport";
            String schema = "https";
            HttpResponse<HttpStream> connectResponse = httpClientConnection.sendExtendedConnectAndGetResponse(request, protocol, schema, Duration.ofSeconds(5));
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.html#section-3.3
            // "If the server receives such a header, it MAY include a WT-Protocol field in a successful (2xx) response."
            String negotiatedProtocol = null;
            Optional<String> wtProtocolHeader = connectResponse.headers().firstValue("wt-protocol");
            if (wtProtocolHeader.isPresent()) {
                try {
                    negotiatedProtocol = StructuredFields.parseString(wtProtocolHeader.get());
                }
                catch (StructuredFieldsException e) {
                    // Malformed WT-Protocol header; treat as if no protocol was negotiated.
                }
            }
            CapsuleProtocolStream connectStream = new CapsuleProtocolStreamImpl(connectResponse.body());
            WebTransportContext context = new WebTransportContext(request.uri(), negotiatedProtocol);
            SessionImpl session = new SessionImpl(httpClientConnection, context, connectStream, unidirectionalStreamHandler, bidirectionalStreamHandler, this);
            registerSession(session);
            return session;
        }
        catch (InterruptedException e) {
            // Thrown by sendExtendedConnectAndGetResponse
            throw new InterruptedIOException("HTTP CONNECT request was interrupted");
        }
    }

    @Override
    public URI getServerUri() {
        return URI.create("https://" + server + ":" + serverPort);
    }

    @Override
    public int getMaxConcurrentSessions() {
        return (int) maxSessions;
    }

    static public Builder newBuilder() {
        return new ClientSessionFactoryBuilder();
    }

    private static class ClientSessionFactoryBuilder implements Builder {

        private URI serverUri;
        private Http3Client httpClient;
        private int maxStreamsQueued = 3;
        private int maxDatagramsQueued = 3;

        @Override
        public ClientSessionFactory build() throws IOException {
            return new ClientSessionFactoryImpl(serverUri, httpClient, Executors.newCachedThreadPool(), maxStreamsQueued, maxDatagramsQueued);
        }

        @Override
        public Builder serverUri(URI serverUri) {
            this.serverUri = serverUri;
            return this;
        }

        @Override
        public Builder httpClient(Http3Client httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        @Override
        public Builder maxStreamsQueued(int maxStreamsQueued) {
            this.maxStreamsQueued = maxStreamsQueued;
            return this;
        }

        @Override
        public Builder maxDatagramsQueued(int maxDatagramsQueued) {
            this.maxDatagramsQueued = maxDatagramsQueued;
            return this;
        }
    }
}
