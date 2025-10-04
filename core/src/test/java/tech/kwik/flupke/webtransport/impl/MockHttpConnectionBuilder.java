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
package tech.kwik.flupke.webtransport.impl;

import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.core.Http3ClientConnection;
import tech.kwik.flupke.core.HttpStream;
import tech.kwik.flupke.test.WriteableByteArrayInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.kwik.flupke.webtransport.impl.ClientSessionFactoryImpl.SETTINGS_WT_MAX_SESSIONS;

public class MockHttpConnectionBuilder {

    private HttpStream bidirectionalStream;
    private HttpStream unidirectionalStream;
    private HttpStream extendedConnectStream;
    private Http3ClientConnection http3connection;

    public Http3Client buildClient() throws Exception {
        Http3ClientConnection http3connection = buildHttp3Connection();
        Http3Client client = mock(Http3Client.class);
        when(client.createConnection(any(HttpRequest.class))).thenReturn(http3connection);
        when(http3connection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS)).thenReturn(Optional.of(1L));
        return client;
    }

    public MockHttpConnectionBuilder withExtendedConnectStream(InputStream input) throws IOException {
        return withExtendedConnectStream(input, mock(OutputStream.class));
    }

    public MockHttpConnectionBuilder withExtendedConnectStream(InputStream input, OutputStream output) throws IOException {
        extendedConnectStream = mock(HttpStream.class);
        when(extendedConnectStream.getInputStream()).thenReturn(input);
        when(extendedConnectStream.getOutputStream()).thenReturn(output);
        when(extendedConnectStream.getStreamId()).thenReturn(4L);
        return this;
    }

    public MockHttpConnectionBuilder withUnidirectionalStreamInputOuput(OutputStream output) {
        unidirectionalStream = mock(HttpStream.class);
        when(unidirectionalStream.getOutputStream()).thenReturn(output);
        return this;
    }

    public MockHttpConnectionBuilder with(HttpStream httpStream) {
        unidirectionalStream = httpStream;
        bidirectionalStream = httpStream;
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
        if (extendedConnectStream == null) {
            withExtendedConnectStream(new WriteableByteArrayInputStream());
        }
        if (unidirectionalStream == null) {
            withUnidirectionalStreamInputOuput(new ByteArrayOutputStream());
        }
        if (bidirectionalStream == null) {
            withBidirectionalStreamInputOuput(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        }

        when(http3connection.sendExtendedConnect(any(HttpRequest.class), anyString(), anyString(), any(Duration.class))).thenReturn(extendedConnectStream);
        when(http3connection.createUnidirectionalStream(anyLong())).thenReturn(unidirectionalStream);
        when(http3connection.createBidirectionalStream()).thenReturn(bidirectionalStream);
        return http3connection;
    }

    public Http3ClientConnection getHttp3connection() {
        return http3connection;
    }

    public HttpStream getExtendedConnectStream() {
        return extendedConnectStream;
    }
}


