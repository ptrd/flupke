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

import tech.kwik.flupke.Http3Connection;
import tech.kwik.flupke.HttpStream;

import java.util.function.Consumer;

public interface Http3ServerConnection extends Http3Connection {

    /**
     * HTTP/3 extension method: allow registration of a handler for a bidirectional stream. To distinguish the
     * bidirectional stream from normal HTTP/3 request-response streams, the stream should start with a frame
     * with a new frame type.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-extensions-to-http-3
     * "Extensions are permitted to use new frame types (Section 7.2), ..."
     * @param frameType      the frame type that is used to distinguish the bidirectional stream from normal HTTP/3
     *                       request-response streams and is the type of the first frame that is sent on the stream.
     * @param streamHandler
     */
    void registerBidirectionalStreamHandler(long frameType, Consumer<HttpStream> streamHandler);
}
