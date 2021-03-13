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

import net.luminis.qpack.Decoder;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;


public class RequestHeadersFrame extends HeadersFrame {

    public RequestHeadersFrame() {
    }

    public void setMethod(String method) {
        pseudoHeaders.put(":method", method);
    }

    public void setUri(URI uri) {
        String path = uri.getPath();
        if (path.isBlank()) {
            path = "/";
        }
        pseudoHeaders.put(":path", path);
        pseudoHeaders.put(":authority", uri.getHost() + ":" + uri.getPort());
    }

    @Override
    public RequestHeadersFrame parsePayload(byte[] headerBlock, Decoder decoder) throws IOException {
        return (RequestHeadersFrame) super.parsePayload(headerBlock, decoder);
    }

    protected void addPseudoHeaders(List<Map.Entry<String, String>> qpackHeaders) {
        // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1.1.1
        // "All HTTP/3 requests MUST include exactly one value for the ":method", ":scheme", and ":path"
        // pseudo-header fields, unless it is a CONNECT request; "
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":method", pseudoHeaders.getOrDefault(":method", "GET")));
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":scheme", pseudoHeaders.getOrDefault(":scheme", "https")));
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":path", pseudoHeaders.getOrDefault(":path", "/")));
        // "Clients that generate HTTP/3 requests directly SHOULD use the ":authority" pseudo-header field instead of the Host field."
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":authority", pseudoHeaders.get(":authority")));
    }


    @Override
    protected void extractAndRemovePseudoHeader(Map<String, List<String>> headersMap) throws ProtocolException {

    }
}
