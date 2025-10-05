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
package tech.kwik.flupke.webtransport;

import tech.kwik.flupke.server.Http3ApplicationProtocolFactory;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.webtransport.impl.WebTransportExtensionFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class WebTransportHttp3ApplicationProtocolFactory extends Http3ApplicationProtocolFactory {

    public static final int MAX_CONCURRENT_PEER_INITIATED_UNIDIRECTIONAL_STREAMS = 100;
    public static final int MAX_CONCURRENT_PEER_INITIATED_BIDIRECTIONAL_STREAMS = 100;

    private final int maxConcurrentPeerInitiatedUnidirectionalStreams;
    private final int maxConcurrentPeerInitiatedBidirectionalStreams;
    private final WebTransportExtensionFactory webTransportExtensionFactory;

    public WebTransportHttp3ApplicationProtocolFactory(HttpRequestHandler requestHandler) {
        this(requestHandler, MAX_CONCURRENT_PEER_INITIATED_UNIDIRECTIONAL_STREAMS, MAX_CONCURRENT_PEER_INITIATED_BIDIRECTIONAL_STREAMS);
    }

    public WebTransportHttp3ApplicationProtocolFactory(HttpRequestHandler requestHandler, int maxConcurrentPeerInitiatedUnidirectionalStreams) {
        this(requestHandler, maxConcurrentPeerInitiatedUnidirectionalStreams, MAX_CONCURRENT_PEER_INITIATED_BIDIRECTIONAL_STREAMS);
    }

    public WebTransportHttp3ApplicationProtocolFactory(HttpRequestHandler requestHandler, int maxConcurrentPeerInitiatedUnidirectionalStreams, int maxConcurrentPeerInitiatedBidirectionalStreams) {
        super(requestHandler);
        this.maxConcurrentPeerInitiatedUnidirectionalStreams = maxConcurrentPeerInitiatedUnidirectionalStreams;
        this.maxConcurrentPeerInitiatedBidirectionalStreams = maxConcurrentPeerInitiatedBidirectionalStreams;
        webTransportExtensionFactory = new WebTransportExtensionFactory();
        setExtensions(Map.of("webtransport", webTransportExtensionFactory));
    }

    public void registerWebTransportServer(String path, Consumer<Session> callback) {
        webTransportExtensionFactory.registerWebTransportServer(path, callback);
    }

    public void setExecutor(ExecutorService executor) {
        webTransportExtensionFactory.setExecutor(executor);
    }

    @Override
    public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
        return maxConcurrentPeerInitiatedUnidirectionalStreams;
    }

    @Override
    public int maxConcurrentPeerInitiatedBidirectionalStreams() {
        return maxConcurrentPeerInitiatedBidirectionalStreams;
    }

    @Override
    public long maxTotalPeerInitiatedUnidirectionalStreams() {
        return Integer.MAX_VALUE;
    }
}
