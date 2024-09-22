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
package net.luminis.http3.webtransport.impl;

import net.luminis.http3.Http3Client;
import net.luminis.http3.core.CapsuleProtocolStream;
import net.luminis.http3.core.Http3ClientConnection;
import net.luminis.http3.core.HttpError;
import net.luminis.http3.webtransport.ClientSessionFactory;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.function.Consumer;

import static net.luminis.http3.webtransport.Constants.STREAM_TYPE_WEBTRANSPORT;


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

            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-extended-connect-in-http-3
            // "To use WebTransport over HTTP/3, clients MUST send the SETTINGS_ENABLE_CONNECT_PROTOCOL setting with a value of "1"."
            httpClientConnection.addSettingsParameter(SETTINGS_WEBTRANSPORT_MAX_SESSIONS, 1);
            httpClientConnection.connect();

            maxSessions = httpClientConnection.getSettingsParameter(SETTINGS_WEBTRANSPORT_MAX_SESSIONS).orElse(0L);

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
            CapsuleProtocolStream connectStream = httpClientConnection.sendExtendedConnectWithCapsuleProtocol(request, protocol, schema, Duration.ofSeconds(5));
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
