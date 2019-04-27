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


// https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.1
public class DataFrame extends Http3Frame {

    private byte[] payload;

    public DataFrame() {
        payload = new byte[0];
    }

    public byte[] toBytes() {
        int length = 1 + 1 + payload.length;
        byte[] data = new byte[length];
        data[0] = 0x00;
        data[1] = (byte) payload.length;
        System.arraycopy(payload, 0, data, 2, payload.length);
        return data;
    }


    public DataFrame parsePayload(byte[] payload) {
        this.payload = payload;
        return this;
    }

    byte[] getData() {
        return payload;
    }
}
