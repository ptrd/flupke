/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.server.ServerConnection;
import tech.kwik.flupke.core.HttpError;
import tech.kwik.flupke.core.HttpStream;
import tech.kwik.flupke.impl.ConnectionError;
import tech.kwik.flupke.impl.DataFrame;
import tech.kwik.flupke.impl.HeadersFrame;
import tech.kwik.flupke.impl.SettingsFrame;
import tech.kwik.flupke.test.CapturingEncoder;
import tech.kwik.flupke.test.NoOpEncoderDecoderBuilder;
import tech.kwik.flupke.test.QuicStreamBuilder;
import tech.kwik.flupke.webtransport.impl.WebTransportExtensionFactory;
import tech.kwik.qpack.Encoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static tech.kwik.flupke.impl.Http3ConnectionImpl.FRAME_TYPE_DATA;


public class Http3ServerConnectionImplTest {

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private NoOpEncoderDecoderBuilder noOpEncoderDecoderBuilder;

    @BeforeEach
    void setUp() {
        noOpEncoderDecoderBuilder = new NoOpEncoderDecoderBuilder();
    }

    //region request handling
    @Test
    void handlerIsCalledWithMethodAndPathFromHeadersFrame() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "GET", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(handler).handleRequest(
                argThat(req ->
                        req.method().equals("GET") && req.path().equals("/index.html")),
                any(HttpServerResponse.class));
    }

    @Test
    void requestHeadersShouldBePassedToHandler() throws Exception {
        // Given
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "GET", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHeaders(Map.of("X-Custom-Header", "CustomValue"))
                .withHandler(handler)
                .buildServerConnection();
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(handler).handleRequest(
                argThat(req -> req.headers().firstValue("X-Custom-Header").orElse("").equals("CustomValue")),
                any(HttpServerResponse.class));
    }

    @Test
    void requestBodyShouldBePassedToHandler() throws Exception {
        // Given
        AtomicReference<String> bodyContent = new AtomicReference<>("");
        HttpRequestHandler handler = (request, response) -> {
            bodyContent.set(new String(request.body().readAllBytes()));
            response.setStatus(200);
        };
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "POST", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();

        ByteBuffer requestData = ByteBuffer.allocate(600);
        requestData.put(fakeHeadersFrameData());
        requestData.put(new byte[] { FRAME_TYPE_DATA, 0x04, 0x62, 0x6f, 0x64, 0x79 });
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(requestData.array()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        assertThat(bodyContent.get()).isEqualTo("body");
    }

    @Test
    void requestBodyStreamMayContainUnknownFrames() throws Exception {
        // Given
        AtomicReference<String> bodyContent = new AtomicReference<>("");
        HttpRequestHandler handler = (request, response) -> {
            bodyContent.set(new String(request.body().readAllBytes()));
            response.setStatus(200);
        };
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "POST", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();

        byte[] streamData = new byte[] {
                FRAME_TYPE_DATA, 0x04, 0x62, 0x6f, 0x64, 0x79, // DATA frame with "body"
                0x21, 0x00,                                   // unknown frame type with zero length
                0x40, 0x40, 0x07, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, // unknown frame type with length and payload
                FRAME_TYPE_DATA, 0x04, 0x62, 0x6f, 0x64, 0x79  // another DATA frame with "body"
        };
        ByteBuffer requestData = ByteBuffer.allocate(600);
        requestData.put(fakeHeadersFrameData());
        requestData.put(streamData);
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(requestData.array()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        assertThat(bodyContent.get()).isEqualTo("bodybody");
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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(requestResponseStream).resetStream(anyLong());
    }

    @Test
    void whenDataFrameIsShorterThanIndicatedConnectionErrorIsThrown() throws Exception {
        // Given
        HttpRequestHandler handler = (request, response) -> {
            request.body().readAllBytes();
            response.setStatus(200);
        };
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "POST", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();

        byte dataFrameLength = 0x39;
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(new byte[] { FRAME_TYPE_DATA, dataFrameLength, 0x62, 0x6f, 0x64, 0x79 }).build();

        // When
        assertThatThrownBy(() ->
                http3Connection.handleHttpRequest(new HeadersFrame(":method", "GET", ":path", "/"), requestResponseStream, noOpEncoderDecoderBuilder.encoder()))
                // Then
                .isInstanceOf(ConnectionError.class)
                .hasMessageContaining("262");
    }

    @Test
    void whenDataFrameIsShorterThanIndicatedSingleReadShouldThrowConnectionError() throws Exception {
        // Given
        HttpRequestHandler handler = (request, response) -> {
            int read;
            do {
                read = request.body().read();
            } while (read != -1);
            response.setStatus(200);
        };
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "POST", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();

        byte dataFrameLength = 0x39;
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(new byte[] { FRAME_TYPE_DATA, dataFrameLength, 0x62, 0x6f, 0x64, 0x79 }).build();

        // When
        assertThatThrownBy(() ->
                http3Connection.handleHttpRequest(new HeadersFrame(":method", "GET", ":path", "/"), requestResponseStream, noOpEncoderDecoderBuilder.encoder()))
                // Then
                .isInstanceOf(ConnectionError.class)
                .hasMessageContaining("262");
    }

    @Test
    void whenDataFrameIsIncompleteConnectionErrorIsThrown() throws Exception {
        // Given
        HttpRequestHandler handler = (request, response) -> {
            request.body().readAllBytes();
            response.setStatus(200);
        };
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "POST", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();

        byte[] invalidVarInt = new byte[] { 0x40 };
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(invalidVarInt).build();

        // When
        assertThatThrownBy(() ->
                http3Connection.handleHttpRequest(new HeadersFrame(":method", "GET", ":path", "/"), requestResponseStream, noOpEncoderDecoderBuilder.encoder()))
                // Then
                .isInstanceOf(ConnectionError.class)
                .hasMessageContaining("262");
    }

    @Test
    void afterReadingRequestStreamItShouldBeClosed() throws Exception {
        // Given
        HttpRequestHandler handler = (request, response) -> {
            request.body().readAllBytes();
            response.setStatus(200);
        };
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "POST", ":scheme", "https", ":authority", "example.com", ":path", "/index.html"))
                .withHandler(handler)
                .buildServerConnection();

        QuicStream requestResponseStream = mock(QuicStream.class);
        when(requestResponseStream.getInputStream()).thenReturn(mock(InputStream.class));
        when(requestResponseStream.getOutputStream()).thenReturn(mock(OutputStream.class));

        // When
        http3Connection.handleHttpRequest(new HeadersFrame(":method", "GET", ":path", "/"), requestResponseStream, noOpEncoderDecoderBuilder.encoder());

        // Then
        verify(requestResponseStream.getInputStream()).close();
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
        QuicStream stream = new QuicStreamBuilder().withOutputStream(output).build();
        http3Connection.handleHttpRequest(requestHeadersFrame, stream, noOpEncoderDecoderBuilder.encoder());

        // Then
        HeadersFrame responseHeadersFrame = new HeadersFrame().parsePayload(output.toByteArray(), noOpEncoderDecoderBuilder.decoder());
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
        QuicStream stream = new QuicStreamBuilder().withOutputStream(output).build();
        http3Connection.handleHttpRequest(requestHeadersFrame, stream, noOpEncoderDecoderBuilder.encoder());

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
        QuicStream stream = new QuicStreamBuilder().withOutputStream(output).build();
        CapturingEncoder encoder = new CapturingEncoder();
        http3Connection.handleHttpRequest(requestHeadersFrame, stream, encoder);

        // Then
        assertThat(encoder.getCapturedHeaders()).containsKey(":status").containsValue("500");
    }
    //endregion

    //region request limits
    @Test
    void requestDataLargerThanMaxIsNotAccepted() throws Exception {
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
    void requestHeadersLargerThanMaxIsNotAccepted() throws Exception {
        // Given
        long maxHeaderSize = 1000;
        long maxDataSize = Long.MAX_VALUE;
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), mock(HttpRequestHandler.class), maxHeaderSize, maxDataSize, executor, emptyMap());

        HeadersFrame largeHeaders = new HeadersFrame("superlarge", "*".repeat(1000));
        byte[] data = largeHeaders.toBytes(Encoder.newBuilder().build());

        assertThatThrownBy(() ->
                // When
                http3Connection.parseHttp3Frames(new ByteArrayInputStream(data)))
                // Then
                .isInstanceOf(HttpError.class)
                .hasMessageContaining("max header");
    }

    @Test
    void requestThatIsAbortedWithErrorDiscardsStream() throws Exception {
        // Given
        long maxHeaderSize = 1000;
        long maxDataSize = Long.MAX_VALUE;
        Http3ServerConnectionImpl http3Connection = new Http3ServerConnectionImpl(createMockQuicConnection(), mock(HttpRequestHandler.class), maxHeaderSize, maxDataSize, executor, emptyMap());

        HeadersFrame largeHeaders = new HeadersFrame("superlarge", "*".repeat(1000));
        byte[] data = largeHeaders.toBytes(Encoder.newBuilder().build());

        // When
        QuicStream quicStream = new QuicStreamBuilder()
                .withInputData(data)
                .withOutputStream(new ByteArrayOutputStream())
                .build();
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
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

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
        QuicStream requestResponseStream = new QuicStreamBuilder()
                .withInputData(fakeHeadersFrameData())
                .withOutputStream(outputStream)
                .build();

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
        doAnswer(new StatusCallbackAnswer(200))
                .when(extensionHandler).handleExtendedConnect(any(HttpHeaders.class), anyString(), anyString(), anyString(), any(IntConsumer.class), any(HttpStream.class));
        Http3ServerExtensionFactory extensionFactory = http3ServerConnection -> extensionHandler;

        CapturingEncoder encoder = new CapturingEncoder();
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "websockets", ":authority", "example.com", ":path", "/"))
                .withExtensionHandler("websockets", extensionFactory)
                .withEncoder(encoder)
                .buildServerConnection();
        OutputStream outputStream = mock(OutputStream.class);
        QuicStream requestResponseStream = new QuicStreamBuilder()
                .withInputData(fakeHeadersFrameData())
                .withOutputStream(outputStream)
                .build();

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
    //endregion

    //region HTTP/3 extensions
    @Test
    void http3serverExtensionIsCalledWhenRegisteredProperly() throws Exception {
        // Given
        Http3ServerExtension extensionHandler = mock(Http3ServerExtension.class);
        doAnswer(new StatusCallbackAnswer(200))
                .when(extensionHandler).handleExtendedConnect(any(HttpHeaders.class), anyString(), anyString(), anyString(), any(IntConsumer.class), any(HttpStream.class));
        Http3ServerExtensionFactory extensionFactory = http3ServerConnection -> extensionHandler;

        CapturingEncoder encoder = new CapturingEncoder();
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "webtransport", ":authority", "example.com", ":path", "/"))
                .withEncoder(encoder)
                .withExtensionHandler("webtransport", extensionFactory)
                .buildServerConnection();
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(extensionHandler).handleExtendedConnect(
                any(HttpHeaders.class),
                argThat(p -> p.equals("webtransport")),
                argThat(a -> a.equals("example.com")),
                argThat(p -> p.equals("/")),
                any(IntConsumer.class),
                any(HttpStream.class));
        assertThat(encoder.getCapturedHeaders().get(":status")).isEqualTo("200");
    }

    @Test
    void whenExtensionReturns404ConnectStreamShouldBeClosed() throws Exception {
        // Given
        Http3ServerExtension extensionHandler = mock(Http3ServerExtension.class);
        doAnswer(new StatusCallbackAnswer(404))
                .when(extensionHandler).handleExtendedConnect(any(HttpHeaders.class), anyString(), anyString(), anyString(), any(IntConsumer.class), any(HttpStream.class));
        Http3ServerExtensionFactory extensionFactory = http3ServerConnection -> extensionHandler;

        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "webtransport", ":authority", "example.com", ":path", "/"))
                .withEncoder(noOpEncoderDecoderBuilder.encoder())
                .withExtensionHandler("webtransport", extensionFactory)
                .buildServerConnection();
        OutputStream outputStream = mock(OutputStream.class);
        QuicStream requestResponseStream = new QuicStreamBuilder()
                .withInputData(fakeHeadersFrameData())
                .withOutputStream(outputStream)
                .build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        verify(outputStream).close();
    }

    @Test
    void whenExtensionAccessesStreamBeforeSettingStatusExceptionShouldBeThrown() throws Exception {
        // Given
        AtomicReference<Exception> exceptionHolder = new AtomicReference<>();

        Http3ServerExtension extensionHandler = new Http3ServerExtension() {
            @Override
            public void handleExtendedConnect(HttpHeaders headers, String protocol, String authority, String path, IntConsumer statusCallback, HttpStream stream) {
                // Accessing the stream before setting the status
                try {
                    stream.getOutputStream().write("This should not be allowed".getBytes());
                }
                catch (Exception e) {
                    exceptionHolder.set(e);
                }
            }
        };

        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .withHeaders(Map.of(":method", "CONNECT", ":protocol", "webtransport", ":authority", "example.com", ":path", "/"))
                .withEncoder(noOpEncoderDecoderBuilder.encoder())
                .withExtensionHandler("webtransport", http3ServerConnection -> extensionHandler)
                .buildServerConnection();
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(fakeHeadersFrameData()).build();

        // When
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        assertThat(exceptionHolder.get()).isNotNull();
    }

    @Test
    void whenExtensionHandlerForBidirectionalStreamsIsRegisteredItShouldBeCalled() throws Exception {
        // Given
        Http3ServerConnectionImpl http3Connection = new HttpConnectionBuilder()
                .buildServerConnection();
        Consumer<HttpStream> handler = mock(Consumer.class);
        http3Connection.registerBidirectionalStreamHandler(0x3e, handler);

        // When
        byte[] input = new byte[] { 0x3e, (byte) 0xca, (byte) 0xfe };
        QuicStream requestResponseStream = new QuicStreamBuilder().withInputData(input).build();
        http3Connection.handleBidirectionalStream(requestResponseStream);

        // Then
        ArgumentCaptor<HttpStream> streamArgumentCaptor = ArgumentCaptor.forClass(HttpStream.class);
        verify(handler).accept(streamArgumentCaptor.capture());
        assertThat(streamArgumentCaptor.getValue().getInputStream().readAllBytes()).isEqualTo(new byte[] { 0x3e, (byte) 0xca, (byte) 0xfe });
    }

    @Test
    void extensionSpecificSettingShouldBeSendInHttp3SettingsFrame() throws Exception {
        // Given
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        HttpConnectionBuilder builder = new HttpConnectionBuilder()
                .withExtensionHandler("webtransport", new WebTransportExtensionFactory())
                .withControlStreamOutputSentTo(controlStreamOutput);

        // When
        Http3ServerConnectionImpl http3ServerConnection = builder.buildServerConnection();

        // Then
        ByteBuffer serializedSettingsFrame = ByteBuffer.wrap(controlStreamOutput.toByteArray());
        serializedSettingsFrame.get(); // skip first byte (type)
        SettingsFrame settingsFrame = new SettingsFrame().parsePayload(serializedSettingsFrame);
        assertThat(settingsFrame.getParameter(0x14e9cd29L)).isEqualTo(1L);
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

    private QuicConnection createMockQuicConnection() throws Exception {
        ServerConnection connection = mock(ServerConnection.class);
        QuicStream stream = mock(QuicStream.class);
        when(connection.createStream(false)).thenReturn(stream);
        when(stream.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return connection;
    }

    private static class StatusCallbackAnswer implements Answer<Void> {
        final int status;

        public StatusCallbackAnswer(int status) {
            this.status = status;
        }

        public Void answer(InvocationOnMock invocation) {
            IntConsumer statusCallback = (IntConsumer) invocation.getArguments()[4];
            statusCallback.accept(status);
            return null;
        }
    }
    //endregion
}