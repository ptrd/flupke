/*
 * Copyright © 2023, 2024 Peter Doornbosch
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
import tech.kwik.flupke.core.CapsuleProtocolStream;
import tech.kwik.flupke.core.Http3ClientConnection;
import tech.kwik.flupke.core.HttpError;
import tech.kwik.flupke.impl.CapsuleProtocolStreamImpl;
import tech.kwik.flupke.webtransport.ClientSessionFactory;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.time.Duration;
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
     * @param serverUri     server URI, only the host and port are used (i.e. path etc. is ignored)
     * @param httpClient    the client to use for creating the HTTP/3 connection
     * @throws IOException  if the connection to the server cannot be established
     */
    public ClientSessionFactoryImpl(URI serverUri, Http3Client httpClient) throws IOException {
        this.server = serverUri.getHost();
        this.serverPort = serverUri.getPort();

        try {
            HttpRequest request = HttpRequest.newBuilder(new URI("https://" + server + ":" + serverPort)).build();
            httpClientConnection = httpClient.createConnection(request);

            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html#section-3.1
            // "A client supporting WebTransport over HTTP/3 MUST send the SETTINGS_WT_MAX_SESSIONS setting with a value greater than "0"."
            httpClientConnection.addSettingsParameter(SETTINGS_WT_MAX_SESSIONS, 1);
            httpClientConnection.connect();

            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html#section-5.2
            // "This document defines a SETTINGS_WT_MAX_SESSIONS setting that allows the server to limit the maximum number
            //  of concurrent WebTransport sessions on a single HTTP/3 connection.
            //  The client MUST NOT open more simultaneous sessions than indicated in the server SETTINGS parameter. "
            maxSessions = httpClientConnection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS).orElse(0L);

            httpClientConnection.registerUnidirectionalStreamType(STREAM_TYPE_WEBTRANSPORT, this::handleUnidirectionalStream);
            httpClientConnection.registerBidirectionalStreamHandler(this::handleBidirectionalStream);
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
    public Session createSession(URI webTransportUri, Consumer<WebTransportStream> unidirectionalStreamHandler,
                                 Consumer<WebTransportStream> bidirectionalStreamHandler) throws IOException, HttpError {
        if (!server.equals(webTransportUri.getHost()) || serverPort != webTransportUri.getPort()) {
            throw new IllegalArgumentException("WebTransport URI must have the same host and port as the server URI used with the constructor");
        }
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-limiting-the-number-of-simu
        // "The client MUST NOT open more sessions than indicated in the server SETTINGS parameters. "
        if (sessionRegistry.size() >= maxSessions) {
            throw new IllegalStateException("Maximum number of sessions reached");
        }

        try {

            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-creating-a-new-session
            // "In order to create a new WebTransport session, a client can send an HTTP CONNECT request.
            //  The :protocol pseudo-header field ([RFC8441]) MUST be set to webtransport.
            //  The :scheme field MUST be https. "
            String protocol = "webtransport";
            String schema = "https";
            HttpRequest request = HttpRequest.newBuilder(webTransportUri).build();
            CapsuleProtocolStream connectStream = new CapsuleProtocolStreamImpl(httpClientConnection.sendExtendedConnect(request, protocol, schema, Duration.ofSeconds(5)));
            SessionImpl session = new SessionImpl(httpClientConnection, connectStream, unidirectionalStreamHandler, bidirectionalStreamHandler, this);
            registerSession(session);
            return session;
        }
        catch (InterruptedException e) {
            throw new HttpError("HTTP CONNECT request was interrupted");
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
}
