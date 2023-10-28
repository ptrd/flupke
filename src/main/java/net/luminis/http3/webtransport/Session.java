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
package net.luminis.http3.webtransport;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A WebTransport session.
 * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-conventions-and-definitions
 * "A WebTransport session is a single communication context established between a client and a server. It may correspond
 *  to a specific transport-layer connection, or it may be a logical entity within an existing multiplexed transport-layer
 *  connection. WebTransport sessions are logically independent from one another even if some sessions can share an
 *  underlying transport-layer connection."
 */
public interface Session {

    /**
     * Create a unidirectional stream.
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
     * "Any WebTransport protocol SHALL provide the following operations on the session:
     *  - create a unidirectional stream
     *   Creates an outgoing unidirectional stream; this operation may block until the flow control of the underlying
     *   protocol allows for it to be completed."
     * @return
     * @throws IOException
     */
    WebTransportStream createUnidirectionalStream() throws IOException;

    /**
     * Create a bidirectional stream.
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
     * "Any WebTransport protocol SHALL provide the following operations on the session:
     *  - create a bidirectional stream
     *   Creates an outgoing bidirectional stream; this operation may block until the flow control of the underlying
     *   protocol allows for it to be completed."
     */
    WebTransportStream createBidirectionalStream() throws IOException;

    /**
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
     * "Any WebTransport protocol SHALL provide the following operations on the session:
     *  -  receive a unidirectional stream
     *   Removes a stream from the queue of incoming unidirectional streams, if one is available."
     */
    void setUnidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler);

    /**
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
     * "Any WebTransport protocol SHALL provide the following operations on the session:
     *  -  receive a bidirectional stream
     *  Removes a stream from the queue of incoming unidirectional streams, if one is available."
     */
    void setBidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler);

    /**
     * Close the session.
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-session-wide-features
     * "Any WebTransport protocol SHALL provide the following operations on the session:
     *  terminate a session"
     */
    void close(long applicationErrorCode, String applicationErrorMessage);

    /**
     * Close the session with error code 0 and empty string as error message.
     */
    void close();

    /**
     * Register a listener for the session terminated event.
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-session-wide-features
     * "Any WebTransport protocol SHALL provide the following events:
     *  session terminated event"
     *
     * @param listener   event listener for the session terminated event
     */
    void registerSessionTerminatedEventListener(BiConsumer<Long, String> listener);
}
