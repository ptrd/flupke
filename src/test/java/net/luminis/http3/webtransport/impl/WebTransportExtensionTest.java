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

import net.luminis.http3.server.Http3ServerConnection;
import net.luminis.http3.webtransport.Session;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

class WebTransportExtensionTest {

    @Test
    void whenPathsMatchExtendedConnectReturns200() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers);

        // When
        int httpStatus = webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service", mock());

        // Then
        assertThat(httpStatus).isEqualTo(200);
    }

    @Test
    void whenPathsDoNotMatchExtendedConnectReturns404() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers);

        // When
        int httpStatus = webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/welcome", mock());

        // Then
        assertThat(httpStatus).isEqualTo(404);
    }

    @Test
    void whenRequestPathPartlyMatchesExtendedConnectReturns404() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers);

        // When
        int httpStatus = webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/services", mock());

        // Then
        assertThat(httpStatus).isEqualTo(404);
    }

    @Test
    void whenRequestPathIsPrefixOfRegisteredPathExtendedConnectReturns404() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers);

        // When
        int httpStatus = webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/serv", mock());

        // Then
        assertThat(httpStatus).isEqualTo(404);
    }

    @Test
    void whenRequestPathContainsQueryParamsExtendedConnectReturnsSuccess() {
        // Given
        Map<String, Consumer<Session>> handlers = Map.of("/service", session -> {});
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers);

        // When
        int httpStatus = webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service?prop=value", mock());

        // Then
        assertThat(httpStatus).isEqualTo(200);
    }

}