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
package net.luminis.http3.core;

import java.io.IOException;
import java.util.function.Consumer;

public interface Http3Connection {

    /**
     * HTTP/3 extension method: allow registration of a new unidirectional stream type.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-extensions-to-http-3
     * "Extensions are permitted to use (...) new unidirectional stream types (Section 6.2)."
     * @param streamType
     * @param handler
     */
    void registerUnidirectionalStreamType(long streamType, Consumer<HttpStream> handler);

    /**
     * HTTP/3 extension method: create a new unidirectional stream for the given stream type.
     * The data that is sent on this stream is not framed in HTTP/3 frames. The stream type is sent in conformance with
     * the HTTP/3 specification.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-extensions-to-http-3
     * "Extensions are permitted to use (...) new unidirectional stream types (Section 6.2)."
     * @param streamType  the stream type to use for the new unidirectional stream, must not be one of the standard types or a reserved type.
     * @return  HTTP stream that does not use HTTP/3 framing, has the stream type already sent and for which only the
     * outputstream is valid.
     * @throws IOException
     */
    HttpStream createUnidirectionalStream(long streamType) throws IOException;

    /**
     * HTTP/3 extension method: create a new bidirectional stream.
     * The returned stream does _not_ perform HTTP/3 framing; it's the callers responsibility to send the data in HTTP/3
     * frames or in a format that is compatible with HTTP/3 framing.
     */
    HttpStream createBidirectionalStream() throws IOException;

    /**
     * HTTP/3 extension method for adding additional settings.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-extensions-to-http-3
     * "Extensions are permitted to use new frame types (Section 7.2), new settings (Section 7.2.4.1), ..."
     * Note that this method must be called before the connection is established.
     */
    void addSettingsParameter(long identifier, long value);
}
