/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
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

import net.luminis.quic.InvalidIntegerEncodingException;
import net.luminis.quic.VariableLengthInteger;

import java.io.IOException;
import java.nio.ByteBuffer;

// https://www.rfc-editor.org/rfc/rfc9114.html#name-settings
public class SettingsFrame extends Http3Frame {

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-settings
    public static final int SETTINGS_FRAME_TYPE = 0x04;

    // https://www.rfc-editor.org/rfc/rfc9204.html#name-settings-registration
    public static final int QPACK_MAX_TABLE_CAPACITY = 0x01;
    public static final int QPACK_BLOCKED_STREAMS = 0x07;

    // https://www.rfc-editor.org/rfc/rfc9220#name-iana-considerations
    public static final int SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08;

    private int qpackMaxTableCapacity;
    private int qpackBlockedStreams;
    private boolean settingsEnableConnectProtocol;

    public SettingsFrame(int qpackMaxTableCapacity, int qpackBlockedStreams) {
        this(qpackMaxTableCapacity, qpackBlockedStreams, false);
    }

    public SettingsFrame(int qpackMaxTableCapacity, int qpackBlockedStreams, boolean enableConnectProtocol) {
        this.qpackMaxTableCapacity = qpackMaxTableCapacity;
        this.qpackBlockedStreams = qpackBlockedStreams;
        this.settingsEnableConnectProtocol = enableConnectProtocol;
    }

    public SettingsFrame() {
        this(0, 0, false);
    }

    public SettingsFrame parsePayload(ByteBuffer buffer) throws IOException {
        while (buffer.remaining() > 0) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-settings
            // "The payload of a SETTINGS frame consists of zero or more parameters. Each parameter consists of
            //  a setting identifier and a value, both encoded as QUIC variable-length integers."
            int identifier = 0;
            long value = 0;
            try {
                identifier = (int) VariableLengthInteger.parseLong(buffer);
                value = VariableLengthInteger.parseLong(buffer);
                switch (identifier) {
                    // https://tools.ietf.org/html/draft-ietf-quic-qpack-07#section-8.1
                    case QPACK_MAX_TABLE_CAPACITY:
                        qpackMaxTableCapacity = (int) value;
                        break;
                    case QPACK_BLOCKED_STREAMS:
                        qpackBlockedStreams = (int) value;
                        break;
                    // https://www.rfc-editor.org/rfc/rfc9220#name-iana-considerations
                    // "Value: 0x08
                    //  Setting Name: SETTINGS_ENABLE_CONNECT_PROTOCOL"
                    case SETTINGS_ENABLE_CONNECT_PROTOCOL:
                        if (value == 1L) {
                            // https://www.rfc-editor.org/rfc/rfc8441#section-3
                            // "The value of the parameter MUST be 0 or 1."
                            // "Upon receipt of SETTINGS_ENABLE_CONNECT_PROTOCOL with a value of 1, a client MAY use the
                            //  Extended CONNECT as defined in this document when creating new streams."
                            settingsEnableConnectProtocol = true;
                        }
                        break;
                    default:
                        // https://www.rfc-editor.org/rfc/rfc9114.html#name-settings
                        // "An implementation MUST ignore any parameter with an identifier it does not understand."
                }
            }
            catch (InvalidIntegerEncodingException e) {
                throw new IOException(e);
            }
        }
        return this;
    }

    public ByteBuffer getBytes() {
        ByteBuffer serializedParams = ByteBuffer.allocate(3 * 8);

        serializedParams.put((byte) QPACK_MAX_TABLE_CAPACITY);
        VariableLengthInteger.encode(qpackMaxTableCapacity, serializedParams);

        serializedParams.put((byte) QPACK_BLOCKED_STREAMS);
        VariableLengthInteger.encode(qpackBlockedStreams, serializedParams);

        if (settingsEnableConnectProtocol) {
            serializedParams.put((byte) SETTINGS_ENABLE_CONNECT_PROTOCOL);
            VariableLengthInteger.encode(1, serializedParams);
        }

        int paramLength = serializedParams.position();
        ByteBuffer buffer = ByteBuffer.allocate(1 + VariableLengthInteger.bytesNeeded(paramLength) + paramLength);
        buffer.put((byte) SETTINGS_FRAME_TYPE);
        VariableLengthInteger.encode(paramLength, buffer);
        buffer.put(serializedParams.array(), 0, paramLength);
        return buffer;
    }

    public int getQpackMaxTableCapacity() {
        return qpackMaxTableCapacity;
    }

    public int getQpackBlockedStreams() {
        return qpackBlockedStreams;
    }

    public boolean isSettingsEnableConnectProtocol() {
        return settingsEnableConnectProtocol;
    }
}
