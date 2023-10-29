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
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.verify;

class SessionImplTest {

    private MockHttpConnectionBuilder builder;
    private SessionFactoryImpl factory;
    private URI defaultWebtransportUri;

    @BeforeEach
    void setupDefaults() {
        defaultWebtransportUri = URI.create("https://example.com/webtransport");
        factory = new SessionFactoryImpl();
        builder = new MockHttpConnectionBuilder();
    }

    @Test
    void creatingUnidirectionalStreamShouldSendStreamType() throws Exception {
        // Given
        Http3Client client = builder
                .buildClient();
        Session session = factory.createSession(client, defaultWebtransportUri);

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
        Session session = factory.createSession(client, defaultWebtransportUri);

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

        Session session = factory.createSession(client, defaultWebtransportUri);

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

        Session session = factory.createSession(client, defaultWebtransportUri);

        // When
        WebTransportStream wtUnidirectionalStream = session.createBidirectionalStream();
        wtUnidirectionalStream.getOutputStream().write("anything".getBytes());

        // Then
        verify(builder.getHttp3connection()).createBidirectionalStream();
        assertThat(output.toByteArray()[2]).isEqualTo((byte) 0x04);
    }
}