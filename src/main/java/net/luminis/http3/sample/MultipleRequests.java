package net.luminis.http3.sample;

import net.luminis.http3.Http3Client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;


public class MultipleRequests {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Missing argument, expected: <server address> <path> [<path>...]");
            System.exit(1);
        }

        HttpClient client = Http3Client.newHttpClient();
        for (int i = 1; i < args.length; i++) {
            URI requestUri = URI.create("http://" + args[0] + "/" + args[i]);
            HttpRequest request = HttpRequest.newBuilder().uri(requestUri).build();
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Got HTTP response " + httpResponse);
            System.out.println("-   HTTP headers: " + httpResponse.headers());
            System.out.println("-   HTTP body (" + httpResponse.body().length() + " bytes):");
            if (httpResponse.body().length() > 10 * 1024) {
                String outputFile = "http3-response.txt";
                Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
                System.out.println("Response written to file: " + outputFile);
            } else {
                System.out.println(httpResponse.body());
            }
        }

    }
}
