package net.luminis.httpclient;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpHeadersTest {

    @Test
    public void createWithTruePredicateReturnsAll() {
        HttpHeaders headers = HttpHeaders.of(Map.of("key", List.of("value1", "value2")), (s1, s2) -> true);

        assertThat(headers.map()).isEqualTo(Map.of("key", List.of("value1", "value2")));
    }

    @Test
    public void createWithFalsePredicateReturnsNone() {
        HttpHeaders headers = HttpHeaders.of(Map.of("key", List.of("value1", "value2")), (s1, s2) -> false);

        assertThat(headers.map()).isEmpty();
    }

    @Test
    public void createWitValuePredicateReturnsSomeValues() {
        HttpHeaders headers = HttpHeaders.of(Map.of("key", List.of("value1", "value2")), (s1, s2) -> s2.endsWith("2"));

        assertThat(headers.map()).isEqualTo(Map.of("key", List.of("value2")));
    }

    @Test
    public void createWitKeyPredicateReturnsSomeValues() {
        HttpHeaders headers = HttpHeaders.of(
                Map.of("key1", List.of("value1", "value2"),
                        "key2", List.of("value3", "value4")),
                (s1, s2) -> s1.endsWith("2"));

        assertThat(headers.map()).isEqualTo(Map.of("key2", List.of("value3", "value4")));
    }
}