/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
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

import net.luminis.http3.server.HttpError;
import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.*;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.NullLogger;
import net.luminis.quic.QuicStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;


public class Http3Connection {

    public static final int DEFAULT_PORT = 443;

    private final QuicClientConnection quicConnection;
    private InputStream serverControlStream;
    private InputStream serverEncoderStream;
    private InputStream serverPushStream;
    private int serverQpackMaxTableCapacity;
    private int serverQpackBlockedStreams;
    private final Decoder qpackDecoder;
    private Statistics connectionStats;
    private boolean initialized;
    private Encoder qpackEncoder;

    public Http3Connection(String host, int port) throws IOException {
        this(host, port, false, null);
    }

    public Http3Connection(String host, int port, boolean disableCertificateCheck, Logger logger) throws IOException {
        this(createQuicConnection(host, port, disableCertificateCheck, logger));
    }

    public Http3Connection(QuicConnection quicConnection) {
        this.quicConnection = (QuicClientConnection) quicConnection;

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
        qpackEncoder = new Encoder();
    }

    public void connect(int connectTimeoutInMillis) throws IOException {
        synchronized (this) {
            if (!quicConnection.isConnected()) {
                Version quicVersion = determinePreferredQuicVersion();
                String applicationProtocol = quicVersion.equals(Version.QUIC_version_1) ? "h3" : determineH3Version(quicVersion);
                quicConnection.connect(connectTimeoutInMillis, applicationProtocol, null, Collections.emptyList());
            }
            if (!initialized) {
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

                initialized = true;
            }
        }
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
        QuicStream httpStream = quicConnection.createStream(true);
        sendRequest(request, httpStream);
        Http3Response<T> http3Response = receiveResponse(request, responseBodyHandler, httpStream);
        return http3Response;
    }

    private static QuicConnection createQuicConnection(String host, int port, boolean disableCertificateCheck, Logger logger) throws SocketException, UnknownHostException {
        QuicClientConnectionImpl.Builder builder = QuicClientConnectionImpl.newBuilder();
        try {
            builder.uri(new URI("//" + host + ":" + port));
        } catch (URISyntaxException e) {
            // Impossible
            throw new RuntimeException();
        }
        Version quicVersion = determinePreferredQuicVersion();
        builder.version(quicVersion);
        if (disableCertificateCheck) {
            builder.noServerCertificateCheck();
        }
        builder.logger(logger != null? logger: new NullLogger());
        return builder.build();
    }

