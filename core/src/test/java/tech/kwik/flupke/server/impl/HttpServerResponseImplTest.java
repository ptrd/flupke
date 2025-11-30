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
package tech.kwik.flupke.server.impl;

import org.junit.jupiter.api.Test;
import tech.kwik.core.QuicStream;
import tech.kwik.flupke.test.CapturingEncoder;
import tech.kwik.flupke.test.NoOpEncoderDecoderBuilder;
import tech.kwik.flupke.test.QuicStreamBuilder;
import tech.kwik.qpack.Encoder;

import java.io.OutputStream;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpServerResponseImplTest {

    @Test
    void statusCodeOfZeroShouldNotBeAccepted() {
        HttpServerResponseImpl response = new HttpServerResponseImpl(mock(QuicStream.class), mock(Encoder.class));

        assertThatThrownBy(
                // When
                () -> response.setStatus(0))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void statusCodeOfFourDigitsShouldNotBeAccepted() {
        HttpServerResponseImpl response = new HttpServerResponseImpl(mock(QuicStream.class), mock(Encoder.class));
        assertThatThrownBy(
                // When
                () -> response.setStatus(1000))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void whenStatusNotSetGetStatusShouldThrow() {
        HttpServerResponseImpl response = new HttpServerResponseImpl(mock(QuicStream.class), mock(Encoder.class));
        assertThatThrownBy(
                // When
                () -> response.status())
                // Then
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
    }

    @Test
    void whenStatusNotSetGetOutputStreamShouldThrow() {
        // Given
        NoOpEncoderDecoderBuilder encoderBuilder = new NoOpEncoderDecoderBuilder();
        QuicStream quicStream = mock(QuicStream.class);
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoderBuilder.encoder());

        // When / Then
        assertThatThrownBy(response::getOutputStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status not set");
    }

    @Test
    void callingSetHeadersAfterGetOutputStreamShouldThrow() {
        // Given
        NoOpEncoderDecoderBuilder encoderBuilder = new NoOpEncoderDecoderBuilder();
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getOutputStream()).thenReturn(mock(OutputStream.class));
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoderBuilder.encoder());
        response.setStatus(200);

        // When
        response.getOutputStream();

        // Then
        assertThatThrownBy(() ->
                response.setHeaders(null)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenHeadersAreSetTheyShouldBeDecodedIntoHeadersFrame() {
        // Given
        QuicStream quicStream = new QuicStreamBuilder().build();

        CapturingEncoder encoder = new CapturingEncoder();
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder);
        response.setStatus(200);

        // When
        response.setHeaders(HttpHeaders.of(
                Map.of("Content-Type", List.of("text/plain"),
                        "Cache-Control", List.of("no-cache")),
                (s, s2) -> true));
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders().get("content-type")).isEqualTo("text/plain");
        assertThat(encoder.getCapturedHeaders().get("cache-control")).isEqualTo("no-cache");
    }

    @Test
    void whenNoHeadersAreSetHeadersFrameShouldContainPseudoHeadersOnly() {
        // Given
        QuicStream quicStream = new QuicStreamBuilder().build();

        CapturingEncoder encoder = new CapturingEncoder();
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder);
        response.setStatus(200);

        // When
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders().keySet()).allMatch(key -> key.startsWith(":"));
    }
}
