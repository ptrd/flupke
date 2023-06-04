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

import net.luminis.http3.server.HttpError;
import net.luminis.quic.QuicConnection;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class Http3ConnectionImplTest {

    @Test
    public void readFrameShouldThrowErrorWhenDataFrameTooLarge() {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection);
        byte[] data = new byte[10000];
        data[0] = 0x00; // Type: Data frame
        data[1] = 0x4f; // Length: 0x4fff = 4095
        data[2] = (byte) 0xff;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        // When
        assertThatThrownBy(() ->
                connection.readFrame(inputStream, Long.MAX_VALUE, 4094))
                .isInstanceOf(HttpError.class)
                .hasMessageContaining("max data");
    }

    @Test
    public void unknownFrameIsIgnored() throws IOException, HttpError {
        // Given
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection);
        byte[] data = new byte[100];
        data[0] = 0x21; // Type: reserved
        data[1] = 0x09; // Length: 9
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        // When
        Http3Frame frame = connection.readFrame(inputStream, Long.MAX_VALUE, 4094);

        // Then
        assertThat(frame).isInstanceOf(UnknownFrame.class);
        assertThat(inputStream.available()).isEqualTo(89);
    }
}