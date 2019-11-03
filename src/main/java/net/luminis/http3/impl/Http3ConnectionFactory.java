/*
 * Copyright © 2019 Peter Doornbosch
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

import net.luminis.http3.Http3Client;

import java.io.IOException;
import java.net.http.HttpRequest;

public class Http3ConnectionFactory {

    private final Http3Client http3Client;

    public Http3ConnectionFactory(Http3Client http3Client) {
        this.http3Client = http3Client;
    }

    public Http3Connection getConnection(HttpRequest request) throws IOException {
        String host = request.uri().getHost();
        int port = request.uri().getPort();
        if (port <= 0) {
            port = 443;
        }

        Http3Connection http3Connection = new Http3Connection(host, port);
        if (http3Client.receiveBufferSize().isPresent()) {
            http3Connection.setReceiveBufferSize(http3Client.receiveBufferSize().get());
        }
        return http3Connection;
    }
}
