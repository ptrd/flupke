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
package net.luminis.http3.test;

import net.luminis.http3.impl.Http3ConnectionImpl;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;

import java.io.OutputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class Http3ConnectionBuilder {

    private OutputStream unidirectionalOutputStream;

    public Http3ConnectionBuilder withUnidirectionalQuicStream(OutputStream output) {
        unidirectionalOutputStream = output;
        return this;
    }

    public Http3ConnectionImpl build() {
        QuicConnection quicConnection = mock(QuicConnection.class);

        if (unidirectionalOutputStream != null) {
            QuicStream unidirectionalStream = mock(QuicStream.class);
            when(unidirectionalStream.getOutputStream()).thenReturn(unidirectionalOutputStream);
            when(quicConnection.createStream(false)).thenReturn(unidirectionalStream);
        }

        Http3ConnectionImpl connection = new Http3ConnectionImpl(quicConnection);

        return connection;
    }
}
