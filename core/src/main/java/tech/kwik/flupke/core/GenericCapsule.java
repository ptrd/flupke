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
package tech.kwik.flupke.core;

import tech.kwik.core.generic.VariableLengthInteger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class GenericCapsule implements Capsule {

    private long type;
    private long length;
    private byte[] value;

    public GenericCapsule(long type, byte[] value) {
        this.type = type;
        this.length = value.length;
        this.value = value;
    }

    @Override
    public int write(OutputStream outputStream) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + value.length);
        VariableLengthInteger.encode(type, buffer);
        VariableLengthInteger.encode(length, buffer);
        buffer.put(value);
        // Write to output stream in one operation, to avoid multiple data frames.
        outputStream.write(buffer.array(), 0, buffer.position());
        return buffer.position();
    }

    @Override
    public long getType() {
        return type;
    }

    public long getLength() {
        return length;
    }

    public byte[] getData() {
        return value;
    }

    @Override
    public String toString() {
        return "Capsule[type=" + type + ", length=" + length + "]";
    }
}
