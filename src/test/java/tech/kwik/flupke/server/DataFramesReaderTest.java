/*
 * Copyright © 2025 Peter Doornbosch
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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.kwik.flupke.impl.Http3ConnectionImpl.FRAME_TYPE_DATA;

class DataFramesReaderTest {

    @Test
    void testReadSingleByte() throws Exception {
        byte[] inputData = new byte[] { FRAME_TYPE_DATA, 0x01, 0x7f };
        DataFramesReader reader = new DataFramesReader(new ByteArrayInputStream(inputData), 100);

        assertThat(reader.read()).isEqualTo(0x7f);
        assertThat(reader.read()).isEqualTo(-1);
    }

    @Test
    void readDataFromMultipleDataFrames() throws Exception {
        byte[] inputData = new byte[] {
                FRAME_TYPE_DATA, 0x01, (byte) 0xca,
                FRAME_TYPE_DATA, 0x02, (byte) 0xfe, (byte) 0xba,
                FRAME_TYPE_DATA, 0x01, (byte) 0xbe
        };
        DataFramesReader reader = new DataFramesReader(new ByteArrayInputStream(inputData), 100);

        byte[] buffer = new byte[10];
        int offset = 0;
        int read;
        do {
            read = reader.read(buffer, offset, buffer.length - offset);
            if (read > 0) {
                offset += read;
            }
        }
        while (read >= 0);
        byte[] data = Arrays.copyOf(buffer, offset);

        assertThat(data).isEqualTo(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe });
    }

    @Test
    void readDataFromMultipleDataFramesOneByOne() throws Exception {
        byte[] inputData = new byte[] {
                FRAME_TYPE_DATA, 0x01, (byte) 0xca,
                FRAME_TYPE_DATA, 0x02, (byte) 0xfe, (byte) 0xba,
                FRAME_TYPE_DATA, 0x01, (byte) 0xbe
        };
        DataFramesReader reader = new DataFramesReader(new ByteArrayInputStream(inputData), 100);

        byte[] buffer = new byte[10];
        int offset = 0;
        int read;
        do {
            read = reader.read();
            if (read >= 0) {
                buffer[offset] = (byte) read;
                offset ++;
            }
        }
        while (read >= 0);
        byte[] data = Arrays.copyOf(buffer, offset);

        assertThat(data).isEqualTo(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe });
    }

    @Test
    void readPartOfDataFrame() throws Exception {
        byte[] inputData = new byte[] {
                FRAME_TYPE_DATA, 0x04, 0x01, 0x02, 0x03, 0x04
        };
        DataFramesReader reader = new DataFramesReader(new ByteArrayInputStream(inputData), 100);

        byte[] buffer = new byte[2];
        int read = reader.read(buffer);
        assertThat(read).isEqualTo(2);
        assertThat(buffer).isEqualTo(new byte[] { 0x01, 0x02 });

        read = reader.read(buffer);
        assertThat(read).isEqualTo(2);
        assertThat(buffer).isEqualTo(new byte[] { 0x03, 0x04 });

        read = reader.read(buffer);
        assertThat(read).isEqualTo(-1);
    }
}