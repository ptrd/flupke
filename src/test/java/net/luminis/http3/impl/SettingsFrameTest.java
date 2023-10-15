/*
 * Copyright © 2023 Peter Doornbosch
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

import net.luminis.quic.InvalidIntegerEncodingException;
import net.luminis.quic.VariableLengthInteger;
import org.junit.Test;

import java.nio.ByteBuffer;

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
    public void testSerializedCanBeParsedCorrectly() {
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

    private Long nextLong(ByteBuffer buffer) throws InvalidIntegerEncodingException {
        return VariableLengthInteger.parseLong(buffer);
    }
}
