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
package tech.kwik.flupke.server;

import java.net.InetAddress;
import java.net.http.HttpHeaders;
import java.time.Instant;


public class HttpServerRequest {

    private final String method;
    private final String path;
    private final HttpHeaders headers;
    private final InetAddress clientAddress;
    private final Instant requestTime;

    public HttpServerRequest(String method, String path, HttpHeaders headers, InetAddress clientAddress) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.clientAddress = clientAddress;
        requestTime = Instant.now();
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public InetAddress clientAddress() {
        return clientAddress;
    }

    public Instant time() {
        return requestTime;
    }
}
