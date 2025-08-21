/*
 * Copyright © 2024 Peter Doornbosch
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
package tech.kwik.flupke.test;


import tech.kwik.qpack.impl.EncoderImpl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An encoder that captures the headers that are passed to it, so a test can easily verify which headers are send to the encoder.
 */
public class CapturingEncoder extends EncoderImpl {

    private Map<String, String> capturedHeaders = new HashMap<>();

    public ByteBuffer compressHeaders(List<Map.Entry<String, String>> headers) {
        headers.forEach(header -> capturedHeaders.put(header.getKey(), header.getValue()));
        return ByteBuffer.allocate(0);
    }

    public Map<String, String> getCapturedHeaders() {
        return capturedHeaders;
    }
}
