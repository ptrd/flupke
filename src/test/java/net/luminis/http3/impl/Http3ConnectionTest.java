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

import net.luminis.quic.QuicStream;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


public class Http3ConnectionTest {

    @Test
    public void testServerSettingsFrameIsProcessed() throws IOException {
        Http3Connection http3Connection = new Http3Connection("www.example.com",4433);

        InputStream serverControlInputStream = new ByteArrayInputStream(new byte[]{
                0x00,  // type: control stream
                0x04,  // frame type: SETTINGS
                0x04,  // payload length
                // frame payload
                0x01, // identifier: SETTINGS_QPACK_MAX_TABLE_CAPACITY
                32,   // value
                0x07, // identifier: SETTINGS_QPACK_BLOCKED_STREAMS
                16    // value
        });

        QuicStream mockedServerControlStream = Mockito.mock(QuicStream.class);
        when(mockedServerControlStream.getInputStream()).thenReturn(serverControlInputStream);
        http3Connection.registerServerInitiatedStream(mockedServerControlStream);

        assertThat(http3Connection.getServerQpackMaxTableCapacity()).isEqualTo(32);
        assertThat(http3Connection.getServerQpackBlockedStreams()).isEqualTo(16);
    }
}
