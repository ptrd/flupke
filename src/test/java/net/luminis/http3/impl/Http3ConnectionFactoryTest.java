package net.luminis.http3.impl;

import net.luminis.http3.Http3Client;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Http3ConnectionFactoryTest {

    @Test
    public void requestsForSameAddressReuseConnection() throws Exception {
        HttpRequest request1 = HttpRequest.newBuilder().uri(new URI("http://www.example.com:433/index.html")).build();
        HttpRequest request2 = HttpRequest.newBuilder().uri(new URI("http://www.example.com:433/whatever.html")).build();

        Http3ConnectionFactory connectionFactory = new Http3ConnectionFactory((Http3Client) Http3Client.newHttpClient());

        Http3Connection connection1 = connectionFactory.getConnection(request1);
        Http3Connection connection2 = connectionFactory.getConnection(request2);

        assertThat(connection1).isSameAs(connection2);
    }

    @Test
    public void requestsToDifferentServersDontReuseConnection() throws Exception {
        HttpRequest request1 = HttpRequest.newBuilder().uri(new URI("http://www.example.com:433/index.html")).build();
        HttpRequest request2 = HttpRequest.newBuilder().uri(new URI("http://www.developer.com:433/whatever.html")).build();

        Http3ConnectionFactory connectionFactory = new Http3ConnectionFactory((Http3Client) Http3Client.newHttpClient());

        Http3Connection connection1 = connectionFactory.getConnection(request1);
        Http3Connection connection2 = connectionFactory.getConnection(request2);

        assertThat(connection1).isNotSameAs(connection2);
    }

    @Test
    public void requestsToDifferentPortsDontReuseConnection() throws Exception {
        HttpRequest request1 = HttpRequest.newBuilder().uri(new URI("http://www.example.com:433/index.html")).build();
        HttpRequest request2 = HttpRequest.newBuilder().uri(new URI("http://www.example.com:80/whatever.html")).build();

        Http3ConnectionFactory connectionFactory = new Http3ConnectionFactory((Http3Client) Http3Client.newHttpClient());

        Http3Connection connection1 = connectionFactory.getConnection(request1);
        Http3Connection connection2 = connectionFactory.getConnection(request2);

        assertThat(connection1).isNotSameAs(connection2);
    }

    @Test
    public void invalidHostnameThrowsCheckedException() throws Exception {
        Http3ConnectionFactory connectionFactory = new Http3ConnectionFactory((Http3Client) Http3Client.newHttpClient());
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("https://www.doeshopefullystillnotexist.com/")).build();

        assertThatThrownBy(
                () -> connectionFactory.getConnection(request)
        ).isInstanceOf(IOException.class);
    }

}