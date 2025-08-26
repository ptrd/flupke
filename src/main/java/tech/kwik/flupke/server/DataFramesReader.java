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

import tech.kwik.core.generic.VariableLengthInteger;

import java.io.IOException;
import java.io.InputStream;

import static tech.kwik.flupke.impl.Http3ConnectionImpl.FRAME_TYPE_DATA;

public class DataFramesReader extends InputStream {

    private final InputStream dataFramesStream;
    private long remainingDataFrameContent;

    public DataFramesReader(InputStream inputStream, long maxDataSize) {
        this.dataFramesStream = inputStream;
        checkData();
    }

    private void checkData() {
        if (remainingDataFrameContent == 0) {
            try {
                long type = VariableLengthInteger.parseLong(dataFramesStream);
                long frameLength = VariableLengthInteger.parseLong(dataFramesStream);

                if (type != FRAME_TYPE_DATA) {
                    throw new IOException("Expected DATA frame, got frame type " + type);
                }
                remainingDataFrameContent = frameLength;
            }
            catch (IOException e) {
                // End of file, so no more data.
                return;
            }
        }
    }

    public InputStream getDataFramesStream() {
        return this;
    }

    @Override
    public int read() throws IOException {
        checkData();
        if (remainingDataFrameContent <= 0) {
            return -1;
        }
        remainingDataFrameContent--;
        return dataFramesStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkData();
        if (remainingDataFrameContent <= 0) {
            return -1;
        }
        if (remainingDataFrameContent < len) {
            int read = dataFramesStream.read(b, off, (int) remainingDataFrameContent);
            remainingDataFrameContent -= read;
            return read;
        }
        else {
            remainingDataFrameContent -= len;
            return dataFramesStream.read(b, off, len);
        }
    }
}
