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
package tech.kwik.flupke.server.impl;

import tech.kwik.flupke.server.HttpServerRequest;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpHeaders;
import java.time.Instant;


public class HttpServerRequestImpl implements HttpServerRequest {

    private final String method;
    private final String path;
    private final HttpHeaders headers;
    private final InetSocketAddress clientAddress;
    private final Instant requestTime;
    private final InputStream bodyInputStream;

    public HttpServerRequestImpl(String method, String path, HttpHeaders headers, InetSocketAddress clientAddress, InputStream bodyInputStream) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.bodyInputStream = bodyInputStream;
        this.clientAddress = clientAddress;
        requestTime = Instant.now();
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public InputStream body() {
        return bodyInputStream;
    }

    @Override
    public InetAddress clientAddress() {
        return clientAddress.getAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return clientAddress;
    }

    @Override
    public Instant time() {
        return requestTime;
    }
}
