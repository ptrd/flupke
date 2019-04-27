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
import org.assertj.core.util.Lists;
import org.junit.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class HeadersFrameTest {

    @Test
    public void frameReturnsNormalHeadersReturnedByDecoder() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/plain")
        ));
        HeadersFrame headersFrame = new HeadersFrame().parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers()).containsEntry("content-type", List.of("text/plain"));
    }

    @Test
    public void frameDoesNotReturnPseudoHeaderReturnedByDecoder() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/plain")
        ));
        HeadersFrame headersFrame = new HeadersFrame().parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers()).doesNotContainKey(":status");
    }

    @Test
    public void decodedResponseWithoutStatusPseudoHeaderThrows() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(Lists.emptyList());

        assertThatThrownBy(
                () -> new HeadersFrame().parsePayload(new byte[0], qpackDecoder))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void headersFrameBytesShouldStartWithHeadersFrameType() {
        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.wrap(new byte[0]));

        byte[] bytes = new HeadersFrame().toBytes(qpackEncoder);

        https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.2: The HEADERS frame (type=0x1)...
        assertThat(bytes).startsWith(0x01);  // Headers frame
    }

    @Test
    public void headersFrameSizeIsEncodedAsVarInt() {
        Encoder qpackEncoder = mock(Encoder.class);
        ByteBuffer payload = ByteBuffer.allocate(67);
        payload.put(new byte[67]);  // Put ByteByffer in "write" mode
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(payload);

        byte[] bytes = new HeadersFrame().toBytes(qpackEncoder);

        assertThat(bytes).startsWith(0x01, 0x40, 67);
    }

    @Test
    public void compressHeadersAreCopiedIntoHeadersFrame() {
        Encoder qpackEncoder = mock(Encoder.class);
        byte[] payload = new byte[] { 0x33, 0x10, 0x5f, 0x6e, 0x00, 0x3c, 0x77, 0x7f };
        ByteBuffer payloadBuffer = ByteBuffer.allocate(payload.length);
        payloadBuffer.put(payload);

        when(qpackEncoder.compressHeaders(anyList())).thenReturn(payloadBuffer);

        byte[] bytes = new HeadersFrame().toBytes(qpackEncoder);

        assertThat(bytes).startsWith(0x01, 0x08, 0x33, 0x10, 0x5f, 0x6e, 0x00, 0x3c, 0x77, 0x7f);
    }

}
