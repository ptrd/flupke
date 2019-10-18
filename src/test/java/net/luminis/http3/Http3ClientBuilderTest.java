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
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;


public class Http3ClientBuilderTest {

    @Test
    public void testBuilderCreatesHttp3Client() {
        HttpClient client = new Http3ClientBuilder().build();

        assertThat(client).isInstanceOf(Http3Client.class);
    }

    @Test
    public void newBuilderMethodCreatesHttp3Client() {
        HttpClient client = Http3Client.newBuilder().build();

        assertThat(client).isInstanceOf(Http3Client.class);
    }

    @Test
    public void testBuilderPassesConnectionTimeout() {
        HttpClient client = new Http3ClientBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        assertThat(client.connectTimeout().get()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    public void testConnectionTimeoutNotSet() {
        HttpClient client = new Http3ClientBuilder()
                .build();

        // https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html#connectTimeout()
        // "If the connect timeout duration was not set in the client's builder, then the Optional is empty."
        assertThat(client.connectTimeout().isEmpty()).isTrue();
    }

    @Test
    public void testBuilderPassesReceiveBufferSize() {
        Http3Client client = (Http3Client) new Http3ClientBuilder()
                .receiveBufferSize(100_000)
                .build();

        assertThat(client.receiveBufferSize()).hasValue(100_000L);
    }

    @Test
    public void testReceiveBufferSizeNotSet() {
        Http3Client client = (Http3Client) new Http3ClientBuilder()
                .build();

        assertThat(client.receiveBufferSize()).isEmpty();
    }

}
