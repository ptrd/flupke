package net.luminis.httpclient;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;


public class FileBodyHandlerImpl implements HttpResponse.BodyHandler<Path> {

    private Path file;

    public FileBodyHandlerImpl(Path file) {
        this.file = file;
    }

    @Override
    public HttpResponse.BodySubscriber<Path> apply(HttpResponse.ResponseInfo responseInfo)
    {
        return new HttpResponse.BodySubscriber<Path>() {
            private FileOutputStream outputStream;
            private Flow.Subscription subscription;
            private CompletableFuture<Path> future = new CompletableFuture<>();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
                try {
                    outputStream = new FileOutputStream(file.toFile());
                } catch (FileNotFoundException e) {
                    subscription.cancel();
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onNext(List<ByteBuffer> buffers) {
                buffers.forEach(buffer -> {
                    try {
                        outputStream.write(buffer.array());
                    } catch (IOException e) {
                        subscription.cancel();
                        throw new RuntimeException(e);
                    }
                });
            }

            @Override
            public void onComplete() {
                try {
                    future.complete(file);
                    outputStream.close();
                } catch (IOException e) {
                    // Never mind.
                }
            }

            @Override
            public CompletionStage<Path> getBody() {
                return future;
            }
        };
    }
}
