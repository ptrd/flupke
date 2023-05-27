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
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class Http3ConnectionImpl implements Http3Connection {

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-stream-types
    public static final int STREAM_TYPE_CONTROL_STREAM = 0x00;
    public static final int STREAM_TYPE_PUSH_STREAM = 0x01;

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

    protected final QuicConnection quicConnection;

    public Http3ConnectionImpl(QuicConnection quicConnection) {
        this.quicConnection = quicConnection;
    }

    protected void startControlStream() {
        try {
            // https://www.rfc-editor.org/rfc/rfc9114.html#name-control-streams
            // "Each side MUST initiate a single control stream at the beginning of the connection and send its SETTINGS
            //  frame as the first frame on this stream."
            QuicStream clientControlStream = quicConnection.createStream(false);
            OutputStream clientControlOutput = clientControlStream.getOutputStream();
            clientControlOutput.write(STREAM_TYPE_CONTROL_STREAM);

            ByteBuffer settingsFrame = new SettingsFrame(0, 0).getBytes();
            clientControlStream.getOutputStream().write(settingsFrame.array(), 0, settingsFrame.limit());
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

    // https://www.rfc-editor.org/rfc/rfc9114.html#name-error-handling
    protected void connectionError(int http3ErrorCode) {
    }
}
