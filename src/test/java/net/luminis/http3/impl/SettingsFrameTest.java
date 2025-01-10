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
package net.luminis.http3.impl;

import tech.kwik.core.generic.InvalidIntegerEncodingException;
import tech.kwik.core.generic.VariableLengthInteger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


public class SettingsFrameTest {

    @Test
    public void testSerializedFrame() throws InvalidIntegerEncodingException {
        // Given
        SettingsFrame settingsFrame = new SettingsFrame(32, 10);

        // When
        ByteBuffer serializedFrame = settingsFrame.getBytes();
        serializedFrame.rewind();

        // Then
        assertThat(nextLong(serializedFrame)).isEqualTo(0x4L);  // Type
        assertThat(nextLong(serializedFrame)).isEqualTo(4);
        assertThat(nextLong(serializedFrame)).isEqualTo(0x1L);  // QPACK max table capacity
        assertThat(nextLong(serializedFrame)).isEqualTo(32);
        assertThat(nextLong(serializedFrame)).isEqualTo(0x7L);  // QPACK blocked streams
        assertThat(nextLong(serializedFrame)).isEqualTo(10);
        assertThat(serializedFrame.remaining()).isEqualTo(0);
    }

    @Test
    public void testSerializedFrameWithConnectProtocol() throws InvalidIntegerEncodingException {
        // Given
        SettingsFrame settingsFrame = new SettingsFrame(32, 10, true);

        // When
        ByteBuffer serializedFrame = settingsFrame.getBytes();
        serializedFrame.rewind();

        // Then
        assertThat(nextLong(serializedFrame)).isEqualTo(0x4L);  // Type
        assertThat(nextLong(serializedFrame)).isEqualTo(6);
        assertThat(nextLong(serializedFrame)).isEqualTo(0x1L);  // QPACK max table capacity
        assertThat(nextLong(serializedFrame)).isEqualTo(32);
        assertThat(nextLong(serializedFrame)).isEqualTo(0x7L);  // QPACK blocked streams
        assertThat(nextLong(serializedFrame)).isEqualTo(10);
        assertThat(nextLong(serializedFrame)).isEqualTo(0x8L);  // Enable connect protocol
        assertThat(nextLong(serializedFrame)).isEqualTo(1);
        assertThat(serializedFrame.remaining()).isEqualTo(0);
    }

    @Test
    public void largeParameterValueShouldSerializeCorrectly() throws InvalidIntegerEncodingException {
        // Given
        SettingsFrame settingsFrame = new SettingsFrame(1024, 10);

        // When
        ByteBuffer serializedFrame = settingsFrame.getBytes();
        serializedFrame.rewind();

        // Then
        assertThat(nextLong(serializedFrame)).isEqualTo(0x4L);  // Type
        assertThat(nextLong(serializedFrame)).isGreaterThanOrEqualTo(4);
        assertThat(nextLong(serializedFrame)).isEqualTo(0x1L);  // QPACK max table capacity
        assertThat(nextLong(serializedFrame)).isEqualTo(1024);
    }

    @Test
    public void testSerializedCanBeParsedCorrectly() throws IOException {
        // Given
        SettingsFrame settingsFrame = new SettingsFrame(32, 10, true);

        // When
        ByteBuffer serializedFrame = settingsFrame.getBytes();
        serializedFrame.rewind();

        // Then
        SettingsFrame parsedFrame = new SettingsFrame().parsePayload(serializedFrame);
        assertThat(parsedFrame.getQpackMaxTableCapacity()).isEqualTo(32);
        assertThat(parsedFrame.getQpackBlockedStreams()).isEqualTo(10);
        assertThat(parsedFrame.isSettingsEnableConnectProtocol()).isTrue();
    }

    @Test
    public void additionalSettingsAreSerialized() throws InvalidIntegerEncodingException {
        // Given
        SettingsFrame settingsFrame = new SettingsFrame();
        settingsFrame.addAdditionalSettings(Map.of(0xc671706aL, 1L, 0x22L, 2L));

        // When
        ByteBuffer serializedFrame = settingsFrame.getBytes();
        serializedFrame.rewind();

        // Then
        VariableLengthInteger.parse(serializedFrame);  // Skip type
        VariableLengthInteger.parse(serializedFrame);  // Skip length
        Map<Long, Long> parameters = readParameters(serializedFrame);

        assertThat(parameters)
                .containsEntry(0xc671706aL, 1L)
                .containsEntry(0x22L, 2L);
    }

    @Test
    public void additionalSettingsAreParsed() throws Exception {
        // Given
        SettingsFrame originalSettingsFrame = new SettingsFrame();
        originalSettingsFrame.addAdditionalSettings(Map.of(0xc671706aL, 1L, 0x22L, 2L));
        ByteBuffer serializedFrame = originalSettingsFrame.getBytes();
        serializedFrame.rewind();

        // When
        SettingsFrame parsedSettingsFrame = new SettingsFrame();
        parsedSettingsFrame.parsePayload(serializedFrame);

        // Then
        assertThat(parsedSettingsFrame.getParameter(0xc671706aL)).isEqualTo(1L);
        assertThat(parsedSettingsFrame.getParameter(0x22L)).isEqualTo(2L);
    }

    @Test
    public void unknownParameterIsNull() throws Exception {
        // Given
        ByteBuffer serializedFrame = new SettingsFrame().getBytes();
        serializedFrame.rewind();

        // When
        SettingsFrame parsedSettingsFrame = new SettingsFrame();
        parsedSettingsFrame.parsePayload(serializedFrame);

        // Then
        assertThat(parsedSettingsFrame.getParameter(0xc671706aL)).isNull();
    }

    private static Map<Long, Long> readParameters(ByteBuffer serializedFrame) throws InvalidIntegerEncodingException {
        Map<Long, Long> parameters = new HashMap<>();
        while (serializedFrame.remaining() > 0) {
            long identifier = VariableLengthInteger.parseLong(serializedFrame);
            long value = VariableLengthInteger.parseLong(serializedFrame);
            parameters.put(identifier, value);
        }
        return parameters;
    }

    private Long nextLong(ByteBuffer buffer) throws InvalidIntegerEncodingException {
        return VariableLengthInteger.parseLong(buffer);
    }
}
