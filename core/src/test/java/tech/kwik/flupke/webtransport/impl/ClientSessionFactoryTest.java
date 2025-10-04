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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.HttpError;
import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.core.Http3ClientConnection;
import tech.kwik.flupke.test.FieldReader;
import tech.kwik.flupke.test.WriteableByteArrayInputStream;
import tech.kwik.flupke.webtransport.ClientSessionFactory;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.*;
import static tech.kwik.flupke.webtransport.impl.ClientSessionFactoryImpl.SETTINGS_WT_MAX_SESSIONS;
import static tech.kwik.flupke.webtransport.impl.SessionImplTest.captureHttpConnectionBidirectionalStreamHandler;
import static tech.kwik.flupke.webtransport.impl.SessionImplTest.httpStreamWith;

class ClientSessionFactoryTest {

    private ClientSessionFactory factory;
    private Http3Client client;

    @BeforeEach
    void setup() throws IOException {
        client = mock(Http3Client.class);
    }

    @Test
    void createWebTransportSessionShouldSendExtendedConnect() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client, 10);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        Session session = factory.createSession(new URI("https://example.com:443/webtransport"));

        // Then
        verify(http3connection).sendExtendedConnect(
                any(HttpRequest.class),
                argThat(s -> s.equals("webtransport")),
                argThat(p -> p.equals("https")),
                any());
        assertThat(session).isNotNull();
    }

    @Test
    void whenExtendedConnectFailsWith404AnHttpErrorIsThrown() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client, 10);
        when(http3connection.sendExtendedConnect(any(), any(), any(), any())).thenThrow(new HttpError("", 404));
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

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
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client, 10);
        InOrder inOrder = inOrder(http3connection);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        factory.createSession(new URI("https://example.com:443/webtransport"));

        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html#section-2.2
        // "When an HTTP/3 connection is established, both the client and the server send a SETTINGS_WT_MAX_SESSIONS
        //  setting to indicate support for WebTransport over HTTP/3. "
        // Then
        inOrder.verify(http3connection).addSettingsParameter(longThat(l -> l == 0x14e9cd29L), longThat(l -> l > 0));
        inOrder.verify(http3connection).connect();
    }

    @Test
    void whenCreatingSessionWebtransportStreamTypeIsRegistered() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client, 10);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

        // When
        factory.createSession(new URI("https://example.com:443/webtransport"));

        // Then
        verify(http3connection).registerUnidirectionalStreamType(longThat(type -> type == 0x54), any(Consumer.class));
    }

    @Test
    void whenCreatingSessionSessionLimitShouldBeRespected() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client, 1);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);
        factory.createSession(new URI("https://example.com:443/webtransport"));

        assertThatThrownBy(() ->
                // When
                factory.createSession(new URI("https://example.com:443/webtransport")))
                // Then
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenCreatingSessionWithHandlersTheseAreUsed() throws Exception {
        // Given
        createMockHttp3ConnectionForExtendedConnect(client, 10);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

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
    void whenWebtransportServerSendsDataBeforeBeforeReturningTheConnectResponseDataShouldBeReceived() throws Exception {
        // The action that the server will perform during the extended CONNECT request handling: (asynchronously) start a bidirectional stream and send data on it.
        Consumer<Http3ClientConnection> webtransportStreamCreationAction = (connection) -> {
            Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(connection);
            // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
            String binarySignalValue = "\u0040\u0041";
            String binarySessionId = "\u0004";  // (one byte, just 0x04)
            handler.accept(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello bi from fast server!").getBytes(StandardCharsets.UTF_8))));
        };

        // When: during the extended CONNECT request handling, the server immediately starts a stream and sends data on it before returning the CONNECT response
        createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(client, webtransportStreamCreationAction);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

        Consumer<WebTransportStream> bidirectionalStreamHandler = mock(Consumer.class);
        Session session = factory.createSession(new URI("https://example.com:443/wt"), mock(Consumer.class), bidirectionalStreamHandler);
        session.open();

        // Then: the bidirectionalStreamHandler should be called with the data from the server
        ArgumentCaptor<WebTransportStream> bidirectionalStreamHandlerCaptor = ArgumentCaptor.forClass(WebTransportStream.class);
        verify(bidirectionalStreamHandler).accept(bidirectionalStreamHandlerCaptor.capture());
        assertThat(new String(bidirectionalStreamHandlerCaptor.getValue().getInputStream().readAllBytes())).isEqualTo("Hello bi from fast server!");
    }

    @Test
    void limitNumberOfStreamsIsBufferedWhenThereIsYetNoSession() throws Exception {
        int numberOfStreams = 5;
        Consumer<Http3ClientConnection> webtransportStreamCreationAction = (connection) -> {
            Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(connection);
            for (int i = 0; i < numberOfStreams; i++) {
                // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
                String binarySignalValue = "\u0040\u0041";
                String binarySessionId = "\u0004";  // (one byte, just 0x04)
                handler.accept(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello bi from fast server!").getBytes(StandardCharsets.UTF_8))));
            }
        };

        // When: during the extended CONNECT request handling, the server immediately starts a stream and sends data on it before returning the CONNECT response
        createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(client, webtransportStreamCreationAction);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

        Consumer<WebTransportStream> bidirectionalStreamHandler = mock(Consumer.class);
        Session session = factory.createSession(new URI("https://example.com:443/wt"), mock(Consumer.class), bidirectionalStreamHandler);
        session.open();

        int maxStreamsBuffered = 3;
        verify(bidirectionalStreamHandler, times(maxStreamsBuffered)).accept(any(WebTransportStream.class));
    }

    @Test
    void whenStreamHandlerIsCalledSessionIsSet() throws Exception {
        Consumer<Http3ClientConnection> webtransportStreamCreationAction = (connection) -> {
            Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(connection);
            // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
            String binarySignalValue = "\u0040\u0041";
            String binarySessionId = "\u0004";  // (one byte, just 0x04)
            handler.accept(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello bi from fast server!").getBytes(StandardCharsets.UTF_8))));
        };

        // When: during the extended CONNECT request handling, the server immediately starts a stream and sends data on it before returning the CONNECT response
        createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(client, webtransportStreamCreationAction);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);

        AtomicBoolean streamHandlerCalled = new AtomicBoolean(false);
        AtomicReference<Session> session = new AtomicReference<>();
        Consumer<WebTransportStream> bidirectionalStreamHandler = (stream) -> {
            streamHandlerCalled.set(true);
            // When the stream handler is called, the session should already have been set
            assertThat(session.get()).isNotNull();
        };
        session.set(factory.createSession(new URI("https://example.com:443/wt"), mock(Consumer.class), bidirectionalStreamHandler));
        session.get().open();

        assertThat(streamHandlerCalled.get()).isTrue();
    }

    @Test
    void whenReceivingStreamForSessionThatIsAlreadyClosedTheStreamShouldBeIgnored() throws Exception {
        // Given
        Http3ClientConnection http3connection = createMockHttp3ConnectionForExtendedConnect(client, 10);
        factory = new ClientSessionFactoryImpl(URI.create("https://example.com:443/"), client);
        Session session1 = factory.createSession(new URI("https://example.com:443/wt"));
        session1.open();
        session1.close();

        Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(http3connection);
        // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
        String binarySignalValue = "\u0040\u0041";
        String binarySessionId = "\u0004";  // (one byte, just 0x04)

        // When
        HttpStream httpStream = httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hi from server!").getBytes(StandardCharsets.UTF_8)));
        handler.accept(httpStream);

        // Then
        assertThat(session1.getSessionId()).isEqualTo(4L);
        verify(httpStream).resetStream(anyLong());
        verify(httpStream).abortReading(anyLong());
    }

    private static Http3ClientConnection createMockHttp3ConnectionForExtendedConnect(Http3Client client, long maxWebTransportSessions) throws Exception {
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        when(client.createConnection(any())).thenReturn(http3connection);
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getStreamId()).thenReturn(4L);  // Control stream stream ID
        when(httpStream.getInputStream()).thenReturn(new WriteableByteArrayInputStream());  // Reading from this stream will block
        when(httpStream.getOutputStream()).thenReturn(mock(OutputStream.class));
        when(http3connection.sendExtendedConnect(any(), any(), any(), any())).thenReturn(httpStream);
        when(http3connection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS)).thenReturn(Optional.of(maxWebTransportSessions));
        return http3connection;
    }

    private Http3ClientConnection createMockHttp3ConnectionForExtendedConnectWithDelayedResponseAfterAction(Http3Client client, Consumer<Http3ClientConnection> action) throws Exception {
        // Create a simple mock connection that will return a CapsuleProtocolStream after the action has been started.
        Http3ClientConnection http3connection = mock(Http3ClientConnection.class);
        when(client.createConnection(any())).thenReturn(http3connection);
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getStreamId()).thenReturn(4L);  // Control stream stream ID
        when(httpStream.getInputStream()).thenReturn(new WriteableByteArrayInputStream());  // Reading from this stream will block
        when(httpStream.getOutputStream()).thenReturn(mock(OutputStream.class));

        when(http3connection.sendExtendedConnect(any(), any(), any(), any())).thenReturn(httpStream);
        when(http3connection.getPeerSettingsParameter(SETTINGS_WT_MAX_SESSIONS)).thenReturn(Optional.of(10L));

        // Simulate the server performing the action asynchronously before returning the response to the extended CONNECT request.
        when(http3connection.sendExtendedConnect(any(), any(), any(), any())).thenAnswer(invocation -> {
            action.accept(http3connection);
            return httpStream;
        });
        return http3connection;
    }
}