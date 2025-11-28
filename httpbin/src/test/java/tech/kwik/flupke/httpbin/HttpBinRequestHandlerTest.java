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
package tech.kwik.flupke.httpbin;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;
import tech.kwik.flupke.server.impl.HttpServerRequestImpl;
import tech.kwik.flupke.server.impl.HttpServerResponseImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpBinRequestHandlerTest {

    private HttpBinRequestHandler httpBinRequestHandler;

    @BeforeEach
    void setUp() {
        httpBinRequestHandler = new HttpBinRequestHandler();
    }

    @Test
    void simpleHeaderShouldBeReturnedInJsonObject() throws Exception {
        HttpServerRequest request = new HttpServerRequestImpl("GET",
                "/headers",
                "www.example.com",
                HttpHeaders.of(Map.of("User-Agent", List.of("JUnit-Test")), (s1, s2) -> true),
                null,
                mock(java.io.InputStream.class));
        HttpServerResponse response = mock(HttpServerResponse.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(output);
        httpBinRequestHandler.handleRequest(request, response);

        assertThatJson(output.toString()).isEqualTo("{headers:{'User-Agent':['JUnit-Test']}}");
    }

    @Test
    void postingHeadersShouldReturnHeaders() throws Exception {
        JSONObject body = new JSONObject()
                .put("headers", new JSONObject()
                        .put("Content-Type", "application/json")
                        .put("Set-Cookie", List.of("sessionId=abc123", "theme=light", "lang=en-US"))
                        .put("User-Agent", "JUnit-Test"));
        HttpServerRequest request = new HttpServerRequestImpl("POST",
                "/headers",
                "www.example.com",
                HttpHeaders.of(Map.of(), (s1, s2) -> true),
                null,
                new ByteArrayInputStream(body.toString().getBytes()));
        HttpServerResponseWithHeaders response = new HttpServerResponseWithHeaders();

        httpBinRequestHandler.handleRequest(request, response);

        HttpHeaders responseHeaders = response.getHeaders();

        assertThat(responseHeaders.firstValue("Content-Type")).hasValue("application/json");
        assertThat(responseHeaders.firstValue("User-Agent")).hasValue("JUnit-Test");
        List<String> setCookieHeaders = responseHeaders.allValues("Set-Cookie");
        assertThat(setCookieHeaders).containsExactly("sessionId=abc123", "theme=light", "lang=en-US");
    }

    private static class HttpServerResponseWithHeaders extends HttpServerResponseImpl {

        private HttpHeaders headers;

        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public OutputStream getOutputStream() {
            return mock(OutputStream.class);
        }

        @Override
        public void setHeaders(HttpHeaders headers) {
            this.headers = headers;
        }
    }
}