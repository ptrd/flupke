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
import tech.kwik.flupke.impl.ConnectionError;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

import static tech.kwik.flupke.impl.Http3ConnectionImpl.FRAME_TYPE_DATA;
import static tech.kwik.flupke.impl.Http3ConnectionImpl.H3_FRAME_ERROR;

public class DataFramesReader extends InputStream {

    private final PushbackInputStream dataFramesStream;
    private long remainingDataFrameContent;
    private ConnectionError dataFramesStreamException;

    public DataFramesReader(InputStream inputStream, long maxDataSize) {
        if (inputStream instanceof PushbackInputStream) {
            this.dataFramesStream = (PushbackInputStream) inputStream;
        }
        else {
            this.dataFramesStream = new PushbackInputStream(inputStream);
        }
    }

    private boolean checkForMoreData() {
        if (remainingDataFrameContent == 0) {
            try {
                int read = dataFramesStream.read();
                if (read == -1) {
                    // End of file, so no more data.
                    return false;
                }
                dataFramesStream.unread(read);
                long type = VariableLengthInteger.parseLong(dataFramesStream);
                long frameLength = VariableLengthInteger.parseLong(dataFramesStream);

                if (type != FRAME_TYPE_DATA) {
                    // TODO: handle other frame types
                }
                remainingDataFrameContent = frameLength;
            }
            catch (IOException e) {
                // https://www.rfc-editor.org/rfc/rfc9114.html#section-7.1
                // "Each frame's payload MUST contain exactly the fields identified in its description. A frame payload
                //  that contains additional bytes after the identified fields or a frame payload that terminates before
                //  the end of the identified fields MUST be treated as a connection error of type H3_FRAME_ERROR. "
                dataFramesStreamException = new ConnectionError(H3_FRAME_ERROR);
                return false;
            }
        }
        return remainingDataFrameContent > 0;
    }

    public InputStream getDataFramesStream() {
        return this;
    }

    @Override
    public int read() throws IOException {
        if (!checkForMoreData()) {
            return -1;
        }
        int data = dataFramesStream.read();
        if (data == -1) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#section-7.1
            // "A frame payload (...) that terminates before the end of the identified fields MUST be treated as a connection error of type H3_FRAME_ERROR. "
            dataFramesStreamException = new ConnectionError(H3_FRAME_ERROR);
            return -1;
        }
        remainingDataFrameContent--;
        return data;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (!checkForMoreData()) {
            return -1;
        }
        assert remainingDataFrameContent > 0;

        int read;
        if (remainingDataFrameContent < len) {
            read = dataFramesStream.read(b, off, (int) remainingDataFrameContent);
        }
        else {
            read = dataFramesStream.read(b, off, len);
        }
        if (read == -1) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#section-7.1
            // "A frame payload (...) that terminates before the end of the identified fields MUST be treated as a connection error of type H3_FRAME_ERROR. "
            dataFramesStreamException = new ConnectionError(H3_FRAME_ERROR);
            return -1;
        }
        remainingDataFrameContent -= read;
        return read;
    }

    public void checkForConnectionError() throws ConnectionError {
        if (dataFramesStreamException != null) {
            throw dataFramesStreamException;
        }
    }
}
