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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PushbackInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VariableLengthIntegerUtilTest {

    @Test
    public void testWrite() throws IOException {
        // Given
        ByteArrayOutputStream stream = new ByteArrayOutputStream(10);

        // When
        VariableLengthIntegerUtil.write(494878333L, stream);

        // Then
        assertThat(stream.size()).isEqualTo(4);
        assertThat(stream.toByteArray()[0]).isEqualTo((byte) 0x9d);
        assertThat(stream.toByteArray()[1]).isEqualTo((byte) 0x7f);
        assertThat(stream.toByteArray()[2]).isEqualTo((byte) 0x3e);
        assertThat(stream.toByteArray()[3]).isEqualTo((byte) 0x7d);
    }

    @Test
    public void testReadWithPushBack() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0x9d, (byte) 0x7f, (byte) 0x3e, (byte) 0x7d };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(494878333L);
        assertThat(stream.available()).isGreaterThanOrEqualTo(4);
        assertThat(stream.read()).isEqualTo(0x9d);
        assertThat(stream.read()).isEqualTo(0x7f);
        assertThat(stream.read()).isEqualTo(0x3e);
        assertThat(stream.read()).isEqualTo(0x7d);
    }

    @Test
    public void testReadWithPushBackOfOneByteValue() throws IOException {
        // Given
        byte[] data = new byte[] { 0x25 };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(37L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfTwoByteValue() throws IOException {
        // Given
        byte[] data = new byte[] { 0x7b, (byte) 0xbd };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(15293L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfFourByteValue() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0x9d, 0x7f, 0x3e, 0x7d };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(494878333L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfEightByteValue() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0xc2, 0x19, 0x7c, 0x5e, (byte) 0xff, 0x14, (byte) 0xe8, (byte) 0x8c };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(151288809941952652L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfMaxValue() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo((1L << 62) - 1);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfValueInTwoBytesWhereOneSuffices() throws IOException {
        // Given
        byte[] data = new byte[] { 0x40, 0x25 };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(37L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfValueInFourBytesWhereOneSuffices() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0x80, 0x00, 0x00, 0x25 };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(37L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfTruncatedValueThrowsAndLeavesStreamIntact() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0x9d, 0x7f };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        assertThatThrownBy(() -> VariableLengthIntegerUtil.peekLong(stream)).isInstanceOf(EOFException.class);

        // Then
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }

    @Test
    public void testReadWithPushBackOfNonMinimalEncodedValue() throws IOException {
        // Given
        byte[] data = new byte[] { (byte) 0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05 };
        PushbackInputStream stream = new PushbackInputStream(new ByteArrayInputStream(data), 8);

        // When
        long value = VariableLengthIntegerUtil.peekLong(stream);

        // Then
        assertThat(value).isEqualTo(5L);
        assertThat(stream.readAllBytes()).isEqualTo(data);
    }
}
