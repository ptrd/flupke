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

import net.luminis.quic.generic.InvalidIntegerEncodingException;
import net.luminis.quic.generic.VariableLengthInteger;

import java.nio.ByteBuffer;

// https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.5
public class SettingsFrame extends Http3Frame {

    // https://www.rfc-editor.org/rfc/rfc9220#name-iana-considerations
    public static final int SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08;

    private int qpackMaxTableCapacity;
    private int qpackBlockedStreams;
    private boolean settingsEnableConnectProtocol;

    public SettingsFrame(int qpackMaxTableCapacity, int qpackBlockedStreams) {
        this.qpackMaxTableCapacity = qpackMaxTableCapacity;
        this.qpackBlockedStreams = qpackBlockedStreams;
    }

    public SettingsFrame() {
    }

    public SettingsFrame parsePayload(ByteBuffer buffer) {
        while (buffer.remaining() > 0) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-settings
            // "The payload of a SETTINGS frame consists of zero or more parameters. Each parameter consists of
            //  a setting identifier and a value, both encoded as QUIC variable-length integers."
            int identifier = 0;
            long value = 0;
            try {
                identifier = (int) VariableLengthInteger.parseLong(buffer);
                value = VariableLengthInteger.parseLong(buffer);
            } catch (InvalidIntegerEncodingException e) {
                // TODO: if this happens, we can close/abort this connection
            }
            switch (identifier) {
                // https://tools.ietf.org/html/draft-ietf-quic-qpack-07#section-8.1
                case 0x01:
                    qpackMaxTableCapacity = (int) value;
                    break;
                case 0x07:
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
        return this;
    }

    public ByteBuffer getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.put((byte) 0x04);  // Frame Type (var int)
        buffer.put((byte) 0x00);  // Payload length (var int) (placeholder)

        buffer.put((byte) 0x01);  // Identifier (var int) QPACK_MAX_TABLE_CAPACITY  (https://tools.ietf.org/html/draft-ietf-quic-qpack-08#section-8.1)
        VariableLengthInteger.encode(qpackMaxTableCapacity, buffer);

        buffer.put((byte) 0x07);  // Identifier (var int) QPACK_BLOCKED_STREAMS  (https://tools.ietf.org/html/draft-ietf-quic-qpack-08#section-8.1)
        VariableLengthInteger.encode(qpackMaxTableCapacity, buffer);

        int length = buffer.position() - 2;
        buffer.put(1, (byte) length);
        buffer.limit(buffer.position());
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
