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
package net.luminis.http3.webtransport.impl;

import net.luminis.http3.core.CapsuleProtocolStream;
import net.luminis.http3.server.Http3ServerConnection;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static net.luminis.http3.webtransport.impl.SessionImplTest.httpStreamWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerSessionFactoryImplTest {

    @Test
    void whenDataIsReceivedByFactoryItShouldBePassedToTheSession() throws Exception{
        // Given
        ServerSessionFactoryImpl factory = new ServerSessionFactoryImpl(mock(Http3ServerConnection.class));

        Session session = factory.createServerSession(emptyCapsuleProtocolStream());
        Consumer<WebTransportStream> bidirectionalStreamHandler = mock(Consumer.class);
        session.setBidirectionalStreamReceiveHandler(bidirectionalStreamHandler);
        session.open();

        // 0x41 is the signal value for bidirectional streams; encoded as variable length integer it is 0x4041!
        String binarySignalValue = "\u0040\u0041";
        String binarySessionId = "\u0000";  // (one byte, just 0x04)

        // When
        factory.handleBidirectionalStream(httpStreamWith(new ByteArrayInputStream((binarySignalValue + binarySessionId + "Hello from client!").getBytes(StandardCharsets.UTF_8))));

        // Then
        ArgumentCaptor<WebTransportStream> bidirectionalStreamHandlerCaptor = ArgumentCaptor.forClass(WebTransportStream.class);
        verify(bidirectionalStreamHandler).accept(bidirectionalStreamHandlerCaptor.capture());
        assertThat(new String(bidirectionalStreamHandlerCaptor.getValue().getInputStream().readAllBytes())).isEqualTo("Hello from client!");
    }

    CapsuleProtocolStream emptyCapsuleProtocolStream() throws IOException {
        CapsuleProtocolStream capsuleProtocolStream = mock(CapsuleProtocolStream.class);
        when(capsuleProtocolStream.receive()).thenAnswer(invocation -> {
            Thread.sleep(1000);  // simulate blocking read
            return null;
        });
        return capsuleProtocolStream;
    }
}