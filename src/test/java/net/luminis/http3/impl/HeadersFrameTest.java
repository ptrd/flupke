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
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;


public class HeadersFrameTest {

    @Test
    public void frameReturnsNormalHeadersReturnedByDecoder() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/plain")
        ));
        HeadersFrame headersFrame = new HeadersFrame().parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers().map()).containsEntry("content-type", List.of("text/plain"));
    }

    @Test
    public void frameDoesNotReturnPseudoHeaderReturnedByDecoder() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/plain")
        ));
        HeadersFrame headersFrame = new HeadersFrame().parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers().map()).doesNotContainKey(":status");
    }

    @Test
    public void headersFrameBytesShouldStartWithHeadersFrameType() {
        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.wrap(new byte[0]));

        byte[] bytes = new HeadersFrame().toBytes(qpackEncoder);

        assertThat(bytes).startsWith(0x01);  // Headers frame type
    }

    @Test
    public void headersFrameSizeIsEncodedAsVarInt() {
        Encoder qpackEncoder = mock(Encoder.class);
        ByteBuffer payload = ByteBuffer.allocate(67);  // 67 bytes does not fit in 1 byte variable length integer
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

    @Test
    public void httpHeadersAreMappedToQpack() {
        HttpHeaders httpHeaders = HttpHeaders.of(
                Map.of("headername1", List.of("value1"),
                        "headername2", List.of("value2")), (a, b) -> true);
        HeadersFrame headersFrame = new HeadersFrame(httpHeaders, Collections.emptyMap());

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        assertThat(listCaptor.getValue())
                .contains(new AbstractMap.SimpleEntry("headername1", "value1"))
                .contains(new AbstractMap.SimpleEntry("headername2", "value2"));
    }

    @Test
    public void httpHeaderNamesAreConvertedToLowercaseBeforeEncoding() {
        HttpHeaders httpHeaders = HttpHeaders.of(
                Map.of("HeaderName", List.of("CamelCasedValue")), (a, b) -> true);
        HeadersFrame headersFrame = new HeadersFrame(httpHeaders, Collections.emptyMap());

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        assertThat(listCaptor.getValue())
                .contains(new AbstractMap.SimpleEntry("headername", "CamelCasedValue"));
    }

    @Test
    public void multiValuedHttpHeadersAreCompressedAsSingleValue() {
        HttpHeaders httpHeaders = HttpHeaders.of(
                Map.of("headername1", List.of("value1", "value2", "value3"),
                        "headername2", List.of("value20")), (a, b) -> true);
        HeadersFrame headersFrame = new HeadersFrame(httpHeaders, Collections.emptyMap());

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        assertThat(listCaptor.getValue())
                .contains(new AbstractMap.SimpleEntry("headername1", "value1,value2,value3"))
                .contains(new AbstractMap.SimpleEntry("headername2", "value20"));
    }

    @Test
    public void pseudoHeadersMustBeEncodedFirst() throws Exception {
        // When
        HeadersFrame headersFrame = new HeadersFrame(
                HttpHeaders.of(Map.of("Example-Field", List.of("value")), (a, b) -> true),
                Map.of(":method", "GET", ":scheme", "https", ":path", "/", ":authority", "www.example.com")
        );

        // Then
        Encoder encoder = mockEncoder();
        headersFrame.toBytes(encoder);
        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(encoder, times(1)).compressHeaders(listCaptor.capture());

        List<Map.Entry<String, String>> compressedItems = listCaptor.getValue();
        assertThat(compressedItems.get(0).getKey()).startsWith((":"));
        assertThat(compressedItems.get(compressedItems.size()-1).getKey()).isEqualTo("example-field");
    }

    @Test
    public void multiValueHeadersShouldBeParsedCorrectly() throws Exception {
        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/plain"),
                new AbstractMap.SimpleEntry<>("set-cookie", "sample=foo"),
                new AbstractMap.SimpleEntry<>("set-cookie", "sample=bar")
        ));
        HeadersFrame headersFrame = new HeadersFrame().parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers().map()).containsEntry("set-cookie", List.of("sample=foo", "sample=bar"));
    }

    private HeadersFrame createHeadersFrame(String method, URI uri) {
        HeadersFrame headersFrame = new HeadersFrame(null, Map.of(
                ":method", method,
                ":authority", uri.getHost() + ":" + uri.getPort(),
                ":path", uri.getPath()
        ));
        return headersFrame;
    }

    private Encoder mockEncoder() {
        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));
        return qpackEncoder;
    }
}
