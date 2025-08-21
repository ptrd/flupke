/*
 * Copyright © 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.server;

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.generic.InvalidIntegerEncodingException;
import tech.kwik.core.generic.VariableLengthInteger;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ServerConnection;
import tech.kwik.flupke.core.HttpError;
import tech.kwik.flupke.core.HttpStream;
import tech.kwik.flupke.impl.*;
import tech.kwik.qpack.Encoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static tech.kwik.flupke.impl.SettingsFrame.SETTINGS_ENABLE_CONNECT_PROTOCOL;


/**
 * Http connection serving HTTP requests using a given HttpRequestHandler.
 */
public class Http3ServerConnectionImpl extends Http3ConnectionImpl implements Http3ServerConnection, ApplicationProtocolConnection {

    public static int DEFAULT_MAX_HEADER_SIZE = 10 * 1024;
    public static int DEFAULT_MAX_DATA_SIZE = 10 * 1024 * 1024;

    private final HttpRequestHandler requestHandler;
    private final InetAddress clientAddress;
    private final long maxHeaderSize;
    private final long maxDataSize;
    private final ExecutorService executor;
    private final Encoder encoder;
    private final Map<String, Http3ServerExtensionFactory> extensionFactories;
    private final Map<String, Http3ServerExtension> instantiatedExtensions;
    private final ReentrantLock extensionInstantiationLock;
    private Map<Long, Consumer<HttpStream>> bidirectionalStreamHandler = new ConcurrentHashMap<>();

    public Http3ServerConnectionImpl(QuicConnection quicConnection, HttpRequestHandler requestHandler, ExecutorService executorService, Map<String, Http3ServerExtensionFactory> extensions) {
        this(quicConnection, requestHandler, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_DATA_SIZE, executorService, extensions);
    }

    public Http3ServerConnectionImpl(QuicConnection quicConnection, HttpRequestHandler requestHandler, long maxHeaderSize, long maxDataSize, ExecutorService executorService, Map<String, Http3ServerExtensionFactory> extensions) {
        super(quicConnection);
        this.requestHandler = requestHandler;
        this.maxHeaderSize = maxHeaderSize;
        this.maxDataSize = maxDataSize;
        this.executor = executorService;
        this.extensionFactories = extensions;
        this.instantiatedExtensions = new ConcurrentHashMap<>();
        encoder = Encoder.newBuilder().build();
        clientAddress = ((ServerConnection) quicConnection).getInitialClientAddress();
        settingsParameters.put((long) SETTINGS_ENABLE_CONNECT_PROTOCOL, 1L);
        extensionFactories.values().forEach(factory -> {
            factory.getExtensionSettings().forEach((key, value) -> {
                settingsParameters.put(key, value);
            });
        });
        startControlStream();
        extensionInstantiationLock = new ReentrantLock();
    }

    @Override
    public void acceptPeerInitiatedStream(QuicStream quicStream) {
        executor.execute(() -> handleIncomingStream(quicStream));
    }

    @Override
    public void registerBidirectionalStreamHandler(long frameType, Consumer<HttpStream> streamHandler) {
        bidirectionalStreamHandler.put(frameType, streamHandler);
    }

    @Override
    protected void handleBidirectionalStream(QuicStream quicStream) {
        try {
            PushbackInputStream requestStream = new PushbackInputStream(quicStream.getInputStream(), 8);
            ByteBuffer buffer = ByteBuffer.allocate(8);
            int bytesRead = requestStream.read(buffer.array());
            long frameType = VariableLengthInteger.parseLong(buffer);
            requestStream.unread(buffer.array(), 0, bytesRead);
            if (bidirectionalStreamHandler.containsKey(frameType)) {
                bidirectionalStreamHandler.get(frameType).accept(wrapWith(quicStream, requestStream));
            }
            else {
                handleStandardRequestResponseStream(quicStream, requestStream);
            }
        }
        catch (IOException | InvalidIntegerEncodingException e) {
            quicStream.abortReading(H3_INTERNAL_ERROR);
            sendHttpErrorResponse(500, "", quicStream);
        }
    }

