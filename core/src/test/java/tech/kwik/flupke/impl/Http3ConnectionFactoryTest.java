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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.core.Http3ClientConnection;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Http3ConnectionFactoryTest {

    private Http3ConnectionFactory connectionFactory;

    @BeforeEach
    public void setupObjectUnderTest() {
        connectionFactory = new Http3ConnectionFactory((Http3Client) Http3Client.newHttpClient());
    }

    @Test
    public void requestsForSameAddressReuseConnection() throws Exception {
        HttpRequest request1 = HttpRequest.newBuilder().uri(new URI("http://localhost:433/index.html")).build();
        HttpRequest request2 = HttpRequest.newBuilder().uri(new URI("http://localhost:433/whatever.html")).build();

        Http3ClientConnection connection1 = connectionFactory.getConnection(request1);
        Http3ClientConnection connection2 = connectionFactory.getConnection(request2);

        assertThat(connection1).isSameAs(connection2);
    }

    @Test
    public void requestsToDifferentServersDontReuseConnection() throws Exception {
        HttpRequest request1 = HttpRequest.newBuilder().uri(new URI("http://localhost:433/index.html")).build();
        HttpRequest request2 = HttpRequest.newBuilder().uri(new URI("http://www.developer.com:433/whatever.html")).build();

        Http3ClientConnection connection1 = connectionFactory.getConnection(request1);
        Http3ClientConnection connection2 = connectionFactory.getConnection(request2);

        assertThat(connection1).isNotSameAs(connection2);
    }

    @Test
    public void requestsToDifferentPortsDontReuseConnection() throws Exception {
        HttpRequest request1 = HttpRequest.newBuilder().uri(new URI("http://localhost:433/index.html")).build();
        HttpRequest request2 = HttpRequest.newBuilder().uri(new URI("http://localhost:80/whatever.html")).build();

        Http3ClientConnection connection1 = connectionFactory.getConnection(request1);
        Http3ClientConnection connection2 = connectionFactory.getConnection(request2);

        assertThat(connection1).isNotSameAs(connection2);
    }

    @Test
    public void invalidHostnameThrowsCheckedException() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("https://www.doeshopefullystillnotexist.com/")).build();

        assertThatThrownBy(
                () -> connectionFactory.getConnection(request)
        ).isInstanceOf(IOException.class);
    }

    @Test
    @Disabled("test ignored because it takes too long")
    public void testDefaultConnectionTimeout() throws Exception {
        // Given
        Http3ConnectionFactory connectionFactory = new Http3ConnectionFactory((Http3Client) Http3Client.newHttpClient());
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:61482/index.html")).build();
        Http3ClientConnection connection = connectionFactory.getConnection(request);

        Instant start = Instant.now();
        assertThatThrownBy(
                // When
                () -> connection.connect())
                .isInstanceOf(IOException.class);
        Instant finished = Instant.now();

        // Then
        assertThat(Duration.between(start, finished).toSeconds()).isGreaterThanOrEqualTo(5);
    }

    @Test
    public void newConnection() throws Exception {
        // Given
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:433/index.html")).build();
        Http3ClientConnection originalConnection = connectionFactory.getConnection(request);

        // When
        Http3ClientConnection newConnection = connectionFactory.getConnection(request, true, false);

        // Then
        assertThat(newConnection).isNotSameAs(originalConnection);
        assertThat(connectionFactory.getConnection(request)).isSameAs(originalConnection);
    }

    @Test
    public void newConnectionWithReplace() throws Exception {
        // Given
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:433/index.html")).build();
        Http3ClientConnection originalConnection = connectionFactory.getConnection(request);

        // When
        Http3ClientConnection newConnection = connectionFactory.getConnection(request, true, true);

        // Then
        assertThat(newConnection).isNotSameAs(originalConnection);
        assertThat(connectionFactory.getConnection(request)).isSameAs(newConnection);
    }
}