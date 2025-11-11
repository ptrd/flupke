/*
 * Copyright © 2025 Peter Doornbosch
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
package tech.kwik.flupke.sample;

import tech.kwik.core.log.Logger;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.flupke.Http3Client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Sample application demonstrating the use of different body subscribers with the Flupke HTTP3 client.
 */
public class BodySubscribers {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Missing argument: server URL");
            System.exit(1);
        }
        new BodySubscribers().run(args);
    }

    void run(String[] args) {
        URI serverUrl = getServerUrl(args);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(serverUrl)
                .header("User-Agent", "Flupke http3 library")
                .timeout(Duration.ofSeconds(10))
                .build();

        Logger stdoutLogger = new SysOutLogger();
        stdoutLogger.useRelativeTime(true);
        stdoutLogger.logPackets(false);

        HttpClient client = Http3Client.newBuilder()
                .disableCertificateCheck()
                .logger(stdoutLogger)
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        System.setProperty("tech.kwik.core.no-security-warnings", "true");

        try {
            System.out.println("************ Executing request with String body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofString(), stringBodyProcessor);

            System.out.println("\n\n************ Executing request with InputStream body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofInputStream(), inputStreamBodyProcessor);

            System.out.println("\n\n************ Executing request with byte[] body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofByteArray(), byteArrayBodyProcessor);

            System.out.println("\n\n************ Executing request with Lines body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofLines(), linesBodyProcessor);

            System.out.println("\n\n************ Executing request with discarding body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.discarding(), v -> {});

            System.out.println("\n\n************ Executing request with ByteArrayConsumer body subscriber ************");
            File outputFile = new File("http3-response-bytes-consumer.txt");
            FileOutputStream output = new FileOutputStream(outputFile);
            Consumer<Optional<byte[]>> bodyConsumer = body -> {
                try {
                    if (body.isPresent()) {
                        output.write(body.get());
                    }
                    else {
                        output.close();
                        System.out.println("Response body written to file: " + outputFile.getAbsolutePath() + " (" + Files.size(outputFile.toPath()) + " bytes)");
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofByteArrayConsumer(bodyConsumer), v -> {});

            HttpResponse.BodyHandler<byte[]> downstreamHandler = HttpResponse.BodyHandlers.ofByteArray();
            System.out.println("\n\n************ Executing request with Buffering body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.buffering(downstreamHandler, 5000), byteArrayBodyProcessor);

            System.out.println("\n\n************ Executing request with File body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofFile(Paths.get("http3-response-file")), fileBodyProcessor);

            System.out.println("\n\n************ Executing request with Publisher body subscriber ************");
            executeRequestWith(client, request, HttpResponse.BodyHandlers.ofPublisher(), publisherBodyProcessor);

            System.out.println("\n\nDone. All requests completed.");
        }
        catch (IOException e) {
            System.err.println("Request failed: " + e.getMessage());
        }
        catch (InterruptedException e) {
            System.err.println("Request interrupted: " + e.getMessage());
        }
    }

    private static <T> void executeRequestWith(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, Consumer<T> bodyProcessor) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        HttpResponse<T> httpResponse = client.send(request, bodyHandler);
        long end = System.currentTimeMillis();
        reportResult(httpResponse, end - start, bodyProcessor);
    }

    Consumer<String> stringBodyProcessor = body -> {
        try {
            System.out.println("-   HTTP body (" + body.length() + " bytes.");
            if (body.length() > 1024) {
                String outputFile = "http3-response.txt";
                Files.write(Paths.get(outputFile), body.getBytes());
                System.out.println("Response body written to file: " + outputFile);
            } else {
                System.out.println(body);
            }
            System.out.println("Custom body processing: Body length is " + body.length());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    Consumer<InputStream> inputStreamBodyProcessor = body -> {
        try {
            System.out.println("-   HTTP body (input stream).");
            long totalBytes = body.transferTo(Files.newOutputStream(Paths.get("http3-response-stream.txt")));
            System.out.println("Response body written to file: http3-response-stream.txt (" + totalBytes + " bytes)");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    Consumer<byte[]> byteArrayBodyProcessor = body -> {
        try {
            System.out.println("-   HTTP body (byte array).");
            if (body.length > 1024) {
                String outputFile = "http3-response-bytes.txt";
                Files.write(Paths.get(outputFile), body);
                System.out.println("Response body written to file: " + outputFile);
            } else {
                System.out.println(new String(body));
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    Consumer<Path> fileBodyProcessor = body -> {
        try {
            System.out.println("-   HTTP body (file).");
            long totalBytes = Files.size(body);
            System.out.println("Response body written to file: http3-response-file (" + totalBytes + " bytes)");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    Consumer<Stream<String>> linesBodyProcessor = body -> {
        System.out.println("-   HTTP body (lines): #" + body.count() + " lines received.");
    };

    Consumer<Flow.Publisher<List<ByteBuffer>>> publisherBodyProcessor = bodyPublisher -> {
        try {
            FileOutputStream output = new FileOutputStream("http3-response-publisher.txt");
            WriteToFileSubscriber subscriber = new WriteToFileSubscriber(output);
            bodyPublisher.subscribe(subscriber);
            subscriber.getResult().join();
        }
        catch (IOException e) {
            System.out.println("Error creating output file: " + e.getMessage() + ". Cannot subscribe to publisher.");
            throw new RuntimeException(e);
        }
    };

    static class WriteToFileSubscriber implements Flow.Subscriber<List<ByteBuffer>> {

        private final FileOutputStream output;
        private Flow.Subscription subscription;
        private CompletableFuture<Void> result = new CompletableFuture<>();

        WriteToFileSubscriber(FileOutputStream output) {
            this.output = output;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            try {
                for (ByteBuffer buffer : item) {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    output.write(bytes);
                }
                subscription.request(1);
            }
            catch (IOException e) {
                subscription.cancel();
                System.out.println("Error writing to file: " + e.getMessage() + ". Subscription cancelled.");
            }
        }

        @Override
        public void onError(Throwable throwable) {
            try {
                output.close();
            }
            catch (IOException e) {
                subscription.cancel();
                System.out.println("Error writing to file: " + e.getMessage() + ". Subscription cancelled.");
            }
            finally {
                System.out.println("Error received from publisher: " + throwable.getMessage());
                result.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            try {
                output.close();
                System.out.println("Response body written to file: http3-response-publisher.txt");
            }
            catch (IOException e) {
            }
            finally {
                result.complete(null);
            }
        }

        public CompletableFuture<Void> getResult() {
            return result;
        }
    }

    private static URI getServerUrl(String[] args) {
        String input;
        if (args.length == 2) {
            input = args[0] + ":" + args[1];
        } else {
            input = args[0];
        }
        if (!input.contains("://")) {
            input = "https://" + input;
        }

        return URI.create(input);
    }

    private static <T> void reportResult(HttpResponse<T> httpResponse, long duration, Consumer<T> bodyProcessor) throws IOException {
        System.out.println("Request completed in " + duration + " ms");
        System.out.println("Got HTTP response " + httpResponse);
        System.out.println("-   HTTP headers: ");
        httpResponse.headers().map().forEach((k, v) -> System.out.println("--  " + k + "\t" + v));
        bodyProcessor.accept(httpResponse.body());
    }
}