    protected void handleStandardRequestResponseStream(QuicStream quicStream, InputStream requestStream) {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-bidirectional-streams
        // "All client-initiated bidirectional streams are used for HTTP requests and responses."
        try {
            HeadersFrame headersFrame = readRequestHeadersFrame(requestStream, maxHeaderSize);
            String httpMethod = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_METHOD);
            if (httpMethod.equals("CONNECT")) {
                handleConnectMethod(quicStream, headersFrame);
            }
            else {
                List<Http3Frame> receivedFrames = parseHttp3Frames(requestStream);
                receivedFrames.add(headersFrame);
                handleHttpRequest(receivedFrames, quicStream, encoder);
            }
        }
        catch (IOException ioError) {
            quicStream.abortReading(H3_INTERNAL_ERROR);
            sendHttpErrorResponse(500, "", quicStream);
        }
        catch (HttpError httpError) {
            quicStream.abortReading(H3_REQUEST_REJECTED);
            sendHttpErrorResponse(httpError.getStatusCode(), httpError.getMessage(), quicStream);
        }
        catch (StreamError e) {
            streamError(e.getHttp3ErrorCode(), quicStream);
        }
        catch (ConnectionError e) {
            connectionError(e.getHttp3ErrorCode());
        }
    }

    private void handleConnectMethod(QuicStream quicStream, HeadersFrame headersFrame) {
        if (headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_PROTOCOL) != null) {
            handleExtendedConnectMethod(quicStream, headersFrame);
        }
        else {
            // https://www.rfc-editor.org/rfc/rfc9110#section-9
            // "An origin server that receives a request method that is unrecognized or not implemented SHOULD respond
            // with the 501 (Not Implemented) status code. "
            sendHttpErrorResponse(501, "", quicStream);
        }
    }

    private void handleExtendedConnectMethod(QuicStream quicStream, HeadersFrame headersFrame) {
        String extensionType = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_PROTOCOL);
        Http3ServerExtension http3ServerExtension = getHttp3ServerExtension(extensionType);

        if (http3ServerExtension != null) {
            String authority = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_AUTHORITY);
            String path = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_PATH);

            AtomicInteger returnedStatusCode = new AtomicInteger();
            IntConsumer statusCallback = statusCode -> {
                returnedStatusCode.set(statusCode);
                sendHttpStatus(statusCode, null, quicStream, statusCode != 200);
            };
            HttpStream streamWrapper = new HttpStreamImpl(quicStream) {
                @Override
                public OutputStream getOutputStream() {
                    if (returnedStatusCode.get() != 200) {
                        // If the status code is not 200, we should not write to the output stream
                        throw new IllegalStateException("Cannot get output stream when status code is not 200");
                    }
                    return super.getOutputStream();
                }
            };
            http3ServerExtension.handleExtendedConnect(headersFrame.headers(), extensionType, authority, path, statusCallback, streamWrapper);
        }
        else {
            // https://www.rfc-editor.org/rfc/rfc9220.html#name-websockets-upgrade-over-htt
            // "If a server advertises support for Extended CONNECT but receives an Extended CONNECT request with a
            //  ":protocol" value that is unknown or is not supported, the server SHOULD respond to the request with a 501
            //  (Not Implemented) status code"
            sendHttpErrorResponse(501, "", quicStream);
        }
    }

    private Http3ServerExtension getHttp3ServerExtension(String extensionType) {
        extensionInstantiationLock.lock();
        try {
            if (instantiatedExtensions.get(extensionType) != null) {
                return instantiatedExtensions.get(extensionType);
            }

            if (extensionFactories.get(extensionType) != null) {
                Http3ServerExtension http3ServerExtension = extensionFactories.get(extensionType).createExtension(this);
                instantiatedExtensions.put(extensionType, http3ServerExtension);
                return http3ServerExtension;
            }
            else {
                return null;
            }
        }
        finally {
            extensionInstantiationLock.unlock();
        }
    }

    HeadersFrame readRequestHeadersFrame(InputStream inputStream, long maxHeadersSize) throws IOException, HttpError, ConnectionError, StreamError {
        long frameType = VariableLengthInteger.parseLong(inputStream);
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-expressing-http-semantics-i
        // "An HTTP message (request or response) consists of:
        //  the header section, including message control data, sent as a single HEADERS frame, (...)"
        // "Receipt of an invalid sequence of frames MUST be treated as a connection error of type H3_FRAME_UNEXPECTED."
        if (frameType != FRAME_TYPE_HEADERS) {
            throw new ConnectionError(H3_FRAME_UNEXPECTED);
        }
        int payloadLength = VariableLengthInteger.parse(inputStream);
        if (payloadLength > maxHeadersSize) {
            throw new HttpError("max header size exceeded", 431);
        }
        HeadersFrame headersFrame = new HeadersFrame().parsePayload(readExact(inputStream, payloadLength), qpackDecoder);
        String method = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_METHOD);
        String scheme = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_SCHEME);
        String path = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_PATH);
        if (method == null ||
                (!method.equals("CONNECT") &&
                        (scheme == null || path == null || !hasValidAuthorityHeader(headersFrame))) ||
                (method.equals("CONNECT") &&
                        headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_AUTHORITY) == null)) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-request-pseudo-header-field
            // "All HTTP/3 requests MUST include exactly one value for the :method, :scheme, and :path pseudo-header \
            //  fields, unless the request is a CONNECT request; see Section 4.4."
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-request-pseudo-header-field
            // "An HTTP request that omits mandatory pseudo-header fields or contains invalid values for those
            //  pseudo-header fields is malformed."
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-malformed-requests-and-resp
            // "Malformed requests or responses that are detected MUST be treated as a stream error of type H3_MESSAGE_ERROR."
            throw new StreamError(H3_MESSAGE_ERROR);
        }
        return headersFrame;
    }

    private boolean hasValidAuthorityHeader(HeadersFrame headersFrame) {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-request-pseudo-header-field
        // "If the :scheme pseudo-header field identifies a scheme that has a mandatory authority component
        //  (including "http" and "https"), the request MUST contain either an :authority pseudo-header field or a
        //  Host header field."
        String scheme = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_SCHEME);
        if (scheme.equals("http") || scheme.equals("https")) {
            String authority = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_AUTHORITY);
            if (authority == null) {
                authority = headersFrame.headers().firstValue("host").orElse(null);
            }
            return authority != null && !authority.isBlank();
        }
        else {
            return true;
        }
    }

    private void sendHttpErrorResponse(int statusCode, String message, QuicStream quicStream) {
        sendHttpStatus(statusCode, message, quicStream, true);
    }

    private void sendHttpStatus(int statusCode, String message, QuicStream quicStream, boolean closeOutput) {
        try {
            OutputStream outputStream = quicStream.getOutputStream();
            HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.PSEUDO_HEADER_STATUS, Integer.toString(statusCode));
            outputStream.write(headersFrame.toBytes(encoder));
        }
        catch (IOException e) {
        }
        finally {
            if (closeOutput) {
                try {
                    quicStream.getOutputStream().close();
                }
                catch (IOException e) {}
            }
        }
    }

    List<Http3Frame> parseHttp3Frames(InputStream requestStream) throws IOException, HttpError {
        // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1
        // "An HTTP message (request or response) consists of:
        //   1.  the header section, sent as a single HEADERS frame (see Section 7.2.2),
        //   2.  optionally, the content, if present, sent as a series of DATA frames (see Section 7.2.1), and
        //   3.  optionally, the trailer section, if present, sent as a single HEADERS frame."
        List<Http3Frame> receivedFrames = new ArrayList<>();
        int headerSize = 0;
        int dataSize = 0;

        Http3Frame frame;
        while ((frame = readFrame(requestStream, maxHeaderSize - headerSize, maxDataSize - dataSize)) != null) {
            receivedFrames.add(frame);
            if (frame instanceof HeadersFrame) {
                headerSize += ((HeadersFrame) frame).getHeadersSize();
            }
            else if (frame instanceof DataFrame) {
                dataSize += ((DataFrame) frame).getDataLength();
            }
        }

        return receivedFrames;
    }

    void handleHttpRequest(List<Http3Frame> receivedFrames, QuicStream quicStream, Encoder qpackEncoder) throws HttpError {
        HeadersFrame headersFrame = (HeadersFrame) receivedFrames.stream()
                .filter(f -> f instanceof HeadersFrame)
                .findFirst()
                .orElseThrow(() -> new HttpError("", 400));  // TODO

        String method = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_METHOD);
        String path = headersFrame.getPseudoHeader(HeadersFrame.PSEUDO_HEADER_PATH);
        HttpServerRequest request = new HttpServerRequest(method, path, null, clientAddress);
        HttpServerResponse response = new HttpServerResponse() {
            private boolean outputStarted;
            private DataFrameWriter dataFrameWriter;

            @Override
            public OutputStream getOutputStream() {
                if (!outputStarted) {
                    HeadersFrame headersFrame = new HeadersFrame(HeadersFrame.PSEUDO_HEADER_STATUS, Integer.toString(status()));
                    OutputStream outputStream = quicStream.getOutputStream();
                    try {
                        outputStream.write(headersFrame.toBytes(qpackEncoder));
                    } catch (IOException e) {
                        // Ignore, there is nothing we can do. Note Kwik will not throw exception when writing to stream.
                    }
                    outputStarted = true;
                    dataFrameWriter = new DataFrameWriter(quicStream.getOutputStream());
                }
                return dataFrameWriter;
            }

            @Override
            public long size() {
                if (dataFrameWriter != null) {
                    return dataFrameWriter.getBytesWritten();
                }
                else {
                    return 0;
                }
            }
        };

        try {
            requestHandler.handleRequest(request, response);
            if (!response.isStatusSet()) {
                response.setStatus(405);
            }
            response.getOutputStream().close();
        }
        catch (IOException e) {
            // Ignore, there is nothing we can do. Note Kwik will not throw exception when writing to stream
            // (except when writing to a closed stream)
        }
    }

}
