package net.luminis.httpclient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

public class StringBodyPublisherImpl implements HttpRequest.BodyPublisher {

    private final byte[] bodyBytes;

    public StringBodyPublisherImpl(String body) {
        bodyBytes = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public long contentLength() {
        return bodyBytes.length;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (n > 0) {
                    subscriber.onNext(ByteBuffer.wrap(bodyBytes));
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
            }
        });
    }
}
