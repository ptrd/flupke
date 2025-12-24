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
package tech.kwik.flupke.test;

import tech.kwik.qpack.Decoder;
import tech.kwik.qpack.Encoder;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A no-op implementation of the Encoder and Decoder interfaces for testing purposes.
 * This implementation simply stores the headers passed to the encoder and returns them
 * unchanged when decoding.
 */
public class NoOpEncoderDecoderBuilder {

    private List<Map.Entry<String, String>> mockEncoderCompressedHeaders = new ArrayList<>();

    public Encoder encoder() {
        return new Encoder() {
            @Override
            public ByteBuffer compressHeaders(List<Map.Entry<String, String>> headers) {
                mockEncoderCompressedHeaders = headers;
                int uncompressedSize = headers.stream().mapToInt(e -> e.getKey().length() + e.getValue().length() + 2).sum();
                ByteBuffer buffer = ByteBuffer.allocate(uncompressedSize);
                // Simulate writing uncompressed headers
                buffer.limit(uncompressedSize);
                buffer.position(uncompressedSize);
                return buffer;
            }
        };
    }

    public Decoder decoder() {
        return new Decoder() {
            @Override
            public List<Map.Entry<String, String>> decodeStream(InputStream inputStream) {
                return mockEncoderCompressedHeaders;
            }
        };
    }
}
