/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
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

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.generic.VariableLengthInteger;
import tech.kwik.flupke.Http3Connection;
import tech.kwik.flupke.HttpError;
import tech.kwik.flupke.HttpStream;
import tech.kwik.qpack.Decoder;
import tech.kwik.qpack.Encoder;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static tech.kwik.flupke.impl.SettingsFrame.QPACK_BLOCKED_STREAMS;
import static tech.kwik.flupke.impl.SettingsFrame.QPACK_MAX_TABLE_CAPACITY;
import static tech.kwik.flupke.impl.SettingsFrame.SETTINGS_ENABLE_CONNECT_PROTOCOL;

public class Http3ConnectionImpl implements Http3Connection {

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-stream-types
    public static final int STREAM_TYPE_CONTROL_STREAM = 0x00;
    public static final int STREAM_TYPE_PUSH_STREAM = 0x01;

    // https://www.rfc-editor.org/rfc/rfc9204.html#name-stream-type-registration
    public static final int STREAM_TYPE_QPACK_ENCODER = 0x02;
    public static final int STREAM_TYPE_QPACK_DECODER = 0x03;

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-http-3-error-codes
    // "No error. This is used when the connection or stream needs to be closed, but there is no error to signal.
    public static final int H3_NO_ERROR = 0x0100;
    // "Peer violated protocol requirements in a way that does not match a more specific error code or endpoint declines
    //  to use the more specific error code."
    public static final int H3_GENERAL_PROTOCOL_ERROR = 0x0101;
    // "An internal error has occurred in the HTTP stack."
    public static final int H3_INTERNAL_ERROR = 0x0102;
    // "The endpoint detected that its peer created a stream that it will not accept."
    public static final int H3_STREAM_CREATION_ERROR = 0x0103;
    // A stream required by the HTTP/3 connection was closed or reset.
    public static final int H3_CLOSED_CRITICAL_STREAM = 0x0104;
    // A frame was received that was not permitted in the current state or on the current stream.
    public static final int H3_FRAME_UNEXPECTED = 0x0105;
    // A frame that fails to satisfy layout requirements or with an invalid size was received.
    public static final int H3_FRAME_ERROR = 0x0106;
    // The endpoint detected that its peer is exhibiting a behavior that might be generating excessive load.
    public static final int H3_EXCESSIVE_LOAD = 0x0107;
    // A stream ID or push ID was used incorrectly, such as exceeding a limit, reducing a limit, or being reused.
    public static final int H3_ID_ERROR = 0x0108;
    // An endpoint detected an error in the payload of a SETTINGS frame.
    public static final int H3_SETTINGS_ERROR = 0x0109;
    // No SETTINGS frame was received at the beginning of the control stream.
    public static final int H3_MISSING_SETTINGS = 0x010a;
    // A server rejected a request without performing any application processing.
    public static final int H3_REQUEST_REJECTED = 0x010b;
    // The request or its response (including pushed response) is cancelled.
    public static final int H3_REQUEST_CANCELLED = 0x010c;
    // The client's stream terminated without containing a fully formed request.
    public static final int H3_REQUEST_INCOMPLETE = 0x010d;
    // An HTTP message was malformed and cannot be processed.
    public static final int H3_MESSAGE_ERROR = 0x010e;
    // The TCP connection established in response to a CONNECT request was reset or abnormally closed.
    public static final int H3_CONNECT_ERROR = 0x010f;
    // The requested operation cannot be served over HTTP/3. The peer should retry over HTTP/1.1."
    public static final int H3_VERSION_FALLBACK = 0x0110;

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-frame-types
    public static final int FRAME_TYPE_DATA = 0x00;
    public static final int FRAME_TYPE_HEADERS = 0x01;
    public static final int FRAME_TYPE_CANCEL_PUSH = 0x03;
    public static final int FRAME_TYPE_SETTINGS = 0x04;
    public static final int FRAME_TYPE_PUSH_PROMISE = 0x05;
    public static final int FRAME_TYPE_GOAWAY = 0x07;
    public static final int FRAME_TYPE_MAX_PUSH_ID = 0x0d;

    protected final QuicConnection quicConnection;
    protected InputStream peerEncoderStream;
    protected int peerQpackBlockedStreams;
    protected int peerQpackMaxTableCapacity;
    protected Map<Long, Consumer<HttpStream>> unidirectionalStreamHandler = new HashMap<>();
    protected final Decoder qpackDecoder;
    protected final Map<Long, Long> settingsParameters;
    protected final Map<Long, Long> peerSettingsParameters;
    protected final CountDownLatch settingsFrameReceived;
    private final List<Long> internalSettingsParameterIds = List.of(
            (long) QPACK_MAX_TABLE_CAPACITY,
            (long) QPACK_BLOCKED_STREAMS,
            (long) SETTINGS_ENABLE_CONNECT_PROTOCOL
    );
    protected Encoder qpackEncoder;


