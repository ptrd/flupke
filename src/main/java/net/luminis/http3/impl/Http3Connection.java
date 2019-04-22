/*
 * Copyright © 2019 Peter Doornbosch
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
package net.luminis.http3.impl;

import net.luminis.quic.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;


public class Http3Connection {

    private final QuicConnection quicConnection;
    private final String host;
    private final int port;
    private InputStream serverControlStream;
    private InputStream serverEncoderStream;
    private InputStream serverPushStream;
    private int serverQpackMaxTableCapacity;
    private int serverQpackBlockedStreams;

    public Http3Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        SysOutLogger logger = new SysOutLogger();
        logger.logInfo(true);
        logger.logPackets(true);
        logger.useRelativeTime(true);

        quicConnection = new QuicConnection(host, port, Version.IETF_draft_18, logger);
        quicConnection.setServerStreamCallback(stream -> doAsync(() -> registerServerInitiatedStream(stream)));
    }

    public void connect(int connectTimeoutInMillis) throws IOException {
        quicConnection.connect(connectTimeoutInMillis, "h3-19");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {}

    }

    public void send(HttpRequest request) {
        System.out.println("Sending HTTP3 request to " + host + ":" + port);
    }

    void registerServerInitiatedStream(QuicStream stream) {
        try {
            int streamType = stream.getInputStream().read();
            if (streamType == 0x00) {
                // https://tools.ietf.org/html/draft-ietf-quic-http-19#section-3.2.1
                // "A control stream is indicated by a stream type of "0x00"."
                serverControlStream = stream.getInputStream();
                processControlStream();
            }
            else if (streamType == 0x01) {
                // https://tools.ietf.org/html/draft-ietf-quic-http-19#section-3.2.2
                // "A push stream is indicated by a stream type of "0x01","
                serverPushStream = stream.getInputStream();
            }
            else if (streamType == 0x02) {
                // https://tools.ietf.org/html/draft-ietf-quic-qpack-07#section-4.2.1
                // "An encoder stream is a unidirectional stream of type "0x02"."
                serverEncoderStream = stream.getInputStream();;
            }
            else {

            }
        } catch (IOException e) {
            // TODO: if this happens, we can close/abort this connection
            System.err.println("ERROR while reading server initiated stream: " + e);
        }
    }

    private void processControlStream() throws IOException {
        int frameType = VariableLengthInteger.parse(serverControlStream);
        // https://tools.ietf.org/html/draft-ietf-quic-http-19#section-3.2.1
        // "Each side MUST initiate a single control stream at the beginning of
        //   the connection and send its SETTINGS frame as the first frame on this
        //   stream. "
        // https://tools.ietf.org/html/draft-ietf-quic-http-19#section-4.2.5
        // "The SETTINGS frame (type=0x4)..."
        if (frameType != 0x04) {
            throw new RuntimeException("Invalid frame on control stream");
        }
        int frameLength = VariableLengthInteger.parse(serverControlStream);
        byte[] payload = new byte[frameLength];
        readExact(serverControlStream, payload);

        // https://tools.ietf.org/html/draft-ietf-quic-http-19#section-4.2.5
        // "The payload of a SETTINGS frame consists of zero or more parameters.
        //   Each parameter consists of a setting identifier and a value, both
        //   encoded as QUIC variable-length integers."
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        while (payloadBuffer.remaining() > 0) {
            int identifier = VariableLengthInteger.parse(payloadBuffer);
            int value = VariableLengthInteger.parse(payloadBuffer);
            switch (identifier) {
                // https://tools.ietf.org/html/draft-ietf-quic-qpack-07#section-8.1
                case 0x01:
                    serverQpackMaxTableCapacity = value;
                    break;
                case 0x07:
                    serverQpackBlockedStreams = value;
                    break;
                default:
                    ;
                // "An implementation MUST ignore the contents for any SETTINGS
                //   identifier it does not understand."
            }
        }
    }

    private void readExact(InputStream inputStream, byte[] payload) throws IOException {
        int offset = 0;
        while (offset < payload.length) {
            int read = inputStream.read(payload, offset, payload.length - offset);
            if (read > 0) {
                offset += read;
            }
            else {
                throw new EOFException();
            }
        }
    }

    private void doAsync(Runnable task) {
        new Thread(task).start();
    }

    public int getServerQpackMaxTableCapacity() {
        return serverQpackMaxTableCapacity;
    }

    public int getServerQpackBlockedStreams() {
        return serverQpackBlockedStreams;
    }
}
