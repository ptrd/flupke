/*
 * Copyright © 2024, 2025 Peter Doornbosch
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

import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportHttp3ApplicationProtocolFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static tech.kwik.flupke.server.Http3ApplicationProtocolFactory.HTTP3_PROTOCOL_ID;

/**
 * A simple echo server for WebTransport.
 */
public class WebTransportEchoServer {

    private final File certificate;
    private final File key;
    private final int port;

    public static void main(String[] args) throws Exception {
        List<String> argList = List.of(args);
        if (argList.size() != 3) {
            usageAndExit();
        }

        File certFile = new File(argList.get(0));
        File certKeyFile = new File(argList.get(1));

        if (! certFile.exists() || ! certKeyFile.exists()) {
            usageAndExit();
        }
        int port = Integer.parseInt(argList.get(2));
        new WebTransportEchoServer(certFile, certKeyFile, port).start();
    }

    private static void usageAndExit() {
        System.err.println("Usage: cert file, cert key file, port number");
        System.exit(1);
    }

    public WebTransportEchoServer(File certificate, File key, int port) {
        this.certificate = certificate;
        this.key = key;
        this.port = port;
    }

    private void start() throws Exception {
        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                // Need to specify how many streams the client is allowed to have open concurrently in one QUIC connection, see Kwik readme for more info.
                .maxOpenPeerInitiatedUnidirectionalStreams(3)  // HTTP/3 requires at least 3, this echo protocol doesn't need an additional amount as it does not use unidirectional streams
                .maxOpenPeerInitiatedBidirectionalStreams(10)   // HTTP/3 CONNECT request requires 1, so minimum is 2 (to allow at least 1 bidirectional WebTransport stream)
                .build();

        SysOutLogger log = new SysOutLogger();
        log.logInfo(true);

        ServerConnector serverConnector = ServerConnector.builder()
                .withPort(port)
                .withCertificate(new FileInputStream(certificate), new FileInputStream(key))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .build();

        HttpRequestHandler dummyGetHandler = (request, response) -> {
            if (request.method().equals("GET")) {
                response.setStatus(200);
            }
            else {
                response.setStatus(405);
            }
        };
        // Set a handler for non-CONNECT requests; a no-op handler (e.g. (req, resp) -> {} ) would also work, but would
        // always return an error status (4xx), which might be confusing.
        HttpRequestHandler httpRequestHandler = dummyGetHandler;

        WebTransportHttp3ApplicationProtocolFactory webTransportProtocolFactory = new WebTransportHttp3ApplicationProtocolFactory(httpRequestHandler);
        webTransportProtocolFactory.registerWebTransportServer("/echo", this::startEchoHandler);
        serverConnector.registerApplicationProtocol(HTTP3_PROTOCOL_ID, webTransportProtocolFactory);
        serverConnector.start();
    }

    private void startEchoHandler(Session session) {
        System.out.println("Starting echo handler for WebTransport session: " + session.getSessionId());

        session.registerSessionTerminatedEventListener((errorCode, message) -> {
            System.out.println("Session " + session.getSessionId() + " closed with error code " + errorCode);
        });

        session.setBidirectionalStreamReceiveHandler(stream -> {
            try {
                stream.getInputStream().transferTo(stream.getOutputStream());
                stream.getOutputStream().close();
                System.out.println("Processed a request for session " + session.getSessionId() + " response sent");
            }
            catch (IOException e) {
                System.out.println("IO error while processing request: " + e.getMessage());
            }
        });

        // Make sure the session is opened _after_ setting the handlers, otherwise we might miss incoming streams.
        session.open();
    }
}
