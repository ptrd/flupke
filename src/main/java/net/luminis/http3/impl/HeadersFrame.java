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
import net.luminis.qpack.Encoder;
import net.luminis.quic.VariableLengthInteger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;


// https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.2
public class HeadersFrame extends Http3Frame {

    private int statusCode;
    private Map<String, List<String>> httpHeaders;
    private List<Map.Entry<String, String>> qpackHeaders;


    public HeadersFrame() {
        httpHeaders = new HashMap<>();
        qpackHeaders = new ArrayList<>();
    }

    public byte[] toBytes(Encoder encoder) {
        ByteBuffer compressedHeaders = encoder.compressHeaders(qpackHeaders);
        compressedHeaders.flip();

        ByteBuffer payloadLength = ByteBuffer.allocate(4);
        VariableLengthInteger.encode(compressedHeaders.limit(), payloadLength);
        payloadLength.flip();

        byte[] data = new byte[1 + payloadLength.limit() + compressedHeaders.limit()];
        data[0] = 0x01;  // Header frame
        payloadLength.get(data, 1, payloadLength.limit());
        compressedHeaders.get(data, 1 + payloadLength.limit(), compressedHeaders.limit());

        return data;
    }

    public HeadersFrame parsePayload(byte[] headerBlock, Decoder decoder) throws IOException {
        List<Map.Entry<String, String>> responseHeaders = decoder.decodeStream(new ByteArrayInputStream(headerBlock));
        responseHeaders.stream()
                .filter(entry -> !entry.getKey().equals(":status"))
                .forEach(entry -> httpHeaders.put(entry.getKey(), List.of(entry.getValue())));

        statusCode = Integer.parseInt(
                responseHeaders.stream()
                .filter(entry -> entry.getKey().equals(":status"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Missing pseudo header :status"))
                .getValue());

        return this;
    }

    public void setMethod(String method) {
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":method", method));
    }

    public void setUri(URI uri) {
        String path = uri.getPath();
        if (path.isBlank()) {
            path = "/";
        }
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":path", path));
        // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
        // "All HTTP/2 requests MUST include exactly one valid value for the
        //   ":method", ":scheme", and ":path" pseudo-header fields"
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":scheme", "https"));

        // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
        // "Clients that generate HTTP/2 requests directly SHOULD use the ":authority"
        //  pseudo-header field instead of the Host header field."
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":authority", uri.getHost() + ":" + uri.getPort()));
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return httpHeaders;
    }
}
