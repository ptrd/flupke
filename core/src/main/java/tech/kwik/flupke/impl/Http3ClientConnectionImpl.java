/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.impl;

import tech.kwik.core.DatagramSocketFactory;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.Statistics;
import tech.kwik.core.generic.VariableLengthInteger;
import tech.kwik.core.log.Logger;
import tech.kwik.core.log.NullLogger;
import tech.kwik.flupke.Http3ClientConnection;
import tech.kwik.flupke.Http3ConnectionSettings;
import tech.kwik.flupke.HttpError;
import tech.kwik.flupke.HttpStream;
import tech.kwik.qpack.Encoder;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
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
import java.util.concurrent.*;
import java.util.function.Consumer;

import static tech.kwik.flupke.impl.SettingsFrame.SETTINGS_ENABLE_CONNECT_PROTOCOL;


public class Http3ClientConnectionImpl extends Http3ConnectionImpl implements Http3ClientConnection {

    public static final int MAX_DATA_FRAME_READ_CHUNK_SIZE = 8192;
    public static long MAX_RECEIVED_HEADER_SIZE = Long.MAX_VALUE;
    public static long MAX_RECEIVED_DATA_SIZE = Long.MAX_VALUE;

    private InputStream serverPushStream;
    private Statistics connectionStats;
    private boolean initialized;
    private Consumer<HttpStream> bidirectionalStreamHandler;
    private long maxReceivedHeaderSize = MAX_RECEIVED_HEADER_SIZE;
    private long maxReceivedDataSize = MAX_RECEIVED_DATA_SIZE;

    public Http3ClientConnectionImpl(String host, int port) throws IOException {
        this(host, port, DEFAULT_CONNECT_TIMEOUT, defaultConnectionSettings(), null, null);
    }

    public Http3ClientConnectionImpl(String host, int port, Duration connectTimeout, Http3ConnectionSettings connectionSettings, DatagramSocketFactory datagramSocketFactory, Logger logger) throws IOException {
        this(createQuicConnection(host, port, connectTimeout, connectionSettings, datagramSocketFactory, logger));
    }

