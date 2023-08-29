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

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;


public class DataFrameTest {

    @Test
    public void dataFrameStartWithFrameType() {
        byte[] frameBytes = new DataFrame().toBytes();

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.1: DATA frames (type=0x0) ....
        assertThat(frameBytes).startsWith(0x00);
    }

    @Test
    public void emptyDataFrameContainsTypeAndLength() {
        byte[] frameBytes = new DataFrame().toBytes();

        assertThat(frameBytes).containsExactly(0x00, 0x00);
    }

    @Test
    public void dataFrameWithShortPayloadShouldHaveOneBytePayloadLength() {
        byte[] frameBytes = new DataFrame("hello world".getBytes()).toBytes();

        assertThat(frameBytes).startsWith(0x00, 11);
        assertThat(frameBytes).endsWith("hello world".getBytes());
        assertThat(frameBytes.length).isEqualTo(1 + 1 + 11);
    }

    @Test
    public void dataFrameWithLongerPayloadShouldHaveTwoBytesPayloadLength() {
        String content = "01234567890123456789012345678901234567890123456789012345678901234567890123456789";
        byte[] frameBytes = new DataFrame(content.getBytes()).toBytes();

        assertThat(frameBytes).startsWith(0x00, 0x40, 80);
        assertThat(frameBytes.length).isEqualTo(1 + 2 + 80);
    }

    @Test
    public void testCreatePayloadFromSlicedBuffer() {
        // Given
        byte[] rawPayload = new byte[] { 0x00, 0x00, 0x00, (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, 0x00, 0x00, 0x00 };
        ByteBuffer buffer = ByteBuffer.wrap(rawPayload);
        buffer.position(3);
        buffer.limit(7);
        ByteBuffer slice = buffer.slice();

        // When
        DataFrame dataFrame = new DataFrame(slice);

        // Then
        byte[] payload = dataFrame.getPayload();
        assertThat(payload).isEqualTo(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe });
        // And second call gives same result
        payload = dataFrame.getPayload();
        assertThat(payload).isEqualTo(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe });
    }

    @Test
    public void testCreatePayloadFromBuffer() {
        // Given
        byte[] rawPayload = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe };
        ByteBuffer buffer = ByteBuffer.wrap(rawPayload);

        // When
        DataFrame dataFrame = new DataFrame(buffer);

        // Then
        byte[] payload = dataFrame.getPayload();
        assertThat(payload).isEqualTo(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe });
    }

    @Test
    public void testParseFrame() throws Exception {
        // Given
        byte[] rawFrame = new byte[] { 0x00, 0x04, (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe };

        // When
        DataFrame dataFrame = new DataFrame().parse(rawFrame);

        // Then
        byte[] payload = dataFrame.getPayload();
        assertThat(payload).isEqualTo(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe });
    }

    @Test
    public void repeatedlyCallingToBytesShouldReturnSameResult() {
        // Given
        DataFrame dataFrame = new DataFrame("hello world".getBytes());

        // When
        byte[] frameBytes1 = dataFrame.toBytes();
        byte[] frameBytes2 = dataFrame.toBytes();

        // Then
        assertThat(frameBytes1).isEqualTo(frameBytes2);
    }

    @Test
    public void testDataFrameLength() {
        // Given
        byte[] rawData = new byte[100];
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        buffer.get(new byte[10]);
        ByteBuffer data = buffer.slice();

        // When
        DataFrame dataFrame = new DataFrame(data);

        // Then
        assertThat(dataFrame.getDataLength()).isEqualTo(90);
    }
}
