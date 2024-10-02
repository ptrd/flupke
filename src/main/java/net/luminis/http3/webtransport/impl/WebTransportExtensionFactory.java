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
package net.luminis.http3.webtransport.impl;

import net.luminis.http3.server.Http3ServerConnection;
import net.luminis.http3.server.Http3ServerExtension;
import net.luminis.http3.server.Http3ServerExtensionFactory;
import net.luminis.http3.webtransport.Session;
import net.luminis.quic.concurrent.DaemonThreadFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class WebTransportExtensionFactory implements Http3ServerExtensionFactory {

    private final Map<String, Consumer<Session>> webTransportHandlers = new HashMap<>();
    private ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory("webtransport"));

    @Override
    public Http3ServerExtension createExtension(Http3ServerConnection http3ServerConnection) {
        return new WebTransportExtension(http3ServerConnection, webTransportHandlers, executor);
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
