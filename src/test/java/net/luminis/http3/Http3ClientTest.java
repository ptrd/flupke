/*
 * Copyright © 2019 Peter Doornbosch
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
package net.luminis.http3;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class Http3ClientTest {

    @Test
    public void followRedirectsIsNever() {
        HttpClient httpClient = Http3Client.newHttpClient();

        assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    }

    @Test
    public void testSendTimesOutIfNoConnection() throws Exception {

        HttpClient httpClient = new Http3ClientBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://www.example.com:4433"))
                .build();

        Instant start = Instant.now();

        assertThatThrownBy(
                () -> httpClient.send(request, null))
                .isInstanceOf(ConnectException.class);

        Instant finished = Instant.now();
        assertThat(Duration.between(start, finished).toMillis()).isLessThan(500);
    }

    @Test
    public void testDefaultConnectionTimeout() throws Exception {
        HttpClient httpClient = new Http3ClientBuilder()
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://www.example.com:4433"))
                .build();

        Instant start = Instant.now();

        assertThatThrownBy(
                () -> httpClient.send(request, null))
                .isInstanceOf(ConnectException.class);

        Instant finished = Instant.now();
        assertThat(Duration.between(start, finished).toSeconds()).isGreaterThanOrEqualTo(5);
    }
}