    private void sendRequest(HttpRequest request, QuicStream httpStream) throws IOException {
        OutputStream requestStream = httpStream.getOutputStream();

        RequestHeadersFrame headersFrame = new RequestHeadersFrame();
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

        Http3Frame frame;
        while ((frame = readFrame(responseStream)) != null) {
            if (frame instanceof ResponseHeadersFrame) {
                responseState.gotHeader();
                if (responseInfo == null) {
                    // First frame should contain :status pseudo-header and other headers that the body handler might use to determine what kind of body subscriber to use
                    responseInfo = new HttpResponseInfo((ResponseHeadersFrame) frame);

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
                    responseInfo.add((HeadersFrame) frame);
                }
            }
            else if (frame instanceof DataFrame) {
                responseState.gotData();
                ByteBuffer data = ByteBuffer.wrap(((DataFrame) frame).getPayload());
                bodySubscriber.onNext(List.of(data));
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

    Http3Frame readFrame(InputStream responseStream) throws IOException {
        long frameType;
        try {
            frameType = VariableLengthInteger.parseLong(responseStream);
        }
        catch (EOFException endOfStream) {
            return null;
        }

        int payloadLength = VariableLengthInteger.parse(responseStream);
        byte[] payload = new byte[payloadLength];
        readExact(responseStream, payload);

        if (frameType > 0x0e) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-extensions-to-http-3
            // "Implementations MUST discard data or abort reading on unidirectional streams that have unknown or unsupported types."
            return new UnknownFrame();
        }

        Http3Frame frame;
        switch ((int) frameType) {
            case 0x01:
                frame = new ResponseHeadersFrame().parsePayload(payload, qpackDecoder);
                break;
            case 0x00:
                frame = new DataFrame().parsePayload(payload);
                break;
            default:
                // Not necessarily a protocol error, could be a frame (from RFC-9114) Flupke not yet supports
                throw new RuntimeException("Unexpected frame type " + frameType);
        }
        return frame;
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

    private static Version determinePreferredQuicVersion() {
        String quicVersionEnvVar = System.getenv("QUIC_VERSION");
        if (quicVersionEnvVar != null) {
            quicVersionEnvVar = quicVersionEnvVar.trim().toLowerCase();
            if (quicVersionEnvVar.equals("1")) {
                return Version.QUIC_version_1;
            }
            if (quicVersionEnvVar.startsWith("draft-")) {
                try {
                    int draftNumber = Integer.parseInt(quicVersionEnvVar.substring("draft-".length(), quicVersionEnvVar.length()));
                    if (draftNumber >= 29 && draftNumber <= 32) {
                        return Version.parse(0xff000000 + draftNumber);
                    }
                } catch (NumberFormatException e) {}
            }
            System.err.println("Unsupported QUIC version '" + quicVersionEnvVar + "'; should be one of: 1, draft-29, draft-30, draft-31, draft-32.");
        }
        return Version.QUIC_version_1;
    }

    private static String determineH3Version(Version quicVersion) {
        if (quicVersion.atLeast(Version.IETF_draft_29)) {
            String versionString = quicVersion.toString();
            return "h3-" + versionString.substring(versionString.length() - 2, versionString.length());
        }
        else {
            return "";
        }
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

    /**
     * Sends a CONNECT method request.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method:
     * "In HTTP/2 and HTTP/3, the CONNECT method is used to establish a tunnel over a single stream."
     *
     * @param request the request object; note that the HTTP method specified in this request is ignored
     * @return
     */
    public HttpStream sendConnect(HttpRequest request) throws IOException, HttpError {
        QuicStream httpStream = quicConnection.createStream(true);

        // https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method
        // "- The :method pseudo-header field is set to "CONNECT"
        //  - The :scheme and :path pseudo-header fields are omitted
        //  - The :authority pseudo-header field contains the host and port to connect to"
        RequestHeadersFrame headersFrame = new RequestHeadersFrame();
        headersFrame.setMethod("CONNECT");
        headersFrame.setPseudoHeader(":authority", request.uri().getHost() + ":" + request.uri().getPort());
        httpStream.getOutputStream().write(headersFrame.toBytes(qpackEncoder));

        Http3Frame responseFrame = readFrame(httpStream.getInputStream());
        if (responseFrame instanceof ResponseHeadersFrame) {
            int statusCode = ((ResponseHeadersFrame) responseFrame).statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return new HttpStream(httpStream);
            }
            else {
                throw new HttpError("CONNECT request failed", statusCode);
            }
        }
        else {
            if (responseFrame != null) {
                throw new ProtocolException("Expected headers frame, got " + responseFrame.getClass().getSimpleName());
            }
            else {
                throw new ProtocolException("Got empty response from server");
            }
        }
    }

    private static class HttpResponseInfo implements HttpResponse.ResponseInfo {
        private HttpHeaders headers;
        private final int statusCode;

        public HttpResponseInfo(ResponseHeadersFrame headersFrame) throws ProtocolException {
            headers = headersFrame.headers();
            statusCode = headersFrame.statusCode();
        }

        @Override
        public int statusCode() {
           return statusCode;
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public HttpClient.Version version() {
            return null;
        }

        public void add(HeadersFrame headersFrame) {
            Map<String, List<String>> mergedHeadersMap = new HashMap<>();
            mergedHeadersMap.putAll(headers.map());
            mergedHeadersMap.putAll(headersFrame.headers().map());
            headers = HttpHeaders.of(mergedHeadersMap, (a,b) -> true);
        }
    }

    public class HttpStream {

        private final QuicStream quicStream;
        private final OutputStream outputStream;
        private final InputStream inputStream;

        public HttpStream(QuicStream quicStream) {
            this.quicStream = quicStream;

            outputStream = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    quicStream.getOutputStream().write(new DataFrame(new byte[] { (byte) b }).toBytes());
                }

                @Override
                public void write(byte[] b) throws IOException {
                    quicStream.getOutputStream().write(new DataFrame(b).toBytes());
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    ByteBuffer data = ByteBuffer.wrap(b);
                    data.position(off);
                    data.limit(len);
                    quicStream.getOutputStream().write(new DataFrame(data).toBytes());
                }

                @Override
                public void flush() throws IOException {
                    quicStream.getOutputStream().flush();
                }

                @Override
                public void close() throws IOException {
                    quicStream.getOutputStream().close();
                }
            };

            inputStream = new InputStream() {

                private ByteBuffer dataBuffer;

                @Override
                public int available() throws IOException {
                    if (checkData()) {
                        return dataBuffer.remaining();
                    }
                    else {
                        return 0;
                    }
                }

                @Override
                public int read() throws IOException {
                    if (checkData()) {
                        return dataBuffer.get();
                    }
                    else {
                        return -1;
                    }
                }

                @Override
                public int read(byte[] b) throws IOException {
                   if (checkData()) {
                        int count = Integer.min(dataBuffer.remaining(), b.length);
                        dataBuffer.get(b, 0, count);
                        return count;
                    }
                    else {
                        return -1;
                    }
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                   if (checkData()) {
                        int count = Integer.min(dataBuffer.remaining(), len - off);
                        dataBuffer.get(b, off, count);
                        return count;
                    }
                    else {
                        return -1;
                    }

                }

                private boolean checkData() throws IOException {
                    if (dataBuffer == null || dataBuffer.position() == dataBuffer.limit()) {
                        return readData();
                    }
                    else {
                        return dataBuffer.position() < dataBuffer.limit();
                    }
                }

                private boolean readData() throws IOException {
                    Http3Frame frame = readFrame(quicStream.getInputStream());
                    if (frame instanceof DataFrame) {
                        dataBuffer = ByteBuffer.wrap(((DataFrame) frame).getPayload());
                        return true;
                    }
                    else if (frame == null) {
                        // End of stream
                        return false;
                    }
                    return false;
                }
            };
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public InputStream getInputStream() {
            return inputStream;
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
