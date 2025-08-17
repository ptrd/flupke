/*
 * Copyright © 2025 Peter Doornbosch
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
package tech.kwik.flupke.webtransport.impl;

import java.net.URI;
import java.net.http.HttpHeaders;

import static java.util.Collections.emptyMap;

public class WebTransportContext {

    private final String authority;
    private final String pathAndQuery;
    private final HttpHeaders headers;

    public WebTransportContext(HttpHeaders headers, String authority, String pathAndQuery) {
        this.headers = headers;
        this.authority = authority;
        this.pathAndQuery = pathAndQuery;
    }

    public WebTransportContext(URI webTransportUri) {
        this.headers = HttpHeaders.of(emptyMap(), (k, v) -> true);
        this.authority = webTransportUri.getAuthority();
        this.pathAndQuery = webTransportUri.getPath() + (webTransportUri.getQuery() != null ? "?" + webTransportUri.getQuery() : "");
    }

    public String getAuthority() {
        return authority;
    }

    public String getPathAndQuery() {
        return pathAndQuery;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
}
