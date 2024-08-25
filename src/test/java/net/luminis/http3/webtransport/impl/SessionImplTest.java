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
import net.luminis.http3.core.Http3ClientConnection;
import net.luminis.http3.core.HttpError;
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.test.ByteUtils;
import net.luminis.http3.test.WriteableByteArrayInputStream;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionImplTest {

    private MockHttpConnectionBuilder builder;
    private SessionFactoryImpl factory;
    private URI defaultWebtransportUri;

    @BeforeEach
    void setupDefaults() {
        defaultWebtransportUri = URI.create("https://example.com:443/webtransport");
        builder = new MockHttpConnectionBuilder();
    }

    @Test
    void creatingUnidirectionalStreamShouldSendStreamType() throws Exception {
        // Given
        Http3Client client = builder
                .buildClient();
        Session session = createSessionWith(client);

        // When
        WebTransportStream wtUnidirectionalStream = session.createUnidirectionalStream();
        wtUnidirectionalStream.getOutputStream().write("anything".getBytes());

        // Then
        verify(builder.getHttp3connection()).createUnidirectionalStream(longThat(unidirectionalStreamType -> unidirectionalStreamType == 0x54));
    }

    @Test
    void creatingUnidirectionalStreamShouldSendSessionId() throws Exception {
        // Given
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Http3Client client = builder
                .withUnidirectionalStreamInputOuput(output)
                .buildClient();
        Session session = createSessionWith(client);

        // When
        WebTransportStream wtUnidirectionalStream = session.createUnidirectionalStream();
        wtUnidirectionalStream.getOutputStream().write("Hi".getBytes());

        // Then
        assertThat(output.toByteArray()[0]).isEqualTo((byte) 0x04);
        assertThat(output.toByteArray()[1]).isEqualTo("H".getBytes(StandardCharsets.UTF_8)[0]);
    }

    @Test
    void creatingBidirectionalStreamShouldSendSignalValue() throws Exception {
        // Given
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Http3Client client = builder
                .withBidirectionalStreamInputOuput(new ByteArrayInputStream(new byte[0]), output)
                .buildClient();

        Session session = createSessionWith(client);

        // When
        WebTransportStream wtUnidirectionalStream = session.createBidirectionalStream();
        wtUnidirectionalStream.getOutputStream().write("anything".getBytes());

        // Then
        verify(builder.getHttp3connection()).createBidirectionalStream();
        // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
        assertThat(output.toByteArray()[0]).isEqualTo((byte) 0x40);
        assertThat(output.toByteArray()[1]).isEqualTo((byte) 0x41);
    }

    @Test
    void creatingBidirectionalStreamShouldSendSessionId() throws Exception {
        // Given
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Http3Client client = builder
                .withBidirectionalStreamInputOuput(new ByteArrayInputStream(new byte[0]), output)
                .buildClient();

        Session session = createSessionWith(client);

        // When
        WebTransportStream wtUnidirectionalStream = session.createBidirectionalStream();
        wtUnidirectionalStream.getOutputStream().write("anything".getBytes());

        // Then
        verify(builder.getHttp3connection()).createBidirectionalStream();
        assertThat(output.toByteArray()[2]).isEqualTo((byte) 0x04);
    }

    @Test
    void whenSettingUnidirectionalStreamHandlerTheSessionStreamHandlerWillReceiveIt() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(createOpenInputStream())
                .buildClient();
        Session session = createSessionWith(client);

        Consumer<HttpStream> handler = captureHttpConnectionUnidirectionalStreamHandler(builder.getHttp3connection());

        AtomicReference<String> receivedMessage = new AtomicReference<>();

        // When session registers a handler for unidirectional streams
        session.setUnidirectionalStreamReceiveHandler(stream -> {
            receivedMessage.set(readStringFrom(stream.getInputStream()));
        });

        // And When the peer sends something on a (new) unidirectional stream
        String binarySessionId = "\u0004";  // (one byte, just 0x04)
        handler.accept(httpStreamWith(new ByteArrayInputStream((binarySessionId + "Hello from peer!").getBytes(StandardCharsets.UTF_8))));

        // Then the session handler receives it
        assertThat(receivedMessage.get()).isEqualTo("Hello from peer!");
    }

    @Test
    void whenCreatingSessionWithUnidirectionalStreamHandlerTheSessionStreamHandlerWillReceive() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(createOpenInputStream())
                .buildClient();

        AtomicReference<String> receivedMessage = new AtomicReference<>();
        Consumer<WebTransportStream> unidirectionalStreamHandler = stream -> {
            receivedMessage.set(readStringFrom(stream.getInputStream()));
        };
        createSessionWith(client, unidirectionalStreamHandler, s -> {});
        Consumer<HttpStream> handler = captureHttpConnectionUnidirectionalStreamHandler(builder.getHttp3connection());

        // When the peer sends something on a (new) unidirectional stream
        String binarySessionId = "\u0004";  // (one byte, just 0x04)
        handler.accept(httpStreamWith(new ByteArrayInputStream((binarySessionId + "Hello from peer!").getBytes(StandardCharsets.UTF_8))));

        // Then the session handler receives it
        assertThat(receivedMessage.get()).isEqualTo("Hello from peer!");
    }

    @Test
    void whenSettingBidirectionalStreamHandlerTheSessionStreamHandlerWillReceiveIt() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(createOpenInputStream())
                .buildClient();

        AtomicReference<String> receivedMessage = new AtomicReference<>();
        Session session = createSessionWith(client);
        Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(builder.getHttp3connection());

        // When session registers a handler for bidirectional streams
        session.setBidirectionalStreamReceiveHandler(stream -> {
            receivedMessage.set(readStringFrom(stream.getInputStream()));
        });

        // And When the peer sends something on a (new) unidirectional stream
        // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
        String binarySignalValue = "\u0040\u0041";
        String binarySessionId = "\u0004";  // (one byte, just 0x04)
        handler.accept(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello from peer!").getBytes(StandardCharsets.UTF_8))));

        // Then the session handler receives it
        assertThat(receivedMessage.get()).isEqualTo("Hello from peer!");
    }

    @Test
    void whenCreatingSessionWithBidirectionalStreamHandlerTheSessionStreamHandlerWillReceive() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(createOpenInputStream())
                .buildClient();

        AtomicReference<String> receivedMessage = new AtomicReference<>();
        Consumer<WebTransportStream> bidirectionalStreamHandler = stream -> {
            receivedMessage.set(readStringFrom(stream.getInputStream()));
        };
        createSessionWith(client, s -> {}, bidirectionalStreamHandler);
        Consumer<HttpStream> handler = captureHttpConnectionBidirectionalStreamHandler(builder.getHttp3connection());

        // And When the peer sends something on a (new) bidirectional stream
        // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
        String binarySignalValue = "\u0040\u0041";
        String binarySessionId = "\u0004";  // (one byte, just 0x04)
        handler.accept(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello from peer!").getBytes(StandardCharsets.UTF_8))));

        // Then the session handler receives it
        assertThat(receivedMessage.get()).isEqualTo("Hello from peer!");
    }

    @Test
    void whenNoHandlerSet() throws Exception {
        // Given
        Http3Client client = builder
                .buildClient();
        Session session = createSessionWith(client);

        Consumer<HttpStream> handler = captureHttpConnectionUnidirectionalStreamHandler(builder.getHttp3connection());

        AtomicReference<String> receivedMessage = new AtomicReference<>();

        // When no handler is registered

        // And When the peer sends something on a (new) unidirectional stream
        String binarySessionId = "\u0004";  // (one byte, just 0x04)
        handler.accept(httpStreamWith(new ByteArrayInputStream((binarySessionId + "Going nowhere").getBytes(StandardCharsets.UTF_8))));

        // Then
        // Nothing (expect no exception)
    }

    @Test
    void whenSessionIsCreatedCapsuleParserShouldHaveBeenRegistered() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(new WriteableByteArrayInputStream())
                .buildClient();

        // When
        Session session = createSessionWith(client);

        // Then
        verify(builder.getCapsuleProtocolStream()).registerCapsuleParser(anyLong(), any(Function.class));
    }

    @Test
    void whenReceivingCloseTheCallbackIsCalled() throws Exception {
        // Given
        WriteableByteArrayInputStream inputStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(inputStream)
                .buildClient();

        BiConsumer<Long, String> sessionTerminatedEventListener = mock(BiConsumer.class);
        Session session = createSessionWith(client);
        session.registerSessionTerminatedEventListener(sessionTerminatedEventListener);

        // When
        String closeWebtransportSessionCapsuleBinary = "68430400000000";
        inputStream.write(ByteUtils.hexToBytes(closeWebtransportSessionCapsuleBinary));
        // Processing connect stream happens async, so give other thread a chance to proces
        Thread.sleep(10);

        // Then
        ArgumentCaptor<Long> errorCodeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(sessionTerminatedEventListener).accept(errorCodeCaptor.capture(), anyString());
        assertThat(errorCodeCaptor.getValue()).isEqualTo(0L);
    }

    @Test
    void whenReadingConnectStreamIsClosedSessionIsClosed() throws Exception {
        // Given
        WriteableByteArrayInputStream inputStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(inputStream)
                .buildClient();
        Session session = createSessionWith(client);

        BiConsumer<Long, String> sessionTerminatedEventListener = mock(BiConsumer.class);
        session.registerSessionTerminatedEventListener(sessionTerminatedEventListener);

        // When
        inputStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        ArgumentCaptor<Long> errorCodeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(sessionTerminatedEventListener).accept(errorCodeCaptor.capture(), anyString());
        assertThat(errorCodeCaptor.getValue()).isEqualTo(0L);
    }

    @Test
    void whenReadingConnectStreamFailsSessionIsClosed() throws Exception {
        // Given
        WriteableByteArrayInputStream inputStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(inputStream)
                .buildClient();
        Session session = createSessionWith(client);

        BiConsumer<Long, String> sessionTerminatedEventListener = mock(BiConsumer.class);
        session.registerSessionTerminatedEventListener(sessionTerminatedEventListener);

        // When
        String closeWebtransportSessionCapsuleBinary = "6843040000";  // 2 bytes missing
        inputStream.write(ByteUtils.hexToBytes(closeWebtransportSessionCapsuleBinary));
        inputStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        ArgumentCaptor<Long> errorCodeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(sessionTerminatedEventListener).accept(errorCodeCaptor.capture(), anyString());
        assertThat(errorCodeCaptor.getValue()).isEqualTo(0L);
    }

    @Test
    void whenSessionIsClosedExistingUnidirectionalStreamsAreResetAndAborted() throws Exception {
        // Given
        HttpStream unidirectionalHttpStream = mockHttpStream();
        InputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .with(unidirectionalHttpStream)
                .buildClient();
        Session session = createSessionWith(client);

        session.createUnidirectionalStream();

        // When
        connectStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        verify(unidirectionalHttpStream).resetStream(anyLong());
        verify(unidirectionalHttpStream, never()).abortReading(anyLong());
    }

    @Test
    void whenSessionIsClosedExistingPeerInitiatedUnidirectionalStreamsAreAborted() throws Exception {
        // Given
        InputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .buildClient();

        Consumer<WebTransportStream> unidirectionalStreamHandler = stream -> {};
        createSessionWith(client, unidirectionalStreamHandler, null);
        Consumer<HttpStream> handler = captureHttpConnectionUnidirectionalStreamHandler(builder.getHttp3connection());

        // When the peer opens a unidirectional stream
        String binarySessionId = "\u0004";  // (one byte, just 0x04)
        HttpStream unidirectionalHttpStream = httpStreamWith(new ByteArrayInputStream((binarySessionId + "Hello from peer!").getBytes(StandardCharsets.UTF_8)));
        handler.accept(unidirectionalHttpStream);

        // When
        connectStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(100);

        // Then
        verify(unidirectionalHttpStream).abortReading(anyLong());
        verify(unidirectionalHttpStream, never()).resetStream(anyLong());
    }

    @Test
    void whenSessionIsClosedExistingBidirectionalStreamsAreResetAndAborted() throws Exception {
        // Given
        HttpStream bidirectionalHttpStream = mockHttpStream();
        InputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .with(bidirectionalHttpStream)
                .buildClient();

        Session session = createSessionWith(client, null, null);
        session.createBidirectionalStream();

        // When
        connectStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        verify(bidirectionalHttpStream).resetStream(anyLong());
        verify(bidirectionalHttpStream).abortReading(anyLong());
    }

    @Test
    void onSessionUnidirectionalStreamCanBeOpened() throws Exception {
        // Given
        InputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .buildClient();
        Session session = createSessionWith(client);
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // When
        WebTransportStream unidirectionalStream = session.createUnidirectionalStream();

        // Then
        assertThat(unidirectionalStream).isNotNull();
    }

    @Test
    void withClosedSessionNoNewUnidirectionalStreamCanBeOpened() throws Exception {
        // Given
        InputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .buildClient();
        Session session = createSessionWith(client);
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // When
        connectStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        assertThatThrownBy(() -> session.createUnidirectionalStream())
                .isInstanceOf(IOException.class)
                .hasMessage("Session is closed");
    }

    @Test
    void withClosedSessionNoNewBidirectionalStreamCanBeOpened() throws Exception {
        // Given
        InputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .buildClient();
        Session session = createSessionWith(client);
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // When
        connectStream.close();
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        assertThatThrownBy(() -> session.createBidirectionalStream())
                .isInstanceOf(IOException.class)
                .hasMessage("Session is closed");
    }

    @Test
    void whenClosedUnidirectionalStreamHandlerIsNotCalledAnymoreWhenPeerOpensStream() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(createOpenInputStream())
                .buildClient();
        Consumer<WebTransportStream> unidirectionalStreamHandler = mock(Consumer.class);
        SessionImpl session = (SessionImpl) createSessionWith(client, unidirectionalStreamHandler, null);
        session.close();

        // When
        HttpStream incomingUnidirectionalStream = httpStreamWith(new ByteArrayInputStream(new byte[0]));
        session.handleUnidirectionalStream(incomingUnidirectionalStream);

        // Then
        verify(unidirectionalStreamHandler, never()).accept(any());
        verify(incomingUnidirectionalStream).abortReading(anyLong());
    }

    @Test
    void whenClosedBidirectionalStreamHandlerIsNotCalledAnymoreWhenPeerOpensStream() throws Exception {
        // Given
        Http3Client client = builder
                .withCapsuleProtocolStream(createOpenInputStream())
                .buildClient();
        Consumer<WebTransportStream> bidirectionalStreamHandler = mock(Consumer.class);
        SessionImpl session = (SessionImpl) createSessionWith(client, null, bidirectionalStreamHandler);
        session.close();

        // When
        HttpStream incomingBidirectionalStream = httpStreamWith(new ByteArrayInputStream(new byte[0]));
        session.handleBidirectionalStream(incomingBidirectionalStream);

        // Then
        verify(bidirectionalStreamHandler, never()).accept(any());
        verify(incomingBidirectionalStream).abortReading(anyLong());
        verify(incomingBidirectionalStream).resetStream(anyLong());
    }

    @Test
    void closingSessionShouldSendCloseWebtransportSessionCapsule() throws Exception {
        // Given
        Http3Client client = builder
                .buildClient();
        Session session = createSessionWith(client);

        // When
        session.close(0, "bye");

        // Then
        verify(builder.getCapsuleProtocolStream()).sendAndClose(argThat(capsule ->
                ((CloseWebtransportSessionCapsule) capsule).getApplicationErrorCode() == 0));
    }

    @Test
    void whenReceivingCloseConnectStreamMustBeClosed() throws Exception {
        // Given
        WriteableByteArrayInputStream connectStream = new WriteableByteArrayInputStream();
        Http3Client client = builder
                .withCapsuleProtocolStream(connectStream)
                .buildClient();
        Session session = createSessionWith(client);

        // When
        String closeWebtransportSessionCapsuleBinary = "68430400000000";
        connectStream.write(ByteUtils.hexToBytes(closeWebtransportSessionCapsuleBinary));
        // Processing connect stream happens async, so give other thread a chance to process
        Thread.sleep(10);

        // Then
        verify(builder.getCapsuleProtocolStream()).close();
    }

    @Test
    void callingCloseWhenAlreadyClosedShouldDoNothing() throws Exception {
        // Given
        Http3Client client = builder
                .buildClient();
        Session session = createSessionWith(client);

        // When
        session.close(0, "bye");
        session.close(0, "bye");

        // Then
        verify(builder.getCapsuleProtocolStream(), times(1)).sendAndClose(any());
    }

    /**
     * Returns an input stream that is open but empty, so any read will block forever.
     * @return
     */
    private static InputStream createOpenInputStream() {
        return new WriteableByteArrayInputStream();
    }

    private Session createSessionWith(Http3Client client) throws Exception {
        return new SessionFactoryImpl(defaultWebtransportUri, client).createSession(defaultWebtransportUri);
    }

    private static HttpStream mockHttpStream() {
        HttpStream bidirectionalHttpStream = mock(HttpStream.class);
        when(bidirectionalHttpStream.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return bidirectionalHttpStream;
    }

    private Session createSessionWith(Http3Client client, Consumer<WebTransportStream> unidirectionalStreamHandler, Consumer<WebTransportStream> bidirectionalStreamHandler) throws IOException, HttpError {
        return new SessionFactoryImpl(defaultWebtransportUri, client).createSession(defaultWebtransportUri, unidirectionalStreamHandler, bidirectionalStreamHandler);
    }

    static Consumer<HttpStream> captureHttpConnectionUnidirectionalStreamHandler(Http3ClientConnection http3Connection) {
        ArgumentCaptor<Consumer<HttpStream>> handlerCapturer = ArgumentCaptor.forClass(Consumer.class);
        verify(http3Connection).registerUnidirectionalStreamType(longThat(type -> type == 0x54), handlerCapturer.capture());
        Consumer<HttpStream> handler = handlerCapturer.getValue();
        return handler;
    }

    static Consumer<HttpStream> captureHttpConnectionBidirectionalStreamHandler(Http3ClientConnection http3Connection) {
        ArgumentCaptor<Consumer<HttpStream>> handlerCapturer = ArgumentCaptor.forClass(Consumer.class);
        verify(http3Connection).registerBidirectionalStreamHandler(handlerCapturer.capture());
        Consumer<HttpStream> handler = handlerCapturer.getValue();
        return handler;
    }

    static HttpStream httpStreamWith(ByteArrayInputStream byteArrayInputStream) {
        HttpStream mock = mock(HttpStream.class);
        when(mock.getInputStream()).thenReturn(byteArrayInputStream);
        return mock;
    }

    private String readStringFrom(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}