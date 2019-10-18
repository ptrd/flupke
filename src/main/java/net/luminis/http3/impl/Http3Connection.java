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

import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;


public class Http3Connection {

    private final QuicConnection quicConnection;
    private final String host;
    private final int port;
    private InputStream serverControlStream;
    private InputStream serverEncoderStream;
    private InputStream serverPushStream;
    private int serverQpackMaxTableCapacity;
    private int serverQpackBlockedStreams;
    private final Decoder qpackDecoder;

    public Http3Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        SysOutLogger logger = new SysOutLogger();
        logger.logInfo(true);
        logger.logPackets(true);
        logger.useRelativeTime(true);
        logger.logRecovery(true);

        quicConnection = new QuicConnection(host, port, Version.IETF_draft_23, logger);
        quicConnection.setServerStreamCallback(stream -> doAsync(() -> registerServerInitiatedStream(stream)));

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.1
        // "clients MUST omit or specify a value of zero for the QUIC transport parameter "initial_max_bidi_streams"."
        quicConnection.setMaxAllowedBidirectionalStreams(0);

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2
        // "To reduce the likelihood of blocking,
        //   both clients and servers SHOULD send a value of three or greater for
        //   the QUIC transport parameter "initial_max_uni_streams","
        quicConnection.setMaxAllowedUnidirectionalStreams(3);

        qpackDecoder = new Decoder();
    }

    public void connect(int connectTimeoutInMillis) throws IOException {
        quicConnection.connect(connectTimeoutInMillis, "h3-23");

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
        // "Each side MUST initiate a single control stream at the beginning of
        //   the connection and send its SETTINGS frame as the first frame on this
        //   stream."
        QuicStream clientControlStream = quicConnection.createStream(false);
        OutputStream clientControlOutput = clientControlStream.getOutputStream();
        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
        // "A control stream is indicated by a stream type of "0x00"."
        clientControlOutput.write(0x00);

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-2.3
        // "After the QUIC connection is
        //   established, a SETTINGS frame (Section 4.2.5) MUST be sent by each
        //   endpoint as the initial frame of their respective HTTP control stream
        //   (see Section 3.2.1)."
        ByteBuffer settingsFrame = new SettingsFrame(0, 0).getBytes();
        clientControlStream.getOutputStream().write(settingsFrame.array(), 0, settingsFrame.limit());
        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
        // " The sender MUST NOT close the control stream."
    }


    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {

        QuicStream httpStream = quicConnection.createStream(true);
        OutputStream requestStream = httpStream.getOutputStream();
        InputStream responseStream = httpStream.getInputStream();

        HeadersFrame headersFrame = new HeadersFrame();
        headersFrame.setMethod(request.method());
        headersFrame.setUri(request.uri());

        requestStream.write(headersFrame.toBytes(new Encoder()));
        requestStream.write(new DataFrame().toBytes());
        requestStream.close();

        HeadersFrame responseHeadersFrame = null;
        List<DataFrame> responseDataFrames = new ArrayList<>();

        HttpResponse.BodySubscriber<T> bodySubscriber = null;

        int frameType;
        boolean firstFrame = true;
        while ((frameType = readFrameType(responseStream)) >= 0) {
            if (firstFrame && frameType != 0x01) {
                throw new ProtocolException("First frame on HTTP3 request/response stream should be a HEADERS frame");
            }
            firstFrame = false;

            int payloadLength = VariableLengthInteger.parse(responseStream);
            byte[] payload = new byte[payloadLength];
            readExact(responseStream, payload);

            switch (frameType) {
                case 0x01:
                    responseHeadersFrame = new HeadersFrame().parsePayload(payload, qpackDecoder);
                    bodySubscriber = responseBodyHandler.apply(new HttpResponseInfo(responseHeadersFrame));
                    bodySubscriber.onSubscribe(new Flow.Subscription() {
                        @Override
                        public void request(long n) {}

                        @Override
                        public void cancel() {
                            System.out.println("BodySubscriber has cancelled the subscription.");
                        }
                    });
                    break;
                case 0x00:
                    responseDataFrames.add(new DataFrame().parsePayload(payload));
                    bodySubscriber.onNext(List.of(ByteBuffer.wrap(payload)));
                    break;
                default:
                    // Not necessarily a protocol error, could be a frame we not yet support
                    throw new RuntimeException("Unexpected frame type " + frameType);
            }
        }

        bodySubscriber.onComplete();

        Http3Response<T> http3Response = new Http3Response<T>(
                request,
                responseHeadersFrame.statusCode(),
                HttpHeaders.of(responseHeadersFrame.headers(), (k, v) -> true),
                bodySubscriber.getBody());

        return http3Response;
    }

    int readFrameType(InputStream inputStream) throws IOException {
        try {
            return VariableLengthInteger.parse(inputStream);
        }
        catch (EOFException endOfStream) {
            return -1;
        }
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
        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
        // "Each side MUST initiate a single control stream at the beginning of
        //   the connection and send its SETTINGS frame as the first frame on this
        //   stream. "
        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.5
        // "The SETTINGS frame (type=0x4)..."
        if (frameType != 0x04) {
            throw new RuntimeException("Invalid frame on control stream");
        }
        int frameLength = VariableLengthInteger.parse(serverControlStream);
        byte[] payload = new byte[frameLength];
        readExact(serverControlStream, payload);

        SettingsFrame settingsFrame = new SettingsFrame().parsePayload(ByteBuffer.wrap(payload));
        serverQpackMaxTableCapacity = settingsFrame.getQpackMaxTableCapacity();
        serverQpackBlockedStreams = settingsFrame.getQpackBlockedStreams();
    }

    private void readExact(InputStream inputStream, byte[] payload) throws IOException {
        int offset = 0;
        while (offset < payload.length) {
            int read = inputStream.read(payload, offset, payload.length - offset);
            if (read > 0) {
                offset += read;
            }
            else {
                throw new EOFException("Stream closed by peer");
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

    public void setReceiveBufferSize(long receiveBufferSize) {
        quicConnection.setDefaultStreamReceiveBufferSize(receiveBufferSize);
    }

    private class HttpResponseInfo implements HttpResponse.ResponseInfo {
        private final HeadersFrame headersFrame;

        public HttpResponseInfo(HeadersFrame headersFrame) {

            this.headersFrame = headersFrame;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(headersFrame.headers(), (k, v) -> true);
        }

        @Override
        public HttpClient.Version version() {
            return null;
        }
    }
}
