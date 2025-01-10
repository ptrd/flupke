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
package net.luminis.http3.test;

import net.luminis.http3.impl.Http3ConnectionImpl;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class Http3ConnectionBuilder {

    private OutputStream unidirectionalOutputStream;
    private InputStream bidirectionalInputStream;
    private OutputStream bidirectionalOutputStream;

    public Http3ConnectionBuilder withUnidirectionalQuicStream(OutputStream output) {
        unidirectionalOutputStream = output;
        return this;
    }

    public Http3ConnectionImpl build() throws IOException {
        QuicConnection quicConnection = mock(QuicConnection.class);

        if (unidirectionalOutputStream != null) {
            QuicStream unidirectionalStream = mock(QuicStream.class);
            when(unidirectionalStream.getOutputStream()).thenReturn(unidirectionalOutputStream);
            when(quicConnection.createStream(false)).thenReturn(unidirectionalStream);
        }

        if (bidirectionalInputStream !=null || bidirectionalOutputStream != null) {
            QuicStream bidirectionalStream = mock(QuicStream.class);
            when(bidirectionalStream.getInputStream()).thenReturn(bidirectionalInputStream);
            when(bidirectionalStream.getOutputStream()).thenReturn(bidirectionalOutputStream);
            when(quicConnection.createStream(true)).thenReturn(bidirectionalStream);
        }

        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection);

        return connection;
    }

    public Http3ConnectionBuilder withBidirectionalQuicStream(ByteArrayInputStream input, ByteArrayOutputStream output) {
        bidirectionalInputStream = input;
        bidirectionalOutputStream = output;
        return this;
    }
}
