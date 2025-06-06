package tech.kwik.flupke.httpclient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;

public class HttpRequest {

    private final String method;
    private final URI uri;
    private final HttpHeaders headers;
    private final BodyPublisher bodyPublisher;
    private Optional<Duration> timeout;

    public static HttpRequest.Builder newBuilder() {
        return new Builder();
    }

    private HttpRequest(String method, URI uri, Map<String, List<String>> headers, BodyPublisher bodyPublisher, Optional<Duration> timeout) {
        this.method = method;
        this.uri = uri;
        this.headers = new HttpHeaders(headers);
        this.bodyPublisher = bodyPublisher;
        this.timeout = timeout;
    }

    public URI uri() {
        return uri;
    }

    public String method() {
        return method;
    }

    public HttpHeaders headers() {
        return new HttpHeaders();
    }

    public Optional<Duration> timeout() {
        return timeout;
    }

    public Optional<HttpRequest.BodyPublisher> bodyPublisher() {
        return Optional.ofNullable(bodyPublisher);
    }

    public interface BodyPublisher extends Flow.Publisher<ByteBuffer> {
        long contentLength();
    }

    public static class Builder {

        private String method = "GET";
        private URI uri;
        private BodyPublisher bodyPublisher;
        private Map<String, List<String>> headers = new HashMap<>();
        private Optional<Duration> timeout = Optional.empty();

        public HttpRequest build() {
            return new HttpRequest(method, uri, headers, bodyPublisher, timeout);
        }

        public Builder header(String name, String value) {
            headers.compute(name, (key, old) -> old == null? new ArrayList<>(List.of(value)): addToList(old, value));
            return this;
        }

        public Builder timeout(Duration duration) {
            this.timeout = Optional.of(duration);
            return this;
        }

        public HttpRequest.Builder POST(HttpRequest.BodyPublisher bodyPublisher) {
            this.bodyPublisher = bodyPublisher;
            method = "POST";
            return this;
        }

        public Builder uri(URI serverUrl) {
            this.uri = serverUrl;
            return this;
        }

        static private List<String> addToList(List<String> list, String value) {
            list.add(value);
            return list;
        }
    }

    public static class BodyPublishers {

        public static HttpRequest.BodyPublisher ofString(String body) {
            return new StringBodyPublisherImpl(body);
        }

        public static BodyPublisher ofFile(Path inputPath) {
            return new FilePublisherImpl(inputPath);
        }
    }
}
