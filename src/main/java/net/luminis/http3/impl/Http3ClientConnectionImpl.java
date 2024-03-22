/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024 Peter Doornbosch
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

import net.luminis.http3.Http3ConnectionSettings;
import net.luminis.http3.core.*;
import net.luminis.qpack.Encoder;
import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.Statistics;
import net.luminis.quic.generic.VariableLengthInteger;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.NullLogger;

import java.io.*;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;


public class Http3ClientConnectionImpl extends Http3ConnectionImpl implements Http3ClientConnection {

    private InputStream serverPushStream;
    private Statistics connectionStats;
    private boolean initialized;
    private Encoder qpackEncoder;
    private final CountDownLatch settingsFrameReceived;
    private boolean settingsEnableConnectProtocol;
    private Consumer<HttpStream> bidirectionalStreamHandler;

    public Http3ClientConnectionImpl(String host, int port) throws IOException {
        this(host, port, DEFAULT_CONNECT_TIMEOUT, defaultConnectionSettings(), null);
    }

    public Http3ClientConnectionImpl(String host, int port, Duration connectTimeout, Http3ConnectionSettings connectionSettings, Logger logger) throws IOException {
        this(createQuicConnection(host, port, connectTimeout, connectionSettings, logger));
    }

    public Http3ClientConnectionImpl(QuicConnection quicConnection) {
        super(quicConnection);

        quicConnection.setPeerInitiatedStreamCallback(stream -> doAsync(() -> handleIncomingStream(stream)));

        qpackEncoder = new Encoder();

        settingsFrameReceived = new CountDownLatch(1);
    }

    public Http3ClientConnectionImpl(String host, int port, Encoder encoder) throws IOException {
        this(host, port);
        qpackEncoder = encoder;
    }

