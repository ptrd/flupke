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
package net.luminis.http3.webtransport.sample;

import net.luminis.http3.Http3Client;
import net.luminis.http3.Http3ClientBuilder;
import net.luminis.http3.core.HttpError;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;
import net.luminis.http3.webtransport.impl.ClientSessionFactoryImpl;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;

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

            int count = 3;
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
