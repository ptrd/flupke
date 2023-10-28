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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;

import static org.assertj.core.api.Assertions.assertThat;

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
}