    @Override
    public void connect() throws IOException {
        synchronized (this) {
            if (! ((QuicClientConnection) quicConnection).isConnected()) {
                ((QuicClientConnection) quicConnection).connect();
            }
            if (!initialized) {
                startControlStream();
                initialized = true;
            }
        }
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
        QuicStream httpStream = quicConnection.createStream(true);
        sendRequest(request, httpStream);
        try {
            Http3Response<T> http3Response = receiveResponse(request, responseBodyHandler, httpStream);
            return http3Response;
        }
        catch (MalformedResponseException e) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-malformed-requests-and-resp
            // "Malformed requests or responses that are detected MUST be treated as a stream error of type H3_MESSAGE_ERROR."
            throw new ProtocolException(e.getMessage());  // TODO: generate stream error
        } catch (HttpError e) {
            throw new ProtocolException(e.getMessage());
        }
    }

    private static QuicConnection createQuicConnection(String host, int port, Duration connectTimeout, Http3ConnectionSettings connectionSettings, Logger logger) throws SocketException, UnknownHostException {
        QuicClientConnection.Builder builder = QuicClientConnection.newBuilder();
        try {
            builder.uri(new URI("//" + host + ":" + port));
        } catch (URISyntaxException e) {
            // Impossible
            throw new RuntimeException();
        }
        builder.version(determinePreferredQuicVersion());
        builder.connectTimeout(connectTimeout);
        builder.applicationProtocol("h3");
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
        // "Therefore, the transport parameters sent by both clients and servers MUST allow the peer to create at least
        //  three unidirectional streams. These transport parameters SHOULD also provide at least 1,024 bytes of
        //  flow-control credit to each unidirectional stream."
        builder.maxOpenPeerInitiatedUnidirectionalStreams(3 + connectionSettings.maxAdditionalPeerInitiatedUnidirectionalStreams());
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-bidirectional-streams
        // "HTTP/3 does not use server-initiated bidirectional streams, though an extension could define a use for
        //  these streams."
        builder.maxOpenPeerInitiatedBidirectionalStreams(0 + connectionSettings.maxAdditionalPeerInitiatedBidirectionalStreams());
        if (connectionSettings.disableCertificateCheck()) {
            builder.noServerCertificateCheck();
        }
        builder.logger(logger != null? logger: new NullLogger());
        return builder.build();
    }

    private void sendRequest(HttpRequest request, QuicStream httpStream) throws IOException {
        OutputStream requestStream = httpStream.getOutputStream();

        // https://www.rfc-editor.org/rfc/rfc9114.html#name-request-pseudo-header-field
        // "All HTTP/3 requests MUST include exactly one value for the :method, :scheme, and :path pseudo-header fields,
        //  unless the request is a CONNECT request;"
        // "If the :scheme pseudo-header field identifies a scheme that has a mandatory authority component (including
        //  "http" and "https"), the request MUST contain either an :authority pseudo-header field or a Host header field."
        Map<String, String> pseudoHeaders = Map.of(
                ":method", request.method(),
                ":scheme", "https",
                ":authority", extractAuthority(request.uri()),
                ":path", extractPath(request.uri())
        );
        HeadersFrame headersFrame = new HeadersFrame(request.headers(), pseudoHeaders);
        requestStream.write(headersFrame.toBytes(qpackEncoder));

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

    private <T> Http3Response<T> receiveResponse(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, QuicStream httpStream) throws IOException, MalformedResponseException, HttpError {
        InputStream responseStream = httpStream.getInputStream();
        ResponseFramesSequenceChecker frameSequenceChecker = new ResponseFramesSequenceChecker(httpStream);

        HeadersFrame headersFrame = readHeadersFrame(responseStream, frameSequenceChecker);
        HttpResponseInfo responseInfo = new HttpResponseInfo(headersFrame);

        HttpResponse.BodySubscriber<T> bodySubscriber = responseBodyHandler.apply(responseInfo);
        if (bodySubscriber == null) {
            httpStream.abortReading(H3_REQUEST_CANCELLED);
            throw new IllegalArgumentException("Body handler returned null body subscriber.");
        }
        BodySubscriptionHandler bodySubscriptionHandler = new BodySubscriptionHandler(httpStream, frameSequenceChecker, bodySubscriber, responseInfo);
        bodySubscriber.onSubscribe(bodySubscriptionHandler);

        // Check if a fatal exception has occurred while reading body. If so, throw it.
        // This is necessary because some body handlers (e.g. ofString) do not throw an exception when they receive an error from the publisher.
        bodySubscriptionHandler.checkError();

        return new Http3Response<>(
                request,
                responseInfo.statusCode(),
                responseInfo.headers(),
                bodySubscriber.getBody());
    }

    private HeadersFrame readHeadersFrame(InputStream responseStream, ResponseFramesSequenceChecker frameSequenceChecker) throws IOException, HttpError {
        Http3Frame frame = readFrame(responseStream);
        if (frame == null) {
            throw new EOFException("end of stream");
        }
        else if (frame instanceof HeadersFrame) {
            frameSequenceChecker.gotHeader();
            return (HeadersFrame) frame;
        }
        else if (frame instanceof DataFrame) {
            frameSequenceChecker.gotData();
            return null;  // Will not happen, checker will throw exception
        }
        else {
            frameSequenceChecker.gotOther(frame);
            // If it gets here, the unknown frame is ignored, continue to (try to) read a headers frame
            return readHeadersFrame(responseStream, frameSequenceChecker);
        }
    }

    void registerServerInitiatedStream(QuicStream stream) {
        handleUnidirectionalStream(stream);
    }

    @Override
    public void registerBidirectionalStreamHandler(Consumer<HttpStream> streamHandler) {
        bidirectionalStreamHandler = streamHandler;
    }
    
    @Override
    protected void registerStandardStreamHandlers() {
        super.registerStandardStreamHandlers();
        unidirectionalStreamHandler.put((long) STREAM_TYPE_PUSH_STREAM, httpStream -> setServerPushStream(httpStream.getInputStream()));
    }

    private void setServerPushStream(InputStream stream) {
        serverPushStream = stream;
    }

    @Override
    protected SettingsFrame processControlStream(InputStream controlStream) {
        SettingsFrame settingsFrame = super.processControlStream(controlStream);
        if (settingsFrame != null) {
            // Read settings that only apply to client role.
            settingsEnableConnectProtocol = settingsFrame.isSettingsEnableConnectProtocol();
        }

        // Unblock who is waiting for settings frame to be received.
        settingsFrameReceived.countDown();

        return settingsFrame;
    }

    private void doAsync(Runnable task) {
        new Thread(task).start();
    }

    private static QuicConnection.QuicVersion determinePreferredQuicVersion() {
        String quicVersionEnvVar = System.getenv("QUIC_VERSION");
        if (quicVersionEnvVar != null) {
            quicVersionEnvVar = quicVersionEnvVar.trim().toLowerCase();
            if (quicVersionEnvVar.equals("1")) {
                return QuicConnection.QuicVersion.V1;
            }
            if (quicVersionEnvVar.equals("2")) {
                return QuicConnection.QuicVersion.V2;
            }
            System.err.println("Unsupported QUIC version '" + quicVersionEnvVar + "'; should be one of: 1, 2");
        }
        return QuicConnection.QuicVersion.V1;
    }

    public int getServerQpackMaxTableCapacity() {
        return peerQpackMaxTableCapacity;
    }

    public int getServerQpackBlockedStreams() {
        return peerQpackBlockedStreams;
    }

    public void setReceiveBufferSize(long receiveBufferSize) {
        quicConnection.setDefaultBidirectionalStreamReceiveBufferSize(receiveBufferSize);
    }

    @Override
    public Statistics getConnectionStats() {
        return connectionStats;
    }

    @Override
    public HttpStream sendConnect(HttpRequest request) throws IOException, HttpError {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method
        // "A CONNECT request MUST be constructed as follows:
        //  - The :method pseudo-header field is set to "CONNECT"
        //  - The :scheme and :path pseudo-header fields are omitted
        //  - The :authority pseudo-header field contains the host and port to connect to (equivalent to the authority-form of the request-target of CONNECT requests; see Section 7.1 of [HTTP])."
        HeadersFrame headersFrame = new HeadersFrame(request.headers(), Map.of(
                ":authority", extractAuthority(request.uri()),
                ":method", "CONNECT"));

        return new HttpStreamImpl(createHttpStream(headersFrame));
    }

    @Override
    public HttpStream sendExtendedConnect(HttpRequest request, String protocol, String scheme, Duration settingsFrameTimeout) throws InterruptedException, HttpError, IOException {
        if (! settingsFrameReceived.await(settingsFrameTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new HttpError("No SETTINGS frame received in time.");
        }
        if (! settingsEnableConnectProtocol) {
            throw new HttpError("Server does not support Extended Connect (RFC 9220).");
        }

        // https://www.rfc-editor.org/rfc/rfc8441#section-4
        // "A new pseudo-header field :protocol MAY be included on request HEADERS indicating the desired protocol to be
        //  spoken on the tunnel created by CONNECT. The pseudo-header field is single valued and contains a value from
        //  the "Hypertext Transfer Protocol (HTTP) Upgrade Token Registry located at <https://www.iana.org/assignments/http-upgrade-tokens/>"
        // "On requests that contain the :protocol pseudo-header field, the :scheme and :path pseudo-header fields of
        //  the target URI (see Section 5) MUST also be included."
        HeadersFrame headersFrame = new HeadersFrame(request.headers(), Map.of(
                ":authority", extractAuthority(request.uri()),
                ":method", "CONNECT",
                ":protocol", protocol,
                ":scheme", scheme,
                ":path", extractPath(request.uri())));

        return new HttpStreamImpl(createHttpStream(headersFrame));
    }

    @Override
    public CapsuleProtocolStream sendExtendedConnectWithCapsuleProtocol(HttpRequest request, String protocol, String scheme, Duration settingsFrameTimeout) throws InterruptedException, HttpError, IOException {
        if (! settingsFrameReceived.await(settingsFrameTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new HttpError("No SETTINGS frame received in time.");
        }
        if (! settingsEnableConnectProtocol) {
            throw new HttpError("Server does not support Extended Connect (RFC 9220).");
        }

        // https://www.rfc-editor.org/rfc/rfc8441#section-4
        // "A new pseudo-header field :protocol MAY be included on request HEADERS indicating the desired protocol to be
        //  spoken on the tunnel created by CONNECT. The pseudo-header field is single valued and contains a value from
        //  the "Hypertext Transfer Protocol (HTTP) Upgrade Token Registry located at <https://www.iana.org/assignments/http-upgrade-tokens/>"
        // "On requests that contain the :protocol pseudo-header field, the :scheme and :path pseudo-header fields of
        //  the target URI (see Section 5) MUST also be included."
        HeadersFrame headersFrame = new HeadersFrame(request.headers(), Map.of(
                ":authority", extractAuthority(request.uri()),
                ":method", "CONNECT",
                ":protocol", protocol,
                ":scheme", scheme,
                ":path", extractPath(request.uri())));

        return capsuleProtocol(createHttpStream(headersFrame));
    }

    private CapsuleProtocolStream capsuleProtocol(QuicStream httpStream) {
        return new CapsuleProtocolStream() {

            private Map<Long, Function<InputStream, Capsule>> capsuleParsers = new HashMap<>();
            private PushbackInputStream inputStream = new PushbackInputStream(httpStream.getInputStream(), 8);

            @Override
            public Capsule receive() throws IOException {
                long type = VariableLengthIntegerUtil.peekLong(inputStream);
                if (capsuleParsers.containsKey(type)) {
                    try {
                        return capsuleParsers.get(type).apply(inputStream);
                    }
                    catch (UncheckedIOException ioException) {
                        throw ioException.getCause();
                    }
                }
                else {
                    return parseGenericCapsule();
                }
            }

            @Override
            public void send(Capsule capsule) throws IOException {
                capsule.write(httpStream.getOutputStream());
                httpStream.getOutputStream().flush();
            }

            @Override
            public void sendAndClose(Capsule capsule) throws IOException {
                capsule.write(httpStream.getOutputStream());
                httpStream.getOutputStream().close();
            }

            @Override
            public void close() throws IOException {
                httpStream.getOutputStream().close();
            }

            @Override
            public long getStreamId() {
                return httpStream.getStreamId();
            }

            @Override
            public void registerCapsuleParser(long type, Function<InputStream, Capsule> parser) {
                capsuleParsers.put(type, parser);
            }

            private Capsule parseGenericCapsule() throws IOException {
                long type = VariableLengthInteger.parseLong(inputStream);
                long length = VariableLengthInteger.parseLong(inputStream);
                byte[] data = new byte[(int) length];
                httpStream.getInputStream().read(data);
                return new GenericCapsule(type, data);
            }
        };
    }

    @Override
    protected void handleBidirectionalStream(QuicStream quicStream) {
        if (bidirectionalStreamHandler != null) {
            bidirectionalStreamHandler.accept(wrap(quicStream));
        }
        else {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-bidirectional-streams
            // "Clients MUST treat receipt of a server-initiated bidirectional stream as a connection error of type
            //  H3_STREAM_CREATION_ERROR unless such an extension has been negotiated."
            connectionError(H3_STREAM_CREATION_ERROR);
        }
    }

    private QuicStream createHttpStream(HeadersFrame headersFrame) throws IOException, HttpError {
        QuicStream httpStream = quicConnection.createStream(true);
        httpStream.getOutputStream().write(headersFrame.toBytes(qpackEncoder));

        Http3Frame responseFrame = readFrame(httpStream.getInputStream());
        if (responseFrame instanceof HeadersFrame) {
            HttpResponseInfo responseInfo;
            try {
                responseInfo = new HttpResponseInfo((HeadersFrame) responseFrame);
            }
            catch (MalformedResponseException e) {
                throw new ProtocolException("Malformed response from server: missing status code");
            }
            int statusCode = responseInfo.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return httpStream;
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

    static String extractPath(URI uri) {
        String path = uri.getPath();
        if (path.isBlank()) {
            path = "/";
        }
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        return path;
    }

    static String extractAuthority(URI uri) {
        int port = uri.getPort();
        if (port <= 0) {
            port = Http3ClientConnectionImpl.DEFAULT_HTTP3_PORT;
        }
        return uri.getHost() + ":" + port;
    }

    private static Http3ConnectionSettings defaultConnectionSettings() {
        return new Http3ConnectionSettings() {
            @Override
            public boolean disableCertificateCheck() {
                return false;
            }

            @Override
            public int maxAdditionalPeerInitiatedUnidirectionalStreams() {
                return 0;
            }

            @Override
            public int maxAdditionalPeerInitiatedBidirectionalStreams() {
                return 0;
            }
        };
    }

    private static class HttpResponseInfo implements HttpResponse.ResponseInfo {
        private HttpHeaders headers;
        private final int statusCode;

        public HttpResponseInfo(HeadersFrame headersFrame) throws MalformedResponseException {
            headers = headersFrame.headers();
            String statusCodeHeader = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_STATUS);
            if (statusCodeHeader != null && isNumeric(statusCodeHeader)) {
                statusCode = Integer.parseInt(statusCodeHeader);
            }
            else {
                throw new MalformedResponseException("missing or invalid status code");
            }
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

        private boolean isNumeric(String value) {
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    /**
     * Implementation of HttpStream that sends and receives data encapsulated in HTTP3 (data) frames.
     */
    public class HttpStreamImpl implements HttpStream {

        private final QuicStream quicStream;
        private final OutputStream outputStream;
        private final InputStream inputStream;

        public HttpStreamImpl(QuicStream quicStream) {
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
                    Http3Frame frame = null;
                    try {
                        frame = readFrame(quicStream.getInputStream());
                    } catch (HttpError e) {
                        throw new IOException(e);
                    }
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

        @Override
        public long getStreamId() {
            return quicStream.getStreamId();
        }

        @Override
        public void abortReading(long errorCode) {
            quicStream.abortReading(errorCode);
        }

        @Override
        public void resetStream(long errorCode) {
            quicStream.resetStream(errorCode);
        }
    }

    private static class ResponseFramesSequenceChecker {

        private final QuicStream httpStream;

        public ResponseFramesSequenceChecker(QuicStream httpStream) {
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


        public void gotOther(Http3Frame frame) throws ProtocolException {
            if (frame instanceof UnknownFrame) {
                // Ignore, no status change.
            } else {
                throw new ProtocolException("only header and body frames are allowed on response stream");
            }
        }

        public void done() throws ProtocolException {
            if (status == ResponseStatus.INITIAL) {
                throw new ProtocolException("Missing header frame (quic stream " + httpStream.getStreamId() + ")");
            }
        }
    }

    private class BodySubscriptionHandler<T> implements Flow.Subscription {
        private final InputStream inputStream;
        private final QuicStream httpStream;
        private final ResponseFramesSequenceChecker frameSequenceChecker;
        private final HttpResponse.BodySubscriber<T> bodySubscriber;
        private final HttpResponseInfo responseInfo;
        private volatile IOException bodyReadException;

        public BodySubscriptionHandler(QuicStream httpStream, ResponseFramesSequenceChecker frameSequenceChecker,
                                       HttpResponse.BodySubscriber bodySubscriber, HttpResponseInfo responseInfo) {
            this.inputStream = httpStream.getInputStream();
            this.httpStream = httpStream;
            this.frameSequenceChecker = frameSequenceChecker;
            this.bodySubscriber = bodySubscriber;
            this.responseInfo = responseInfo;
        }

        @Override
        public void request(long n) {
            try {
                DataFrame dataFrame;
                do {
                    dataFrame = readDataFrame(inputStream);
                    if (dataFrame != null) {
                        frameSequenceChecker.gotData();
                        n--;
                        bodySubscriber.onNext(List.of(ByteBuffer.wrap(dataFrame.getPayload())));
                    }
                } while (n > 0 && dataFrame != null);

                if (dataFrame == null) {
                    // End of stream
                    frameSequenceChecker.done();
                    bodySubscriber.onComplete();
                    close();
                }
            } catch (IOException e) {
                bodyReadException = e;
                bodySubscriber.onError(e);
                close();
            }
        }

        @Override
        public void cancel() {
            httpStream.abortReading(H3_REQUEST_CANCELLED);
            bodySubscriber.onComplete();
            close();
        }

        public void checkError() throws IOException {
            if (bodyReadException != null) {
                throw bodyReadException;
            }
        }

        private void close() {
            connectionStats = quicConnection.getStats();
        }

        private DataFrame readDataFrame(InputStream inputStream) throws IOException {
            Http3Frame frame;
            try {
                frame = readFrame(inputStream);
                if (frame == null) {
                    return null;
                }
            } catch (HttpError e) {
                throw new IOException(e);
            }
            if (frame instanceof DataFrame) {
                return (DataFrame) frame;
            }
            if (frame instanceof HeadersFrame) {
                frameSequenceChecker.gotHeader();
                addTrailingHeaders((HeadersFrame) frame);
                // No additional frames expected, but if more frames follow, an error must be generated, so
                return readDataFrame(inputStream);
            }
            else {
                frameSequenceChecker.gotOther(frame);
                // If it gets here, the unknown frame is ignored, continue to (try to) read a data frame
                return readDataFrame(inputStream);
            }
        }

        private void addTrailingHeaders(HeadersFrame headersFrame) {
            responseInfo.add(headersFrame);
        }
    }
}