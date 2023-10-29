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
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.SessionFactory;
import net.luminis.http3.webtransport.WebTransportStream;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.function.Consumer;

public class SessionFactoryImpl implements SessionFactory {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-http-3-settings-parameter-r
    // "The SETTINGS_WEBTRANSPORT_MAX_SESSIONS parameter indicates that the specified HTTP/3 endpoint is
    //  WebTransport-capable and the number of concurrent sessions it is willing to receive."
    private static final long SETTINGS_WEBTRANSPORT_MAX_SESSIONS = 0xc671706aL;

    @Override
    public Session createSession(Http3Client httpClient, URI serverUri) throws IOException, HttpError {
        return createSession(httpClient, serverUri, s -> {}, s -> {}, () -> {});
    }

    @Override
    public Session createSession(Http3Client httpClient, URI serverUri, Consumer<WebTransportStream> unidirectionalStreamHandler, Consumer<WebTransportStream> bidirectionalStreamHandler, Runnable closedCallback) throws IOException, HttpError {
        try {
            HttpRequest request = HttpRequest.newBuilder(serverUri).build();
            Http3ClientConnection httpClientConnection = httpClient.createConnection(request);
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-extended-connect-in-http-3
            // "To use WebTransport over HTTP/3, clients MUST send the SETTINGS_ENABLE_CONNECT_PROTOCOL setting with a value of "1"."
            httpClientConnection.addSettingsParameter(SETTINGS_WEBTRANSPORT_MAX_SESSIONS, 1);
            httpClientConnection.connect();

            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-creating-a-new-session
            // "In order to create a new WebTransport session, a client can send an HTTP CONNECT request.
            //  The :protocol pseudo-header field ([RFC8441]) MUST be set to webtransport.
            //  The :scheme field MUST be https. "
            String protocol = "webtransport";
            String schema = "https";
            CapsuleProtocolStream connectStream = httpClientConnection.sendExtendedConnectWithCapsuleProtocol(request, protocol, schema, Duration.ofSeconds(5));
            return new SessionImpl(httpClientConnection, connectStream, () -> {});
        }
        catch (InterruptedException e) {
            throw new HttpError("HTTP CONNECT request was interrupted");
        }
    }
}
