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
package tech.kwik.flupke.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class WriteableByteArrayInputStreamTest {

    @Test
    void shouldBeAbleToReadDataThatIsWritten() throws IOException {
        WriteableByteArrayInputStream stream = new WriteableByteArrayInputStream();
        stream.write(new byte[] { 0x01, 0x02, 0x03, 0x04 });

        byte[] data = new byte[4];
        stream.read(data);
        assertThat(data).isEqualTo(new byte[] { 0x01, 0x02, 0x03, 0x04 });
    }

    @Test
    void shouldBeAbleToReadMoreDataThatIsWrittenLater() throws IOException {
        // Given
        WriteableByteArrayInputStream stream = new WriteableByteArrayInputStream();
        stream.write(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        byte[] data = new byte[4];
        stream.read(data);

        // When
        stream.write(new byte[] { 0x07, 0x08, 0x09 });
        byte[] moreData = new byte[3];
        stream.read(moreData);

        assertThat(moreData).isEqualTo(new byte[] { 0x07, 0x08, 0x09 });
    }

    @Test
    void readOperationShouldTerminateWhenStreamClosed() throws IOException {
        WriteableByteArrayInputStream stream = new WriteableByteArrayInputStream();
        stream.write(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        stream.close();

        byte[] data = stream.readAllBytes();
        assertThat(data).isEqualTo(new byte[] { 0x01, 0x02, 0x03, 0x04 });
    }
}