    public Http3ConnectionImpl(QuicConnection quicConnection) {
        this.quicConnection = quicConnection;
        qpackDecoder = Decoder.newBuilder().build();
        settingsParameters = new HashMap<>();
        settingsParameters.put((long) QPACK_MAX_TABLE_CAPACITY, 0L);
        settingsParameters.put((long) QPACK_BLOCKED_STREAMS, 0L);

        peerSettingsParameters = new HashMap<>();

        settingsFrameReceived = new CountDownLatch(1);

        registerStandardStreamHandlers();
        qpackEncoder = Encoder.newBuilder().build();
    }

    @Override
    public void registerUnidirectionalStreamType(long streamType, Consumer<HttpStream> handler) {
        // ensure the stream type is not one of the standard types
        if (streamType >= 0x00 && streamType <= 0x03) {
            throw new IllegalArgumentException("Cannot register standard stream type");
        }
        if (isReservedStreamType(streamType)) {
            throw new IllegalArgumentException("Cannot register reserved stream type");
        }
        unidirectionalStreamHandler.put(streamType, handler);
    }

    @Override
    public HttpStream createUnidirectionalStream(long streamType) throws IOException {
        // ensure the stream type is not one of the standard types
        if (streamType >= 0x00 && streamType <= 0x03) {
            throw new IllegalArgumentException("Cannot create standard stream type");
        }
        if (isReservedStreamType(streamType)) {
            throw new IllegalArgumentException("Cannot create reserved stream type");
        }
        QuicStream quicStream = quicConnection.createStream(false);
        VariableLengthIntegerUtil.write(streamType, quicStream.getOutputStream());
        return new HttpStream() {
            @Override
            public OutputStream getOutputStream() {
                return quicStream.getOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                throw new IllegalStateException("Cannot read from unidirectional stream");
            }

            @Override
            public long getStreamId() {
                return quicStream.getStreamId();
            }

            @Override
            public boolean isBidirectional() {
                return false;
            }

            @Override
            public void abortReading(long errorCode) {
                quicStream.abortReading(errorCode);
            }

            @Override
            public void resetStream(long errorCode) {
                quicStream.resetStream(errorCode);
            }
        };
    }

    @Override
    public HttpStream createBidirectionalStream() throws IOException {
        return wrap(quicConnection.createStream(true));
    }

    @Override
    public void addSettingsParameter(long identifier, long value) {
        if (identifier < 0) {
            throw new IllegalArgumentException("Identifier must be a positive integer");
        }
        if (internalSettingsParameterIds.contains(identifier)) {
            throw new IllegalArgumentException("Cannot overwrite internal settings parameter");
        }
        settingsParameters.put(identifier, value);
    }

