/*
 * Copyright © 2026 Peter Doornbosch
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
package tech.kwik.flupke.sample.webtransport.interop;

import tech.kwik.core.QuicConnection;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportHttp3ApplicationProtocolFactory;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static tech.kwik.flupke.server.Http3ApplicationProtocolFactory.HTTP3_PROTOCOL_ID;

/**
 * Server for WebTransport interop testing.
 * see https://github.com/quic-interop/quic-interop-runner/blob/master/webtransport.md
 *
 * TODO: maybe move to interop module? (like it's done in kwik)
 */
public class WebTransportInteropServer {

    private final boolean retry;
    private final KeyStore keyStore;
    private final String certificateAlias;
    private final String keyPassword;
    private final int port;
    private final File wwwDir;

    public WebTransportInteropServer(boolean retry, KeyStore keyStore, String certificateAlias, String keyPassword, int port, File wwwDir) {
        this.retry = retry;
        this.keyStore = keyStore;
        this.certificateAlias = certificateAlias;
        this.keyPassword = keyPassword;
        this.port = port;
        this.wwwDir = wwwDir;
    }

    public static void main(String[] args) throws Exception {
        boolean retry = false;
        if (args.length < 5) {
            System.out.println("Expected at least 5 arguments, but got " + args.length);
            usageAndExit(Arrays.asList(args));
        }
        List<String> argList = List.of(args);
        if (argList.size() != 5 && argList.size() != 6) {
            System.out.println("Expected 5 or 6 arguments, but got " + argList.size());
            usageAndExit(argList);
        }

        File keyStoreFile = new File(argList.get(0));
        if (!keyStoreFile.exists()) {
            System.err.println("Cannot open keystore file " + argList.get(0));
            System.exit(1);
        }

        String certificateAlias = argList.get(1);
        String keyStorePassword = argList.get(2);
        String keyPassword = argList.get(3);

        KeyStore keyStore = KeyStore.getInstance(keyStoreFile, keyStorePassword.toCharArray());
        if (keyStore.getCertificateChain(certificateAlias) == null) {
            System.err.println("Certificate alias '" + certificateAlias + "' not found in keystore");
            System.exit(1);
        }

        int port = Integer.parseInt(argList.get(4));

        File wwwDir = null;
        if (argList.size() > 5) {
            wwwDir = new File(argList.get(5));
            if (!wwwDir.exists() || !wwwDir.isDirectory() || !wwwDir.canRead()) {
                System.err.println("Cannot read www dir '" + wwwDir + "'");
                System.exit(1);
            }
        }
        new WebTransportInteropServer(retry, keyStore, certificateAlias, keyPassword, port, wwwDir).start();
    }

    private static void usageAndExit(List<String> actualArgs) {
        System.err.println("Usage: keystore-file cert-alias keystore-password key-password port-number [www dir]");
        System.out.println("Actual args: " + actualArgs);
        System.exit(1);
    }


    private void start() throws Exception {
        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxIdleTimeoutInSeconds(30)
                .maxUnidirectionalStreamBufferSize(1_000_000)
                .maxBidirectionalStreamBufferSize(1_000_000)
                .maxConnectionBufferSize(10_000_000)
                .maxOpenPeerInitiatedUnidirectionalStreams(10)
                .maxOpenPeerInitiatedBidirectionalStreams(100)
                .retryRequired(retry)
                .connectionIdLength(8)
                .build();

        SysOutLogger log = new SysOutLogger();
        log.logInfo(true);

        ServerConnector serverConnector = ServerConnector.builder()
                .withPort(port)
                .withKeyStore(keyStore, certificateAlias, keyPassword.toCharArray())
                .withSupportedVersions(List.of(QuicConnection.QuicVersion.V1))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .build();

        String webTransportPath = determineWebTransportEndpoints();
        List<String> supportedApplicationProtocols = determineProtocols();

        HttpRequestHandler httpRequestHandler = (request, response) -> {
            if (request.path().equals(webTransportPath)) {
                if (!request.method().equals("CONNECT")) {
                    System.out.println("Received non-CONNECT request on WebTransport path: " + request.method() + " " + request.path());
                    response.setStatus(405);  // Method Not Allowed
                    response.getOutputStream().write(("Method " + request.method() + " not allowed. " +
                            "This is a WebTransport server. " +
                            "Use CONNECT method to establish a WebTransport session.\n").getBytes());
                }
                else {
                    System.out.println("Received valid CONNECT request for WebTransport session on path " + request.path());
                }
            }
            else {
                System.out.println("Received request for non-WebTransport path: " + request.method() + " " + request.path());
                response.setStatus(404);  // Not Found
            }
        };

        WebTransportHttp3ApplicationProtocolFactory webTransportProtocolFactory = new WebTransportHttp3ApplicationProtocolFactory(httpRequestHandler);
        System.out.println("Starting WebTransport server on path " + webTransportPath);
        webTransportProtocolFactory.setMaxStreamsQueued(10);
        webTransportProtocolFactory.registerWebTransportServer(webTransportPath, supportedApplicationProtocols, this::startWebtransport);
        serverConnector.registerApplicationProtocol(HTTP3_PROTOCOL_ID, webTransportProtocolFactory);
        serverConnector.start();
    }

