/*
 * Copyright © 2025 Peter Doornbosch
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
import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.impl.CapsuleProtocolStreamImpl;
import tech.kwik.flupke.impl.Http3ConnectionImpl;
import tech.kwik.flupke.test.TestExecutor;
import tech.kwik.flupke.test.WriteableByteArrayInputStream;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbstractSessionFactoryImplTest {

    private AbstractSessionFactoryImpl sessionFactory;

    @BeforeEach
    void setUp() {
        sessionFactory = new AbstractSessionFactoryImpl(null, 3) {};
    }

    @Test
    void whenSessionNotOpenUnidirectionalStreamShouldBeQueued() {
        sessionFactory.registerSession(createSession(4L));

        // When
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.isUnidirectional()).thenReturn(true);
        when(httpStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 0x04 }));
        sessionFactory.handleUnidirectionalStream(httpStream);

        // Then
        verify(httpStream, never()).abortReading(anyLong());
    }

    @Test
    void whenSessionNotOpenBidirectionalStreamShouldBeQueued() {
        sessionFactory.registerSession(createSession(4L));

        // When
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.isBidirectional()).thenReturn(true);
        when(httpStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 0x04 }));
        sessionFactory.handleUnidirectionalStream(httpStream);

        // Then
        verify(httpStream, never()).abortReading(anyLong());
    }

    @Test
    void whenSessionOpenBidirectionalStreamShould() {
        // Given
        Consumer<WebTransportStream> handler = mock(Consumer.class);
        SessionImpl session = createSession(4L, handler);
        sessionFactory.registerSession(session);
        session.open();

        // When
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.isUnidirectional()).thenReturn(true);
        when(httpStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 0x04 }));
        sessionFactory.handleUnidirectionalStream(httpStream);

        // Then
        verify(httpStream, never()).abortReading(anyLong());
        verify(handler, times(1)).accept(any(WebTransportStream.class));
    }

    private SessionImpl createSession(long sessionId) {
        HttpStream controlStream = mock(HttpStream.class);
        when(controlStream.getInputStream()).thenReturn(new WriteableByteArrayInputStream());
        when(controlStream.getStreamId()).thenReturn(sessionId);
        SessionImpl newSession = new SessionImpl(mock(Http3ConnectionImpl.class), mock(WebTransportContext.class),
                new CapsuleProtocolStreamImpl(controlStream), sessionFactory);
        return newSession;
    }

    private SessionImpl createSession(long sessionId, Consumer<WebTransportStream> unidirectionalStreamHandler) {
        HttpStream controlStream = mock(HttpStream.class);
        when(controlStream.getInputStream()).thenReturn(new WriteableByteArrayInputStream());
        when(controlStream.getStreamId()).thenReturn(sessionId);
        SessionImpl newSession = new SessionImpl(mock(Http3ConnectionImpl.class), mock(WebTransportContext.class),
                new CapsuleProtocolStreamImpl(controlStream), unidirectionalStreamHandler, s -> {}, sessionFactory);
        return newSession;
    }

    private SessionImpl createSessionFor(AbstractSessionFactoryImpl factory, long sessionId) {
        HttpStream controlStream = mock(HttpStream.class);
        when(controlStream.getInputStream()).thenReturn(new WriteableByteArrayInputStream());
        when(controlStream.getStreamId()).thenReturn(sessionId);
        return new SessionImpl(mock(Http3ConnectionImpl.class), mock(WebTransportContext.class),
                new CapsuleProtocolStreamImpl(controlStream), factory);
    }

    @Test
    void datagramsReceivedBeforeSessionObjectIsCreatedAreDeliveredWhenHandlerIsSet() throws Exception {
        // Given
        TestExecutor testExecutor = new TestExecutor();
        AbstractSessionFactoryImpl factoryWithExecutor = new AbstractSessionFactoryImpl(testExecutor, 3) {};
        long sessionId = 4L;
        byte[] datagram = new byte[] { 1, 2, 3 };

        // A datagram arrives before the session object has been created
        factoryWithExecutor.handleEarlyDatagram(sessionId, datagram);

        // The session is then created, registered
        SessionImpl session = createSessionFor(factoryWithExecutor, sessionId);
        factoryWithExecutor.registerSession(session);

        // When the user sets a datagram handler and opens the session
        List<byte[]> received = new ArrayList<>();
        session.setDatagramHandler(received::add);
        session.open();
        testExecutor.executeAllPendingTasks();

        // Then the early datagram is delivered to the handler
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(datagram);
    }

}