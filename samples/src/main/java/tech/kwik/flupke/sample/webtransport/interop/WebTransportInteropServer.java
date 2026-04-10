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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static tech.kwik.flupke.server.Http3ApplicationProtocolFactory.HTTP3_PROTOCOL_ID;

/**
 * Server for WebTransport interop testing.
 * see https://github.com/quic-interop/quic-interop-runner/blob/master/webtransport.md
 *
 * TODO: maybe move to interop module? (like it's done in kwik)
 */
public class WebTransportInteropServer extends WebTransportInterop {

    private final KeyStore keyStore;
    private final String certificateAlias;
    private final String keyPassword;
    private final int port;
    private final File wwwDir;
    private List<File> endpoints;

    public WebTransportInteropServer(KeyStore keyStore, String certificateAlias, String keyPassword, int port, File wwwDir) {
        this.keyStore = keyStore;
        this.certificateAlias = certificateAlias;
        this.keyPassword = keyPassword;
        this.port = port;
        this.wwwDir = wwwDir;
    }

    public static void main(String[] args) throws Exception {
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
        new WebTransportInteropServer(keyStore, certificateAlias, keyPassword, port, wwwDir).start();
    }

    private static void usageAndExit(List<String> actualArgs) {
        System.err.println("Usage: keystore-file cert-alias keystore-password key-password port-number [www dir]");
        System.out.println("Actual args: " + actualArgs);
        System.exit(1);
    }

