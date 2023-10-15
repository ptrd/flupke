/*
 * Copyright © 2023 Peter Doornbosch
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
package net.luminis.http3.core;

import net.luminis.http3.server.HttpError;
import net.luminis.quic.Statistics;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

public interface Http3ClientConnection extends Http3Connection {

    static final int DEFAULT_HTTP3_PORT = 443;

    void setReceiveBufferSize(long receiveBufferSize);

    void connect(int connectTimeoutInMillis) throws IOException;

    <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException;

    /**
     * Sends a CONNECT method request.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method:
     * "In HTTP/2 and HTTP/3, the CONNECT method is used to establish a tunnel over a single stream."
     *
     * @param request the request object; note that the HTTP method specified in this request is ignored
     * @return
     */
    HttpStream sendConnect(HttpRequest request) throws IOException, HttpError;

    /**
     * Sends an Extended CONNECT request (that can be used for tunneling other protocols like websocket and webtransport).
     * See https://www.rfc-editor.org/rfc/rfc9220.html and  https://www.rfc-editor.org/rfc/rfc8441.html.
     * Note that this method is only supported by servers that support Extended Connect (RFC 9220). If the server does
     * not support it, an HttpError is thrown. In any case, the client has to wait for the SETTINGS frame to be received
     * (to determine whether the server supports Extended Connect), so this method may block for a while or throw a
     * HttpError if the SETTINGS frame is not received in time.
     * @param request
     * @param protocol  the protocol to use over the tunneled connection (e.g. "websocket" or "webtransport")
     * @param scheme    "http" or "https"
     * @param settingsFrameTimeout  max time to wait for the SETTINGS frame to be received
     * @return
     * @throws IOException
     * @throws HttpError
     * @throws InterruptedException
     */
    HttpStream sendExtendedConnect(HttpRequest request, String protocol, String scheme, Duration settingsFrameTimeout) throws InterruptedException, HttpError, IOException;

    /**
     * Sends an Extended CONNECT request (that can be used for tunneling other protocols like websocket and webtransport),
     * and returns a Capsule Protocol layer on top of the stream that results from the CONNECT request.
     * See https://www.rfc-editor.org/rfc/rfc9220.html,  https://www.rfc-editor.org/rfc/rfc8441.html and
     * https://www.rfc-editor.org/rfc/rfc9297.html#name-the-capsule-protocol.
     * Note that this method is only supported by servers that support Extended Connect (RFC 9220). If the server does
     * not support it, an HttpError is thrown. In any case, the client has to wait for the SETTINGS frame to be received
     * (to determine whether the server supports Extended Connect), so this method may block for a while or throw a
     * HttpError if the SETTINGS frame is not received in time.
     * @param request
     * @param protocol  the protocol to use over the tunneled connection (e.g. "websocket" or "webtransport")
     * @param scheme    "http" or "https"
     * @param settingsFrameTimeout  max time to wait for the SETTINGS frame to be received
     * @return
     * @throws IOException
     * @throws HttpError
     * @throws InterruptedException
     */
    CapsuleProtocolStream sendExtendedConnectWithCapsuleProtocol(HttpRequest request, String protocol, String scheme, Duration settingsFrameTimeout) throws InterruptedException, HttpError, IOException;

    /**
     * HTTP/3 extension method: allow registration of a handler for an (incoming) bidirectional stream.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-bidirectional-streams
     * "HTTP/3 does not use server-initiated bidirectional streams, though an extension could define a use for these streams."
     * @param streamHandler
     */
    void registerBidirectionalStreamHandler(Consumer<HttpStream> streamHandler);

    Statistics getConnectionStats();
}
