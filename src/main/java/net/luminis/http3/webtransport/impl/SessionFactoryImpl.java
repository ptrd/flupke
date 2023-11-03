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
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.SessionFactory;
import net.luminis.http3.webtransport.WebTransportStream;
import net.luminis.quic.generic.VariableLengthInteger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static net.luminis.http3.webtransport.Constants.STREAM_TYPE_WEBTRANSPORT;

public class SessionFactoryImpl implements SessionFactory {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-http-3-settings-parameter-r
    // "The SETTINGS_WEBTRANSPORT_MAX_SESSIONS parameter indicates that the specified HTTP/3 endpoint is
    //  WebTransport-capable and the number of concurrent sessions it is willing to receive."
    private static final long SETTINGS_WEBTRANSPORT_MAX_SESSIONS = 0xc671706aL;

    private Map<Long, SessionImpl> sessionRegistry = new ConcurrentHashMap<>();

    @Override
    public Session createSession(Http3Client httpClient, URI serverUri) throws IOException, HttpError {
        return createSession(httpClient, serverUri, s -> {}, s -> {}, () -> {});
    }

    @Override
    public Session createSession(Http3Client httpClient, URI serverUri, Consumer<WebTransportStream> unidirectionalStreamHandler,
                                 Consumer<WebTransportStream> bidirectionalStreamHandler, Runnable closedCallback) throws IOException, HttpError {
        try {
            HttpRequest request = HttpRequest.newBuilder(serverUri).build();
            Http3ClientConnection httpClientConnection = httpClient.createConnection(request);
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-extended-connect-in-http-3
            // "To use WebTransport over HTTP/3, clients MUST send the SETTINGS_ENABLE_CONNECT_PROTOCOL setting with a value of "1"."
            httpClientConnection.addSettingsParameter(SETTINGS_WEBTRANSPORT_MAX_SESSIONS, 1);
            httpClientConnection.connect();

            httpClientConnection.registerUnidirectionalStreamType(STREAM_TYPE_WEBTRANSPORT, this::handleUnidirectionalStream);
            httpClientConnection.registerBidirectionalStreamHandler(this::handleBidirectionalStream);

            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-creating-a-new-session
            // "In order to create a new WebTransport session, a client can send an HTTP CONNECT request.
            //  The :protocol pseudo-header field ([RFC8441]) MUST be set to webtransport.
            //  The :scheme field MUST be https. "
            String protocol = "webtransport";
            String schema = "https";
            CapsuleProtocolStream connectStream = httpClientConnection.sendExtendedConnectWithCapsuleProtocol(request, protocol, schema, Duration.ofSeconds(5));
            long sessionId = connectStream.getStreamId();
            SessionImpl session = new SessionImpl(httpClientConnection, connectStream, unidirectionalStreamHandler, bidirectionalStreamHandler, closedCallback);
            sessionRegistry.put(sessionId, session);
            return session;
        }
        catch (InterruptedException e) {
            throw new HttpError("HTTP CONNECT request was interrupted");
        }
    }

    void handleUnidirectionalStream(HttpStream httpStream) {
        try {
            InputStream inputStream = httpStream.getInputStream();
            long sessionId = VariableLengthInteger.parseLong(inputStream);
            SessionImpl session = sessionRegistry.get(sessionId);
            if (session != null) {
                session.handleUnidirectionalStream(inputStream);
            }
        }
        catch (IOException e) {
            // Reading session id failed, nothing to do.
        }
    }

    private void handleBidirectionalStream(HttpStream httpStream) {
        try {
            InputStream inputStream = httpStream.getInputStream();
            long signalValue = VariableLengthInteger.parseLong(inputStream);
            if (signalValue == 0x41) {
                long sessionId = VariableLengthInteger.parseLong(inputStream);
                SessionImpl session = sessionRegistry.get(sessionId);
                if (session != null) {
                    session.handleBidirectionalStream(httpStream);
                }
            }
        }
        catch (IOException e) {
            // Reading session id or signal value failed, nothing to do.
        }
    }
}
