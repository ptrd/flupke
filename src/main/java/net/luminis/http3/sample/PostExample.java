/*
 * Copyright © 2019 Peter Doornbosch
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Sends a POST request with body content read from given file.
 */
public class PostExample {

    public static void main(String[] args) throws IOException, InterruptedException {

        try {
            if (args.length != 2) {
                System.err.println("Missing argument(s). Usage: <file to post> <server url>");
                System.exit(1);
            }

            Path inputPath = Path.of(args[0]);
            File inputFile = inputPath.toFile();
            if (!inputFile.exists()) {
                System.err.println("Input file '" + inputFile + "' does not exist.");
            }
            if (!inputFile.canRead()) {
                System.err.println("Input file '" + inputFile + "' is not readable.");
            }

            URI serverUrl = URI.create(args[1]);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(serverUrl)
                    .POST(HttpRequest.BodyPublishers.ofFile(inputPath))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpClient client = Http3Client.newHttpClient();
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
            System.out.println("Connection statistics: " + ((Http3Client) client).getConnectionStatistics());
        }
        catch (Exception e) {
            System.out.println("PostExample: " + e);
        }
    }
}
