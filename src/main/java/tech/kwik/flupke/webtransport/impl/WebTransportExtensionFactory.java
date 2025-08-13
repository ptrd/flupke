/*
 * Copyright © 2024 Peter Doornbosch
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class WebTransportExtensionFactory implements Http3ServerExtensionFactory {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html#section-9.2
    // "Setting Name: WT_MAX_SESSIONS
    //  Value: 0x14e9cd29
    public static final long WT_MAX_SESSIONS = 0x14e9cd29L;

    private final Map<String, Consumer<Session>> webTransportHandlers = new HashMap<>();
    private ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory("webtransport"));

    @Override
    public Http3ServerExtension createExtension(Http3ServerConnection http3ServerConnection) {
        return new WebTransportExtension(http3ServerConnection, webTransportHandlers, executor);
    }

    @Override
    public Map<Long, Long> getExtensionSettings() {
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html#section-3.1
        // "A server supporting WebTransport over HTTP/3 MUST send both the SETTINGS_WT_MAX_SESSIONS setting with
        //  a value greater than "0" ..."
        return Map.of(WT_MAX_SESSIONS, 1L);
    }

    /**
     * Register a WebTransport server handler for a given path. The handler is called when a client connects to the server
     * using the given path. The handler is called with a Session object that represents the WebTransport connection on its own thread.
     * @param path
     * @param callback
     */
    public void registerWebTransportServer(String path, Consumer<Session> callback) {
        webTransportHandlers.put(path, callback);
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor);
    }
}
