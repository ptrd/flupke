/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.impl;

import tech.kwik.core.generic.InvalidIntegerEncodingException;
import tech.kwik.core.generic.VariableLengthInteger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

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
    private Map<Long, Long> settings;

    public SettingsFrame(int qpackMaxTableCapacity, int qpackBlockedStreams) {
        this(qpackMaxTableCapacity, qpackBlockedStreams, false);
    }

    public SettingsFrame(int qpackMaxTableCapacity, int qpackBlockedStreams, boolean enableConnectProtocol) {
        this.qpackMaxTableCapacity = qpackMaxTableCapacity;
        this.qpackBlockedStreams = qpackBlockedStreams;
        this.settingsEnableConnectProtocol = enableConnectProtocol;
        settings = new HashMap<>();
        settings.put((long) QPACK_MAX_TABLE_CAPACITY, (long) qpackMaxTableCapacity);
        settings.put((long) QPACK_BLOCKED_STREAMS, (long) qpackBlockedStreams);
        settings.put((long) SETTINGS_ENABLE_CONNECT_PROTOCOL, enableConnectProtocol ? 1L : 0L);
    }

    public SettingsFrame() {
        this(0, 0, false);
    }

    public SettingsFrame parsePayload(ByteBuffer buffer) throws IOException {
        while (buffer.remaining() > 0) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-settings
            // "The payload of a SETTINGS frame consists of zero or more parameters. Each parameter consists of
            //  a setting identifier and a value, both encoded as QUIC variable-length integers."
            try {
                long identifier = VariableLengthInteger.parseLong(buffer);
                long value = VariableLengthInteger.parseLong(buffer);
                if (identifier == QPACK_MAX_TABLE_CAPACITY) {
                    qpackMaxTableCapacity = (int) value;
                }
                else if (identifier == QPACK_BLOCKED_STREAMS) {
                    qpackBlockedStreams = (int) value;
                }
                else if (identifier == SETTINGS_ENABLE_CONNECT_PROTOCOL) {
                    // https://www.rfc-editor.org/rfc/rfc9220#name-iana-considerations
                    // "Value: 0x08
                    //  Setting Name: SETTINGS_ENABLE_CONNECT_PROTOCOL"
                    if (value == 1L) {
                        // https://www.rfc-editor.org/rfc/rfc8441#section-3
                        // "The value of the parameter MUST be 0 or 1."
                        // "Upon receipt of SETTINGS_ENABLE_CONNECT_PROTOCOL with a value of 1, a client MAY use the
                        //  Extended CONNECT as defined in this document when creating new streams."
                        settingsEnableConnectProtocol = true;
                    }
                }
                settings.put(identifier, value);
            }
            catch (InvalidIntegerEncodingException e) {
                throw new IOException(e);
            }
        }
        return this;
    }

    public ByteBuffer getBytes() {
        int nrOfParams = settings.size();
        int maxPossibleSize = nrOfParams * 2 * 8;
        ByteBuffer serializedParams = ByteBuffer.allocate(maxPossibleSize);

        settings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    // Only write the SETTINGS_ENABLE_CONNECT_PROTOCOL parameter if it is set to 1.
                    if (entry.getKey() != SETTINGS_ENABLE_CONNECT_PROTOCOL || entry.getValue() == 1) {
                        VariableLengthInteger.encode(entry.getKey(), serializedParams);
                        VariableLengthInteger.encode(entry.getValue(), serializedParams);
                    }
                });

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

    public void addParameters(Map<Long, Long> settingsParameters) {
        this.settings.putAll(settingsParameters);
    }

    public Long getParameter(long identifier) {
        return settings.get(identifier);
    }

    public Map<Long, Long> getAllParameters() {
        return settings;
    }

    public void putParameter(long identifier, long value) {
        settings.put(identifier, value);
    }
}
