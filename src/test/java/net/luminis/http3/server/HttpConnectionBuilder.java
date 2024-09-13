/*
 * Copyright © 2024 Peter Doornbosch
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
package net.luminis.http3.server;

import net.luminis.http3.core.Http3Connection;
import net.luminis.http3.impl.Http3ConnectionImpl;
import net.luminis.http3.test.FieldSetter;
import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.QuicStream;
import net.luminis.quic.server.ServerConnection;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpConnectionBuilder {

    private Map<String, String> headers;
    private HttpRequestHandler handler;
    private Encoder encoder;

    public HttpConnectionBuilder withHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public HttpConnectionBuilder withHandler(HttpRequestHandler handler) {
        this.handler = handler;
        return this;
    }

    public HttpConnectionBuilder withEncoder(Encoder encoder) {
        this.encoder = encoder;
        return this;
    }

    public Http3ServerConnection buildServerConnection() throws Exception {
        if (handler == null) {
            handler = mock(HttpRequestHandler.class);
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        QuicStream httpControlStream = mock(QuicStream.class);
        when(httpControlStream.getOutputStream()).thenReturn(mock(OutputStream.class));
        ServerConnection quicConnection = mock(ServerConnection.class);
        when(quicConnection.createStream(anyBoolean())).thenReturn(httpControlStream);
        Http3ServerConnection http3Connection = new Http3ServerConnection(quicConnection, handler, executor);
        if (encoder != null) {
            FieldSetter.setField(http3Connection, Http3ServerConnection.class.getDeclaredField("encoder"), encoder);
        }
        mockDecoderWithHeaders(http3Connection, headers);
        return http3Connection;
    }

    private Decoder mockDecoderWithHeaders(Http3Connection http3Connection, Map<String, String>... headerFramesContents) throws NoSuchFieldException, IOException {
        // To relief caller from duty to assemble a complete and correct (QPack) header payload, the qpackDecoder is
        // mocked to return decent headers.
        Decoder mockedQPackDecoder = mock(Decoder.class);
        FieldSetter.setField(http3Connection, Http3ConnectionImpl.class.getDeclaredField("qpackDecoder"), mockedQPackDecoder);
        when(mockedQPackDecoder.decodeStream(any(InputStream.class))).thenAnswer(new Answer() {
            private int invocation = 0;
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                Map<String, String> headers = headerFramesContents[invocation];
                invocation++;
                return headers.entrySet().stream().collect(Collectors.toList());
            }
        });

        return mockedQPackDecoder;
    }
}
