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
package tech.kwik.flupke.httpbin;

import tech.kwik.core.QuicConnection;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.Http3ApplicationProtocolFactory;
import tech.kwik.flupke.server.HttpRequestHandler;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public class HttpBinServer {

    private final File certificate;
    private final File key;
    private final int port;

    public static void main(String[] args) throws Exception {
        boolean retry = false;
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
        new HttpBinServer(certFile, certKeyFile, port).start();
    }

    private static void usageAndExit() {
        System.err.println("Usage: cert file, cert key file, port number");
        System.exit(1);
    }

    public HttpBinServer(File certificate, File key, int port) {
        this.certificate = certificate;
        this.key = key;
        this.port = port;
    }

    private void start() throws Exception {

        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxIdleTimeoutInSeconds(30)
                .maxUnidirectionalStreamBufferSize(1_000_000)
                .maxBidirectionalStreamBufferSize(1_000_000)
                .maxConnectionBufferSize(10_000_000)
                .maxOpenPeerInitiatedUnidirectionalStreams(10)
                .maxOpenPeerInitiatedBidirectionalStreams(100)
                .retryRequired(false)
                .connectionIdLength(8)
                .build();

        SysOutLogger log = new SysOutLogger();
        log.logInfo(true);

        ServerConnector serverConnector = ServerConnector.builder()
                .withPort(port)
                .withCertificate(new FileInputStream(certificate), new FileInputStream(key))
                .withSupportedVersions(List.of(QuicConnection.QuicVersion.V1))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .build();


        HttpRequestHandler httpbinRequestHandler = new HttpBinRequestHandler();
        Http3ApplicationProtocolFactory http3ApplicationProtocolFactory = new Http3ApplicationProtocolFactory(httpbinRequestHandler);
        serverConnector.registerApplicationProtocol("h3", http3ApplicationProtocolFactory);
        serverConnector.start();
    }

}