    private List<String> determineProtocols() {
        String protocolsEnvVar = System.getenv("PROTOCOLS");
        List<String> protocols = Arrays.stream(protocolsEnvVar.split(" ")).collect(Collectors.toList());
        return protocols;
    }

    private String determineWebTransportEndpoints() {
        // The server MAY determine expected WebTransport endpoints by scanning the /www/ directory for top-level subdirectories at startup. Each such subdirectory name (e.g., webtransport1 in /www/webtransport1/) corresponds to an endpoint path (e.g., /webtransport1), including empty subdirectories for session-only tests like handshake. When a session is established on a path with a matching subdirectory, the server MUST serve requested files from the corresponding subpaths within /www/. Alternatively, implementations MAY accept sessions on any path without scanning or restricting, serving files relative to the session path if a matching structure exists in /www/.
        // Scan /www/ for top-level subdirectories and log them as expected WebTransport endpoints.
        File[] subdirs = wwwDir.listFiles(File::isDirectory);
        if (subdirs != null) {
            System.out.println("Expected WebTransport endpoints:");
            for (File subdir : subdirs) {
                // if subdir is empty, print it as well, as it can be used for session-only tests like handshake
                if (subdir.listFiles() == null || subdir.listFiles().length == 0) {
                    System.out.println(" - " + subdir.getName() + " (empty, for session-only tests)");
                }
                else {
                    System.out.println(" - " + subdir.getName());
                }
            }
        }
        return subdirs != null && subdirs.length > 0 ? "/" + subdirs[0].getName() : "/webtransport";
    }

    private void startWebtransport(Session session) {
        System.out.println("Starting handler for WebTransport session: " + session.getSessionId());
        File sessionDir = new File(wwwDir, session.getPath());
        if (sessionDir.exists() && sessionDir.isDirectory() && sessionDir.canRead()) {
            System.out.println("Serving files for session " + session.getSessionId() + " from directory " + sessionDir.getPath());
        }
        else {
            System.out.println("No directory found for session " + session.getSessionId() + ", not serving any files");
            System.out.println("contents of www dir:");
            File[] subdirs = wwwDir.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    System.out.println(" - " + subdir.getName());
                }
            }
        }

        try {
            Files.writeString(java.nio.file.Path.of("/downloads/negotiated_protocol.txt"), session.getNegotiatedProtocol().get() + "\n");
        }
        catch (java.io.IOException e) {
            System.err.println("Failed to write negotiated protocol to file: " + e.getMessage());
        }

        session.registerSessionTerminatedEventListener((errorCode, message) -> {
            System.out.println("Session " + session.getSessionId() + " closed with error code " + errorCode);
        });

        session.setBidirectionalStreamReceiveHandler(stream -> {
            try {
                String request = new String(stream.getInputStream().readAllBytes());
                System.out.println("Received request " + request + " on stream " + stream.getStreamId() + " for session " + session.getSessionId() + " on thread " + Thread.currentThread().getName());

                if (request.startsWith("GET ")) {
                    copyFile(session, stream.getStreamId(), stream.getOutputStream(), request.substring(4).trim());
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        session.setUnidirectionalStreamReceiveHandler(stream -> {
            try {
                String request = new String(stream.getInputStream().readAllBytes());
                System.out.println("Received unidirectional stream with request " + request + " for session " + session.getSessionId() + " on thread " + Thread.currentThread().getName());

                if (request.startsWith("GET ")) {
                    String fileName = request.substring(4).trim();
                    WebTransportStream unidirectionalStream = session.createUnidirectionalStream();
                    OutputStream outputStream = unidirectionalStream.getOutputStream();
                    outputStream.write(("PUSH " + fileName + "\n").getBytes());
                    copyFile(session, stream.getStreamId(), outputStream, fileName);
                }
            }
            catch (IOException e) {
                System.out.println("IO error while processing unidirectional stream: " + e.getMessage());
                throw new UncheckedIOException(e);
            }
        });

        // Make sure the session is opened _after_ setting the handlers, otherwise we might miss incoming streams.
        session.open();
    }

    private void copyFile(Session session, long streamId, OutputStream outputStream, String fileName) throws IOException {
        File requestedFile = new File(new File(wwwDir, session.getPath()), fileName);
        if (requestedFile.exists() && requestedFile.isFile() && requestedFile.canRead()) {
            Files.copy(requestedFile.toPath(), outputStream);
            outputStream.close();
            System.out.println("Served file " + requestedFile.getPath() + " for request " + fileName + " on stream " + streamId + " for session " + session.getSessionId());
        }
        else {
            System.out.println("Requested file not found: " + requestedFile.getPath());
        }
    }
}
