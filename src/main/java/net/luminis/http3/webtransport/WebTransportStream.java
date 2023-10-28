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

import java.io.InputStream;
import java.io.OutputStream;

// https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
public interface WebTransportStream {

    /**
     * Returns an output stream for sending data on this WebTransport stream. A "FIN" is sent when the stream is closed.
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
     * "Any WebTransport protocol SHALL provide the following operations on an individual stream:
     *  - send bytes
     *  Add bytes into the stream send buffer. The sender can also indicate a FIN, signalling the fact that no new data
     *  will be send on the stream. Not applicable for incoming unidirectional streams."
     * @return
     */
    OutputStream getOutputStream();

    /**
     * Returns an input stream for receiving data on this WebTransport stream. When a "FIN" is received, the input stream
     * will be closed.
     * https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-06.html#name-streams
     * "Any WebTransport protocol SHALL provide the following operations on an individual stream:
     *  - receive bytes
     *  Removes bytes from the stream receive buffer. FIN can be received together with the stream data. Not applicable
     *  for outgoing unidirectional streams."
     *
     * @return
     */
    InputStream getInputStream();
}
