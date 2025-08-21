/*
 * Copyright © 2024 Peter Doornbosch
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
package tech.kwik.flupke.sample.webtransport;

import tech.kwik.core.log.Logger;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.Http3ClientBuilder;
import tech.kwik.flupke.core.HttpError;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;
import tech.kwik.flupke.webtransport.impl.ClientSessionFactoryImpl;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public class WebTransportEchoClient {

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length != 1) {
            System.err.println("Missing argument: server URL");
            System.exit(1);
        }

        URI serverUrl = URI.create(args[0]);

        Logger stdoutLogger = new SysOutLogger();

        Http3Client client = (Http3Client) ((Http3ClientBuilder) Http3Client.newBuilder())
                .disableCertificateCheck()
                .logger(stdoutLogger)
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        try {
            ClientSessionFactoryImpl clientSessionFactory = new ClientSessionFactoryImpl(serverUrl, client);

            int count = clientSessionFactory.getMaxConcurrentSessions();
            for (int i = 0; i < count; i++) {
                Session session = clientSessionFactory.createSession(serverUrl);
                session.open();
                System.out.println("Session " + session.getSessionId() + " opened to " + serverUrl);
                WebTransportStream bidirectionalStream = session.createBidirectionalStream();

                String message = "Hello, world! (" + (i+1) + ")";
                bidirectionalStream.getOutputStream().write(message.getBytes());
                System.out.println("Request sent to " + serverUrl + ": " + message);
                bidirectionalStream.getOutputStream().close();
                System.out.print("Response: ");
                bidirectionalStream.getInputStream().transferTo(System.out);
                System.out.println();
                session.close();
                System.out.println("Session closed. ");
            }
            System.out.println("That's it! Bye!");
        }
        catch (IOException | HttpError e) {
            System.err.println("Request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
