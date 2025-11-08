/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.impl;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;


public class Http3ResponseTest {

    @Test
    public void testResponseToString() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://www.google.com"))
                .build();
        HttpHeaders headers = null;
        String body = "response body";

        Http3Response<String> response = new Http3Response<>(request, 200, headers, body);

        assertThat(response.toString()).contains("(GET https://www.google.com) 200");
    }

}
