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
package tech.kwik.flupke.sample;

import tech.kwik.core.log.Logger;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.flupke.Http3Client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

public class BodyHandlerWithStream {

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {

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

        Logger stdoutLogger = new SysOutLogger();
        stdoutLogger.useRelativeTime(true);
        stdoutLogger.logPackets(false);

        HttpClient client = Http3Client.newBuilder()
                .disableCertificateCheck()
                .logger(stdoutLogger)
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        try {
            long start = System.currentTimeMillis();
            HttpResponse<InputStream> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            OutputStream outputFile = Files.newOutputStream(java.nio.file.Paths.get("http3response.dat"));
            long contentSize = httpResponse.body().transferTo(outputFile);
            long end = System.currentTimeMillis();
            System.out.println("Request (" + contentSize + " bytes) completed in " + (end - start) + " ms");
        }
        catch (IOException e) {
            System.err.println("Request failed: " + e.getMessage());
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
