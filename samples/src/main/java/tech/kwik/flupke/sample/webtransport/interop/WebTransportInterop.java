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

import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;

abstract class WebTransportInterop {

    protected void sendUnidirectionalGet(Session session, String file) {
        try {
            WebTransportStream stream = session.createUnidirectionalStream();
            stream.getOutputStream().write(("GET " + file).getBytes());
            stream.getOutputStream().close();
            System.out.println("Sent GET " + file + " on unidirectional stream " + stream.getStreamId() + " for session " + session.getSessionId());
        }
        catch (IOException e) {
            System.err.println("Error sending GET " + file + ": " + e.getMessage());
        }
    }

    protected void sendDatagramGet(Session session, String file) {
        try {
            session.sendDatagram(("GET " + file).getBytes());
            System.out.println("Sent datagram GET " + file);
        }
        catch (IOException e) {
            System.err.println("Error sending GET " + file + ": " + e.getMessage());
        }
    }

    protected void setupUnidirectionalReceive(Session session, List<String> fileNames, File downloadsDir, CountDownLatch latch) {
        session.setUnidirectionalStreamReceiveHandler(stream ->
                receivePush(stream.getInputStream(), String.valueOf(stream.getStreamId()), downloadsDir, latch, session));
        fileNames.forEach(file -> sendUnidirectionalGet(session, file));
    }

    protected void setupDatagramReceive(Session session, List<String> fileNames, File downloadsDir, CountDownLatch latch) {
        session.setDatagramHandler(datagram ->
                receivePush(new ByteArrayInputStream(datagram), "datagram", downloadsDir, latch, session));
        fileNames.forEach(file -> sendDatagramGet(session, file));
    }

    protected void downloadFileBidirectional(Session session, String file, File downloadsDir) throws IOException {
        WebTransportStream stream = session.createBidirectionalStream();
        stream.getOutputStream().write(("GET " + file).getBytes());
        stream.getOutputStream().close();
        System.out.println("Sent GET " + file + " on bidirectional stream " + stream.getStreamId() + " for session " + session.getSessionId());
        byte[] data = stream.getInputStream().readAllBytes();
        File downloadFile = new File(downloadsDir, file);
        Files.write(downloadFile.toPath(), data);
        System.out.println("Downloaded " + file + " to " + downloadFile.getPath());
    }

    protected void receivePush(InputStream inputStream, String streamId, File downloadsDir, CountDownLatch allReceived, Session session) {
        try {
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int b;
            while ((b = inputStream.read()) != -1 && b != '\n') {
                headerBytes.write(b);
            }
            if (b == -1) {
                System.err.println("Received malformed PUSH on stream " + streamId + ": no newline found");
                return;
            }
            String header = headerBytes.toString();
            if (!header.startsWith("PUSH ")) {
                System.err.println("Received unexpected header on stream " + streamId + ": " + header);
                return;
            }
            String fileName = header.substring(5).trim();
            System.out.println("Received PUSH " + fileName + " on thread " + Thread.currentThread().getName());

            File downloadFile = new File(downloadsDir, fileName);
            try (OutputStream outputStream = Files.newOutputStream(downloadFile.toPath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    System.out.println("Read " + bytesRead + " bytes for file " + fileName + " on stream " + streamId);
                    outputStream.write(buffer, 0, bytesRead);
                    System.out.println("Written " + bytesRead + " bytes to file " + downloadFile.getPath() + " for stream " + streamId);
                }
            }
            System.out.println("Received PUSH " + fileName + ", written to " + downloadFile.getPath());
        }
        catch (IOException e) {
            System.err.println("Error receiving PUSH on stream " + streamId + ": " + e.getMessage());
        }
        finally {
            allReceived.countDown();
            if (allReceived.getCount() == 0) {
                System.out.println("All expected files received, closing session");
                try {
                    session.close();
                }
                catch (IOException e) {
                    System.err.println("Error closing session: " + e.getMessage());
                }
            }
            else {
                System.out.println("Waiting for " + allReceived.getCount() + " more files to be received for session " + session.getSessionId());
            }
        }
    }
}
