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

import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.HttpError;
import tech.kwik.flupke.webtransport.impl.ClientSessionFactoryImpl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.function.Consumer;

/**
 * A factory for WebTransport sessions.
 * A WebTransport session requires stream handlers to handle incoming streams. These can
 * be set on the session, but this might lead to race conditions if clients immediately start
 * a stream after session creation. Therefore it is recommended to set the stream handlers
 * when creating the session using this factory.
 */
public interface ClientSessionFactory {

    /**
     * Creates a WebTransport session given a server URI.
     *
     * @param serverUri the WebTransport server URI (host, port and path are all relevant)
     * @return the WebTransport session
     * @throws IOException
     * @throws HttpError
     */
    Session createSession(URI serverUri) throws IOException, HttpError;

    /**
     * Creates a WebTransport session given a HTTP request.
     *
     * @param request the HTTP request initiating the WebTransport session; the method should be CONNECT,
     *                but as some JDKs don't support CONNECT requests, the method is ignored.
     * @return the WebTransport session
     * @throws IOException
     * @throws HttpError
     */
    Session createSession(HttpRequest request) throws IOException, HttpError;

    /**
     * Creates a WebTransport session given a server URI and stream handlers.
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
     * Creates a WebTransport session given a HTTP request and stream handlers.
     * @param request the HTTP request initiating the WebTransport session; the method should be CONNECT,
     *                but as some JDKs don't support CONNECT requests, the method is ignored.
     * @param unidirectionalStreamHandler handler for incoming unidirectional streams
     * @param bidirectionalStreamHandler  handler for incoming bidirectional streams
     * @return the WebTransport session
     * @throws IOException
     * @throws HttpError
     */
    Session createSession(HttpRequest request, Consumer<WebTransportStream> unidirectionalStreamHandler,
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

    static Builder newBuilder() {
        return ClientSessionFactoryImpl.newBuilder();
    }

    interface Builder {
        ClientSessionFactory build() throws IOException;

        Builder serverUri(URI serverUri);

        Builder httpClient(Http3Client httpClient);
    }
}