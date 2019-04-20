package net.luminis.http3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Sample {

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length != 1) {
            System.err.println("Missing argument: server URL");
            System.exit(1);
        }

        URI serverUrl = URI.create(args[0]);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(serverUrl)
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpClient client = Http3Client.newHttpClient();
        HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Got HTTP response " + httpResponse);

    }
}
