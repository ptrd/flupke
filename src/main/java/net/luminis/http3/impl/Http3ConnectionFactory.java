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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class Http3ConnectionFactory {

    private final Http3Client http3Client;
    private final Map<UdpAddress, Http3Connection> connections;

    public Http3ConnectionFactory(Http3Client http3Client) {
        this.http3Client = http3Client;
        connections = new ConcurrentHashMap<>();
    }

    public Http3Connection getConnection(HttpRequest request) throws IOException {
        int port = request.uri().getPort();
        if (port <= 0) {
            port = 443;
        }
        UdpAddress address = new UdpAddress(request.uri().getHost(), port);

        try {
            return connections.computeIfAbsent(address, this::createConnection);
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

    private Http3Connection createConnection(UdpAddress address) {
        Http3Connection http3Connection = null;
        try {
            http3Connection = new Http3Connection(address.host, address.port, http3Client.isDisableCertificateCheck());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (http3Client.receiveBufferSize().isPresent()) {
            http3Connection.setReceiveBufferSize(http3Client.receiveBufferSize().get());
        }
        return http3Connection;
    }

    static class UdpAddress {
        String host;
        int port;

        public UdpAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UdpAddress that = (UdpAddress) o;
            return port == that.port &&
                    Objects.equals(host, that.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port);
        }
    }

}
