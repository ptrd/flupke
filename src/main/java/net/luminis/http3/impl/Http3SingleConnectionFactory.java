/*
 * Copyright © 2021 Peter Doornbosch
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
package net.luminis.http3.impl;

import net.luminis.quic.QuicConnection;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Connection factory that produces a HTTP3 connection that uses a single (pre-established) QUIC connection.
 */
public class Http3SingleConnectionFactory extends Http3ConnectionFactory {

    private final QuicConnection quicConnection;
    private Http3Connection http3Connection;

    public Http3SingleConnectionFactory(QuicConnection quicConnection) {
        super(null);
        this.quicConnection = quicConnection;
    }

    public Http3Connection getConnection(HttpRequest request) throws IOException {
        try {
            synchronized (this) {
                if (http3Connection == null) {
                    http3Connection = createConnection();
                }
            }
            return http3Connection;
        }
        catch (RuntimeException error) {
            if (error.getCause() instanceof IOException) {
                throw (IOException) error.getCause();
            }
            else {
                throw error;
            }
        }
    }

    private Http3Connection createConnection() {
        return new Http3Connection(quicConnection);
    }

}
