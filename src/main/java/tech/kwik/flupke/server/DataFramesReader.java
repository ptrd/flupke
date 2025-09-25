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
import java.io.UncheckedIOException;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

import static tech.kwik.flupke.impl.Http3ConnectionImpl.FRAME_TYPE_DATA;
import static tech.kwik.flupke.impl.Http3ConnectionImpl.H3_FRAME_ERROR;

public class DataFramesReader extends InputStream {

    private final PushbackInputStream dataFramesStream;
    private final long maxDataSize;
    private long remainingDataFrameContent;
    private ConnectionError dataFramesStreamException;
    private long totalDataRead;
    private BiConsumer<Long, PushbackInputStream> nonDataFrameHandler = this::handleNonDataFrame;
    private LongConsumer gotDataFrameCallback = (x) -> {};

    public DataFramesReader(InputStream inputStream, long maxDataSize) {
        this.maxDataSize = maxDataSize;
        if (inputStream instanceof PushbackInputStream) {
            this.dataFramesStream = (PushbackInputStream) inputStream;
        }
        else {
            this.dataFramesStream = new PushbackInputStream(inputStream);
        }
    }

    public DataFramesReader(InputStream input, long maxData, BiConsumer<Long, PushbackInputStream> nonDataFrameHandler,
                            LongConsumer dataFrameCallback) {
        this(input, maxData);
        this.nonDataFrameHandler = nonDataFrameHandler;
        this.gotDataFrameCallback = dataFrameCallback;
    }

    private boolean checkForMoreData() {
        if (remainingDataFrameContent == 0) {
            try {
                long frameType;
                do {
                    int read = dataFramesStream.read();
                    if (read == -1) {
                        // End of file, so no more data.
                        return false;
                    }
                    dataFramesStream.unread(read);

                    frameType = VariableLengthInteger.parseLong(dataFramesStream);
                    if (frameType != FRAME_TYPE_DATA) {
                        nonDataFrameHandler.accept(frameType, dataFramesStream);
                    }
                }
                while (frameType != FRAME_TYPE_DATA);
                long frameLength = VariableLengthInteger.parseLong(dataFramesStream);
                gotDataFrameCallback.accept(frameLength);
                remainingDataFrameContent = frameLength;
            }
            catch (IOException | UncheckedIOException e) {
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

    private void handleNonDataFrame(long frameType, PushbackInputStream dataFramesStream) {
        try {
            long frameLength = VariableLengthInteger.parseLong(dataFramesStream);
            // https://www.rfc-editor.org/rfc/rfc9114.html#section-7.2.8
            // "These frames have no semantics, and they MAY be sent on any stream where frames are allowed to be sent. "
            // https://www.rfc-editor.org/rfc/rfc9114.html#section-9
            // "Implementations MUST ignore unknown or unsupported values in all extensible protocol elements."
            dataFramesStream.skip(frameLength);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream getDataFramesStream() {
        return this;
    }

    @Override
    public int read() throws IOException {
        checkMaxData();
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
        totalDataRead++;
        return data;
    }

    private void checkMaxData() throws IOException {
        if (maxDataSize > 0 && totalDataRead >= maxDataSize) {
            throw new IOException("Maximum data size exceeded");
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkMaxData();
        if (!checkForMoreData()) {
            return -1;
        }
        assert remainingDataFrameContent > 0;

        int read;
        int max = (maxDataSize - totalDataRead > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) (maxDataSize - totalDataRead);
        if (remainingDataFrameContent < len) {
            read = dataFramesStream.read(b, off, Integer.min((int) remainingDataFrameContent, max));
        }
        else {
            read = dataFramesStream.read(b, off, Integer.min(len, max));
        }
        if (read == -1) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#section-7.1
            // "A frame payload (...) that terminates before the end of the identified fields MUST be treated as a connection error of type H3_FRAME_ERROR. "
            dataFramesStreamException = new ConnectionError(H3_FRAME_ERROR);
            return -1;
        }
        remainingDataFrameContent -= read;
        totalDataRead += read;
        return read;
    }

    @Override
    public void close() throws IOException {
        dataFramesStream.close();
    }

    public void checkForConnectionError() throws ConnectionError {
        if (dataFramesStreamException != null) {
            throw dataFramesStreamException;
        }
    }
}
