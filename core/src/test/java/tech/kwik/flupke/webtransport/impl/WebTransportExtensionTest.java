/*
 * Copyright © 2024, 2025 Peter Doornbosch
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

import org.junit.jupiter.api.Test;
import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.server.Http3ServerConnection;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebTransportExtensionTest {

    ExecutorService executor = mock(ExecutorService.class);

    @Test
    void whenPathsMatchExtendedConnectReturns200() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenPathsDoNotMatchExtendedConnectReturns404() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/welcome", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathPartlyMatchesExtendedConnectReturns404() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/services", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathIsPrefixOfRegisteredPathExtendedConnectReturns404() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/serv", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathContainsQueryParamsExtendedConnectReturnsSuccess() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service?prop=value", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    // region protocol negotiation
    @Test
    void whenProtocolMatchesExtendedConnectReturns200() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("\"proto-b\", \"proto-c\"")), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenProtocolMatchesResponseContainsWtProtocolHeader() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("\"proto-b\", \"proto-c\"")), (k, v) -> true);

        // When
        AtomicReference<Map<String, List<String>>> responseHeaders = new AtomicReference<>();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> responseHeaders.set(h), mockHttpStream());

        // Then
        assertThat(responseHeaders.get().get("WT-Protocol")).isEqualTo(List.of("\"proto-b\""));
    }

    @Test
    void whenNoProtocolMatchesExtendedConnectReturns404() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("\"proto-c\", \"proto-d\"")), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenNoApplicationProtocolsRegisteredProtocolHeaderIsIgnored() {
        // Given
        Map<String, WebTransportHandlerRegistration> handlers = Map.of("/service", new WebTransportHandlerRegistration(session -> {}));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("\"proto-x\"")), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }
    // endregion

    private HttpStream mockHttpStream() {
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getInputStream()).thenReturn(mock());
        when(httpStream.getOutputStream()).thenReturn(mock());
        return httpStream;
    }
}