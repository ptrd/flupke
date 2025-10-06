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

import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportHttp3ApplicationProtocolFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class BatonServer {

    private final File certificate;
    private final File key;
    private final int port;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usageAndExit();
        }
        List<String> argList = List.of(args);
        File certFile = new File(argList.get(0));
        File certKeyFile = new File(argList.get(1));

        if (! certFile.exists() || ! certKeyFile.exists()) {
            usageAndExit();
        }
        int port = Integer.parseInt(argList.get(2));
        new BatonServer(certFile, certKeyFile, port).start();
    }

    private static void usageAndExit() {
        System.err.println("Usage: cert file, cert key file, port number");
        System.exit(1);
    }

    public BatonServer(File certificate, File key, int port) {
        this.certificate = certificate;
        this.key = key;
        this.port = port;
    }

    private void start() throws Exception {
        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxIdleTimeoutInSeconds(30)
                .maxUnidirectionalStreamBufferSize(1_000)
                .maxBidirectionalStreamBufferSize(1_000)
                .maxConnectionBufferSize(10_000)
                .maxOpenPeerInitiatedUnidirectionalStreams(2)
                .maxTotalPeerInitiatedUnidirectionalStreams(255)
                .maxOpenPeerInitiatedBidirectionalStreams(2)
                .maxTotalPeerInitiatedBidirectionalStreams(255)
                .retryRequired(false)
                .connectionIdLength(8)
                .build();

        SysOutLogger log = new SysOutLogger();
        log.logInfo(true);

        ServerConnector serverConnector = ServerConnector.builder()
                .withPort(port)
                .withCertificate(new FileInputStream(certificate), new FileInputStream(key))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .build();

        HttpRequestHandler httpNoOpRequestHandler = (request, response) -> {};
        WebTransportHttp3ApplicationProtocolFactory webTransportProtocolFactory = new WebTransportHttp3ApplicationProtocolFactory(httpNoOpRequestHandler);
        webTransportProtocolFactory.registerWebTransportServer("/baton", this::startBatonSession);
        serverConnector.registerApplicationProtocol("h3", webTransportProtocolFactory);
        serverConnector.start();
    }

    void startBatonSession(Session webTransportSession) {
        BatonSession baton = new BatonSession(webTransportSession);

        webTransportSession.setUnidirectionalStreamReceiveHandler(baton::unidirectionalStreamHandler);
        webTransportSession.setBidirectionalStreamReceiveHandler(baton::bidirectionalStreamHandler);
        webTransportSession.open();

        // Start the batons!
        try {
            String query = new URI(webTransportSession.getPath()).getQuery();
            Optional<Integer> initialBattonValue = Pattern.compile("&").splitAsStream(query)
                    .filter(s -> s.startsWith("baton="))
                    .map(s -> s.substring("baton=".length()))
                    .filter(s -> s.matches("\\d+"))
                    .map(Integer::parseInt)
                    .findFirst();

            baton.start(initialBattonValue.orElse(0));
        }
        catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
