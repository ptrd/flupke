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
import net.luminis.http3.core.HttpError;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.*;

class SessionFactoryTest {

    private SessionFactory factory;

    @BeforeEach
    void initObjectUnderTest() {
        factory = new SessionFactoryImpl();
    }

    @Test
    void createWebTransportSessionShouldSendExtendedConnect() throws Exception {
        Http3Client client = mock(Http3Client.class);
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        when(client.createConnection(any())).thenReturn(http3connection);
        CapsuleProtocolStream capsuleProtocolStream = mock(CapsuleProtocolStream.class);
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenReturn(capsuleProtocolStream);
        Session session = factory.createSession(client, new URI("http://example.com/webtransport"));

        verify(http3connection).sendExtendedConnectWithCapsuleProtocol(
                any(HttpRequest.class),
                argThat(s -> s.equals("webtransport")),
                argThat(p -> p.equals("https")),
                any());

        assertThat(session).isNotNull();
    }

    @Test
    void whenExtendedConnectFailsWith404AnHttpErrorIsThrown() throws Exception {
        Http3Client client = mock(Http3Client.class);
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        when(client.createConnection(any())).thenReturn(http3connection);
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenThrow(new HttpError("", 404));
        SessionFactory factory = new SessionFactoryImpl();

        assertThatThrownBy(() -> factory.createSession(client, new URI("http://example.com/webtransport")))
                .isInstanceOf(HttpError.class)
                .hasMessageContaining("404");
    }

    @Test
    void createWebTransportSessionShouldSetParameterBeforeHttpConnect() throws Exception {
        Http3Client client = mock(Http3Client.class);
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        InOrder inOrder = inOrder(http3connection);
        when(client.createConnection(any())).thenReturn(http3connection);
        CapsuleProtocolStream capsuleProtocolStream = mock(CapsuleProtocolStream.class);
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenReturn(capsuleProtocolStream);
        SessionFactory factory = new SessionFactoryImpl();
        Session session = factory.createSession(client, new URI("http://example.com/webtransport"));

        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-08.html#name-protocol-overview
        // "When an HTTP/3 connection is established, both the client and server have to send a SETTINGS_WEBTRANSPORT_MAX_SESSIONS"
        inOrder.verify(http3connection).addSettingsParameter(longThat(l -> l == 0xc671706aL), longThat(l -> l > 0));
        inOrder.verify(http3connection).connect();
    }
}