/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
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
import net.luminis.http3.core.Http3ClientConnection;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static net.luminis.http3.core.Http3ClientConnection.DEFAULT_HTTP3_PORT;


public class Http3ConnectionFactory {

    private final Http3Client http3Client;
    private final Map<UdpAddress, Http3ClientConnection> connections;

    public Http3ConnectionFactory(Http3Client http3Client) {
        this.http3Client = http3Client;
        connections = new ConcurrentHashMap<>();
    }

    public Http3ClientConnection getConnection(HttpRequest request) throws IOException {
        int port = request.uri().getPort();
        if (port <= 0) {
            port = DEFAULT_HTTP3_PORT;
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

    public Http3ClientConnection getConnection(HttpRequest request, boolean createNew, boolean replaceExisting) throws IOException {
        if (replaceExisting && !createNew) {
            throw new IllegalArgumentException("replaceExisting can only be true if createNew is true");
        }

        int port = request.uri().getPort();
        if (port <= 0) {
            port = DEFAULT_HTTP3_PORT;
        }
        UdpAddress address = new UdpAddress(request.uri().getHost(), port);

        try {
            Http3ClientConnection connection;
            if (createNew) {
                connection = createConnection(address);
                if (replaceExisting) {
                    connections.put(address, connection);
                }
            }
            else {
                connection = connections.computeIfAbsent(address, this::createConnection);
            }
            return connection;
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
    
    private Http3ClientConnection createConnection(UdpAddress address) {
        Http3ClientConnection http3Connection;
        try {
            Duration connectTimeout = http3Client.connectTimeout().orElse(Http3ClientConnectionImpl.DEFAULT_CONNECT_TIMEOUT);
            http3Connection = new Http3ClientConnectionImpl(address.host, address.port, connectTimeout, http3Client.isDisableCertificateCheck(), http3Client.getLogger());
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
