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
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.test.FieldReader;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.SessionFactory;
import net.luminis.http3.webtransport.WebTransportStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.luminis.http3.webtransport.impl.SessionImplTest.captureHttpConnectionBidirectionalStreamHandler;
import static net.luminis.http3.webtransport.impl.SessionImplTest.captureHttpConnectionUnidirectionalStreamHandler;
import static net.luminis.http3.webtransport.impl.SessionImplTest.httpStreamWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.*;

class SessionFactoryTest {

    private SessionFactory factory;
    private Http3Client client;

    @BeforeEach
    void setup() throws IOException {
        client = mock(Http3Client.class);
    }

    @Test
    void createWebTransportSessionShouldSendExtendedConnect() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client);
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        Session session = factory.createSession(new URI("https://example.com:443/webtransport"));

        // Then
        verify(http3connection).sendExtendedConnectWithCapsuleProtocol(
                any(HttpRequest.class),
                argThat(s -> s.equals("webtransport")),
                argThat(p -> p.equals("https")),
                any());
        assertThat(session).isNotNull();
    }

    @Test
    void whenExtendedConnectFailsWith404AnHttpErrorIsThrown() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client);
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenThrow(new HttpError("", 404));
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        assertThatThrownBy(() ->
                // When
                factory.createSession(new URI("https://example.com:443/webtransport")))
                // Then
                .isInstanceOf(HttpError.class)
                .hasMessageContaining("404");
    }

    @Test
    void createWebTransportSessionShouldSetParameterBeforeHttpConnect() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client);
        InOrder inOrder = inOrder(http3connection);
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        factory.createSession(new URI("https://example.com:443/webtransport"));

        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-08.html#name-protocol-overview
        // "When an HTTP/3 connection is established, both the client and server have to send a SETTINGS_WEBTRANSPORT_MAX_SESSIONS"
        // Then
        inOrder.verify(http3connection).addSettingsParameter(longThat(l -> l == 0xc671706aL), longThat(l -> l > 0));
        inOrder.verify(http3connection).connect();
    }

    @Test
    void whenCreatingSessionWebtransportStreamTypeIsRegistered() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client);
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        factory.createSession(new URI("https://example.com:443/webtransport"));

        // Then
        verify(http3connection).registerUnidirectionalStreamType(longThat(type -> type == 0x54), any(Consumer.class));
    }

    @Test
    void whenCreatingSessionWithHandlersTheseAreUsed() throws Exception {
        // Given
        createMockHttp3ConnectionForExtendedConnect(client);
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        Consumer<WebTransportStream> unidirectionalStreamHandler = mock(Consumer.class);
        Consumer<WebTransportStream> bidirectionalStreamHandler = mock(Consumer.class);
        Session session = factory.createSession(new URI("https://example.com:443/webtransport"),
                unidirectionalStreamHandler, bidirectionalStreamHandler);

        // Then
        Object handler1 = new FieldReader(session, SessionImpl.class, "unidirectionalStreamReceiveHandler").read();
        assertThat(handler1).isSameAs(unidirectionalStreamHandler);
        // And
        Object handler2 = new FieldReader(session, SessionImpl.class, "bidirectionalStreamReceiveHandler").read();
        assertThat(handler2).isSameAs(bidirectionalStreamHandler);
    }

    @Test
    void whenWebtransportServerSendsDataOnUnidirectionalStreamBeforeBeforeReturningTheConnectResponseDataShouldBeReceived() throws Exception {
        // Given
        CountDownLatch handlerDone = new CountDownLatch(1);  // To make the test wait for the handler to be finished.

        // The action that the server will perform during the extended CONNECT request handling: (asynchronously) start a unidirectional stream and send data on it.
        BiConsumer<Http3ClientConnection, CountDownLatch> webtransportUnidirectionStreamCreationAction = (connection, started) -> {
            Consumer<HttpStream> handler = captureHttpConnectionUnidirectionalStreamHandler(connection);
            String binarySessionId = "\u0004";  // (one byte, just 0x04)
            started.countDown();
            handler.accept(httpStreamWith(new ByteArrayInputStream((binarySessionId + "Hello uni from fast server!").getBytes(StandardCharsets.UTF_8))));
            handlerDone.countDown();
        };

        // When: during the extended CONNECT request handling, the server immediately starts a stream and sends data on it before returning the CONNECT response
        createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(client, webtransportUnidirectionStreamCreationAction);
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        Consumer<WebTransportStream> unidirectionalStreamHandler = mock(Consumer.class);
        factory.createSession(new URI("https://example.com:443/wt"), unidirectionalStreamHandler, mock(Consumer.class));
        handlerDone.await();

        // Then: the unidirectionalStreamHandler should be called with the data from the server
        ArgumentCaptor<WebTransportStream> unidirectionalStreamHandlerCaptor = ArgumentCaptor.forClass(WebTransportStream.class);
        verify(unidirectionalStreamHandler).accept(unidirectionalStreamHandlerCaptor.capture());
        assertThat(new String(unidirectionalStreamHandlerCaptor.getValue().getInputStream().readAllBytes())).isEqualTo("Hello uni from fast server!");
    }

    @Test
    void whenWebtransportServerSendsDataOnBidirectionalStreamBeforeBeforeReturningTheConnectResponseDataShouldBeReceived() throws Exception {
        // Given
        CountDownLatch handlerDone = new CountDownLatch(1);  // To make the test wait for the handler to be finished.

        // The action that the server will perform during the extended CONNECT request handling: (asynchronously) start a bidirectional stream and send data on it.
        BiConsumer<Http3ClientConnection, CountDownLatch> webtransportBidirectionStreamCreationAction = (connection, started) -> {
            Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(connection);
            // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
            String binarySignalValue = "\u0040\u0041";
            String binarySessionId = "\u0004";  // (one byte, just 0x04)
            started.countDown();
            handler.accept(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello bi from fast server!").getBytes(StandardCharsets.UTF_8))));
            handlerDone.countDown();
        };

        // When: during the extended CONNECT request handling, the server immediately starts a stream and sends data on it before returning the CONNECT response
        createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(client, webtransportBidirectionStreamCreationAction);
        factory = new SessionFactoryImpl(URI.create("https://example.com:443/"), client);

        Consumer<WebTransportStream> bidirectionalStreamHandler = mock(Consumer.class);
        factory.createSession(new URI("https://example.com:443/wt"), mock(Consumer.class), bidirectionalStreamHandler);
        handlerDone.await();

        // Then: the bidirectionalStreamHandler should be called with the data from the server
        ArgumentCaptor<WebTransportStream> bidirectionalStreamHandlerCaptor = ArgumentCaptor.forClass(WebTransportStream.class);
        verify(bidirectionalStreamHandler).accept(bidirectionalStreamHandlerCaptor.capture());
        assertThat(new String(bidirectionalStreamHandlerCaptor.getValue().getInputStream().readAllBytes())).isEqualTo("Hello bi from fast server!");
    }

    private static Http3ClientConnection createMockHttp3ConnectionForExtendedConnect(Http3Client client) throws Exception {
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        when(client.createConnection(any())).thenReturn(http3connection);
        CapsuleProtocolStream capsuleProtocolStream = mock(CapsuleProtocolStream.class);
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenReturn(capsuleProtocolStream);
        return http3connection;
    }

    private Http3ClientConnection createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(Http3Client client, BiConsumer<Http3ClientConnection, CountDownLatch> action) throws Exception {
        // Create a simple mock connection that will return a CapsuleProtocolStream after the action has been started.
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        when(client.createConnection(any())).thenReturn(http3connection);
        CapsuleProtocolStream capsuleProtocolStream = mock(CapsuleProtocolStream.class);
        when(capsuleProtocolStream.getStreamId()).thenReturn(4L);  // Control stream stream ID
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenReturn(capsuleProtocolStream);

        // Simulate the server performing the action asynchronously before returning the response to the extended CONNECT request.
        CountDownLatch actionStartedFlag = new CountDownLatch(1);
        when(http3connection.sendExtendedConnectWithCapsuleProtocol(any(), any(), any(), any())).thenAnswer(invocation -> {
            new Thread(() -> {
                action.accept(http3connection, actionStartedFlag);
            }).start();
            actionStartedFlag.await();
            Thread.sleep(5); // Just to "be sure" that the handler accept has reached the await() in SessionFactoryImpl.handleUnidirectionalStream()
            return capsuleProtocolStream;
        });
        return http3connection;
    }
}