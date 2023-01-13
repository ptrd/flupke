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

import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.VariableLengthInteger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;


// https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.2
public abstract class HeadersFrame extends Http3Frame {

    protected HttpHeaders httpHeaders;
    protected Map<String, String> pseudoHeaders;

    public HeadersFrame() {
        pseudoHeaders = new HashMap<>();
        httpHeaders = HttpHeaders.of(Collections.emptyMap(), (a,b) -> true);
    }

    public byte[] toBytes(Encoder encoder) {
        List<Map.Entry<String, String>> qpackHeaders = new ArrayList<>();
        addPseudoHeaders(qpackHeaders);
        addHeaders(qpackHeaders);

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
        List<Map.Entry<String, String>> headersList = decoder.decodeStream(new ByteArrayInputStream(headerBlock));
        Map<String, List<String>> headersMap = headersList.stream().collect(Collectors.toMap(Map.Entry::getKey, this::mapValue));
        // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1.1.1
        // "Pseudo-header fields are not HTTP fields."
        extractPseudoHeaders(headersMap);
        httpHeaders = HttpHeaders.of(headersMap, (key, value) -> ! key.startsWith(":"));
        return this;
    }

    protected abstract void extractPseudoHeaders(Map<String, List<String>> headersMap) throws ProtocolException;

    public void setHeaders(HttpHeaders headers) {
        this.httpHeaders = headers;
    }

    public HttpHeaders headers() {
        return httpHeaders;
    }

    protected abstract void addPseudoHeaders(List<Map.Entry<String, String>> qpackHeaders);

    private void addHeaders(List<Map.Entry<String, String>> qpackHeaders) {
        httpHeaders.map().entrySet().forEach(entry -> {
            String value = entry.getValue().stream().collect(Collectors.joining(","));
            // https://tools.ietf.org/html/draft-ietf-quic-http-28#4.1.1
            // "As in HTTP/2, characters in field names MUST be converted to lowercase prior to their encoding."
            qpackHeaders.add(new AbstractMap.SimpleEntry<>(entry.getKey().toLowerCase(), value));
        });
    }

    private List<String> mapValue(Map.Entry<String, String> entry) {
        return List.of(entry.getValue());
    }
}
