package net.luminis.http3.sample;

import net.luminis.http3.Http3Client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class AsyncHttp3 {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Excpected arguments: <server:port> <path> [<path>....]");
            return;
        }

        String serverAddress = args[0];

        HttpClient client = Http3Client.newHttpClient();
        CompletableFuture<HttpResponse<String>>[] results = new CompletableFuture[args.length - 1];
        for (int i = 0; i < args.length-1; i++) {
            String path = args[i+1];
            URI requestUri = URI.create("https://" + serverAddress + "/" + path);
            HttpRequest request = HttpRequest.newBuilder().uri(requestUri).build();
            HttpResponse<String> httpResponse = null;

            System.out.println("Starting asynchronous request for " + requestUri);
            results[i] = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            results[i].thenApply(response -> { System.out.println("Already done: " + response); return true; });
        }

        // Wait for all requests to finish.
        for (int i = 0; i < args.length-1; i++) {
            results[i].get();
        }

        // Print results
        for (int i = 0; i < args.length-1; i++) {
            HttpResponse<String> httpResponse = results[i].get();
            System.out.println("Got HTTP response " + httpResponse);
            System.out.println("-   HTTP headers: " + httpResponse.headers());
            System.out.println("-   HTTP body (" + httpResponse.body().length() + " bytes):");
            String outputFile = "http-async-response" + i + ".txt";
            try {
                Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
                System.out.println("Response written to file: " + outputFile);
            } catch (IOException e) {
                System.out.println("Writing result to file failed: "+ e);
            }
        }

        System.out.println("Terminating.");
    }
}
