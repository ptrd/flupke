/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.test.Http3ClientConnectionBuilder;
import tech.kwik.flupke.test.Http3ConnectionBuilder;
import tech.kwik.qpack.Encoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static tech.kwik.flupke.impl.Http3ConnectionImpl.*;

public class Http3ConnectionImplTest {

    //region register stream type
    @Test
    public void attemptToRegisterDefaultStreamTypeShouldFail() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), false);

        // When
        assertThatThrownBy(() -> connection.registerUnidirectionalStreamType(STREAM_TYPE_PUSH_STREAM, mock(Consumer.class)))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standard");
    }

    @Test
    public void attemptToRegisterReservedStreamTypeShouldFail() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), false);

        // When
        long reservedType = 0x1f * 3 + 0x21;  // 0x1f * N + 0x21 for non-negative integer values of N
        assertThatThrownBy(() -> connection.registerUnidirectionalStreamType(reservedType, mock(Consumer.class)))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }
    //endregion

    //region register handler
    @Test
    public void registeredHandlerShouldBeCalled() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), false);
        ByteBuffer buffer = ByteBuffer.allocate(11);
        connection.registerUnidirectionalStreamType(0x22, stream -> {
            try {
                buffer.put(stream.getInputStream().readNBytes(11));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // When
        byte[] streamData = new byte[12];
        streamData[0] = 0x22; // Type: 0x22
        System.arraycopy("Hello World".getBytes(), 0, streamData, 1, 11);
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream(streamData));

        connection.handleUnidirectionalStream(quicStream);

        // Then
        assertThat(new String(buffer.array())).isEqualTo("Hello World");
    }
    //endregion

    //region control stream handling
    @Test
    public void closingProcessControlStreamShouldLeadToConnectionError() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // When
        connection.processControlStream(new ByteArrayInputStream(new byte[0]));

        // Then
        ArgumentCaptor<Long> errorCaptor = ArgumentCaptor.forClass(Long.class);
        verify(quicConnection).close(errorCaptor.capture(), any());
        assertThat(errorCaptor.getValue()).isEqualTo(H3_CLOSED_CRITICAL_STREAM);
    }

    @Test
    public void settingsFrameLargerThan5KBShouldLeadToConnectionError() throws IOException {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // Payload consists of (valid) unknown settings parameters, each with a unique 8-byte identifier and 8-byte value
        // (values >= 2^30 are encoded as 8-byte variable length integers), so each parameter takes the maximum of 16 bytes
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (long identifier = 0x4000_0000L; payload.size() <= 5 * 1024; identifier++) {
            VariableLengthIntegerUtil.write(identifier, payload);
            VariableLengthIntegerUtil.write(0x4000_0000L, payload);
        }
        ByteArrayOutputStream controlStreamData = new ByteArrayOutputStream();
        VariableLengthIntegerUtil.write(FRAME_TYPE_SETTINGS, controlStreamData);
        VariableLengthIntegerUtil.write(payload.size(), controlStreamData);
        payload.writeTo(controlStreamData);

        // When
        connection.processControlStream(new ByteArrayInputStream(controlStreamData.toByteArray()));

        // Then
        ArgumentCaptor<Long> errorCaptor = ArgumentCaptor.forClass(Long.class);
        verify(quicConnection).close(errorCaptor.capture(), any());
        assertThat(errorCaptor.getValue()).isEqualTo(H3_EXCESSIVE_LOAD);
    }

    @Test
    public void closingExtensionControlStreamShouldNotLeadToConnectionError() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);
        connection.registerUnidirectionalStreamType(0x22, stream -> {});

        // When
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 0x40 }));
        connection.handleUnidirectionalStream(quicStream);

        // Then
        verify(quicConnection, never()).close(anyLong(), any());
    }

    @Test
    public void unknownExtensionControlStreamLeadsToQuicStreamClose() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // When
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 0x22 }));
        connection.handleUnidirectionalStream(quicStream);

        // Then
        verify(quicConnection, never()).close(anyLong(), any());
        verify(quicStream).abortReading(anyLong());
    }
    //endregion

    //region other streams
    @Test
    public void qpackDecoderStreamShouldNotBeClosed() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // When
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { STREAM_TYPE_QPACK_DECODER }));
        connection.handleUnidirectionalStream(quicStream);

        // Then
        verify(quicConnection, never()).close(anyLong(), any());
        verify(quicStream, never()).abortReading(anyLong());
    }

    @Test
    public void streamWithUnsupportedStreamTypeShouldBeDiscarded() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), false);

        byte[] streamData = new byte[] { 0x37 };
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream(streamData));

        // When
        connection.handleUnidirectionalStream(quicStream);

        // Then
        ArgumentCaptor<Long> errorCodeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(quicStream).abortReading(errorCodeCaptor.capture());
        assertThat(errorCodeCaptor.getValue()).isEqualTo(H3_STREAM_CREATION_ERROR);
    }

    @Test
    public void creatingUnidirectionalStreamShouldSendStreamType() throws IOException {
        // Given
        ByteArrayOutputStream output = new ByteArrayOutputStream(100);
        Http3ConnectionImpl connection = new Http3ConnectionBuilder()
                .withUnidirectionalQuicStream(output)
                .build();

        // When
        connection.createUnidirectionalStream(0x23).getOutputStream().write("Hello World".getBytes());

        // Then
        assertThat(output.toByteArray()[0]).isEqualTo((byte) 0x23);
        assertThat(output.toByteArray()[1]).isEqualTo("H".getBytes(StandardCharsets.US_ASCII)[0]);
    }

    @Test
    public void bidirectionalStreamShouldNotApplyFraming() throws IOException {
        // Given
        ByteArrayOutputStream output = new ByteArrayOutputStream(100);
        Http3ConnectionImpl connection = new Http3ConnectionBuilder()
                .withBidirectionalQuicStream(null, output)
                .build();

        // When
        HttpStream bidirectionalStream = connection.createBidirectionalStream();
        bidirectionalStream.getOutputStream().write("Hello World".getBytes());

        // Then
        assertThat(new String(output.toByteArray())).isEqualTo("Hello World");
    }
    //endregion

    //region settings parameters
    @Test
    public void additionalSettingsParametersShouldBeWrittenToControlStream() throws Exception {
        // Given
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        Http3ConnectionImpl connection = new Http3ClientConnectionBuilder()
                .withUnidirectionalQuicStream(controlStreamOutput)
                .build();

        // When
        connection.addSettingsParameter(0x22, 0x33);
        connection.startControlStream();

        // Then
        assertThat(controlStreamOutput.toByteArray()).isEqualTo(new byte[] { 0x00, 0x04, 0x06, 0x01, 0x0, 0x07, 0x0, 0x22, 0x33 });
    }

    @Test
    public void internalSettingsParameterShouldNotBeOverwrittenByCustomValues() throws Exception {
        // Given
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        Http3ConnectionImpl connection = new Http3ClientConnectionBuilder()
                .withUnidirectionalQuicStream(controlStreamOutput)
                .build();

        // When
        ignoreExceptions(() -> connection.addSettingsParameter(SettingsFrame.QPACK_MAX_TABLE_CAPACITY, 0x1b));
        ignoreExceptions(() -> connection.addSettingsParameter(SettingsFrame.QPACK_BLOCKED_STREAMS, 0x1b));

        connection.startControlStream();

        // Then
        assertThat(controlStreamOutput.toByteArray()).isEqualTo(new byte[] { 0x00, 0x04, 0x04, 0x01, 0x0, 0x07, 0x0 });
    }

    @Test
    void localSettingsParameterShouldNotBeReturnedWhenReadingPeersParameters() throws Exception {
        // Given
        Http3ConnectionImpl connection = new Http3ClientConnectionBuilder()
                .withDefaultSettingsFrame()
                .build();

        // When
        connection.addSettingsParameter(0x22, 0x33);

        // Then
        assertThat(connection.getPeerSettingsParameter(0x22)).isNotPresent();
    }

    @Test
    void settingsParameterSendByPeerShouldBeReceived() throws Exception {
        // Given
        Http3ClientConnectionBuilder http3ClientConnectionBuilder = new Http3ClientConnectionBuilder()
                .withDefaultSettingsFrameSupplementedWith(0x22, 0x33);

        // When
        Http3ConnectionImpl connection = http3ClientConnectionBuilder.build();

        // Then
        assertThat(connection.getPeerSettingsParameter(0x22)).isPresent();
        assertThat(connection.getPeerSettingsParameter(0x22).get()).isEqualTo(0x33);
    }
    //endregion

    //region http stream
    @Test
    void httpStreamShouldReturnPayloadOfSingleSmallDataFrame() throws Exception {
        // Given
        byte[] payload = "Hello World".getBytes(StandardCharsets.US_ASCII);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEqualTo(payload);
    }

    @Test
    void httpStreamShouldReturnPayloadOfSingleLargeDataFrame() throws Exception {
        // Given
        byte[] payload = createPayload(100_000);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEqualTo(payload);
    }

    @Test
    void httpStreamShouldReturnConcatenatedPayloadOfMultipleDataFrames() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(5000);
        byte[] payload3 = createPayload(70_000);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload1, payload2, payload3));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEqualTo(concat(payload1, payload2, payload3));
    }

    @Test
    void httpStreamShouldReturnPayloadOfMultipleDataFramesWhenReadInSmallChunks() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(25);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload1, payload2));
        InputStream inputStream = httpStream.getInputStream();

        // When
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[7];
        int count;
        while ((count = inputStream.read(buffer, 0, buffer.length)) >= 0) {
            received.write(buffer, 0, count);
        }

        // Then
        assertThat(received.toByteArray()).isEqualTo(concat(payload1, payload2));
    }

    @Test
    void httpStreamShouldReturnPayloadOfSingleDataFrameWhenReadWithLargerBuffer() throws Exception {
        // Given
        byte[] payload = createPayload(10);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload));
        InputStream inputStream = httpStream.getInputStream();

        // When
        byte[] buffer = new byte[100];
        int count = inputStream.read(buffer);

        // Then
        assertThat(count).isEqualTo(10);
        assertThat(Arrays.copyOf(buffer, count)).isEqualTo(payload);
        assertThat(inputStream.read(buffer)).isEqualTo(-1);
    }

    @Test
    void httpStreamShouldReturnPayloadOfMultipleDataFramesWhenReadWithSmallBuffer() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(25);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload1, payload2));
        InputStream inputStream = httpStream.getInputStream();

        // When
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[7];
        int count;
        while ((count = inputStream.read(buffer)) >= 0) {
            received.write(buffer, 0, count);
        }

        // Then
        assertThat(received.toByteArray()).isEqualTo(concat(payload1, payload2));
    }

    @Test
    void httpStreamShouldReturnPayloadOfMultipleDataFramesWhenReadPerByte() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(300);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload1, payload2));
        InputStream inputStream = httpStream.getInputStream();

        // When
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        int b;
        while ((b = inputStream.read()) != -1) {
            received.write(b);
        }

        // Then
        assertThat(received.toByteArray()).isEqualTo(concat(payload1, payload2));
    }

    @Test
    void httpStreamShouldReturnEndOfStreamWhenThereAreNoDataFrames() throws Exception {
        // Given
        HttpStream httpStream = createHttpStreamWithInput(new byte[0]);

        // When
        int result = httpStream.getInputStream().read();

        // Then
        assertThat(result).isEqualTo(-1);
    }

    @Test
    void httpStreamShouldSkipEmptyDataFrameWhenReadingAllBytes() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(20);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload1, new byte[0], payload2));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEqualTo(concat(payload1, payload2));
    }

    @Test
    void httpStreamShouldSkipEmptyDataFrameWhenReadPerByte() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(20);
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(payload1, new byte[0], payload2));
        InputStream inputStream = httpStream.getInputStream();

        // When
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        int b;
        while ((b = inputStream.read()) != -1) {
            received.write(b);
        }

        // Then
        assertThat(received.toByteArray()).isEqualTo(concat(payload1, payload2));
    }

    @Test
    void httpStreamShouldReturnEndOfStreamWhenOnlyFrameIsEmptyDataFrame() throws Exception {
        // Given
        HttpStream httpStream = createHttpStreamWithInput(dataFrames(new byte[0]));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEmpty();
    }

    @Test
    void httpStreamShouldIgnoreUnknownFrameBetweenDataFrames() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(20);
        byte[] unknownFrame = new byte[] { 0x21, 0x03, 0x01, 0x02, 0x03 };  // Type: reserved, length 3
        HttpStream httpStream = createHttpStreamWithInput(concat(dataFrames(payload1), unknownFrame, dataFrames(payload2)));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEqualTo(concat(payload1, payload2));
    }

    @Test
    void httpStreamShouldIgnoreUnknownFrameBeforeFirstDataFrame() throws Exception {
        // Given
        byte[] payload = createPayload(10);
        byte[] unknownFrame = new byte[] { 0x21, 0x03, 0x01, 0x02, 0x03 };  // Type: reserved, length 3
        HttpStream httpStream = createHttpStreamWithInput(concat(unknownFrame, dataFrames(payload)));

        // When
        byte[] received = httpStream.getInputStream().readAllBytes();

        // Then
        assertThat(received).isEqualTo(payload);
    }

    @Test
    void httpStreamShouldReturnEndOfStreamWhenTrailersHeadersFrameFollowsData() throws Exception {
        // Given
        byte[] payload1 = createPayload(10);
        byte[] payload2 = createPayload(20);
        HttpHeaders trailers = HttpHeaders.of(Map.of("x-checksum", List.of("abc")), (a, b) -> true);
        byte[] trailersFrame = new HeadersFrame(trailers, Map.of()).toBytes(Encoder.newBuilder().build());
        HttpStream httpStream = createHttpStreamWithInput(concat(dataFrames(payload1, payload2), trailersFrame));
        InputStream inputStream = httpStream.getInputStream();

        // When
        byte[] received = inputStream.readAllBytes();

        // Then
        assertThat(received).isEqualTo(concat(payload1, payload2));
        assertThat(inputStream.read()).isEqualTo(-1);
    }
    //endregion

    //region datagram settings
    @Test
    public void whenDatagramIsEnabledSettingsFrameShouldContainH3DatagramParameter() throws Exception {
        // Given
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        Http3ConnectionImpl connection = new Http3ConnectionBuilder()
                .withDatagramEnabled()
                .withUnidirectionalQuicStream(controlStreamOutput)
                .build();

        // When
        connection.startControlStream();

        // Then: stream type | SETTINGS frame type | payload length | QPACK_MAX_TABLE_CAPACITY=0 | QPACK_BLOCKED_STREAMS=0 | SETTINGS_H3_DATAGRAM=1
        SettingsFrame settingsFrame = new SettingsFrame().parsePayload(ByteBuffer.wrap(controlStreamOutput.toByteArray(), 1, controlStreamOutput.size() - 1));
        assertThat(settingsFrame.getAllParameters()).containsEntry((long) SettingsFrame.SETTINGS_H3_DATAGRAM, 1L);
    }

    @Test
    public void whenDatagramIsNotEnabledSettingsFrameShouldNotContainH3DatagramParameter() throws Exception {
        // Given
        ByteArrayOutputStream controlStreamOutput = new ByteArrayOutputStream();
        Http3ConnectionImpl connection = new Http3ConnectionBuilder()
                .withUnidirectionalQuicStream(controlStreamOutput)
                .build();

        // When
        connection.startControlStream();

        // Then: stream type | SETTINGS frame type | payload length | QPACK_MAX_TABLE_CAPACITY=0 | QPACK_BLOCKED_STREAMS=0
        assertThat(controlStreamOutput.toByteArray()).isEqualTo(new byte[] { 0x00, 0x04, 0x04, 0x01, 0x00, 0x07, 0x00 });
    }
    //endregion

    //region register datagram handler
    @Test
    public void registerDatagramHandlerShouldFailWhenDatagramNotEnabled() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // When
        assertThatThrownBy(() -> connection.registerDatagramHandler(0L, data -> {}))
                // Then
                .isInstanceOf(IllegalStateException.class);

        verify(quicConnection, never()).setDatagramHandler(any());
    }

    @Test
    public void registerDatagramHandlerShouldFailForNegativeStreamId() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), true);

        // When
        assertThatThrownBy(() -> connection.registerDatagramHandler(-4L, data -> {}))
                // Then
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void registerDatagramHandlerShouldFailForStreamIdNotMultipleOfFour() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), true);

        // When
        assertThatThrownBy(() -> connection.registerDatagramHandler(3L, data -> {}))
                // Then
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void registeredHandlerShouldBeCalledWhenMatchingDatagramArrives() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);

        List<byte[]> received = new ArrayList<>();
        connection.registerDatagramHandler(8L, received::add);

        ArgumentCaptor<Consumer<byte[]>> quicHandlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(quicConnection).setDatagramHandler(quicHandlerCaptor.capture());

        // When: datagram arrives with quarter stream id 2 (= stream id 8/4) followed by payload
        byte[] datagram = new byte[] { 0x02, 0x01, 0x02, 0x03 };
        quicHandlerCaptor.getValue().accept(datagram);

        // Then
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    public void malformedDatagramShouldTriggerConnectionError() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);

        connection.registerDatagramHandler(8L, data -> {});

        ArgumentCaptor<Consumer<byte[]>> quicHandlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(quicConnection).setDatagramHandler(quicHandlerCaptor.capture());

        // When: datagram with invalid variable-length integer encoding (0xff is not valid as a first byte)
        quicHandlerCaptor.getValue().accept(new byte[] { (byte) 0xff });

        // Then
        ArgumentCaptor<Long> errorCaptor = ArgumentCaptor.forClass(Long.class);
        verify(quicConnection).close(errorCaptor.capture(), any());
        assertThat(errorCaptor.getValue()).isEqualTo(H3_DATAGRAM_ERROR);
    }

    @Test
    public void registerDefaultDatagramHandlerShouldFailWhenDatagramNotEnabled() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // When
        assertThatThrownBy(() -> connection.registerDefaultDatagramHandler((streamId, data) -> true))
                // Then
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void defaultDatagramHandlerShouldBeCalledWhenNoSpecificHandlerMatches() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);

        List<Long> receivedStreamIds = new ArrayList<>();
        List<byte[]> receivedData = new ArrayList<>();
        connection.registerDefaultDatagramHandler((streamId, data) -> {
            receivedStreamIds.add(streamId);
            receivedData.add(data);
            return true;
        });

        ArgumentCaptor<Consumer<byte[]>> quicHandlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(quicConnection).setDatagramHandler(quicHandlerCaptor.capture());

        // When: datagram arrives with quarter stream id 3 (= stream id 12), no specific handler registered
        byte[] datagram = new byte[] { 0x03, 0x0a, 0x0b };
        quicHandlerCaptor.getValue().accept(datagram);

        // Then
        assertThat(receivedStreamIds).containsExactly(12L);
        assertThat(receivedData).hasSize(1);
        assertThat(receivedData.get(0)).containsExactly(0x0a, 0x0b);
    }

    @Test
    public void defaultDatagramHandlerShouldNotBeCalledWhenSpecificHandlerMatches() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);

        List<byte[]> specificReceived = new ArrayList<>();
        connection.registerDatagramHandler(8L, specificReceived::add);

        BiFunction<Long, byte[], Boolean> defaultHandler = mock(BiFunction.class);
        connection.registerDefaultDatagramHandler(defaultHandler);

        ArgumentCaptor<Consumer<byte[]>> quicConnectionHandlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(quicConnection).setDatagramHandler(quicConnectionHandlerCaptor.capture());

        // When: datagram arrives with quarter stream id 2 (= stream id 8), matching specific handler
        byte[] datagram = new byte[] { 0x02, 0x01, 0x02, 0x03 };
        quicConnectionHandlerCaptor.getValue().accept(datagram);

        // Then
        assertThat(specificReceived).hasSize(1);
        verifyNoInteractions(defaultHandler);
    }

    @Test
    public void registeredHandlerShouldNotBeCalledWhenDatagramArrivesForDifferentStreamId() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);

        List<byte[]> received = new ArrayList<>();
        connection.registerDatagramHandler(8L, received::add);

        ArgumentCaptor<Consumer<byte[]>> quicHandlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(quicConnection).setDatagramHandler(quicHandlerCaptor.capture());

        // When: datagram arrives with quarter stream id 3 (= stream id 12), not 8
        byte[] datagram = new byte[] { 0x03, 0x01, 0x02, 0x03 };
        quicHandlerCaptor.getValue().accept(datagram);

        // Then
        assertThat(received).isEmpty();
    }
    //endregion

    //region send datagram
    @Test
    public void sendDatagramShouldForwardToQuicConnectionWhenEnabled() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);

        // When
        connection.sendDatagram(0, new byte[] { 0x01, 0x02, 0x03 });

        // Then
        verify(quicConnection).sendDatagram(any(byte[].class));
    }

    @Test
    public void sendDatagramShouldPrependQuarterStreamIdBeforeData() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, true);
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);

        // When
        connection.sendDatagram(8, new byte[] { 0x01, 0x02 });

        // Then
        verify(quicConnection).sendDatagram(captor.capture());
        byte[] sent = captor.getValue();
        assertThat(sent[0]).isEqualTo((byte) 0x02);  // quarter stream id = 8/4 = 2
        assertThat(sent[1]).isEqualTo((byte) 0x01);
        assertThat(sent[2]).isEqualTo((byte) 0x02);
    }

    @Test
    public void sendDatagramShouldFailWhenDatagramNotEnabled() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection, false);

        // When
        assertThatThrownBy(() -> connection.sendDatagram(0, new byte[] { 0x01 }))
                // Then
                .isInstanceOf(IllegalStateException.class);

        verify(quicConnection, never()).sendDatagram(any());
    }

    @Test
    public void sendDatagramShouldFailForNegativeStreamId() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), true);

        // When
        assertThatThrownBy(() -> connection.sendDatagram(-4, new byte[] { 0x01 }))
                // Then
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void sendDatagramShouldFailForStreamIdNotMultipleOfFour() {
        // Given
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), true);

        // When
        assertThatThrownBy(() -> connection.sendDatagram(3, new byte[] { 0x01 }))
                // Then
                .isInstanceOf(IllegalArgumentException.class);
    }
    //endregion

    //region helper methods
    private HttpStream createHttpStreamWithInput(byte[] input) {
        Http3ConnectionImpl connection = new Http3ConnectionImpl(mock(QuicConnection.class), false);
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream(input));
        return connection.new HttpStreamImpl(quicStream);
    }

    private byte[] dataFrames(byte[]... payloads) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] payload : payloads) {
            output.writeBytes(new DataFrame(payload).toBytes());
        }
        return output.toByteArray();
    }

    private byte[] createPayload(int size) {
        // Include all byte values (also >= 0x80), to detect sign errors
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i * 7);
        }
        return payload;
    }

    private byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.writeBytes(array);
        }
        return output.toByteArray();
    }

    private void ignoreExceptions(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            // Ignore
        }
    }
    //endregion
}
