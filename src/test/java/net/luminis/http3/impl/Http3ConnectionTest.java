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
import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.TransportParameters;
import net.luminis.quic.QuicStream;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.*;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.or;


public class Http3ConnectionTest {

    @Test
    public void testServerSettingsFrameIsProcessed() throws IOException {
        Http3Connection http3Connection = new Http3Connection("localhost",4433);

        InputStream serverControlInputStream = new ByteArrayInputStream(new byte[]{
                0x00,  // type: control stream
                0x04,  // frame type: SETTINGS
                0x04,  // payload length
                // frame payload
                0x01,  // identifier: SETTINGS_QPACK_MAX_TABLE_CAPACITY
                32,    // value
                0x07,  // identifier: SETTINGS_QPACK_BLOCKED_STREAMS
                16     // value
        });

        QuicStream mockedServerControlStream = mock(QuicStream.class);
        when(mockedServerControlStream.getInputStream()).thenReturn(serverControlInputStream);
        http3Connection.registerServerInitiatedStream(mockedServerControlStream);

        assertThat(http3Connection.getServerQpackMaxTableCapacity()).isEqualTo(32);
        assertThat(http3Connection.getServerQpackBlockedStreams()).isEqualTo(16);
    }

    @Test
    public void testClientSendsSettingsFrameOnControlStream() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);
        QuicClientConnection quicConnection = mock(QuicClientConnection.class);
        FieldSetter.setField(http3Connection, Http3Connection.class.getDeclaredField("quicConnection"), quicConnection);

        QuicStream quicStreamMock = mock(QuicStream.class);
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        when(quicStreamMock.getOutputStream()).thenReturn(controlStreamOutput);
        when(quicConnection.createStream(anyBoolean())).thenReturn(quicStreamMock);
        http3Connection.connect(10);

        assertThat(controlStreamOutput.toByteArray()).isEqualTo(new byte[] {
                0x00,  // type: control stream
                0x04,  // frame type: SETTINGS
                0x04,  // payload length
                // frame payload
                0x01,  // identifier: SETTINGS_QPACK_MAX_TABLE_CAPACITY
                0,     // value
                0x07,  // identifier: SETTINGS_QPACK_BLOCKED_STREAMS
                0      // value
        });
    }

    @Test
    public void testReceiveHttpResponse() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[] {
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length (payload omitted for the test, is covered by the mock decoder
                // Complete response for Data frame
                0x00, // type Data Frame
                0x05, // payload length
                0x4e, // 'N'
                0x69, // 'i'
                0x63, // 'c'
                0x65, // 'e'
                0x21, // '!'
        };
        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        HttpResponse<String> httpResponse = http3Connection.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(httpResponse.statusCode()).isEqualTo(200);
        assertThat(httpResponse.body()).isEqualTo("Nice!");
    }

    @Test
    public void testMissingHeaderFrame() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[] {
                // Complete response for Data frame
                0x00, // type Data Frame
                0x05, // payload length
                0x4e, // 'N'
                0x69, // 'i'
                0x63, // 'c'
                0x65, // 'e'
                0x21, // '!'
        };
        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        assertThatThrownBy(
                () -> http3Connection.send(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("(quic stream");
    }

    @Test
    public void testResponseCanHaveTwoHeadersFrame() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[] {
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length (payload omitted for the test, is covered by the mock decoder
                // Complete response for Data frame
                0x00, // type Data Frame
                0x05, // payload length
                0x4e, // 'N'
                0x69, // 'i'
                0x63, // 'c'
                0x65, // 'e'
                0x21, // '!'
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length (payload omitted for the test, is covered by the mock decoder
        };
        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        HttpResponse<String> httpResponse = http3Connection.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(httpResponse.statusCode()).isEqualTo(200);
        assertThat(httpResponse.body()).isEqualTo("Nice!");
        assertThat(httpResponse.headers().map()).containsKeys("Content-Type", "x-whatever");
    }

    @Test
    public void testStreamAbortedInHeadersFrame() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[]{
                // Partial response for Headers frame, to model aborted stream
                0x01, // type Headers Frame
                0x0f, // payload length
        };

        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        assertThatThrownBy(
                () -> http3Connection.send(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(EOFException.class);
    }

    @Test
    public void testStreamAbortedInDataFrame() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[] {
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length
                // Partial response for Data frame, to model aborted stream
                0x00, // type Data Frame
                0x05, // payload length
                0x4e, // 'N'
                0x69, // 'i'
        };
        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        assertThatThrownBy(
                () -> http3Connection.send(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(EOFException.class);
    }

    @Test
    public void noDataFrameMeansEmptyResponseBody() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[]{
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length
        };
        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        HttpResponse<String> response = http3Connection.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.body()).isEmpty();
    }

    @Test
    public void reservedLargeFrameTypeIsIgnored() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[] {
                // Reserved frame type: 3344129617399856696 (0x2E68BC2B47FB3E38) = 3344129617399856675 * 31 + 33
                (byte) (0x2E | 0xc0),  // Variable length integer: 8 bytes => most significant 2 bits are 1
                0x68,
                (byte) 0xBC,
                0x2B,
                0x47,
                (byte) 0xFB,
                0x3E,
                0x38,
                0x00, // Length 0
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length (payload omitted for the test, is covered by the mock decoder
                // Complete response for Data frame
                0x00, // type Data Frame
                0x05, // payload length
                0x4e, // 'N'
                0x69, // 'i'
                0x63, // 'c'
                0x65, // 'e'
                0x21, // '!'
        };
        mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .build();

        HttpResponse<String> httpResponse = http3Connection.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(httpResponse.statusCode()).isEqualTo(200);
        assertThat(httpResponse.body()).isEqualTo("Nice!");
    }

    @Test
    public void testReadFrameTypeFromStream() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);
        long frameType = http3Connection.readFrameType(new ByteArrayInputStream(new byte[] { 0x01, 0x02, 0x03 }));
        assertThat(frameType).isEqualTo(1);
    }

    @Test
    public void readFrameTypeFromClosedStreamShouldReturnNegativeValue() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);
        InputStream inputStream = new ByteArrayInputStream(new byte[]{ 0x01, 0x02, 0x03 });
        inputStream.read(new byte[3]);
        long frameType = http3Connection.readFrameType(inputStream);
        assertThat(frameType).isEqualTo(-1);
    }

    @Test
    public void postRequestEndodesRequestBodyInDataFrame() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        byte[] responseBytes = new byte[]{
                // Partial response for Headers frame, the rest is covered by the mock decoder
                0x01, // type Headers Frame
                0x00, // payload length
        };
        QuicStream http3Stream = mockQuicConnectionWithStreams(http3Connection, responseBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost"))
                .POST(HttpRequest.BodyPublishers.ofString("This is the request body."))
                .build();

        http3Connection.send(request, HttpResponse.BodyHandlers.ofString());

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(http3Stream.getOutputStream(), times(2)).write(captor.capture());
        byte[] dataFrameBytes = captor.getAllValues().get(1);
        assertThat(dataFrameBytes).endsWith("This is the request body.".getBytes());
    }

    @Test
    public void setupConnectOnlyOnce() throws Exception {
        Http3Connection http3Connection = new Http3Connection("localhost", 4433);

        QuicClientConnection quicConnection = mockQuicConnection(http3Connection);
        when(quicConnection.isConnected()).thenReturn(false, true);  // Assuming (knowing) that Http3Connection.connect calls QuicConnection.isConnected once

        http3Connection.connect(10);
        http3Connection.connect(10);

        verify(quicConnection, times(1)).connect(anyInt(), anyString(), nullable(TransportParameters.class), anyList());
    }

    /**
     * Inserts a mock QuicConnection into the given Http3Connection object.
     * The mocked QuicConnection will return the given response bytes as output on the (first) QuicStream that is
     * created on it.
     *
     * @param http3Connection
     * @param response
     * @throws NoSuchFieldException
     * @throws IOException
     * @return
     */
    private QuicStream mockQuicConnectionWithStreams(Http3Connection http3Connection, byte[] response) throws NoSuchFieldException, IOException {
        QuicClientConnection quicConnection = mock(QuicClientConnection.class);
        FieldSetter.setField(http3Connection, Http3Connection.class.getDeclaredField("quicConnection"), quicConnection);

        QuicStream http3StreamMock = mock(QuicStream.class);
        when(quicConnection.createStream(anyBoolean())).thenReturn(http3StreamMock);
        // Create sink to send the http3 request bytes to.
        when(http3StreamMock.getOutputStream()).thenReturn(mock(OutputStream.class));

        // Return given response on QuicStream's input stream
        when(http3StreamMock.getInputStream()).thenReturn(new ByteArrayInputStream(response));

        // To relief caller from duty to assemble a complete and correct (QPack) header payload, the qpackDecoder is
        // mocked to return decent headers.
        Decoder mockedQPackDecoder = mock(Decoder.class);
        FieldSetter.setField(http3Connection, Http3Connection.class.getDeclaredField("qpackDecoder"), mockedQPackDecoder);
        when(mockedQPackDecoder.decodeStream(any(InputStream.class))).thenAnswer(new Answer() {
            private int invocation = 0;

            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                invocation++;
                if (invocation == 1) {
                    return List.of(new AbstractMap.SimpleEntry<>(":status", "200"), new AbstractMap.SimpleEntry<>("Content-Type", "crap"));
                }
                else if (invocation == 2) {
                    return List.of(new AbstractMap.SimpleEntry<>("x-whatever", "true"));
                }
                else {
                    return List.of(new AbstractMap.SimpleEntry<>("header-number", "3"));
                }
            }
        });

        return http3StreamMock;
    }

    private QuicClientConnection mockQuicConnection(Http3Connection http3Connection) throws NoSuchFieldException, IOException {
        QuicClientConnection quicConnection = mock(QuicClientConnection.class);
        FieldSetter.setField(http3Connection, Http3Connection.class.getDeclaredField("quicConnection"), quicConnection);

        QuicStream http3StreamMock = mock(QuicStream.class);
        when(quicConnection.createStream(anyBoolean())).thenReturn(http3StreamMock);
        // Create sink to send the http3 request bytes to.
        when(http3StreamMock.getOutputStream()).thenReturn(mock(OutputStream.class));

        return quicConnection;
    }

}
