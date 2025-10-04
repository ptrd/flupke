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
import tech.kwik.flupke.server.Http3ServerConnection;
import tech.kwik.flupke.webtransport.Session;

import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

class WebTransportExtensionTest {

    ExecutorService executor = mock(ExecutorService.class);

    @Test
    void whenPathsMatchExtendedConnectReturns200() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service", s -> httpStatus.set(s), mock());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenPathsDoNotMatchExtendedConnectReturns404() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/welcome", s -> httpStatus.set(s), mock());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathPartlyMatchesExtendedConnectReturns404() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/services", s -> httpStatus.set(s), mock());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathIsPrefixOfRegisteredPathExtendedConnectReturns404() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/serv", s -> httpStatus.set(s), mock());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathContainsQueryParamsExtendedConnectReturnsSuccess() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service?prop=value", s -> httpStatus.set(s), mock());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

}