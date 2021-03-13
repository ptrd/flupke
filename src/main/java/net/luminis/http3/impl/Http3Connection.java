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
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.stream.QuicStream;


import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Flow;


public class Http3Connection {

    private final QuicClientConnection quicConnection;
    private final String host;
    private final int port;
    private InputStream serverControlStream;
    private InputStream serverEncoderStream;
    private InputStream serverPushStream;
    private int serverQpackMaxTableCapacity;
    private int serverQpackBlockedStreams;
    private final Decoder qpackDecoder;
    private Statistics connectionStats;
    private boolean connected;

    public Http3Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        SysOutLogger logger = new SysOutLogger();
        logger.logInfo(true);
        logger.logPackets(true);
        logger.useRelativeTime(true);
        logger.logRecovery(true);
        logger.logCongestionControl(true);
        logger.logFlowControl(true);

        QuicClientConnectionImpl.Builder builder = QuicClientConnectionImpl.newBuilder();
        try {
            builder.uri(new URI("//" + host + ":" + port));
        } catch (URISyntaxException e) {
            // Impossible
            throw new RuntimeException();
        }
        builder.version(Version.IETF_draft_29);
        builder.logger(logger);
        quicConnection = builder.build();
        quicConnection.setPeerInitiatedStreamCallback(stream -> doAsync(() -> registerServerInitiatedStream(stream)));

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
        synchronized (this) {
            if (!connected) {
                quicConnection.connect(connectTimeoutInMillis, "h3-29", null, Collections.emptyList());

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

                connected = true;
            }
        }
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
        QuicStream httpStream = quicConnection.createStream(true);
        sendRequest(request, httpStream);
        Http3Response<T> http3Response = receiveResponse(request, responseBodyHandler, httpStream);
        return http3Response;
    }

    private void sendRequest(HttpRequest request, QuicStream httpStream) throws IOException {
        OutputStream requestStream = httpStream.getOutputStream();

        HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.Type.REQUEST);
        headersFrame.setMethod(request.method());
        headersFrame.setUri(request.uri());
        headersFrame.setHeaders(request.headers());
        requestStream.write(headersFrame.toBytes(new Encoder()));

        if (request.bodyPublisher().isPresent()) {
            Flow.Subscriber<ByteBuffer> subscriber = new Flow.Subscriber<>() {

                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    try {
                        DataFrame dataFrame = new DataFrame(item);
                        requestStream.write(dataFrame.toBytes());
                    } catch (IOException e) {
                        // Stop receiving data from publisher.
                        subscription.cancel();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    // Publisher is unable to provide all data.
                    // Ignore
                }

                @Override
                public void onComplete() {
                    try {
                        // Not really necessary, because Kwik probably already sent all data frames.
                        requestStream.flush();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            };
            request.bodyPublisher().get().subscribe(subscriber);
        }

        requestStream.close();
    }

    private <T> Http3Response<T> receiveResponse(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, QuicStream httpStream) throws IOException {
        InputStream responseStream = httpStream.getInputStream();

        HttpResponse.BodySubscriber<T> bodySubscriber = null;
        HttpResponseInfo responseInfo = null;
        ResponseState responseState = new ResponseState(httpStream);

        long frameType;
        while ((frameType = readFrameType(responseStream)) >= 0) {
            int payloadLength = VariableLengthInteger.parse(responseStream);
            byte[] payload = new byte[payloadLength];
            readExact(responseStream, payload);

            if (frameType > 0x0e) {
                // https://tools.ietf.org/html/draft-ietf-quic-http-24#section-9
                // "Implementations MUST discard frames and unidirectional streams that have unknown or unsupported types."
                continue;
            }

            switch ((int) frameType) {
                case 0x01:
                    responseState.gotHeader();
                    HeadersFrame responseHeadersFrame = new HeadersFrame(HeadersFrame.Type.RESPONSE).parsePayload(payload, qpackDecoder);
                    if (responseInfo == null) {
                        // First frame should contain :status pseudo-header and other headers that the body handler might use to determine what kind of body subscriber to use
                        responseInfo = new HttpResponseInfo(responseHeadersFrame);
                        bodySubscriber = responseBodyHandler.apply(responseInfo);
                        bodySubscriber.onSubscribe(new Flow.Subscription() {
                            @Override
                            public void request(long n) {
                                // Always called with max long, so can be safely ignored.
                            }

                            @Override
                            public void cancel() {
                                System.out.println("BodySubscriber has cancelled the subscription.");
                            }
                        });
                    }
                    else {
                        // Must be trailing header
                        responseInfo.add(responseHeadersFrame);
                    }
                    break;
                case 0x00:
                    responseState.gotData();
                    DataFrame dataFrame = new DataFrame().parsePayload(payload);
                    bodySubscriber.onNext(List.of(ByteBuffer.wrap(payload)));
                    break;
                default:
                    // Not necessarily a protocol error, could be a frame we not yet support
                    throw new RuntimeException("Unexpected frame type " + frameType);
            }
        }
        responseState.done();
        bodySubscriber.onComplete();

        connectionStats = quicConnection.getStats();

        return new Http3Response<T>(
                request,
                responseInfo.statusCode(),
                responseInfo.headers(),
                bodySubscriber.getBody());
    }

    long readFrameType(InputStream inputStream) throws IOException {
        try {
            return VariableLengthInteger.parseLong(inputStream);
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

    public Statistics getConnectionStats() {
        return connectionStats;
    }

    private static class HttpResponseInfo implements HttpResponse.ResponseInfo {
        private final Map<String, List<String>> headers;
        private final int statusCode;

        public HttpResponseInfo(HeadersFrame headersFrame) throws ProtocolException {
            headers = new HashMap<>();
            headers.putAll(headersFrame.headers());
            statusCode = headersFrame.statusCode();
        }

        @Override
        public int statusCode() {
           return statusCode;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(headers, (k, v) -> true);
        }

        @Override
        public HttpClient.Version version() {
            return null;
        }

        public void add(HeadersFrame headersFrame) {
            headers.putAll(headersFrame.headers());
        }
    }

    private static class ResponseState {

        private final QuicStream httpStream;

        public ResponseState(QuicStream httpStream) {
            this.httpStream = httpStream;
        }

        enum ResponseStatus {
            INITIAL,
            GOT_HEADER,
            GOT_HEADER_AND_DATA,
            GOT_HEADER_AND_DATA_AND_TRAILING_HEADER,
        };
        ResponseStatus status = ResponseStatus.INITIAL;

        void gotHeader() throws ProtocolException {
            if (status == ResponseStatus.INITIAL) {
                status = ResponseStatus.GOT_HEADER;
            }
            else if (status == ResponseStatus.GOT_HEADER) {
                throw new ProtocolException("Header frame is not allowed after initial header frame (quic stream " + httpStream.getStreamId() + ")");
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA) {
                status = ResponseStatus.GOT_HEADER_AND_DATA_AND_TRAILING_HEADER;
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA_AND_TRAILING_HEADER) {
                throw new ProtocolException("Header frame is not allowed after trailing header frame (quic stream " + httpStream.getStreamId() + ")");
            }
        }

        public void gotData() throws ProtocolException {
            if (status == ResponseStatus.INITIAL) {
                throw new ProtocolException("Missing header frame (quic stream " + httpStream.getStreamId() + ")");
            }
            else if (status == ResponseStatus.GOT_HEADER) {
                status = ResponseStatus.GOT_HEADER_AND_DATA;
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA) {
                // No status change
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA_AND_TRAILING_HEADER) {
                throw new ProtocolException("Data frame not allowed after trailing header frame (quic stream " + httpStream.getStreamId() + ")");
            }
        }

        public void done() throws ProtocolException {
            if (status == ResponseStatus.INITIAL) {
                throw new ProtocolException("Missing header frame (quic stream " + httpStream.getStreamId() + ")");
            }
        }
    }
}