    @Override
    public Optional<Long> getPeerSettingsParameter(long identifier) {
        try {
            settingsFrameReceived.await(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            return Optional.empty();
        }
        return Optional.ofNullable(peerSettingsParameters.get(identifier));
    }

    private boolean isReservedStreamType(long streamType) {
        // https://www.rfc-editor.org/rfc/rfc9114.html#stream-grease
        // Stream types of the format 0x1f * N + 0x21 for non-negative integer values of N are reserved
        return (streamType - 0x21) % 0x1f == 0;
    }

    protected void registerStandardStreamHandlers() {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
        // "Two stream types are defined in this document: control streams (Section 6.2.1) and push streams (Section 6.2.2).
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
        // "A control stream is indicated by a stream type of 0x00."
        unidirectionalStreamHandler.put((long) STREAM_TYPE_CONTROL_STREAM, httpStream -> processControlStream(httpStream.getInputStream()));
        // "[QPACK] defines two additional stream types. Other stream types can be defined by extensions to HTTP/3;..."
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-encoder-and-decoder-streams
        // "An encoder stream is a unidirectional stream of type 0x02."
        unidirectionalStreamHandler.put((long) STREAM_TYPE_QPACK_ENCODER, httpStream -> setPeerEncoderStream(httpStream.getInputStream()));

        unidirectionalStreamHandler.put((long) STREAM_TYPE_QPACK_DECODER, httpStream -> {});
    }

    protected void handleUnidirectionalStream(QuicStream quicStream) {
        InputStream stream = quicStream.getInputStream();
        long streamType;
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
        // "Unidirectional streams, in either direction, are used for a range of purposes. The purpose is indicated by
        //  a stream type, which is sent as a variable-length integer at the start of the stream."
        try {
            streamType = VariableLengthInteger.parseLong(stream);
        }
        catch (IOException ioError) {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
            // "A receiver MUST tolerate unidirectional streams being closed or reset prior to the reception of the
            //  unidirectional stream header."
            return;
        }
        Consumer<HttpStream> streamHandler = unidirectionalStreamHandler.get(streamType);
        if (streamHandler != null) {
            streamHandler.accept(wrap(quicStream));
        }
        else {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-unidirectional-streams
            // "If the stream header indicates a stream type that is not supported by the recipient, the remainder
            //  of the stream cannot be consumed as the semantics are unknown. Recipients of unknown stream types MUST
            //  either abort reading of the stream or discard incoming data without further processing. If reading is
            //  aborted, the recipient SHOULD use the H3_STREAM_CREATION_ERROR error code "
            quicStream.abortReading(H3_STREAM_CREATION_ERROR);
        }
    }

    protected HttpStream wrap(QuicStream quicStream) {
        return wrapWith(quicStream, quicStream.getInputStream());
    }

    protected HttpStream wrapWith(QuicStream quicStream, InputStream inputStream) {
        return new HttpStream() {
            @Override
            public OutputStream getOutputStream() {
                return quicStream.getOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                return inputStream;
            }

            @Override
            public long getStreamId() {
                return quicStream.getStreamId();
            }

            @Override
            public boolean isBidirectional() {
                return quicStream.isBidirectional();
            }

            @Override
            public void abortReading(long errorCode) {
                quicStream.abortReading(errorCode);
            }

            @Override
            public void resetStream(long errorCode) {
                quicStream.resetStream(errorCode);
            }
        };
    }

    protected void startControlStream() {
        try {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
            // "Each side MUST initiate a single control stream at the beginning of the connection and send its SETTINGS
            //  frame as the first frame on this stream."
            QuicStream clientControlStream = quicConnection.createStream(false);
            OutputStream clientControlOutput = clientControlStream.getOutputStream();
            clientControlOutput.write(STREAM_TYPE_CONTROL_STREAM);

            SettingsFrame settingsFrame = new SettingsFrame();
            settingsFrame.addParameters(settingsParameters);
            ByteBuffer serializedSettings = settingsFrame.getBytes();
            clientControlStream.getOutputStream().write(serializedSettings.array(), 0, serializedSettings.limit());
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
            // "The sender MUST NOT close the control stream, and the receiver MUST NOT request that the sender close
            //  the control stream."
        }
        catch (IOException e) {
            // QuicStream's output stream will never throw an IOException, unless stream is closed.
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
            // "If either control stream is closed at any point, this MUST be treated as a connection error of type
            //  H3_CLOSED_CRITICAL_STREAM."
            connectionError(H3_CLOSED_CRITICAL_STREAM);
        }
    }

    protected void processControlStream(InputStream controlStream) {
        try {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
            // "Each side MUST initiate a single control stream at the beginning of the connection and send its SETTINGS
            //  frame as the first frame on this stream."
            long frameType = VariableLengthInteger.parseLong(controlStream);
            // "If the first frame of the control stream is any other frame type, this MUST be treated as a connection error
            //  of type H3_MISSING_SETTINGS."
            if (frameType != (long) FRAME_TYPE_SETTINGS) {
                connectionError(H3_MISSING_SETTINGS);
            }
            int frameLength = VariableLengthInteger.parse(controlStream);
            byte[] payload = readExact(controlStream, frameLength);

            SettingsFrame settingsFrame = new SettingsFrame().parsePayload(ByteBuffer.wrap(payload));
            peerQpackMaxTableCapacity = settingsFrame.getQpackMaxTableCapacity();
            peerQpackBlockedStreams = settingsFrame.getQpackBlockedStreams();
            peerSettingsParameters.putAll(settingsFrame.getAllParameters());
            settingsFrameReceived.countDown();
        }
        catch (IOException e) {
            // "If either control stream is closed at any point, this MUST be treated as a connection error of type
            //  H3_CLOSED_CRITICAL_STREAM."
            connectionError(H3_CLOSED_CRITICAL_STREAM);
        }
    }

    void setPeerEncoderStream(InputStream stream) {
        peerEncoderStream = stream;
    }

    protected Http3Frame readFrame(InputStream input) throws IOException, HttpError {
        return readFrame(input, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    /**
     * Reads one HTTP3 frame from the given input stream (if any).
     * @param input the input stream to read from.
     * @param maxHeadersSize the maximum allowed size for a headers frame; if a headers frame is read and its size exceeds this value, a HttpError is thrown
     * @param maxDataSize the maximum allowed size for a data frame; if a data frame is read and its size exceeds this value, a HttpError is thrown
     * @return the frame read, or null if no frame is available due to end of stream.
     * @throws IOException
     */
    protected Http3Frame readFrame(InputStream input, long maxHeadersSize, long maxDataSize) throws IOException, HttpError {
        // PushbackInputStream only buffers the unread bytes, so it's safe to use it as a temporary wrapper for the input stream.
        PushbackInputStream inputStream = new PushbackInputStream(input, 1);
        int firstByte = inputStream.read();
        if (firstByte == -1) {
            return null;
        }
        inputStream.unread(firstByte);

        long frameType = VariableLengthInteger.parseLong(inputStream);
        int payloadLength = VariableLengthInteger.parse(inputStream);

        Http3Frame frame;
        switch ((int) frameType) {
            case FRAME_TYPE_HEADERS:
                if (payloadLength > maxHeadersSize) {
                    throw new HttpError("max header size exceeded", 414);
                }
                frame = new HeadersFrame().parsePayload(readExact(inputStream, payloadLength), qpackDecoder);
                break;
            case FRAME_TYPE_DATA:
                if (payloadLength > maxDataSize) {
                    throw new HttpError("max data size exceeded", 400);
                }
                frame = new DataFrame().parsePayload(readExact(inputStream, payloadLength));
                break;
            case FRAME_TYPE_SETTINGS:
                frame = new SettingsFrame().parsePayload(ByteBuffer.wrap(readExact(inputStream, payloadLength)));
                break;
            case FRAME_TYPE_GOAWAY:
            case FRAME_TYPE_CANCEL_PUSH:
            case FRAME_TYPE_MAX_PUSH_ID:
            case FRAME_TYPE_PUSH_PROMISE:
                throw new NotYetImplementedException("Frame type " + frameType + " not yet implemented");
            default:
                // https://www.rfc-editor.org/rfc/rfc9114.html#extensions
                // "Extensions are permitted to use new frame types (Section 7.2), ...."
                // "Implementations MUST ignore unknown or unsupported values in all extensible protocol elements."
                inputStream.skip(payloadLength);
                frame = new UnknownFrame();
        }
        return frame;
    }

    protected void connectionError(long http3ErrorCode) {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-error-handling
        // "If an entire connection needs to be terminated, QUIC similarly provides mechanisms to communicate a reason;
        //  see Section 5.3 of [QUIC-TRANSPORT]. This is referred to as a "connection error". Similar to stream errors,
        //  an HTTP/3 implementation can terminate a QUIC connection and communicate the reason using an error code
        //  from Section 8.1."
        quicConnection.close(http3ErrorCode, null);
    }

    protected void streamError(long http3ErrorCode, QuicStream quicStream) {
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-error-handling
        // "When a stream cannot be completed successfully, QUIC allows the application to abruptly terminate (reset)
        //  that stream and communicate a reason; see Section 2.4 of [QUIC-TRANSPORT]. This is referred to as a
        //  "stream error". An HTTP/3 implementation can decide to close a QUIC stream and communicate the type of error."
        quicStream.resetStream(http3ErrorCode);
    }

    protected byte[] readExact(InputStream inputStream, int length) throws IOException {
        byte[] data = inputStream.readNBytes(length);
        if (data.length != length) {
            throw new EOFException("Stream closed by peer");
        }
        return data;
    }

    protected void handleIncomingStream(QuicStream quicStream) {
        if (quicStream.isUnidirectional()) {
            handleUnidirectionalStream(quicStream);
        } else {
            handleBidirectionalStream(quicStream);
        }
    }

    protected void handleBidirectionalStream(QuicStream quicStream) {}

    protected static boolean unknownFrameType(Long frameType) {
        assert frameType != null && frameType >= 0;
        return frameType != FRAME_TYPE_DATA &&
                frameType != FRAME_TYPE_HEADERS &&
                frameType != FRAME_TYPE_CANCEL_PUSH &&
                frameType != FRAME_TYPE_SETTINGS &&
                frameType != FRAME_TYPE_PUSH_PROMISE &&
                frameType != FRAME_TYPE_GOAWAY &&
                frameType != FRAME_TYPE_MAX_PUSH_ID;
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
                        int count = Integer.min(dataBuffer.remaining(), len);
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
                    }
                    catch (HttpError e) {
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
        public boolean isBidirectional() {
            return quicStream.isBidirectional();
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
}
