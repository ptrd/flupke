/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024 Peter Doornbosch
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
package net.luminis.http3.sample;

import net.luminis.http3.Http3ClientBuilder;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncHttp3 {

    public static final String DISABLE_CERT_CHECK_OPTION = "--disableCertificateCheck";

    public static void main(String[] args) throws Exception {
        int argStartIndex = 0;
        if (args.length >= 1 && args[0].equals(DISABLE_CERT_CHECK_OPTION)) {
            argStartIndex++;
        }
        if (argStartIndex + args.length < 2) {
            System.out.println("Excpected arguments: [" + DISABLE_CERT_CHECK_OPTION + "] <download-dir> <url1> [<url2>, ....]");
            return;
        }

        File downloadDir = new File(args[argStartIndex]);
        if (downloadDir.exists() && !downloadDir.isDirectory()) {
            System.out.println("'" + downloadDir + "' exists, but is not a directory");
            return;
        }
        if (! downloadDir.exists()) {
            downloadDir.mkdir();
        }

        int nrOfDownloads = args.length - 1 - argStartIndex;
        URI[] downloadUrls = new URI[nrOfDownloads];
        for (int i = 0; i < nrOfDownloads; i++) {
            downloadUrls[i] = new URI(args[argStartIndex + 1 + i]);
        }

        HttpClient client = new Http3ClientBuilder()
                .disableCertificateCheck()
                .build();
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
        CompletableFuture<Void> allResults = CompletableFuture.allOf(results);
        Duration timeout = Duration.ofMinutes(10);
        try {
            allResults.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (ExecutionException executionException) {
            System.out.println("At least one response failed: " + executionException.getCause());
        }
        catch (TimeoutException timedOut) {
            System.out.println("Not all responses are received within timeout of " + timeout);
        }

        System.out.println("Terminating.");
    }
}
