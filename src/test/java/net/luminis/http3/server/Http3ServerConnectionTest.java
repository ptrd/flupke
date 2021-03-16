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
package net.luminis.http3.server;

import net.luminis.http3.impl.DataFrame;
import net.luminis.http3.impl.RequestHeadersFrame;
import net.luminis.http3.impl.ResponseHeadersFrame;
import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.server.ServerConnection;
import net.luminis.quic.stream.QuicStream;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;


public class Http3ServerConnectionTest {

    private List<Map.Entry<String, String>> mockEncoderCompressedHeaders = new ArrayList<>();

    @Test
    public void handlerIsCalledWithMethodAndPathFromHeadersFrame() throws Exception {
        HttpRequestHandler handler = mock(HttpRequestHandler.class);
        Http3ServerConnection http3Connection = new Http3ServerConnection(createMockQuicConnection(), handler);

        RequestHeadersFrame headersFrame = new RequestHeadersFrame();
        headersFrame.setMethod("GET");
        headersFrame.setUri(new URI("https://www.example.com/index.html"));
        http3Connection.handleHttpRequest(List.of(headersFrame), createMockQuicStream(null), new NoOpEncoder());

        verify(handler).handleRequest(argThat(req ->
                req.method().equals("GET") &&
                req.path().equals("/index.html")
        ), any(HttpServerResponse.class));
    }

    @Test
    public void statusReturnedByHandlerIsWrittenToHeadersFrame() throws Exception {
        // Given
        HttpRequestHandler handler = new HttpRequestHandler() {
            @Override
            public void handleRequest(HttpServerRequest request, HttpServerResponse response) throws IOException {
                response.setStatus(201);
            }
        };
        Http3ServerConnection http3Connection = new Http3ServerConnection(createMockQuicConnection(), handler);

        // When
        RequestHeadersFrame requestHeadersFrame = new RequestHeadersFrame();
        requestHeadersFrame.setMethod("GET");
        requestHeadersFrame.setUri(new URI("https://www.example.com/index.html"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        QuicStream stream = createMockQuicStream(output);
        http3Connection.handleHttpRequest(List.of(requestHeadersFrame), stream, new NoOpEncoder());

        // Then
        ResponseHeadersFrame responseHeadersFrame = new ResponseHeadersFrame().parsePayload(output.toByteArray(), new NoOpDecoder());
        assertThat(responseHeadersFrame.statusCode()).isEqualTo(201);
    }

    @Test
    public void responseWrittenByHandlerIsWrittenToQuicStream() throws Exception {
        // Given
        HttpRequestHandler handler = new HttpRequestHandler() {
            @Override
            public void handleRequest(HttpServerRequest request, HttpServerResponse response) throws IOException {
                response.setStatus(201);
                response.getOutputStream().write("Hello World!".getBytes());
            }
        };

        Http3ServerConnection http3Connection = new Http3ServerConnection(createMockQuicConnection(), handler);

        // When
        RequestHeadersFrame requestHeadersFrame = new RequestHeadersFrame();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        QuicStream stream = createMockQuicStream(output);
        http3Connection.handleHttpRequest(List.of(requestHeadersFrame), stream, new NoOpEncoder());

        // Then
        // Strip of header frame (two bytes: header type and header length (== 0), because of dummy encoder)
        byte[] dataBytes = Arrays.copyOfRange(output.toByteArray(), 2, output.toByteArray().length);
        DataFrame dataFrame = new DataFrame().parse(dataBytes);
        assertThat(dataFrame.getPayload()).isEqualTo("Hello World!".getBytes());
    }

    private QuicConnection createMockQuicConnection() {
        ServerConnection connection = mock(ServerConnection.class);
        QuicStream stream = mock(QuicStream.class);
        when(connection.createStream(false)).thenReturn(stream);
        when(stream.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return connection;
    }

    private QuicStream createMockQuicStream(ByteArrayOutputStream byteArrayOutputStream) {
        if (byteArrayOutputStream == null) {
            byteArrayOutputStream = new ByteArrayOutputStream();
        }
        QuicStream stream = mock(QuicStream.class);
        when(stream.getOutputStream()).thenReturn(byteArrayOutputStream);
        return stream;
    }

    private class NoOpEncoder extends Encoder {
        @Override
        public ByteBuffer compressHeaders(List<Map.Entry<String, String>> headers) {
            mockEncoderCompressedHeaders = headers;
            return mock(ByteBuffer.class);
        }
    }

    private class NoOpDecoder extends Decoder {
        @Override
        public List<Map.Entry<String, String>> decodeStream(InputStream inputStream) throws IOException {
            return mockEncoderCompressedHeaders;
        }
    }
}