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
package tech.kwik.flupke.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
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
        HttpServerRequest request = new HttpServerRequest("GET",
                "/headers",
                HttpHeaders.of(Map.of("User-Agent", List.of("JUnit-Test")), (s1, s2) -> true),
                null);
        HttpServerResponse response = mock(HttpServerResponse.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(output);
        httpBinRequestHandler.handleRequest(request, response);

        assertThatJson(output.toString()).isEqualTo("{headers:{'User-Agent':['JUnit-Test']}}");
    }

}