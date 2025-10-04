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
package tech.kwik.flupke.impl;

/**
 * https://www.rfc-editor.org/rfc/rfc9114.html#name-error-handling
 * "If an entire connection needs to be terminated, QUIC similarly provides mechanisms to communicate a reason; see
 *  Section 5.3 of [QUIC-TRANSPORT]. This is referred to as a "connection error". Similar to stream errors, an HTTP/3
 *  implementation can terminate a QUIC connection and communicate the reason using an error code from Section 8.1."
 */
public class ConnectionError extends Exception {

    private final long http3ErrorCode;

    public ConnectionError(long http3ErrorCode) {
        super("HTTP/3 connection error: " + http3ErrorCode);
        this.http3ErrorCode = http3ErrorCode;
    }

    public long getHttp3ErrorCode() {
        return http3ErrorCode;
    }
}
