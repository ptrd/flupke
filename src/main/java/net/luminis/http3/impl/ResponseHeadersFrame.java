/*
 * Copyright © 2021 Peter Doornbosch
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
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class ResponseHeadersFrame extends HeadersFrame {

    private Optional<Integer> statusCode;

    public ResponseHeadersFrame() {
    }

    @Override
    protected void extractPseudoHeaders(Map<String, List<String>> headersMap) throws ProtocolException {
        if (headersMap.containsKey(":status")) {
            try {
                statusCode = Optional.of(Integer.parseInt(headersMap.get(":status").get(0)));
            } catch (NumberFormatException noNumber) {
                throw new ProtocolException("Invalid status code " + headersMap.get(":status"));
            }
        }
        else {
            statusCode = Optional.empty();
        }
    }

    @Override
    public ResponseHeadersFrame parsePayload(byte[] headerBlock, Decoder decoder) throws IOException {
        return (ResponseHeadersFrame) super.parsePayload(headerBlock, decoder);
    }

    protected void addPseudoHeaders(List<Map.Entry<String, String>> qpackHeaders) {
        // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1.1.1
        // "For responses, a single ":status" pseudo-header field is defined that carries the HTTP status code (...)
        //  This pseudo-header field MUST be included in all responses;"
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":status", String.valueOf(statusCode.get())));
    }

    public int statusCode() {
        return statusCode.get();
    }

    public void setStatus(int statusCode) {
        this.statusCode = Optional.of(statusCode);
    }
}
