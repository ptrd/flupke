package tech.kwik.flupke.httpclient;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Flow;

public class FilePublisherImpl implements HttpRequest.BodyPublisher {

    private Path filePath;

    public FilePublisherImpl(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public long contentLength() {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        final int bufferSize = 8192;
        subscriber.onSubscribe(new Flow.Subscription() {
            BufferedInputStream inputStream = null;

            @Override
            public void request(long n) {
                try {
                    if (inputStream == null) {
                         inputStream = new BufferedInputStream(new FileInputStream(filePath.toFile()), bufferSize);
                    }
                    for (int i = 0; i < n; i++) {
                        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                        int read = inputStream.read(buffer.array());
                        if (read >= 0) {
                            buffer.limit(read);
                            subscriber.onNext(buffer);
                        } else if (read < 0) {
                            subscriber.onComplete();
                            break;
                        }
                    }
                } catch (IOException e) {
                    subscriber.onError(e);
                }
            }

            @Override
            public void cancel() {
            }
        });
    }
}
