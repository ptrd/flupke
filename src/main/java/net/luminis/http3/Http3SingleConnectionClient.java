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
package net.luminis.http3;

import net.luminis.http3.impl.Http3SingleConnectionFactory;
import net.luminis.quic.QuicConnection;

import java.time.Duration;

/**
 * A Http Client that uses a single QUIC connection for all its HTTP3 requests.
 */
public class Http3SingleConnectionClient extends Http3Client {

    public Http3SingleConnectionClient(QuicConnection quicConnection, Duration connectTimeout, Long receiveBufferSize) {
        super(connectTimeout, receiveBufferSize, false);

        http3ConnectionFactory = new Http3SingleConnectionFactory(quicConnection);
    }
}
