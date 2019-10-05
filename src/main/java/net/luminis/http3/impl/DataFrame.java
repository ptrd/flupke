/*
 * Copyright © 2019 Peter Doornbosch
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
import java.nio.ByteBuffer;


// https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.1
public class DataFrame extends Http3Frame {

    private ByteBuffer payload;

    public DataFrame() {
        payload = ByteBuffer.allocate(0);
    }

    public DataFrame(byte[] payload) {
        this.payload = ByteBuffer.wrap(payload);
    }

    public DataFrame(ByteBuffer payload) {
        this.payload = payload;
    }

    public byte[] toBytes() {
        int payloadLength = payload.limit() - payload.position();
        ByteBuffer lengthBuffer = ByteBuffer.allocate(8);
        int varIntLength = VariableLengthInteger.encode(payloadLength, lengthBuffer);
        int dataLength = 1 + varIntLength + payloadLength;
        byte[] data = new byte[dataLength];
        data[0] = 0x00;
        lengthBuffer.flip();  // Prepare for reading length.
        lengthBuffer.get(data, 1, varIntLength);
        payload.get(data, 1 + varIntLength, payloadLength);
        return data;
    }

    public DataFrame parsePayload(byte[] payload) {
        this.payload = ByteBuffer.wrap(payload);
        return this;
    }

    @Override
    public String toString() {
        int payloadLength = payload.limit() - payload.position();
        return "DataFrame[" + payloadLength + "]";
    }
}
