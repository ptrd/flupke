package net.luminis.http3.sample;

import net.luminis.http3.Http3Client;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncHttp3 {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Excpected arguments: <download-dir> <url1> [<url2>, ....]");
            return;
        }

        File downloadDir = new File(args[0]);
        if (downloadDir.exists() && !downloadDir.isDirectory()) {
            System.out.println("'" + downloadDir + "' exists, but is not a directory");
            return;
        }
        if (! downloadDir.exists()) {
            downloadDir.mkdir();
        }

        URI[] downloadUrls = new URI[args.length-1];
        for (int i = 0; i < args.length-1; i++) {
            downloadUrls[i] = new URI(args[i+1]);
        }

        HttpClient client = Http3Client.newHttpClient();
        CompletableFuture<HttpResponse<Path>>[] results = new CompletableFuture[downloadUrls.length];
        for (int i = 0; i < downloadUrls.length; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(downloadUrls[i]).build();
            String outputFile = new File(downloadDir, downloadUrls[i].getPath()).getAbsolutePath();
            HttpResponse.BodyHandler<Path> responseBodyHandler = HttpResponse.BodyHandlers.ofFile(Paths.get(outputFile));

            System.out.println("Starting asynchronous request for " + downloadUrls[i]);
            results[i] = client.sendAsync(request, responseBodyHandler);
            results[i].thenApply(response -> {
                System.out.println("Done: " + response + ", response body written to " + outputFile);
                return true;
            });
        }

        // Wait for all requests to finish.
        try {
            for (int i = 0; i < downloadUrls.length; i++) {
                results[i].get(1, TimeUnit.MINUTES);   // TODO: time out of 1 minute is ok for the interop test, but for other use cases....
            }
        }
        catch (TimeoutException timeout) {
            System.out.println("Request time out.");
        }

        System.out.println("Terminating.");
    }
}
