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
package net.luminis.http3.webtransport.impl;

import net.luminis.http3.Http3Client;
import net.luminis.http3.core.CapsuleProtocolStream;
import net.luminis.http3.core.Http3ClientConnection;
import net.luminis.http3.core.HttpStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockHttpConnectionBuilder {

    private HttpStream bidirectionalStream;
    private HttpStream unidirectionalStream;
    private CapsuleProtocolStream capsuleProtocolStream;

    private Http3ClientConnection http3connection;

    public Http3Client buildClient() throws Exception {
        Http3ClientConnection http3connection = buildHttp3Connection();
        Http3Client client = mock(Http3Client.class);
        when(client.createConnection(any(HttpRequest.class))).thenReturn(http3connection);
        return client;
    }

    public MockHttpConnectionBuilder withCapsuleProtocolStream(InputStream input) {
        capsuleProtocolStream = mock(CapsuleProtocolStream.class);
        when(capsuleProtocolStream.getStreamId()).thenReturn(4L);
        return this;
    }

    public MockHttpConnectionBuilder withUnidirectionalStreamInputOuput(OutputStream output) {
        unidirectionalStream = mock(HttpStream.class);
        when(unidirectionalStream.getOutputStream()).thenReturn(output);
        return this;
    }

    public MockHttpConnectionBuilder withBidirectionalStreamInputOuput(InputStream input, OutputStream output) {
        bidirectionalStream = mock(HttpStream.class);
        when(bidirectionalStream.getInputStream()).thenReturn(input);
        when(bidirectionalStream.getOutputStream()).thenReturn(output);
        return this;
    }

    private Http3ClientConnection buildHttp3Connection() throws Exception {
        http3connection = mock(Http3ClientConnection.class);
        if (capsuleProtocolStream == null) {
            withCapsuleProtocolStream(new ByteArrayInputStream(new byte[0]));
        }
        if (unidirectionalStream == null) {
            withUnidirectionalStreamInputOuput(new ByteArrayOutputStream());
        }
        if (bidirectionalStream == null) {
            withBidirectionalStreamInputOuput(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        }

        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(HttpRequest.class), anyString(), anyString(), any(Duration.class))).thenReturn(capsuleProtocolStream);
        when(http3connection.createUnidirectionalStream(anyLong())).thenReturn(unidirectionalStream);
        when(http3connection.createBidirectionalStream()).thenReturn(bidirectionalStream);
        return http3connection;
    }

    public Http3ClientConnection getHttp3connection() {
        return http3connection;
    }
}


