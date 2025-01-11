package tech.kwik.flupke.httpclient;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public class StringBodyHandlerImpl implements HttpResponse.BodyHandler<String> {

    @Override
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {

        return new HttpResponse.BodySubscriber<String>()
        {
            private List<ByteBuffer> dataBuffers = new ArrayList<>();
            private CompletableFuture<String> future = new CompletableFuture<>();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(List<ByteBuffer> item) {
                // Do not yet convert to string, as multi-byte characters might be split over two byte buffers
                dataBuffers.addAll(item);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                int size = dataBuffers.stream().mapToInt(b -> b.limit()).sum();
                ByteBuffer bytes = ByteBuffer.allocate(size);
                dataBuffers.forEach(buffer -> bytes.put(buffer));
                future.complete(new String(bytes.array()));
            }

            @Override
            public CompletionStage<String> getBody() {
                return future;
            }
        };
    }
}
