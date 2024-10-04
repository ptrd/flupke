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
package net.luminis.http3.server;

import net.luminis.http3.core.HttpError;
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.impl.DataFrame;
import net.luminis.http3.impl.HeadersFrame;
import net.luminis.http3.test.CapturingEncoder;
import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.server.ServerConnection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.util.Collections.emptyMap;
import static net.luminis.http3.impl.Http3ConnectionImpl.FRAME_TYPE_DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


public class Http3ServerConnectionImplTest {

    private List<Map.Entry<String, String>> mockEncoderCompressedHeaders = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    //region request handling
    @Test
    void handlerIsCalledWithMethodAndPathFromHeadersFrame() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "GET", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(handler).handleRequest(
                argThat(req ->
                        req.method().equals("GET") && req.path().equals("/index.html")),
                any(HttpServerResponse.class));
    }
    //endregion

    //region request correctness
    @Test
    void whenMandatoryPseudoHeaderMethodIsMissingResetStreamShouldBeCalled() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":scheme", "https",":authority", "www.example.com:443", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream).resetStream(anyLong());
    }

    @Test
    void whenMandatoryPseudoHeaderSchemeIsMissingResetStreamShouldBeCalled() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "GET",":authority", "www.example.com:443", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream).resetStream(anyLong());
    }

    @Test
    void whenMandatoryPseudoHeaderPathIsMissingResetStreamShouldBeCalled() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "GET", ":scheme", "https", ":authority", "example.com"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream).resetStream(anyLong());
    }

    @Test
    void whenPseudoHeaderAuthorityIsMissingResetStreamShouldBeCalled() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "GET", ":scheme", "https", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream).resetStream(anyLong());
    }

    @Test
    void whenPseudoHeaderAuthorityIsMissingButHostHeaderIsPresentResetStreamShouldNotBeCalled() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of("Host","example.com", ":method", "GET", ":scheme", "https", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream, never()).resetStream(anyLong());
    }

    @Test
    void whenPseudoHeaderAuthorityIsMissingForConnectMethodResetStreamShouldBeCalled() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream).resetStream(anyLong());
    }
    //endregion

    //region response handling
    @Test
    void statusReturnedByHandlerIsWrittenToHeadersFrame() throws Exception {
        // Given
        HttpRequestHandler handler = new HttpRequestHandler() {
            @Override
            public void handleRequest(HttpServerRequest request, HttpServerResponse response) {
                response.setStatus(201);
            }
        };
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), handler, executor, emptyMap());

        // When
        HeadersFrame requestHeadersFrame = createHeadersFrame("GET", new URI("https://www.example.com/index.html"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        QuicStream stream = mockQuicStream(output);
        http3Connection.handleHttpRequest(List.of(requestHeadersFrame), stream, new NoOpEncoder());

        // Then
        HeadersFrame responseHeadersFrame = new HeadersFrame().parsePayload(output.toByteArray(), new NoOpDecoder());
        assertThat(responseHeadersFrame.getPseudoHeader(":status")).isEqualTo("201");
    }

    @Test
    void responseWrittenByHandlerIsWrittenToQuicStream() throws Exception {
        // Given
        HttpRequestHandler handler = new HttpRequestHandler() {
            @Override
            public void handleRequest(HttpServerRequest request, HttpServerResponse response) throws IOException {
                response.setStatus(201);
                response.getOutputStream().write("Hello World!".getBytes());
            }
        };

        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), handler, executor, emptyMap());

        // When
        HeadersFrame requestHeadersFrame = new HeadersFrame();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        QuicStream stream = mockQuicStream(output);
        http3Connection.handleHttpRequest(List.of(requestHeadersFrame), stream, new NoOpEncoder());

        // Then
        // Strip of header frame (two bytes: header type and header length (== 0), because of dummy encoder)
        byte[] dataBytes = Arrays.copyOfRange(output.toByteArray(), 2, output.toByteArray().length);
        DataFrame dataFrame = new DataFrame().parse(dataBytes);
        assertThat(dataFrame.getPayload()).isEqualTo("Hello World!".getBytes());
    }

    @Test
    void statusShouldAlwaysBeSetEvenWhenHandlerDoesNot() throws Exception {
        // Given
        HttpRequestHandler handler = (req, resp) -> {};
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), handler, executor, emptyMap());

        // When
        HeadersFrame requestHeadersFrame = new HeadersFrame();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        QuicStream stream = mockQuicStream(output);
        CapturingEncoder encoder = new CapturingEncoder();
        http3Connection.handleHttpRequest(List.of(requestHeadersFrame), stream, encoder);

        // Then
        assertThat(encoder.getCapturedHeaders()).containsKey(":status");
    }
    //endregion

    //region request limits
    @Test
    void requestDataLargerThanMaxIsNotAccepted() {
        // Given
        long maxHeaderSize = Long.MAX_VALUE;
        long maxDataSize = 2500;
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), mock(HttpRequestHandler.class), maxHeaderSize, maxDataSize, executor, emptyMap());
        byte[] rawData = new byte[10000];
        rawData[0] = FRAME_TYPE_DATA;
        rawData[1] = 0x44; // 0x44ff == 1279
        rawData[2] = (byte) 0xff;
        rawData[3 + 1279 + 0] = FRAME_TYPE_DATA;
        rawData[3 + 1279 + 1] = 0x44; // 0x44ff == 1279
        rawData[3 + 1279 + 2] = (byte) 0xff;

        assertThatThrownBy(() ->
                // When
                http3Connection.parseHttp3Frames(new ByteArrayInputStream(rawData)))
                // Then
                .isInstanceOf(HttpError.class)
                .hasMessageContaining("max data");
    }

    @Test
    void requestHeadersLargerThanMaxIsNotAccepted() {
        // Given
        long maxHeaderSize = 1000;
        long maxDataSize = Long.MAX_VALUE;
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), mock(HttpRequestHandler.class), maxHeaderSize, maxDataSize, executor, emptyMap());

        HeadersFrame largeHeaders = new HeadersFrame("superlarge", "*".repeat(1000));
        byte[] data = largeHeaders.toBytes(new Encoder());

        assertThatThrownBy(() ->
                // When
                http3Connection.parseHttp3Frames(new ByteArrayInputStream(data)))
                // Then
                .isInstanceOf(HttpError.class)
                .hasMessageContaining("max header");
    }

    @Test
    void requestThatIsAbortedWithErrorDiscardsStream() {
        // Given
        long maxHeaderSize = 1000;
        long maxDataSize = Long.MAX_VALUE;
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), mock(HttpRequestHandler.class), maxHeaderSize, maxDataSize, executor, emptyMap());

        HeadersFrame largeHeaders = new HeadersFrame("superlarge", "*".repeat(1000));
        byte[] data = largeHeaders.toBytes(new Encoder());

        // When
        QuicStream quicStream = mockQuicStreamWithInputData(data, new ByteArrayOutputStream());
        http3Connection.handleBidirectionalStream(quicStream);

        // Then
        verify(quicStream).abortReading(anyLong());
    }
    //endregion

    //region CONNECT method
    @Test
    void methodConnectShouldReturnStatus501() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        CapturingEncoder encoder = new CapturingEncoder();
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":authority", "example.com"))
                .withHandler(handler)
                .withEncoder(encoder)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        assertThat(encoder.getCapturedHeaders().get(":status")).isEqualTo("501");
    }

    @Test
    void extendedConnectWithUnsupportProtocolShouldReturnStatus501() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        CapturingEncoder encoder = new CapturingEncoder();
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "websockets", ":authority", "example.com"))
                .withHandler(handler)
                .withEncoder(encoder)
                .buildServerConnection();
        OutputStream outputStream = mock(OutputStream.class);
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData(), outputStream);

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        assertThat(encoder.getCapturedHeaders().get(":status")).isEqualTo("501");
        verify(outputStream).close();
    }

    @Test
    void extendedConnectShouldNotCloseRequestStream() throws Exception {
        // Given
        Http3ServerExtension extensionHandler = mock(Http3ServerExtension.class);
        when(extensionHandler.handleExtendedConnect(any(HttpHeaders.class), anyString(), anyString(), anyString(), any(QuicStream.class))).thenReturn(200);
        Http3ServerExtensionFactory extensionFactory = http3ServerConnection -> extensionHandler;

        CapturingEncoder encoder = new CapturingEncoder();
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "websockets", ":authority", "example.com", ":path", "/"))
                .withExtensionHandler("websockets", extensionFactory)
                .withEncoder(encoder)
                .buildServerConnection();
        OutputStream outputStream = mock(OutputStream.class);
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData(), outputStream);

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        assertThat(encoder.getCapturedHeaders().get(":status")).isEqualTo("200");
        verify(outputStream, never()).close();
    }

    @Test
    void serverShouldSendEnableConnectProtocolSetting() throws Exception {
        // Given
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        HttpConnectionBuilder builder = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "websockets", ":authority", "example.com"))
                .withControlStreamOutputSentTo(controlStreamOutput);

        // When
        Http3ServerConnectionImpl http3Connection = builder.buildServerConnection();

        // Then
        assertThat(controlStreamOutput.toByteArray()).isEqualTo(new byte[] {
                0x00,  // type: control stream
                0x04,  // frame type: SETTINGS
                0x06,  // payload length
                // frame payload
                0x01,  // identifier: SETTINGS_QPACK_MAX_TABLE_CAPACITY
                0,     // value
                0x07,  // identifier: SETTINGS_QPACK_BLOCKED_STREAMS
                0,     // value
                0x08,  // identifier: SETTINGS_ENABLE_CONNECT_PROTOCOL
                1      // value
        });

    }

    @Test
    void http3serverExtensionIsCalledWhenRegisteredProperly() throws Exception {
        // Given
        Http3ServerExtension extensionHandler = mock(Http3ServerExtension.class);
        when(extensionHandler.handleExtendedConnect(any(HttpHeaders.class), anyString(), anyString(), anyString(), any(QuicStream.class))).thenReturn(200);
        Http3ServerExtensionFactory extensionFactory = http3ServerConnection -> extensionHandler;

        CapturingEncoder encoder = new CapturingEncoder();
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "webtransport", ":authority", "example.com", ":path", "/"))
                .withEncoder(encoder)
                .withExtensionHandler("webtransport", extensionFactory)
                .buildServerConnection();
        QuicStream requestResponseStream = mockQuicStreamWithInputData(fakeHeadersFrameData());

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(extensionHandler).handleExtendedConnect(
                any(HttpHeaders.class),
                argThat(p -> p.equals("webtransport")),
                argThat(a -> a.equals("example.com")),
                argThat(p -> p.equals("/")),
                any(QuicStream.class));
        assertThat(encoder.getCapturedHeaders().get(":status")).isEqualTo("200");
    }
    //endregion

    //region HTTP/3 extensions
    @Test
    void whenExtensionHandlerForBidirectionalStreamsIsRegisteredItShouldBeCalled() throws Exception {
        // Given
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .buildServerConnection();
        Consumer<HttpStream> handler = mock(Consumer.class);
        http3Connection.registerBidirectionalStreamHandler(0x3e, handler);

        // When
        byte[] input = new byte[] { 0x3e, (byte) 0xca, (byte) 0xfe };
        QuicStream requestResponseStream = mockQuicStreamWithInputData(input);
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        ArgumentCaptor<HttpStream> streamArgumentCaptor = ArgumentCaptor.forClass(HttpStream.class);
        verify(handler).accept(streamArgumentCaptor.capture());
        assertThat(streamArgumentCaptor.getValue().getInputStream().readAllBytes()).isEqualTo(new byte[] { 0x3e, (byte) 0xca, (byte) 0xfe });
    }
    //endregion

    //region helper methods
    private byte[] fakeHeadersFrameData() {
        return new byte[] {
                0x01,
                0x02,
                (byte) 0xfa,
                0x1e
        };
    }

    private HeadersFrame createHeadersFrame(String method, URI uri) {
        HeadersFrame headersFrame = new HeadersFrame(null, Map.of(
                ":method", method,
                ":authority", uri.getHost() + ":" + uri.getPort(),
                ":path", uri.getPath()
        ));
        return headersFrame;
    }

    private QuicConnection createMockQuicConnection() {
        ServerConnection connection = mock(ServerConnection.class);
        QuicStream stream = mock(QuicStream.class);
        when(connection.createStream(false)).thenReturn(stream);
        when(stream.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return connection;
    }

    private QuicStream mockQuicStreamWithInputData(byte[] inputData) {
        return mockQuicStreamWithInputData(inputData, null);
    }

    private QuicStream mockQuicStream(ByteArrayOutputStream byteArrayOutputStream) {
        return mockQuicStreamWithInputData(null, byteArrayOutputStream);
    }

    private QuicStream mockQuicStreamWithInputData(byte[] inputData, OutputStream outputStream) {
        ByteArrayInputStream byteArrayInputStream = null;
        if (inputData == null) {
            byteArrayInputStream = new ByteArrayInputStream(new byte[0]);
        }
        else {
            byteArrayInputStream = new ByteArrayInputStream(inputData);
        }
        if (outputStream == null) {
            outputStream = new ByteArrayOutputStream();
        }

        QuicStream stream = mock(QuicStream.class);
        when(stream.getOutputStream()).thenReturn(outputStream);
        when(stream.getInputStream()).thenReturn(byteArrayInputStream);
        return stream;
    }

    private class NoOpEncoder extends Encoder {
        @Override
        public ByteBuffer compressHeaders(List<Map.Entry<String, String>> headers) {
            mockEncoderCompressedHeaders = headers;
            int uncompressedSize = headers.stream().mapToInt(e -> e.getKey().length() + e.getValue().length() + 2).sum();
            return ByteBuffer.allocate(uncompressedSize);
        }
    }

    private class NoOpDecoder extends Decoder {
        @Override
        public List<Map.Entry<String, String>> decodeStream(InputStream inputStream) {
            return mockEncoderCompressedHeaders;
        }
    }
    //endregion
}