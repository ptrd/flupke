/*
 * Copyright © 2024, 2025 Peter Doornbosch
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

import tech.kwik.core.concurrent.DaemonThreadFactory;
import tech.kwik.flupke.server.Http3ServerConnection;
import tech.kwik.flupke.server.Http3ServerExtension;
import tech.kwik.flupke.server.Http3ServerExtensionFactory;
import tech.kwik.flupke.webtransport.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static tech.kwik.flupke.webtransport.impl.AbstractSessionFactoryImpl.*;

public class WebTransportExtensionFactory implements Http3ServerExtensionFactory {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.html#section-9.2
    // "Setting Name:SETTINGS_WT_ENABLED
    //  Value: 0x2c7cf000"
    public static final long SETTINGS_WT_ENABLED = 0x2c7cf000L;

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html#section-9.2
    // "Setting Name: WT_MAX_SESSIONS
    //  Value: 0x14e9cd29
    public static final long WT_MAX_SESSIONS = 0x14e9cd29L;

    // https://www.rfc-editor.org/rfc/rfc9297#section-2.1.1
    // "An endpoint can indicate to its peer that it is willing to receive HTTP/3 Datagrams by sending the
    //  SETTINGS_H3_DATAGRAM (0x33) setting with a value of 1."
    private static final long SETTINGS_H3_DATAGRAM = 0x33L;

    private final List<WebTransportHandlerRegistration> handlers = new ArrayList<>();
    private ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory("webtransport"));
    private int maxStreamsQueued;
    private int maxDatagramsQueued;

    @Override
    public Http3ServerExtension createExtension(Http3ServerConnection http3ServerConnection) {
        return new WebTransportExtension(http3ServerConnection, handlers, executor, maxStreamsQueued, maxDatagramsQueued);
    }

    @Override
    public Map<Long, Long> getExtensionSettings() {
        Map<Long, Long> wtSettings = Map.of(
                // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.html#section-2.2
                // "When an HTTP/3 connection is established, the server sends a SETTINGS_WT_ENABLED setting to indicate
                //  support for WebTransport over HTTP/3. "
                SETTINGS_WT_ENABLED, 1L,
                // Advertise all WebTransport setting variants for cross-implementation compatibility.
                SETTINGS_WT_MAX_SESSIONS_DRAFT_04_05, 1L,
                SETTINGS_WT_ENABLE_DRAFT_06, 1L,
                SETTINGS_WT_MAX_SESSIONS_DRAFT_07_12, 1L,
                SETTINGS_WT_MAX_SESSIONS_DRAFT_13_14, 1L
                );
        return wtSettings;
    }

    /**
     * Register a WebTransport server handler for a given path. The handler is called when a client connects to the server
     * using the given path. The handler is called with a Session object that represents the WebTransport connection on its own thread.
     * @param path
     * @param handler
     */
    public void registerWebTransportServer(String path, Consumer<Session> handler) {
        registerWebTransportServer(path, List.of(), handler);
    }

    /**
     * Register a WebTransport server handler for a given path. The handler is called when a client connects to the server
     * using the given path. The handler is called with a Session object that represents the WebTransport connection on its own thread.
     * The applicationProtocols parameter is a list of application protocols that the server supports and is used for
     * application protocol negotiation during the WebTransport handshake.
     * @param path
     * @param applicationProtocols
     * @param handler
     */
    public void registerWebTransportServer(String path, List<String> applicationProtocols, Consumer<Session> handler) {
        handlers.add(new WebTransportHandlerRegistration(path, handler, applicationProtocols));
    }

    /**
     * Register a WebTransport server handler for paths matching the given regex. When multiple regex registrations exist,
     * the first one registered wins. Static path registrations are always tried before regex registrations.
     * The handler is called when a client connects to the server using the given path. The handler is called with a
     * Session object that represents the WebTransport connection on its own thread.
     * @param pathRegex  a regular expression that is matched against the request path (without query string)
     * @param handler
     */
    public void registerWebTransportServerByRegex(String pathRegex, Consumer<Session> handler) {
        registerWebTransportServerByRegex(pathRegex, List.of(), handler);
    }

    /**
     * Register a WebTransport server handler for paths matching the given regex. When multiple regex registrations exist,
     * the first one registered wins. Static path registrations are always tried before regex registrations.
     * The handler is called when a client connects to the server using the given path. The handler is called with a
     * Session object that represents the WebTransport connection on its own thread.
     * The applicationProtocols parameter is a list of application protocols that the server supports and is used for
     * application protocol negotiation during the WebTransport handshake.
     * @param pathRegex            a regular expression that is matched against the request path (without query string)
     * @param applicationProtocols list of application protocols supported by this handler
     * @param handler
     */
    public void registerWebTransportServerByRegex(String pathRegex, List<String> applicationProtocols, Consumer<Session> handler) {
        handlers.add(new WebTransportHandlerRegistration(Pattern.compile(pathRegex), handler, applicationProtocols));
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * Sets the maximum number of streams that can be queued per session. When a new stream is received for a session
     * that is not yet created, it is queued until the session is created. If the number of queued streams exceeds this
     * limit, the stream is rejected with a WEBTRANSPORT_BUFFERED_STREAM_REJECTED error code.
     * @param maxStreamsQueued
     */
    public void setMaxStreamsQueued(int maxStreamsQueued) {
        this.maxStreamsQueued = maxStreamsQueued;
    }

    /**
     * Sets the maximum number of datagrams that can be buffered before a session is established. When a datagram is
     * received for a session that is not yet established, it is buffered until the session is established. If the number
     * of buffered datagrams exceeds this limit, the datagram is dropped.
     * @param maxDatagramsQueued
     */
    public void setMaxDatagramsQueued(int maxDatagramsQueued) {
        this.maxDatagramsQueued = maxDatagramsQueued;
    }
}
