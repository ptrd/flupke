/*
 * Copyright © 2025 Peter Doornbosch
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
package tech.kwik.flupke.sample.webtransport.baton;

import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.Http3ClientBuilder;
import tech.kwik.flupke.HttpError;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.impl.ClientSessionFactoryImpl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BatonClient {

    private final Http3Client httpClient;
    private final ClientSessionFactoryImpl sessionFactory;
    private final URI serverUrl;

    public BatonClient(URI serverUrl) throws IOException, URISyntaxException {

        Http3ClientBuilder builder = Http3Client.newBuilder();
        httpClient = (Http3Client) builder
                .disableCertificateCheck()
                .maxAdditionalOpenPeerInitiatedUnidirectionalStreams(100)
                .maxAdditionalOpenPeerInitiatedBidirectionalStreams(100)
                .build();

        sessionFactory = new ClientSessionFactoryImpl(serverUrl, httpClient);
        this.serverUrl = serverUrl;
    }

    CountDownLatch startSession() throws URISyntaxException {
        int minInitial = 150;
        int start = minInitial + new Random().nextInt(256 - minInitial);
        String parameters = "?baton=" + start;
        URI sessionUrl = new URI(serverUrl + parameters);

        CountDownLatch sessionActive = new CountDownLatch(1);
        try {
            BatonSession baton = new BatonSession();
            Session session = sessionFactory.createSession(sessionUrl, baton::unidirectionalStreamHandler, baton::bidirectionalStreamHandler);
            session.registerSessionTerminatedEventListener((code, msg) -> {
                sessionActive.countDown();
            });
            System.out.println("Starting session with " + sessionUrl);
            baton.setWebTransportSession(session);
        }
        catch (HttpError httpError) {
            System.out.println("Error creating session with http status code " + httpError.getStatusCode());
            System.exit(1);
        }
        catch (IOException e) {
            System.out.println("Error creating session: " + e.getMessage());
            System.exit(1);
        }
        return sessionActive;
    }

    public static void main(String[] args) throws Exception {
        String serverUrl = args.length > 0 && isUrl(args[0]) ? args[0] : "https://localhost:4433/baton";

        BatonClient client = new BatonClient(new URI(serverUrl));
        client.startSession().await();
        client.startSession().await();
        client.startSession().await();
        System.out.println("All sessions completed");
    }

    private static boolean isUrl(String arg) {
        return arg.startsWith("http://") || arg.startsWith("https://");
    }
}