    private void start() throws Exception {
        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxUnidirectionalStreamBufferSize(1_000_000)
                .maxBidirectionalStreamBufferSize(1_000_000)
                .maxConnectionBufferSize(10_000_000)
                .maxOpenPeerInitiatedUnidirectionalStreams(10)
                .maxOpenPeerInitiatedBidirectionalStreams(100)
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

        determineWebTransportEndpoints();
        List<String> supportedApplicationProtocols = determineProtocols();

        HttpRequestHandler httpRequestHandler = (request, response) -> {
            if (endpoints.stream().anyMatch(e -> request.path().startsWith("/" + e.getName()))) {
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

        String testcase = System.getenv("TESTCASE");
        System.out.println("Testcase: " + testcase);

        WebTransportHttp3ApplicationProtocolFactory webTransportProtocolFactory = new WebTransportHttp3ApplicationProtocolFactory(httpRequestHandler);
        webTransportProtocolFactory.setMaxUnidirectionalStreamReceiverBufferSize(1_000_000);
        webTransportProtocolFactory.setMaxStreamsQueued(10);
        Consumer<Session> webTransportHandler;
        if ("transfer-bidirectional-send".equals(testcase)) {
            webTransportHandler = this::startTransferBidirectionalSend;
        }
        else if ("transfer-unidirectional-send".equals(testcase)) {
            webTransportHandler = this::startTransferUnidirectionalSend;
        }
        else if ("transfer-datagram-send".equals(testcase)) {
            webTransportHandler = this::startTransferDatagramSend;
        }
        else {
            webTransportHandler = this::startWebtransport;
        }
        endpoints.stream()
                .map(File::getName)
                .map(name -> "/" + name + "(/.*)?")  // match the endpoint path and any subpaths, e.g. /webtransport1 and /webtransport1/foo/bar
                .forEach(extendedPath -> {
                    System.out.println("Starting WebTransport handler on path " + extendedPath);
                    webTransportProtocolFactory.registerWebTransportServerByRegex(extendedPath,
                            supportedApplicationProtocols, withExceptionLogging(webTransportHandler)); }
                );

        serverConnector.registerApplicationProtocol(HTTP3_PROTOCOL_ID, webTransportProtocolFactory);
        serverConnector.start();
    }

    private List<String> determineProtocols() {
        String protocolsEnvVar = System.getenv("PROTOCOLS");
        List<String> protocols = Arrays.stream(protocolsEnvVar.split(" ")).collect(Collectors.toList());
        return protocols;
    }

    private void determineWebTransportEndpoints() {
        // https://github.com/quic-interop/quic-interop-runner/blob/master/webtransport.md
        // "The server MAY determine expected WebTransport endpoints by scanning the /www/ directory for top-level
        //  subdirectories at startup. Each such subdirectory name (e.g., webtransport1 in /www/webtransport1/)
        //  corresponds to an endpoint path (e.g., /webtransport1), including empty subdirectories for session-only
        //  tests like handshake."
        endpoints = Arrays.asList(wwwDir.listFiles(File::isDirectory));
        if (endpoints != null) {
            System.out.println("Using WebTransport endpoints:");
            for (File subdir : endpoints) {
                // if subdir is empty, print it as well, as it can be used for session-only tests like handshake
                if (subdir.listFiles() == null || subdir.listFiles().length == 0) {
                    System.out.println(" - " + subdir.getName() + " (empty, for session-only tests)");
                }
                else {
                    System.out.println(" - " + subdir.getName());
                }
            }
        }
    }

    File determineSessionServingDir(Session session) {
        File sessionDir = new File(wwwDir, session.getPath());
        if (sessionDir.exists() && sessionDir.isDirectory() && sessionDir.canRead()) {
            System.out.println("Serving files for session " + session.getSessionId() + " from directory " + sessionDir.getPath());
            return sessionDir;
        }
        else {
            // Non-conformant with the spec; let's check if the path has a base dir that matches an endpoint.
            String[] pathElements = session.getPath().split("/");
            if (pathElements.length > 2) {
                // pathElements[0] is empty (path starts with '/'), pathElements[1] is the first segment
                String firstElement = pathElements[1];
                File endpointDir = new File(wwwDir, firstElement);
                if (endpointDir.exists() && endpointDir.isDirectory() && endpointDir.canRead()
                        && endpoints.stream().anyMatch(e -> e.getName().equals(firstElement))) {
                    System.out.println("Path '" + session.getPath() + "' has multiple elements; using endpoint dir " + endpointDir.getPath() + " for session " + session.getSessionId());
                    return endpointDir;
                }
            }
            System.out.println("No directory found for session " + session.getSessionId() + ", not serving any files");
            return null;
        }
    }
    
    private void startWebtransport(Session session) {
        System.out.println("Starting handler for WebTransport session: " + session.getSessionId());
        File sessionServingDir = determineSessionServingDir(session);

        session.getNegotiatedProtocol().ifPresent(protocol -> {
            System.out.println("Negotiated protocol for session " + session.getSessionId() + ": " + protocol);
            try {
                Files.writeString(Path.of("/downloads/negotiated_protocol.txt"), protocol + "\n");
            }
            catch (java.io.IOException e) {
                System.err.println("Failed to write negotiated protocol to file: " + e.getMessage());
            }
        });

        session.registerSessionTerminatedEventListener((errorCode, message) -> {
            System.out.println("Session " + session.getSessionId() + " closed with error code " + errorCode);
        });

        session.setBidirectionalStreamReceiveHandler(stream -> {
            try {
                String request = new String(stream.getInputStream().readAllBytes());
                System.out.println("Received request " + request + " on stream " + stream.getStreamId() + " for session " + session.getSessionId() + " on thread " + Thread.currentThread().getName());

                if (request.startsWith("GET ")) {
                    copyFile(sessionServingDir, stream.getStreamId(), stream.getOutputStream(), request.substring(4).trim());
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
                    copyFile(sessionServingDir, stream.getStreamId(), outputStream, fileName);
                }
            }
            catch (IOException e) {
                System.out.println("IO error while processing unidirectional stream: " + e.getMessage());
                throw new UncheckedIOException(e);
            }
        });

        session.setDatagramHandler(data -> {
            String message = new String(data);
            if (message.startsWith("GET ")) {
                String fileName = message.substring(4).trim();
                File requestedFile = new File(sessionServingDir, fileName);
                if (requestedFile.exists() && requestedFile.isFile() && requestedFile.canRead()) {
                    try {
                        ByteBuffer datagramBuffer = ByteBuffer.allocate(1500);
                        datagramBuffer.put(("PUSH " + fileName + "\n").getBytes());
                        byte[] fileData = Files.readAllBytes(requestedFile.toPath());
                        datagramBuffer.put(fileData);
                        datagramBuffer.flip();
                        byte[] datagramData = new byte[datagramBuffer.limit()];
                        datagramBuffer.get(datagramData);
                        session.sendDatagram(datagramData);
                        System.out.println("Sent datagram with contents of file " + requestedFile.getPath() + " for request " + fileName + " on session " + session.getSessionId());
                    }
                    catch (IOException e) {
                        System.err.println("Error reading file for datagram response: " + e.getMessage());
                    }
                }
                else {
                    System.out.println("Requested file not found for datagram response: " + requestedFile.getPath());
                }
            }
        });

        // Make sure the session is opened _after_ setting the handlers, otherwise we might miss incoming streams.
        session.open();
    }

    private void startTransferBidirectionalSend(Session session) {
        System.out.println("Starting transfer-bidirectional-send handler for session: " + session.getSessionId());

        String requestsEnvVar = System.getenv("REQUESTS");
        if (requestsEnvVar == null || requestsEnvVar.isEmpty()) {
            System.out.println("REQUESTS env var not set, nothing to do");
            return;
        }

        String sessionPath = session.getPath();

        File downloadsDir = resolveDownloadsDir(sessionPath);
        if (downloadsDir == null) {
            return;
        }

        List<String> matchingFiles = extractMatchingFiles(requestsEnvVar, sessionPath);

        session.open();

        List<Thread> threads = matchingFiles.stream()
                .map(file -> startDownloadThread(() -> downloadFile(session, file, downloadsDir)))
                .collect(Collectors.toList());

        waitForDownloads(threads);
        try {
            session.close();
        }
        catch (IOException e) {
            System.err.println("Error closing session: " + e.getMessage());
        }
    }

    private void startTransferUnidirectionalSend(Session session) {
        System.out.println("Starting transfer-unidirectional-send handler for session: " + session.getSessionId());

        String requestsEnvVar = System.getenv("REQUESTS");
        if (requestsEnvVar == null || requestsEnvVar.isEmpty()) {
            System.out.println("REQUESTS env var not set, nothing to do");
            return;
        }

        String sessionPath = session.getPath();

        File downloadsDir = resolveDownloadsDir(sessionPath);
        if (downloadsDir == null) {
            return;
        }

        List<String> matchingFiles = extractMatchingFiles(requestsEnvVar, sessionPath);

        CountDownLatch allReceived = new CountDownLatch(matchingFiles.size());
        session.open();
        setupUnidirectionalReceive(session, matchingFiles, downloadsDir, allReceived);
    }

    private void startTransferDatagramSend(Session session) {
        System.out.println("Starting transfer-datagram-send handler for session: " + session.getSessionId());

        String requestsEnvVar = System.getenv("REQUESTS");
        if (requestsEnvVar == null || requestsEnvVar.isEmpty()) {
            System.out.println("REQUESTS env var not set, nothing to do");
            return;
        }

        String sessionPath = session.getPath();

        File downloadsDir = resolveDownloadsDir(sessionPath);
        if (downloadsDir == null) {
            return;
        }

        List<String> matchingFiles = extractMatchingFiles(requestsEnvVar, sessionPath);

        CountDownLatch allReceived = new CountDownLatch(matchingFiles.size());
        session.open();
        setupDatagramReceive(session, matchingFiles, downloadsDir, allReceived);
    }

    private File resolveDownloadsDir(String sessionPath) {
        File downloadsDir = new File("/downloads", sessionPath);
        downloadsDir.mkdirs();
        if (!downloadsDir.exists() || !downloadsDir.isDirectory() || !downloadsDir.canWrite()) {
            System.out.println("Downloads dir '" + downloadsDir + "' does not exist, is not a directory, or is not writable");
            return null;
        }
        System.out.println("Saving downloaded files to directory " + downloadsDir.getPath());
        return downloadsDir;
    }

    private List<String> extractMatchingFiles(String requestsEnvVar, String sessionPath) {
        return Stream.of(requestsEnvVar.split(" "))
                .filter(r -> r.contains("/"))
                .filter(r -> ("/" + r.substring(0, r.indexOf('/'))).equals(sessionPath))
                .map(r -> r.substring(r.indexOf('/') + 1))
                .collect(Collectors.toList());
    }

    private Thread startDownloadThread(Runnable task) {
        Thread thread = new Thread(task);
        thread.start();
        return thread;
    }

    private void downloadFile(Session session, String file, File downloadsDir) {
        try {
            downloadFileBidirectional(session, file, downloadsDir);
        }
        catch (IOException e) {
            System.err.println("Error downloading " + file + ": " + e.getMessage());
        }
    }

    private void waitForDownloads(List<Thread> threads) {
        long deadline = System.currentTimeMillis() + 90_000;
        for (Thread thread : threads) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    System.err.println("Timed out waiting for downloads to complete");
                    break;
                }
                thread.join(remaining);
                if (thread.isAlive()) {
                    System.err.println("Timed out waiting for downloads to complete");
                    break;
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Consumer<Session> withExceptionLogging(Consumer<Session> handler) {
        return session -> {
            try {
                handler.accept(session);
            }
            catch (Exception e) {
                System.err.println("Unhandled exception in session handler for session " + session.getSessionId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    private void copyFile(File sessionServingDir, long streamId, OutputStream outputStream, String fileName) throws IOException {
        File requestedFile = new File(sessionServingDir, fileName);
        if (requestedFile.exists() && requestedFile.isFile() && requestedFile.canRead()) {
            Files.copy(requestedFile.toPath(), outputStream);
            outputStream.close();
            System.out.println("Served file " + requestedFile.getPath() + " for request " + fileName + " on stream " + streamId + ".");
        }
        else {
            System.out.println("Requested file not found: " + requestedFile.getPath());
        }
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
