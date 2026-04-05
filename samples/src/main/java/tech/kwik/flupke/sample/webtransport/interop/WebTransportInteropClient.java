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

import tech.kwik.core.log.FileLogger;
import tech.kwik.core.log.Logger;
import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.webtransport.ClientSessionFactory;
import tech.kwik.flupke.webtransport.Session;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WebTransportInteropClient {

    public static List TESTCASES = List.of("handshake", "transfer-datagram-receive");

    private File outputDir;
    private Logger logger;
    private File logDir;
    private X509TrustManager trustManager;
    private List<URL> downloadUrls;

    public WebTransportInteropClient(String[] args) throws Exception {
        setupLogging();

        String testCase = processArgs(args);

        URI serverUrl = determineServerUrl();

        createOutputDir(serverUrl);

        Http3Client client = (Http3Client) Http3Client.newBuilder()
                .trustManager(trustManager)
                .logger(logger)
                .enableDatagram()
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        List<String> protocols = Arrays.stream(System.getenv("PROTOCOLS").split(" "))
                .collect(Collectors.toList());

        Session webtransportSession = setupWebTransportSession(serverUrl, client, protocols);

        if (testCase.equals("transfer-datagram-receive")) {
            testTransferDatagramReceive(webtransportSession, downloadUrls);
        }
    }

    String processArgs(String[] args) throws Exception {
        if (args.length < 5) {
            System.out.println("Expected 5 arguments: <downloadDir> <trustStore> <trustStorePassword> <testcase> <URL>");
            System.exit(1);
        }

        int currentArg = 0;
        outputDir = new File(args[currentArg++]);
        if (! outputDir.isDirectory()) {
            outputDir.mkdir();
            System.out.println("Created output directory " + outputDir.getAbsolutePath());
        }
        else {
            System.out.println("Using output directory " + outputDir.getAbsolutePath());
        }

        File trustStoreFile = new File(args[currentArg++]);
        if (! trustStoreFile.exists() || ! trustStoreFile.canRead()) {
            System.out.println("Can't read trust store file " + trustStoreFile);
            System.exit(1);
        }
        String trustStorePassword = args[currentArg++];
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        KeyStore customTrustStore = KeyStore.getInstance(trustStoreFile, trustStorePassword.toCharArray());
        trustManagerFactory.init(customTrustStore);
        trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];

        String testCase = args[currentArg++];
        if (! TESTCASES.contains(testCase)) {
            System.out.println("Invalid argument; test case '" + testCase + "' not known.");
            System.out.println("Available test cases: " + TESTCASES);
        }

        downloadUrls = new ArrayList<>();
        for (; currentArg < args.length; currentArg++) {
            downloadUrls.add(new URL(args[currentArg]));
        }

        return testCase;
    }

    private Session setupWebTransportSession(URI serverUrl, Http3Client client, List<String> protocols) throws Exception {
        ClientSessionFactory clientSessionFactory = ClientSessionFactory.newBuilder()
                .serverUri(serverUrl)
                .httpClient(client)
                .build();

        System.out.println("Starting session on " + serverUrl);
        Session session = clientSessionFactory.createSession(serverUrl, protocols);
        System.out.println("Negotiated: " +  session.getNegotiatedProtocol());
        session.getNegotiatedProtocol().ifPresent(protocol -> {
            try {
                Files.writeString(Path.of("/downloads/negotiated_protocol.txt"), session.getNegotiatedProtocol().get() + "\n");
            }
            catch (IOException e) {
                System.err.println("Failed to write negotiated protocol to file: " + e.getMessage());
            }
        });

        session.registerSessionTerminatedEventListener((code, msg) -> {
            System.out.println("Session terminated with code " + code + " and message: " + msg);
        });
        session.open();
        System.out.println("Session " + session.getSessionId() + " opened to " + serverUrl);

        return session;
    }

    private void createOutputDir(URI serverUrl) {
        String subdir = serverUrl.getPath();
        outputDir = new File(outputDir, subdir);

        if (! outputDir.exists()) {
            outputDir.mkdir();
            System.out.println("Created output directory " + outputDir.getAbsolutePath());
        }
    }

    private URI determineServerUrl() throws Exception {
        URI serverUrl = downloadUrls.get(0).toURI();
        String path = serverUrl.getPath();
        if (path == null || path.isEmpty() || path.equals("/"))  {
            System.out.println("Invalid argument: URL '" + downloadUrls.get(0) + "' must have a path with at least one directory");
            System.exit(1);
        }
        Path dir = Paths.get(path).getName(0);// validate that path has at least one directory
        // replace path of server URL with just the first dir, as that's the URL we need to connect to for the WebTransport session
        return new URI(serverUrl.getScheme(), serverUrl.getUserInfo(), serverUrl.getHost(), serverUrl.getPort(), "/" + dir, null, null);
    }

    private void setupLogging() {
        File dir = new File("/logs");
        if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
            logDir = dir;
        }
        else {
            logDir = new File(".");
            System.out.println("Log directory /logs does not exist; logs will be written to ./flupke_client.log");
        }
        String logFile =  "flupke_client.log";
        try {
            logger = new FileLogger(new File(logDir, logFile));
            logger.logInfo(true);
            logger.logWarning(true);
        }
        catch (IOException e) {
            System.out.println("Cannot open log file " + logFile);
            System.exit(1);
        }
    }

    private void testTransferDatagramReceive(Session session, List<URL> downloadUrls) throws InterruptedException, IOException {
        List<String> fileNames = downloadUrls.stream()
                .map(url -> Paths.get(url.getPath()).getFileName().toString())
                .collect(Collectors.toList());

        CountDownLatch allReceived = new CountDownLatch(fileNames.size());
        session.setDatagramHandler(datagram ->
                receivePush(new ByteArrayInputStream(datagram), outputDir, allReceived, session));

        fileNames.forEach(file -> sendDatagramGet(session, file));

        boolean completed = allReceived.await(30, TimeUnit.SECONDS);
        if (!completed) {
            System.out.println("Timed out waiting for datagrams, received "
                    + (fileNames.size() - allReceived.getCount()) + " of " + fileNames.size());
        }
    }

    private void sendDatagramGet(Session session, String file) {
        try {
            byte[] data = ("GET " + file).getBytes();
            session.sendDatagram(data);
            System.out.println("Sent datagram GET " + file);
        }
        catch (IOException e) {
            System.err.println("Error sending GET " + file + ": " + e.getMessage());
        }
    }

    private void receivePush(ByteArrayInputStream inputStream, File downloadsDir, CountDownLatch allReceived, Session session) {
        try {
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int b;
            while ((b = inputStream.read()) != -1 && b != '\n') {
                headerBytes.write(b);
            }
            if (b == -1) {
                System.err.println("Received malformed PUSH datagram: no newline found");
                return;
            }
            String header = headerBytes.toString();
            if (!header.startsWith("PUSH ")) {
                System.err.println("Received unexpected datagram header: " + header);
                return;
            }
            String fileName = header.substring(5).trim();
            System.out.println("Received PUSH " + fileName);

            File downloadFile = new File(downloadsDir, fileName);
            try (OutputStream outputStream = Files.newOutputStream(downloadFile.toPath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("Written to " + downloadFile.getPath());
        }
        catch (IOException e) {
            System.err.println("Error receiving PUSH datagram: " + e.getMessage());
        }
        finally {
            allReceived.countDown();
            if (allReceived.getCount() == 0) {
                System.out.println("All expected datagrams received, closing session");
                try {
                    session.close();
                }
                catch (IOException e) {
                    System.err.println("Error closing session: " + e.getMessage());
                }
            }
            else {
                System.out.println("Waiting for " + allReceived.getCount() + " more datagrams");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting WebTransport interop client, args: " + String.join(" ", args));

        new WebTransportInteropClient(args);
    }


}
