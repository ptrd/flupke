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

    // region status
    @Test
    void statusCodeOfZeroShouldNotBeAccepted() {
        HttpServerResponseImpl response = new HttpServerResponseImpl(mock(QuicStream.class), mock(Encoder.class), false);

        assertThatThrownBy(
                // When
                () -> response.setStatus(0))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void statusCodeOfFourDigitsShouldNotBeAccepted() {
        HttpServerResponseImpl response = new HttpServerResponseImpl(mock(QuicStream.class), mock(Encoder.class), false);
        assertThatThrownBy(
                // When
                () -> response.setStatus(1000))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void whenStatusNotSetGetStatusShouldThrow() {
        HttpServerResponseImpl response = new HttpServerResponseImpl(mock(QuicStream.class), mock(Encoder.class), false);
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
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoderBuilder.encoder(), false);

        // When / Then
        assertThatThrownBy(response::getOutputStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status not set");
    }

    @Test
    void whenStatus2xxSetOnConnectOutputStreamShouldThrow() {
        // Given
        NoOpEncoderDecoderBuilder encoderBuilder = new NoOpEncoderDecoderBuilder();
        QuicStream quicStream = mock(QuicStream.class);
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoderBuilder.encoder(), true);
        response.setStatus(200);
        // When / Then
        assertThatThrownBy(response::getOutputStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONNECT method cannot send body for 2xx status codes");
    }
    // endregion

    // region headers
    @Test
    void callingSetHeadersAfterGetOutputStreamShouldThrow() {
        // Given
        NoOpEncoderDecoderBuilder encoderBuilder = new NoOpEncoderDecoderBuilder();
        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getOutputStream()).thenReturn(mock(OutputStream.class));
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoderBuilder.encoder(), false);
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
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder, false);
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
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder, false);
        response.setStatus(200);

        // When
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders().keySet()).allMatch(key -> key.startsWith(":"));
    }

    @Test
    void setHeaderShouldAddHeader() {
        // Given
        QuicStream quicStream = new QuicStreamBuilder().build();
        CapturingEncoder encoder = new CapturingEncoder();
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder, false);
        response.setStatus(200);

        // When
        response.addHeader("user-agent", "flupke-client/1.0");
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders())
                .hasSize(2) // :status and user-agent
                .containsEntry("user-agent", "flupke-client/1.0");
    }

    @Test
    void setHeaderMultipleTimeExtendsValueList() {
        // Given
        QuicStream quicStream = new QuicStreamBuilder().build();
        CapturingEncoder encoder = new CapturingEncoder();
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder, false);
        response.setStatus(200);

        // When
        response.addHeader("Set-Cookie", "cookie1=value1");
        response.addHeader("Set-Cookie", "cookie2=value2");
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders()).containsEntry("set-cookie", "cookie1=value1,cookie2=value2");
    }

    @Test
    void setHeaderAndSetHeadersAreCombined() {
        // Given
        QuicStream quicStream = new QuicStreamBuilder().build();
        CapturingEncoder encoder = new CapturingEncoder();
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder, false);
        response.setStatus(200);

        // When
        response.setHeaders(HttpHeaders.of(
                Map.of("Set-Cookie", List.of("cookie1=value1")),
                (s, s2) -> true));
        response.addHeader("user-agent", "flupke-client/1.0");
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders())
                .containsEntry("set-cookie", "cookie1=value1")
                .containsEntry("user-agent", "flupke-client/1.0");
    }

    @Test
    void setHeaderValuesAndSetHeadersAreCombined() {
        // Given
        QuicStream quicStream = new QuicStreamBuilder().build();
        CapturingEncoder encoder = new CapturingEncoder();
        HttpServerResponseImpl response = new HttpServerResponseImpl(quicStream, encoder, false);
        response.setStatus(200);

        // When
        response.setHeaders(HttpHeaders.of(
                Map.of("Set-Cookie", List.of("cookie1=value1")),
                (s, s2) -> true));
        response.addHeader("Set-Cookie", "cookie2=value2");
        response.getOutputStream();

        // Then
        assertThat(encoder.getCapturedHeaders().values())
                .containsAnyOf("cookie1=value1,cookie2=value2", "cookie2=value2,cookie1=value1");
    }
    // endregion
}
