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
package tech.kwik.flupke.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WriteableByteArrayInputStream extends InputStream {

    final ByteBuffer buffer;
    final ReentrantLock readWriteLock;
    final Condition available;
    private volatile boolean closed;

    public WriteableByteArrayInputStream() {
        buffer = ByteBuffer.allocate(1024);
        buffer.limit(0);
        readWriteLock = new ReentrantLock();
        available = readWriteLock.newCondition();
    }

    @Override
    public int read() throws IOException {
        readWriteLock.lock();
        try {
            while (true) {
                if (buffer.hasRemaining()) {
                    return buffer.get();
                }
                else {
                    if (closed) {
                        return -1;
                    }
                    try {
                        available.await(100, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                }
            }
        }
        finally {
            readWriteLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        readWriteLock.lock();
        try {
            closed = true;
            available.signal();
        }
        finally {
            readWriteLock.unlock();
        }
    }

    public void write(byte[] data) {
        readWriteLock.lock();
        try {
            buffer.mark();  // Remember how far reading got
            buffer.position(buffer.limit());  // Write where we left of
            buffer.limit(buffer.capacity());  // Only limited by capacity
            buffer.put(data);
            buffer.limit(buffer.position());  // Limit how much can be read
            buffer.reset();  // Reset position to mark
            available.signal();
        }
        finally {
            readWriteLock.unlock();
        }
    }
}
