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
import tech.kwik.flupke.test.TestExecutor;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebTransportExtensionTest {

    TestExecutor executor = new TestExecutor();

    @Test
    void whenPathsMatchExtendedConnectReturns200() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenPathsDoNotMatchExtendedConnectReturns404() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/welcome", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathPartlyMatchesExtendedConnectReturns404() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/services", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathIsPrefixOfRegisteredPathExtendedConnectReturns404() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/serv", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenRequestPathContainsQueryParamsExtendedConnectReturnsSuccess() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);

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
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);
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
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);
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
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("\"proto-c\", \"proto-d\"")), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void whenNoWtAvailableProtocolsHeaderPresentConnectSucceeds() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of("proto-a", "proto-b")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);
        HttpHeaders headers = HttpHeaders.of(Map.of(), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenWtAvailableProtocolsHeaderIsMalformedItIsIgnoredAndConnectSucceeds() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of("proto-a")));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("not-a-structured-field")), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenNoApplicationProtocolsRegisteredProtocolHeaderIsIgnored() {
        // Given
        List<WebTransportHandlerRegistration> handlers = List.of(new WebTransportHandlerRegistration("/service", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), handlers, executor, 3, 3);
        HttpHeaders headers = HttpHeaders.of(Map.of("WT-Available-Protocols", List.of("\"proto-x\"")), (k, v) -> true);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(headers, "webtransport", "localhost", "/service", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }
    // endregion

    // region regex routing
    @Test
    void whenRegexMatchesExtendedConnectReturns200() {
        // Given
        var regexHandlers = List.of(new WebTransportHandlerRegistration(Pattern.compile("/service/\\d+"), session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), regexHandlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service/42", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(200);
    }

    @Test
    void whenRegexDoesNotMatchExtendedConnectReturns404() {
        // Given
        var regexHandlers = List.of(new WebTransportHandlerRegistration(Pattern.compile("/service/\\d+"), session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), regexHandlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service/abc", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void regexMustMatchEntirePath() {
        // Given
        var regexHandlers = List.of(new WebTransportHandlerRegistration(Pattern.compile("ervice/\\d+"), session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), regexHandlers, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service/42", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThat(httpStatus.get()).isEqualTo(404);
    }

    @Test
    void staticPathTakesPrecedenceOverRegex() {
        // Given
        var registrations = List.of(
                new WebTransportHandlerRegistration(Pattern.compile("/service/.*"), session -> { throw new RuntimeException("should not execute"); }, List.of()),
                new WebTransportHandlerRegistration("/service/special", session -> {}, List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), registrations, executor, 3, 3);

        // When
        AtomicInteger httpStatus = new AtomicInteger();
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service/special", (s, h) -> httpStatus.set(s), mockHttpStream());

        // Then
        assertThatCode(() -> {
            executor.executeAllPendingTasks();
        }).doesNotThrowAnyException();
    }

    @Test
    void whenMultipleHandlersMatchFirstRegisteredShouldBeSelected() {
        // Given
        AtomicBoolean firstHandlerCalled = new AtomicBoolean();
        AtomicBoolean secondHandlerCalled = new AtomicBoolean();
        var regexHandlers = List.of(
                new WebTransportHandlerRegistration(java.util.regex.Pattern.compile("/service/.*"), session -> firstHandlerCalled.set(true), List.of()),
                new WebTransportHandlerRegistration(java.util.regex.Pattern.compile("/service/.*"), session -> secondHandlerCalled.set(true), List.of()));
        WebTransportExtension webTransportExtension = new WebTransportExtension(mock(Http3ServerConnection.class), regexHandlers, executor, 3, 3);

        // When
        webTransportExtension.handleExtendedConnect(mock(HttpHeaders.class), "webtransport", "localhost", "/service/test", (s, h) -> {}, mockHttpStream());
        executor.executeAllPendingTasks();

        // Then
        assertThat(firstHandlerCalled.get()).isEqualTo(true);
        assertThat(secondHandlerCalled.get()).isEqualTo(false);
    }
    // endregion

    private HttpStream mockHttpStream() {
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getInputStream()).thenReturn(mock());
        when(httpStream.getOutputStream()).thenReturn(mock());
        return httpStream;
    }
}