    public Http3ClientConnectionImpl(QuicConnection quicConnection) {
        super(quicConnection);

        quicConnection.setPeerInitiatedStreamCallback(stream -> doAsync(() -> handleIncomingStream(stream)));
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
            CompletableFuture<HttpResponse<T>> response = new CompletableFuture<>();
            receiveResponse(request, responseBodyHandler, httpStream, response);
            return response.get();
        }
        catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for response", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            else {
                throw new IOException("Error while getting response", cause);
            }
        }
        catch (ConnectionError e) {
            connectionError(e.getHttp3ErrorCode());
            throw new ProtocolException("H3 connection error: " + e.getHttp3ErrorCode());
        }
        catch (MalformedResponseException e) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-malformed-requests-and-resp
            // "Malformed requests or responses that are detected MUST be treated as a stream error of type H3_MESSAGE_ERROR."
            streamError(H3_MESSAGE_ERROR, httpStream);
            throw new ProtocolException("H3 stream error: H3_MESSAGE_ERROR");
        }
        catch (HttpError e) {
            return new Http3Response<>(request, e.getStatusCode(), HttpHeaders.of(Map.of(), (a,b) -> true), null);
        }
    }

    public <T> void sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, CompletableFuture<HttpResponse<T>> result) {
        QuicStream httpStream = null;
        try {
            httpStream = quicConnection.createStream(true);
            sendRequest(request, httpStream);
            receiveResponse(request, responseBodyHandler, httpStream, result);
        }
        catch (IOException e) {
            result.completeExceptionally(e);
        }
        catch (ConnectionError e) {
            connectionError(e.getHttp3ErrorCode());
            result.completeExceptionally(new ProtocolException("H3 connection error: " + e.getHttp3ErrorCode()));
        }
        catch (MalformedResponseException e) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-malformed-requests-and-resp
            // "Malformed requests or responses that are detected MUST be treated as a stream error of type H3_MESSAGE_ERROR."
            streamError(H3_MESSAGE_ERROR, httpStream);
            result.completeExceptionally(new ProtocolException("H3 stream error: " + H3_MESSAGE_ERROR));
        }
        catch (HttpError e) {
            result.complete(new Http3Response<>(request, e.getStatusCode(), HttpHeaders.of(Map.of(), (a,b) -> true), null));
        }
    }

    private static QuicConnection createQuicConnection(String host, int port, Duration connectTimeout, Http3ConnectionSettings connectionSettings, DatagramSocketFactory datagramSocketFactory, Logger logger) throws SocketException, UnknownHostException {
        QuicClientConnection.Builder builder = QuicClientConnection.newBuilder();
        try {
            builder.uri(new URI("//" + host + ":" + port));
        }
        catch (URISyntaxException e) {
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
        if (connectionSettings.trustManager() != null) {
            builder.customTrustManager(connectionSettings.trustManager());
        }
        if (connectionSettings.keyManager() != null) {
            builder.clientKeyManager(connectionSettings.keyManager());
        }

        builder.socketFactory(datagramSocketFactory);
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
                        dataFrame.writeTo(requestStream);
                    }
                    catch (IOException e) {
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

    private <T> void receiveResponse(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, QuicStream httpStream,
                                     CompletableFuture<HttpResponse<T>> response) throws IOException, MalformedResponseException, HttpError, ConnectionError {
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

        CompletionStage<T> bodyCompletion = bodySubscriber.getBody();
        bodyCompletion.whenComplete((body, error) -> {
            if (error == null) {
                response.complete(new Http3Response<>(
                        request,
                        responseInfo.statusCode(),
                        responseInfo.headers(),
                        body));
            }
            else {
                response.completeExceptionally(error);
            }
        });
    }

    private HeadersFrame readHeadersFrame(InputStream responseStream, ResponseFramesSequenceChecker frameSequenceChecker) throws IOException, HttpError, ConnectionError {
        try {
            Http3Frame frame = readFrame(responseStream, maxReceivedHeaderSize, maxReceivedDataSize);
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
        catch (EOFException e) {
            throw new ConnectionError(H3_FRAME_ERROR);
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
        return quicConnection.getStats();
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
            throw new ProtocolException("No SETTINGS frame received in time.");
        }
        if (! isEnableConnectProtocol()) {
            throw new ProtocolException("Server does not support Extended Connect (RFC 9220).");
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

    private boolean isEnableConnectProtocol() {
        return getPeerSettingsParameter(SETTINGS_ENABLE_CONNECT_PROTOCOL).orElse(0L) == 1L;
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
            public X509TrustManager trustManager() {
                return null;
            }

            @Override
            public X509ExtendedKeyManager keyManager() {
                return null;
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
                // https://www.rfc-editor.org/rfc/rfc9114.html#section-4.3.2
                // "This pseudo-header field MUST be included in all responses; otherwise, the response is malformed (see Section 4.1.2)."
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

    private static class ResponseFramesSequenceChecker {
        enum ResponseStatus {
            INITIAL,
            GOT_HEADER,
            GOT_HEADER_AND_DATA,
            GOT_HEADER_AND_DATA_AND_TRAILING_HEADER,
        };

        private ResponseStatus status = ResponseStatus.INITIAL;
        private final long streamId;

        public ResponseFramesSequenceChecker(QuicStream httpStream) {
            streamId = httpStream.getStreamId();
        }

        void gotHeader() throws ConnectionError {
            if (status == ResponseStatus.INITIAL) {
                status = ResponseStatus.GOT_HEADER;
            }
            else if (status == ResponseStatus.GOT_HEADER) {
                invalidFrameSequence("Header frame is not allowed after initial header frame.");
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA) {
                status = ResponseStatus.GOT_HEADER_AND_DATA_AND_TRAILING_HEADER;
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA_AND_TRAILING_HEADER) {
                invalidFrameSequence("Header frame is not allowed after trailing header frame.");
            }
        }

        public void gotData() throws ConnectionError {
            if (status == ResponseStatus.INITIAL) {
                invalidFrameSequence("Missing header frame.");
            }
            else if (status == ResponseStatus.GOT_HEADER) {
                status = ResponseStatus.GOT_HEADER_AND_DATA;
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA) {
                // No status change
            }
            else if (status == ResponseStatus.GOT_HEADER_AND_DATA_AND_TRAILING_HEADER) {
                invalidFrameSequence("Data frame not allowed after trailing header frame.");
            }
        }

        public void gotOther(Http3Frame frame) throws ConnectionError {
            if (! (frame instanceof UnknownFrame)) {
                invalidFrameSequence("Only header and body frames are allowed on response stream.");
            }
        }

        public void gotOther(Long frameType) throws ConnectionError {
            if (! unknownFrameType(frameType)) {
                invalidFrameSequence("Only header and body frames are allowed on response stream.");
            }
        }

        public void done() throws ConnectionError {
            if (status == ResponseStatus.INITIAL) {
                invalidFrameSequence("Missing header frame.");
            }
        }

        private void invalidFrameSequence(String error) throws ConnectionError {
            // https://www.rfc-editor.org/rfc/rfc9114.html#section-4.1
            // "Receipt of an invalid sequence of frames MUST be treated as a connection error of type H3_FRAME_UNEXPECTED."
            throw new ConnectionError(H3_FRAME_UNEXPECTED, streamId, error);
        }
    }

    private class BodySubscriptionHandler<T> implements Flow.Subscription {
        private final QuicStream httpStream;
        private final ResponseFramesSequenceChecker frameSequenceChecker;
        private final HttpResponse.BodySubscriber<T> bodySubscriber;
        private final HttpResponseInfo responseInfo;
        private final DataFramesReader dataFramesReader;
        private final ExecutorService executor;
        private volatile IOException bodyReadException;

        public BodySubscriptionHandler(QuicStream httpStream, ResponseFramesSequenceChecker frameSequenceChecker,
                                       HttpResponse.BodySubscriber bodySubscriber, HttpResponseInfo responseInfo) {
            this.httpStream = httpStream;
            this.frameSequenceChecker = frameSequenceChecker;
            this.bodySubscriber = bodySubscriber;
            this.responseInfo = responseInfo;
            dataFramesReader = new DataFramesReader(httpStream.getInputStream(), Long.MAX_VALUE, this::handleNonDataFrame,
                    this::gotDataFrame);
            executor = Executors.newSingleThreadExecutor();  // Intentionally using a single thread executor to have thread confinement for reading body data
        }

        @Override
        public void request(long n) {
            executor.submit(() -> sendData(n));
        }

        private void sendData(long n) {
            try {
                int bytesRead;
                do {
                    byte[] buffer = new byte[MAX_DATA_FRAME_READ_CHUNK_SIZE];
                    bytesRead = dataFramesReader.read(buffer);
                    if (bytesRead > 0) {
                        n--;
                        bodySubscriber.onNext(List.of(ByteBuffer.wrap(buffer, 0, bytesRead)));
                    }
                } while (n > 0 && bytesRead > 0);

                dataFramesReader.checkForConnectionError();

                if (bytesRead < 0) {
                    // End of stream
                    frameSequenceChecker.done();
                    bodySubscriber.onComplete();
                    dispose();
                }
            }
            catch (IOException e) {
                bodyReadException = e;
                bodySubscriber.onError(e);
                dispose();
            }
            catch (ConnectionError e) {
                connectionError(e.getHttp3ErrorCode());
                bodyReadException = new EOFException();
                bodySubscriber.onError(bodyReadException);
                dispose();
            }
        }

        @Override
        public void cancel() {
            httpStream.abortReading(H3_REQUEST_CANCELLED);
            bodySubscriber.onComplete();
            dispose();
        }

        private void dispose() {
            executor.shutdown();
        }

        private void handleNonDataFrame(Long frameType, PushbackInputStream inputStream) {
            if (frameType == FRAME_TYPE_HEADERS) {
                try {
                    inputStream.unread(FRAME_TYPE_HEADERS);
                    Http3Frame frame = readHeadersFrame(inputStream, frameSequenceChecker);
                    addTrailingHeaders((HeadersFrame) frame);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                catch (ConnectionError e) {
                    throw new UncheckedConnectionError(e);
                }
                catch (HttpError e) {
                    // Cannot happen, because readHeadersFrame only throws HttpError when the headers exceeds max size, but client does not impose a max size
                    bodyReadException = new IOException(e);
                    bodySubscriber.onError(bodyReadException);
                }
            }
            else {
                try {
                    frameSequenceChecker.gotOther(frameType);
                    // If it gets here, the frame can and should be ignored.
                    long frameLength = VariableLengthInteger.parseLong(inputStream);
                    inputStream.skip(frameLength);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                catch (ConnectionError e) {
                    throw new UncheckedConnectionError(e);
                }
            }
        }

        private void gotDataFrame(long dataFrameLength) {
            try {
                frameSequenceChecker.gotData();
            }
            catch (ConnectionError e) {
                throw new UncheckedConnectionError(e);
            }
        }

        private void addTrailingHeaders(HeadersFrame headersFrame) {
            responseInfo.add(headersFrame);
        }
    }
}