/*
 * Copyright © 2023 Peter Doornbosch
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

import java.net.ProtocolException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

public class ConnectRequestHeadersFrame extends RequestHeadersFrame {

    @Override
    protected void addPseudoHeaders(List<Map.Entry<String, String>> qpackHeaders) {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method
        // "A CONNECT request MUST be constructed as follows:
        //  - The :method pseudo-header field is set to "CONNECT"
        //  - The :scheme and :path pseudo-header fields are omitted
        //  - The :authority pseudo-header field contains the host and port to connect to (equivalent to the authority-form of the request-target of CONNECT requests; see Section 7.1 of [HTTP])."
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":method", pseudoHeaders.getOrDefault(":method", "CONNECT")));
        qpackHeaders.add(new AbstractMap.SimpleEntry<>(":authority", pseudoHeaders.get(":authority")));
        // https://www.rfc-editor.org/rfc/rfc8441#section-4
        // "A new pseudo-header field :protocol MAY be included ..."
        if (pseudoHeaders.containsKey(":protocol")) {
            qpackHeaders.add(new AbstractMap.SimpleEntry<>(":protocol", pseudoHeaders.get(":protocol")));
            // https://www.rfc-editor.org/rfc/rfc8441#section-4
            // "On requests that contain the :protocol pseudo-header field, the :scheme and :path pseudo-header fields
            //  of the target URI (see Section 5) MUST also be included."
            qpackHeaders.add(new AbstractMap.SimpleEntry<>(":scheme", pseudoHeaders.get(":scheme")));
            qpackHeaders.add(new AbstractMap.SimpleEntry<>(":path", pseudoHeaders.get(":path")));
        }
    }

    @Override
    protected void extractPseudoHeaders(Map<String, List<String>> headersMap) throws ProtocolException {
        List.of(":method", ":scheme", ":path", ":authority", "protocol").forEach(key -> {
            if (headersMap.containsKey(key)) {
                pseudoHeaders.put(key, headersMap.get(key).get(0));
            }
        });
    }
}
