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
package net.luminis.http3.webtransport.impl;

import net.luminis.http3.core.Capsule;
import net.luminis.quic.generic.VariableLengthInteger;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class CloseWebtransportSessionCapsule implements Capsule {

    private final int applicationErrorCode;
    private final String applicationErrorMessage;

    protected CloseWebtransportSessionCapsule(InputStream inputStream) throws IOException {
        int capsuleType = VariableLengthInteger.parse(inputStream);
        int capsuleLength = VariableLengthInteger.parse(inputStream);
        byte[] data = new byte[capsuleLength];
        int n = inputStream.readNBytes(data, 0, capsuleLength);
        if (n != capsuleLength) {
            throw new EOFException("Unexpected end of stream");
        }
        ByteBuffer capsuleData = ByteBuffer.wrap(data);
        applicationErrorCode = capsuleData.getInt();
        applicationErrorMessage = new String(capsuleData.array(), capsuleData.position(), capsuleData.remaining());
    }

    public CloseWebtransportSessionCapsule(int applicationErrorCode, String errorMessage) {
        if (errorMessage.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new IllegalArgumentException("Error message must not be longer than 1024 bytes");
        }
        this.applicationErrorCode = applicationErrorCode;
        this.applicationErrorMessage = errorMessage;
    }

    public int getApplicationErrorCode() {
        return applicationErrorCode;
    }

    public String getApplicationErrorMessage() {
        return applicationErrorMessage;
    }

    @Override
    public String toString() {
        return String.format("CloseWebtransportSessionCapsule[%d,%s]", applicationErrorCode, applicationErrorMessage);
    }

    @Override
    public int write(OutputStream outputStream) throws IOException {
        byte[] msgBytes = applicationErrorMessage.getBytes(StandardCharsets.UTF_8);
        int payloadLength = 4 + msgBytes.length;
        int totalLength = VariableLengthInteger.bytesNeeded(0x2843) + VariableLengthInteger.bytesNeeded(payloadLength) + payloadLength;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        VariableLengthInteger.encode(0x2843, buffer);
        VariableLengthInteger.encode(payloadLength, buffer);
        buffer.putInt(applicationErrorCode);
        buffer.put(msgBytes);
        // Write to output stream in one operation, to avoid multiple data frames.
        outputStream.write(buffer.array(), 0, buffer.position());
        return buffer.position();
    }

    @Override
    public long getType() {
        return 0x2843;
    }
}
