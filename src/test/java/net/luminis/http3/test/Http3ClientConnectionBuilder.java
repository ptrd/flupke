/*
 * Copyright © 2023, 2024 Peter Doornbosch
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

import net.luminis.http3.impl.Http3ClientConnectionImpl;
import net.luminis.http3.impl.Http3ConnectionImpl;
import net.luminis.http3.impl.SettingsFrame;
import net.luminis.qpack.Decoder;
import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.List;

import static net.luminis.http3.impl.Http3ConnectionImpl.STREAM_TYPE_CONTROL_STREAM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class Http3ClientConnectionBuilder {

    private OutputStream unidirectionalOutputStream;
    private QuicConnection quicConnection;
    private SettingsFrame settingsFrame;
    private InputStream bidirectionalInputStream;
    private OutputStream bidirectionalOutputStream;

    public Http3ClientConnectionBuilder withUnidirectionalQuicStream(OutputStream output) {
        unidirectionalOutputStream = output;
        return this;
    }

    public Http3ClientConnectionBuilder withQuicConnection(QuicConnection quicConnection) {
        this.quicConnection = quicConnection;
        return this;
    }

    public Http3ClientConnectionBuilder withDefaultQuicConnection() {
        return this;
    }

    public Http3ClientConnectionImpl build() throws Exception {
        if (quicConnection == null) {
            quicConnection = mock(QuicClientConnection.class);
        }

        if (unidirectionalOutputStream != null) {
            QuicStream unidirectionalStream = mock(QuicStream.class);
            when(unidirectionalStream.getOutputStream()).thenReturn(unidirectionalOutputStream);
            when(quicConnection.createStream(false)).thenReturn(unidirectionalStream);
        }

        Http3ClientConnectionImplExt connection = new Http3ClientConnectionImplExt(quicConnection);
        if (settingsFrame != null) {
            ByteBuffer settingsFrameBytes = settingsFrame.getBytes();
            byte[] controlStreamContent = new byte[1 + settingsFrameBytes.limit()];
            controlStreamContent[0] = STREAM_TYPE_CONTROL_STREAM;
            settingsFrameBytes.flip();
            settingsFrameBytes.get(controlStreamContent, 1, settingsFrameBytes.limit());
            QuicStream stream = mock(QuicStream.class);
            when(stream.isUnidirectional()).thenReturn(true);
            when(stream.getInputStream()).thenReturn(new ByteArrayInputStream(controlStreamContent));
            connection.handleUnidirectionalStream(stream);
        }

        if (bidirectionalInputStream != null) {
            QuicStream bidirectionalStream = mock(QuicStream.class);
            when(quicConnection.createStream(true)).thenReturn(bidirectionalStream);
            when(bidirectionalStream.getInputStream()).thenReturn(bidirectionalInputStream);
            when(bidirectionalStream.getOutputStream()).thenReturn(bidirectionalOutputStream);
            Decoder decoder = mock(Decoder.class);
            when(decoder.decodeStream(any(InputStream.class))).thenReturn(List.of(
                    new AbstractMap.SimpleEntry<>(":status", "200"),
                    new AbstractMap.SimpleEntry<>("content-type", "text/plain")));
            FieldSetter.setField(connection, Http3ConnectionImpl.class.getDeclaredField("qpackDecoder"), decoder);
        }

        return connection;
    }

    public QuicConnection quicConnection() {
        return quicConnection;
    }

    public Http3ClientConnectionBuilder withBidirectionalQuicStream(InputStream quicInputStream, OutputStream quicOutputStream) {
        bidirectionalInputStream = quicInputStream;
        bidirectionalOutputStream = quicOutputStream;
        return this;
    }

    public Http3ClientConnectionBuilder withBidirectionalQuicStream(InputStream quicInputStream) {
        bidirectionalInputStream = quicInputStream;
        bidirectionalOutputStream = new ByteArrayOutputStream();
        return this;
    }

    public Http3ClientConnectionBuilder withDefaultSettingsFrame() {
        settingsFrame = new SettingsFrame(0, 0);
        return this;
    }

    public Http3ClientConnectionBuilder withEnableConnectProtocolSettings() {
        settingsFrame = new SettingsFrame(0, 0, true);
        return this;
    }

    static private class Http3ClientConnectionImplExt extends Http3ClientConnectionImpl {

        public Http3ClientConnectionImplExt(QuicConnection quicConnection) {
            super(quicConnection);
        }

        /**
         * Override to make it public.
         * @param stream
         */
        @Override
        public void handleUnidirectionalStream(QuicStream stream) {
            super.handleUnidirectionalStream(stream);
        }
    }
}
