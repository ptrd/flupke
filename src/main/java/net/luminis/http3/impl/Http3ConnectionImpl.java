/*
 * Copyright © 2023 Peter Doornbosch
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

import net.luminis.http3.core.Http3Connection;
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.server.HttpError;
import net.luminis.qpack.Decoder;
import net.luminis.quic.NotYetImplementedException;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.VariableLengthInteger;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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


    public Http3ConnectionImpl(QuicConnection quicConnection) {
        this.quicConnection = quicConnection;
        qpackDecoder = new Decoder();
        settingsParameters = new HashMap<>();

        registerStandardStreamHandlers();
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
        };
    }

    @Override
    public HttpStream createBidirectionalStream() {
        return wrap(quicConnection.createStream(true));
    }

    @Override
    public void addSettingsParameter(long identifier, long value) {
        settingsParameters.put(identifier, value);  // TODO: check overwrite?
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
            quicStream.closeInput(H3_STREAM_CREATION_ERROR);
        }
    }

    protected HttpStream wrap(QuicStream quicStream) {
        return new HttpStream() {
            @Override
            public OutputStream getOutputStream() {
                return quicStream.getOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                return quicStream.getInputStream();
            }

            @Override
            public long getStreamId() {
                return quicStream.getStreamId();
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

            SettingsFrame settingsFrame = new SettingsFrame(0, 0);
            settingsFrame.addAdditionalSettings(settingsParameters);
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

    protected SettingsFrame processControlStream(InputStream controlStream) {
        try {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
            // "Each side MUST initiate a single control stream at the beginning of the connection and send its SETTINGS
            //  frame as the first frame on this stream."
            long frameType = VariableLengthInteger.parseLong(controlStream);
            // "If the first frame of the control stream is any other frame type, this MUST be treated as a connection error
            //  of type H3_MISSING_SETTINGS."
            if (frameType != (long) FRAME_TYPE_SETTINGS) {
                connectionError(H3_MISSING_SETTINGS);
                return null;
            }
            int frameLength = VariableLengthInteger.parse(controlStream);
            byte[] payload = readExact(controlStream, frameLength);

            SettingsFrame settingsFrame = new SettingsFrame().parsePayload(ByteBuffer.wrap(payload));
            peerQpackMaxTableCapacity = settingsFrame.getQpackMaxTableCapacity();
            peerQpackBlockedStreams = settingsFrame.getQpackBlockedStreams();
            return settingsFrame;
        } catch (IOException e) {
            // "If either control stream is closed at any point, this MUST be treated as a connection error of type
            //  H3_CLOSED_CRITICAL_STREAM."
            connectionError(H3_CLOSED_CRITICAL_STREAM);
            return null;
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
     * @return the frame read, or null if no frame is available.
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

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-error-handling
    protected void connectionError(int http3ErrorCode) {
        quicConnection.close(http3ErrorCode, null);
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
}
