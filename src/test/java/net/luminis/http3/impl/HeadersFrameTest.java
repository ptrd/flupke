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
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST).parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers().map()).containsEntry("content-type", List.of("text/plain"));
    }

    @Test
    public void frameDoesNotReturnPseudoHeaderReturnedByDecoder() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/plain")
        ));
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST).parsePayload(new byte[0], qpackDecoder);

        assertThat(headersFrame.headers().map()).doesNotContainKey(":status");
    }

    @Test
    public void decodedResponseWitInvalidStatusPseudoHeaderThrows() throws Exception {

        Decoder qpackDecoder = mock(Decoder.class);
        when(qpackDecoder.decodeStream(any(InputStream.class))).thenReturn(List.of(new AbstractMap.SimpleEntry<>(":status", "invalid")));

        assertThatThrownBy(
                () -> new HeadersFrame(HeadersFrame.Type.REQUEST).parsePayload(new byte[0], qpackDecoder))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    public void headersFrameBytesShouldStartWithHeadersFrameType() {
        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.wrap(new byte[0]));

        byte[] bytes = new HeadersFrame(HeadersFrame.Type.REQUEST).toBytes(qpackEncoder);

        https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.2: The HEADERS frame (type=0x1)...
        assertThat(bytes).startsWith(0x01);  // Headers frame
    }

    @Test
    public void headersFrameSizeIsEncodedAsVarInt() {
        Encoder qpackEncoder = mock(Encoder.class);
        ByteBuffer payload = ByteBuffer.allocate(67);
        payload.put(new byte[67]);  // Put ByteByffer in "write" mode
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(payload);

        byte[] bytes = new HeadersFrame(HeadersFrame.Type.REQUEST).toBytes(qpackEncoder);

        assertThat(bytes).startsWith(0x01, 0x40, 67);
    }

    @Test
    public void compressHeadersAreCopiedIntoHeadersFrame() {
        Encoder qpackEncoder = mock(Encoder.class);
        byte[] payload = new byte[] { 0x33, 0x10, 0x5f, 0x6e, 0x00, 0x3c, 0x77, 0x7f };
        ByteBuffer payloadBuffer = ByteBuffer.allocate(payload.length);
        payloadBuffer.put(payload);

        when(qpackEncoder.compressHeaders(anyList())).thenReturn(payloadBuffer);

        byte[] bytes = new HeadersFrame(HeadersFrame.Type.REQUEST).toBytes(qpackEncoder);

        assertThat(bytes).startsWith(0x01, 0x08, 0x33, 0x10, 0x5f, 0x6e, 0x00, 0x3c, 0x77, 0x7f);
    }

    @Test
    public void headersFrameShouldContainCompulsaryPseudoHeaders() throws URISyntaxException {
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        headersFrame.setMethod("GET");
        headersFrame.setUri(new URI("/index.html"));

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        List<String> headerNames = listCaptor.getValue().stream().map(entry -> entry.getKey()).collect(Collectors.toList());

        // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
        // "All HTTP/2 requests MUST include exactly one valid value for the
        //   ":method", ":scheme", and ":path" pseudo-header fields"
        assertThat(headerNames).contains(":method");
        assertThat(headerNames).contains(":scheme");
        assertThat(headerNames).contains(":path");
    }

    @Test
    public void headersFrameShouldContainAuthorityHeader() throws URISyntaxException {
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        headersFrame.setMethod("GET");
        headersFrame.setUri(new URI("http://example.com:4433/index.html"));

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        List<String> headerNames = listCaptor.getValue().stream().map(entry -> entry.getKey()).collect(Collectors.toList());

        // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
        // "Clients that generate HTTP/2 requests directly SHOULD use the ":authority"
        //  pseudo-header field instead of the Host header field."
        assertThat(headerNames).contains(":authority");
        assertThat(listCaptor.getValue().stream()
                .filter(entry -> entry.getKey().equals(":authority"))
                .map(entry -> entry.getValue())
                .findFirst().get())
                .isEqualTo("example.com:4433");
    }

    @Test
    public void headersFrameAuthorityHeaderShouldExludeUserInfo() throws URISyntaxException {
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        headersFrame.setMethod("GET");
        headersFrame.setUri(new URI("http://username:password@example.com:4433/index.html"));

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        List<String> headerNames = listCaptor.getValue().stream().map(entry -> entry.getKey()).collect(Collectors.toList());

        // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
        // "The authority MUST NOT include the deprecated "userinfo" subcomponent for "http"
        // or "https" schemed URIs."
        assertThat(headerNames).contains(":authority");
        assertThat(listCaptor.getValue().stream()
                .filter(entry -> entry.getKey().equals(":authority"))
                .map(entry -> entry.getValue())
                .findFirst().get())
                .isEqualTo("example.com:4433");
    }

    @Test
    public void httpHeadersAreMappedToQpack() {
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        HttpHeaders httpHeaders = HttpHeaders.of(
                Map.of("headername1", List.of("value1"),
                        "headername2", List.of("value2")), (a, b) -> true);
        headersFrame.setHeaders(httpHeaders);

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(2)
                .contains(new AbstractMap.SimpleEntry("headername1", "value1"))
                .contains(new AbstractMap.SimpleEntry("headername2", "value2"));
    }

    @Test
    public void httpHeaderNamesAreConvertedToLowercaseBeforeEncoding() {
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        HttpHeaders httpHeaders = HttpHeaders.of(
                Map.of("HeaderName", List.of("CamelCasedValue")), (a, b) -> true);
        headersFrame.setHeaders(httpHeaders);

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
        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        HttpHeaders httpHeaders = HttpHeaders.of(
                Map.of("headername1", List.of("value1", "value2", "value3"),
                        "headername2", List.of("value20")), (a, b) -> true);
        headersFrame.setHeaders(httpHeaders);

        Encoder qpackEncoder = mock(Encoder.class);
        when(qpackEncoder.compressHeaders(anyList())).thenReturn(ByteBuffer.allocate(1));

        headersFrame.toBytes(qpackEncoder);

        ArgumentCaptor<List<Map.Entry<String, String>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(qpackEncoder, times(1)).compressHeaders(listCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(2)
                .contains(new AbstractMap.SimpleEntry("headername1", "value1,value2,value3"))
                .contains(new AbstractMap.SimpleEntry("headername2", "value20"));
    }
}
