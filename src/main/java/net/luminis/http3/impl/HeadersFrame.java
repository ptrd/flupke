/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024 Peter Doornbosch
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
import net.luminis.quic.generic.VariableLengthInteger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;


// https://www.rfc-editor.org/rfc/rfc9114.html#section-7.2.2
public class HeadersFrame extends Http3Frame {

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-request-pseudo-header-field
    public static final String PSEUDO_HEADER_METHOD = ":method";
    public static final String PSEUDO_HEADER_SCHEME = ":scheme";
    public static final String PSEUDO_HEADER_AUTHORITY = ":authority";
    public static final String PSEUDO_HEADER_PATH = ":path";
    // https://www.rfc-editor.org/rfc/rfc9114.html#name-response-pseudo-header-fiel
    public static final String PSEUDO_HEADER_STATUS = ":status";
    // https://www.rfc-editor.org/rfc/rfc9220.html#name-websockets-upgrade-over-htt
    public static final String PSEUDO_HEADER_PROTOCOL = ":protocol";

    protected HttpHeaders httpHeaders;
    protected Map<String, String> pseudoHeaders;

    public HeadersFrame() {
        pseudoHeaders = new HashMap<>();
        httpHeaders = HttpHeaders.of(Collections.emptyMap(), (a,b) -> true);
    }

    public HeadersFrame(String pseudoHeader, String value) {
        pseudoHeaders = new HashMap<>();
        pseudoHeaders.put(pseudoHeader, value);
        httpHeaders = HttpHeaders.of(Collections.emptyMap(), (a,b) -> true);
    }

    public HeadersFrame(HttpHeaders headers, Map<String, String> pseudoHeaders) {
        if (pseudoHeaders.keySet().stream().anyMatch(key -> ! key.startsWith(":"))) {
            throw new IllegalArgumentException("Pseudo headers must start with ':'");
        }
        this.pseudoHeaders = Objects.requireNonNull(pseudoHeaders);
        if (headers != null) {
            this.httpHeaders = headers;
        }
        else {
            httpHeaders = HttpHeaders.of(Collections.emptyMap(), (a,b) -> true);
        }
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
        Map<String, List<String>> headersMap = headersList.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, this::mapValue, this::mergeValues));
        // https://www.rfc-editor.org/rfc/rfc9114#name-http-control-data
        // "Pseudo-header fields are not HTTP fields."
        extractPseudoHeaders(headersMap);
        httpHeaders = HttpHeaders.of(headersMap, (key, value) -> ! key.startsWith(":"));
        return this;
    }

    private void extractPseudoHeaders(Map<String, List<String>> headersMap) throws ProtocolException {
        headersMap.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(":"))
                .forEach(entry -> pseudoHeaders.put(entry.getKey(), entry.getValue().get(0)));
    }

    private void addPseudoHeaders(List<Map.Entry<String, String>> qpackHeaders) {
        pseudoHeaders.entrySet().forEach(entry -> qpackHeaders.add(entry));
    }

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

    private List<String> mergeValues(List<String> value1, List<String> value2) {
        List<String> result = new ArrayList<>();
        result.addAll(value1);
        result.addAll(value2);
        return result;
    }

    public String getPseudoHeader(String header) {
        return pseudoHeaders.get(header);
    }

    public HttpHeaders headers() {
        return httpHeaders;
    }

    /**
     * Returns the size of the uncompressed headers.
     * @return
     */
    public long getHeadersSize() {
        return pseudoHeaders.entrySet().stream()
                .mapToLong(entry -> entry.getKey().length() + entry.getValue().length())
                .sum() +
                httpHeaders.map().entrySet().stream()
                        .mapToLong(entry -> entry.getKey().length() + entry.getValue().stream().mapToLong(String::length).sum())
                        .sum();
    }
}
