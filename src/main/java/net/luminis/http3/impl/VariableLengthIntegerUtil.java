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

import net.luminis.quic.VariableLengthInteger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Util methods for Variable Length Integer; should ultimately be moved to Kwik library.
 */
public class VariableLengthIntegerUtil {

    public static void write(long value, OutputStream outputStream) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(8);
        int numberOfBytes = VariableLengthInteger.encode(value, buffer);
        buffer.flip();
        for (int i = 0; i < numberOfBytes; i++) {
            outputStream.write(buffer.get());
        }
    }
}
