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

import net.luminis.http3.core.Http3ClientConnection;
import net.luminis.http3.server.HttpError;
import net.luminis.qpack.Encoder;
import net.luminis.quic.*;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.NullLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;


public class Http3ClientConnectionImpl extends Http3ConnectionImpl implements Http3ClientConnection {

    private InputStream serverPushStream;
    private Statistics connectionStats;
    private boolean initialized;
    private Encoder qpackEncoder;
    private final CountDownLatch settingsFrameReceived;
    private boolean settingsEnableConnectProtocol;

    public Http3ClientConnectionImpl(String host, int port) throws IOException {
        this(host, port, false, null);
    }

    public Http3ClientConnectionImpl(String host, int port, boolean disableCertificateCheck, Logger logger) throws IOException {
        this(createQuicConnection(host, port, disableCertificateCheck, logger));
    }

    public Http3ClientConnectionImpl(QuicConnection quicConnection) {
        super(quicConnection);

        quicConnection.setPeerInitiatedStreamCallback(stream -> doAsync(() -> registerServerInitiatedStream(stream)));

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.1
        // "clients MUST omit or specify a value of zero for the QUIC transport parameter "initial_max_bidi_streams"."
        quicConnection.setMaxAllowedBidirectionalStreams(0);

        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2
        // "To reduce the likelihood of blocking,
        //   both clients and servers SHOULD send a value of three or greater for
        //   the QUIC transport parameter "initial_max_uni_streams","
        quicConnection.setMaxAllowedUnidirectionalStreams(3);

        qpackEncoder = new Encoder();

        settingsFrameReceived = new CountDownLatch(1);
    }

    public Http3ClientConnectionImpl(String host, int port, Encoder encoder) throws IOException {
        this(host, port, false, null);
        qpackEncoder = encoder;
    }

    @Override
    public void connect(int connectTimeoutInMillis) throws IOException {
        synchronized (this) {
            if (! ((QuicClientConnection) quicConnection).isConnected()) {
                Version quicVersion = determinePreferredQuicVersion();
                String applicationProtocol = quicVersion.equals(Version.QUIC_version_1) ? "h3" : determineH3Version(quicVersion);
                ((QuicClientConnection) quicConnection).connect(connectTimeoutInMillis, applicationProtocol, null, Collections.emptyList());
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

        HttpResponse.BodySubscriber<T> bodySubscriber = null;
        HttpResponseInfo responseInfo = null;
        ResponseState responseState = new ResponseState(httpStream);

        Http3Frame frame;
        while ((frame = readFrame(responseStream)) != null) {
            if (frame instanceof HeadersFrame) {
                responseState.gotHeader();
                if (responseInfo == null) {
                    // First frame should contain :status pseudo-header and other headers that the body handler might use to determine what kind of body subscriber to use
                    responseInfo = new HttpResponseInfo((HeadersFrame) frame);

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

        return new Http3Response<>(
                request,
                responseInfo.statusCode(),
                responseInfo.headers(),
                bodySubscriber.getBody());
    }

    void registerServerInitiatedStream(QuicStream stream) {
        handleUnidirectionalStream(stream);
    }

    @Override
    protected void registerStandardStreamHandlers() {
        super.registerStandardStreamHandlers();
        unidirectionalStreamHandler.put((long) STREAM_TYPE_PUSH_STREAM, this::setServerPushStream);
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
        return peerQpackMaxTableCapacity;
    }

    public int getServerQpackBlockedStreams() {
        return peerQpackBlockedStreams;
    }

    public void setReceiveBufferSize(long receiveBufferSize) {
        quicConnection.setDefaultStreamReceiveBufferSize(receiveBufferSize);
    }

    @Override
    public Statistics getConnectionStats() {
        return connectionStats;
    }

    @Override
    public HttpStreamImpl sendConnect(HttpRequest request) throws IOException, HttpError {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-the-connect-method
        // "A CONNECT request MUST be constructed as follows:
        //  - The :method pseudo-header field is set to "CONNECT"
        //  - The :scheme and :path pseudo-header fields are omitted
        //  - The :authority pseudo-header field contains the host and port to connect to (equivalent to the authority-form of the request-target of CONNECT requests; see Section 7.1 of [HTTP])."
        HeadersFrame headersFrame = new HeadersFrame(request.headers(), Map.of(
                ":authority", extractAuthority(request.uri()),
                ":method", "CONNECT"));

        return createHttpStream(headersFrame);
    }

    @Override
    public HttpStreamImpl sendExtendedConnect(HttpRequest request, String protocol, String scheme, Duration settingsFrameTimeout) throws InterruptedException, HttpError, IOException {
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

        return createHttpStream(headersFrame);
    }

    private HttpStreamImpl createHttpStream(HeadersFrame headersFrame) throws IOException, HttpError {
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
                return new HttpStreamImpl(httpStream);
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
