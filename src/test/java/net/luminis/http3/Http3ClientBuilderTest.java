package net.luminis.http3;

import org.junit.Test;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;


public class Http3ClientBuilderTest {

    @Test
    public void testBuilderCreatesHttp3Client() {
        HttpClient client = new Http3ClientBuilder().build();

        assertThat(client).isInstanceOf(Http3Client.class);
    }
}
