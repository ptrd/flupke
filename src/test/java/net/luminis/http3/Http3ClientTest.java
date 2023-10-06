/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
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

import net.luminis.http3.impl.Http3ClientConnectionImpl;
import net.luminis.http3.impl.Http3ConnectionFactory;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


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
                .uri(new URI("http://localhost:4433"))
                .build();

        Instant start = Instant.now();

        assertThatThrownBy(
                () -> httpClient.send(request, null))
                .isInstanceOf(ConnectException.class);

        Instant finished = Instant.now();
        assertThat(Duration.between(start, finished).toMillis()).isLessThan(500);
    }

    @Test
    public void sendAsyncShouldThrowWhenGettingFutureResultIfSendFails() throws Exception {
        Http3Client httpClient = (Http3Client) new Http3ClientBuilder().build();
        Http3ClientConnectionImpl http3Connection = createMockHttp3Connection(httpClient);
        when(http3Connection.send(any(), any())).thenThrow(new IOException("something went wrong during request/response"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:4433"))
                .build();

        CompletableFuture<HttpResponse<String>> httpResponseFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertThatThrownBy(
                () -> httpResponseFuture.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining("something went wrong during request/response");
    }

    @Test
    public void sendAsyncShouldThrowWhenGettingFutureResultIfRuntimeExceptionOccurs() throws Exception {
        Http3Client httpClient = (Http3Client) new Http3ClientBuilder().build();
        Http3ClientConnectionImpl http3Connection = createMockHttp3Connection(httpClient);
        when(http3Connection.send(any(), any())).thenThrow(new RuntimeException("something went wrong during request/response"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:4433"))
                .build();

        CompletableFuture<HttpResponse<String>> httpResponseFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertThatThrownBy(
                () -> httpResponseFuture.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("something went wrong during request/response");
    }

    private Http3ClientConnectionImpl createMockHttp3Connection(Http3Client httpClient) throws Exception {
        Http3ConnectionFactory http3ConnectionFactory = mock(Http3ConnectionFactory.class);
        Http3ClientConnectionImpl http3Connection = mock(Http3ClientConnectionImpl.class);
        when(http3ConnectionFactory.getConnection(any(HttpRequest.class))).thenReturn(http3Connection);
        FieldSetter.setField(httpClient, Http3Client.class.getDeclaredField("http3ConnectionFactory"), http3ConnectionFactory);
        return http3Connection;
    }
}
