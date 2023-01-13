/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;


public class MultipleRequests {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Missing argument, expected: <server address> <path> [<path>...]");
            System.exit(1);
        }

        HttpClient client = Http3Client.newHttpClient();
        for (int i = 1; i < args.length; i++) {
            String path = args[i];
            String outputFile = "http3-response" + i + ".txt";
            new Thread(() -> {
                URI requestUri = URI.create("http://" + args[0] + "/" + path);
                HttpRequest request = HttpRequest.newBuilder().uri(requestUri).build();
                HttpResponse<String> httpResponse = null;
                try {
                    httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Got HTTP response " + httpResponse);
                System.out.println("-   HTTP headers: " + httpResponse.headers());
                System.out.println("-   HTTP body (" + httpResponse.body().length() + " bytes):");
                if (httpResponse.body().length() > 10 * 1024) {
                    try {
                        Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Response written to file: " + outputFile);
                } else {
                    System.out.println(httpResponse.body());
                }
            }).start();
        }

    }
}
