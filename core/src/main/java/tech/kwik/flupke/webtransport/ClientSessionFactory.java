/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
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

import tech.kwik.flupke.core.HttpError;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

/**
 * A factory for WebTransport sessions.
 */
public interface ClientSessionFactory {

    /**
     * Creates a WebTransport session given a HTTP3 client and a server URI.
     *
     * @param serverUri the WebTransport server URI (host, port and path are all relevant)
     * @return the WebTransport session
     * @throws IOException
     * @throws HttpError
     */
    Session createSession(URI serverUri) throws IOException, HttpError;

    /**
     * Creates a WebTransport session given a HTTP3 client and a server URI.
     *
     * @param serverUri                   the WebTransport server URI (host, port and path are all relevant)
     * @param unidirectionalStreamHandler handler for incoming unidirectional streams
     * @param bidirectionalStreamHandler  handler for incoming bidirectional streams
     * @return the WebTransport session
     * @throws IOException
     * @throws HttpError
     */
    Session createSession(URI serverUri, Consumer<WebTransportStream> unidirectionalStreamHandler,
                          Consumer<WebTransportStream> bidirectionalStreamHandler) throws IOException, HttpError;

    /**
     * Returns the URI of the server this factory connects with.
     * @return  server URI
     */
    URI getServerUri();

    /**
     * Returns the maximum number of concurrent sessions that the server allows.
     * @return
     */
    int getMaxConcurrentSessions();
}