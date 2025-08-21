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
package tech.kwik.flupke.server;


import tech.kwik.flupke.core.HttpStream;

import java.net.http.HttpHeaders;
import java.util.function.IntConsumer;

public interface Http3ServerExtension {

    /**
     * Handle an extended CONNECT request, by returning a status code and consuming the request/response stream.
     * The status callback must be called before consuming the request/response stream.
     * The request/response stream is only available if the status code is in the 2xx range.
     * This method should return immediately.
     * @param headers
     * @param protocol
     * @param authority
     * @param pathAndQuery
     * @param statusCallback
     * @param requestResponseStream
     * @return
     */
    void handleExtendedConnect(HttpHeaders headers, String protocol, String authority, String pathAndQuery, IntConsumer statusCallback, HttpStream requestResponseStream);
}
