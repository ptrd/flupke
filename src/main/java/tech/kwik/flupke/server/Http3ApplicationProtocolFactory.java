/*
 * Copyright © 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.server;


import tech.kwik.flupke.sample.FileServer;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.concurrent.DaemonThreadFactory;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Http3ApplicationProtocolFactory implements ApplicationProtocolConnectionFactory {

    private final HttpRequestHandler httpRequestHandler;
    private final ExecutorService executorService;

    public Http3ApplicationProtocolFactory(HttpRequestHandler requestHandler) {
        this.httpRequestHandler = Objects.requireNonNull(requestHandler);
        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory("http3-connection"));
    }

    /**
     * @deprecated
     * @param wwwDir
     */
    @Deprecated
    public Http3ApplicationProtocolFactory(File wwwDir) {
        if (wwwDir == null) {
            throw new IllegalArgumentException();
        }
        httpRequestHandler = new FileServer(wwwDir);
        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory("http3-connection"));
    }

    @Override
    public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
        return new Http3ServerConnection(quicConnection, httpRequestHandler, executorService);
    }

    @Override
    public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
        // "Therefore, the transport parameters sent by both clients and servers MUST allow the peer to create at least
        //  three unidirectional streams."
        return 3;
    }

    @Override
    public long maxTotalPeerInitiatedUnidirectionalStreams() {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
        // "Therefore, the transport parameters sent by both clients and servers MUST allow the peer to create at least
        //  three unidirectional streams."
        return 3;
    }

    @Override
    public int maxConcurrentPeerInitiatedBidirectionalStreams() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int minUnidirectionalStreamReceiverBufferSize() {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
        // "These transport parameters SHOULD also provide at least 1,024 bytes of flow-control credit to each
        //  unidirectional stream."
        return 1024;
    }

    @Override
    public long maxUnidirectionalStreamReceiverBufferSize() {
        return 1024;
    }

}
