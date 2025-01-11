/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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

import net.luminis.http3.Http3Client;
import net.luminis.http3.Http3ClientBuilder;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
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
                .header("User-Agent", "Flupke http3 library")
                .timeout(Duration.ofSeconds(10))
                .build();

        // Easiest way to create a client with default configuration
        HttpClient defaultClient = Http3Client.newHttpClient();

        // For non-default configuration, use the builder
        Logger stdoutLogger = new SysOutLogger();
        stdoutLogger.useRelativeTime(true);
        stdoutLogger.logPackets(true);

        HttpClient client = ((Http3ClientBuilder) Http3Client.newBuilder())
                .logger(stdoutLogger)
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        try {
            long start = System.currentTimeMillis();
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            long end = System.currentTimeMillis();
            reportResult(httpResponse, end - start);
        }
        catch (IOException e) {
            System.err.println("Request failed: " + e.getMessage());
        }
        catch (InterruptedException e) {
            System.err.println("Request interrupted: " + e.getMessage());
        }
    }

    private static void reportResult(HttpResponse<String> httpResponse, long duration) throws IOException {
        System.out.println("Request completed in " + duration + " ms");
        System.out.println("Got HTTP response " + httpResponse);
        System.out.println("-   HTTP headers: ");
        httpResponse.headers().map().forEach((k, v) -> System.out.println("--  " + k + "\t" + v));
        long downloadSpeed = httpResponse.body().length() / duration;
        System.out.println("-   HTTP body (" + httpResponse.body().length() + " bytes, " + downloadSpeed + " KB/s):");
        if (httpResponse.body().length() > 10 * 1024) {
            String outputFile = "http3-response.txt";
            Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
            System.out.println("Response body written to file: " + outputFile);
        }
        else {
            System.out.println(httpResponse.body());
        }
    }
}
