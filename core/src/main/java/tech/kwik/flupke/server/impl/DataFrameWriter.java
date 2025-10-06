/*
 * Copyright © 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.server.impl;

import tech.kwik.flupke.impl.DataFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Writes data as DataFrame's to the given output stream.
 * To limit frame overhead (2 ~ 3 bytes), write large blocks at a time.
 */
public class DataFrameWriter extends OutputStream {

    private final OutputStream outputStream;
    private long bytesWritten;

    public DataFrameWriter(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(int b) throws IOException {
        outputStream.write(new DataFrame(new byte[] { (byte) b }).toBytes());
        bytesWritten += 1;
    }

    @Override
    public void write(byte[] b) throws IOException {
        outputStream.write(new DataFrame(b).toBytes());
        bytesWritten += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ByteBuffer data = ByteBuffer.wrap(b);
        data.position(off);
        data.limit(len);
        outputStream.write(new DataFrame(data).toBytes());
        bytesWritten += len;
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    public long getBytesWritten() {
        return bytesWritten;
    }
}
