/*
 * Copyright © 2023, 2024 Peter Doornbosch
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
package net.luminis.http3.webtransport.impl;

import net.luminis.http3.core.CapsuleProtocolStream;
import net.luminis.http3.core.Http3Connection;
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.impl.VariableLengthIntegerUtil;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SessionImpl implements Session {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-frame-type-registration
    // "Code: 0x41
    //  Frame Type: WEBTRANSPORT_STREAM"
    public static final int FRAME_TYPE_WEBTRANSPORT_STREAM = 0x41;

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-stream-type-registration
    // "Code: 0x54
    //  Stream Type: WebTransport stream"
    public static final int STREAM_TYPE_WEBTRANSPORT = 0x54;

    private final Http3Connection http3Connection;
    private final long sessionId;

    SessionImpl(Http3Connection http3Connection, CapsuleProtocolStream connectStream, Runnable closedCallback) {
        this.http3Connection = http3Connection;
        sessionId = connectStream.getStreamId();
    }

    @Override
    public WebTransportStream createUnidirectionalStream() throws IOException {
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-unidirectional-streams
        // "The HTTP/3 unidirectional stream type SHALL be 0x54. The body of the stream SHALL be the stream type,
        //  followed by the session ID, encoded as a variable-length integer, followed by the user-specified stream data."
        HttpStream httpStream = http3Connection.createUnidirectionalStream(STREAM_TYPE_WEBTRANSPORT);
        VariableLengthIntegerUtil.write(sessionId, httpStream.getOutputStream());
        return wrap(httpStream);

    }

    @Override
    public WebTransportStream createBidirectionalStream() throws IOException {
        HttpStream httpStream = http3Connection.createBidirectionalStream();
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-bidirectional-streams
        // "The signal value, 0x41, is used by clients and servers to open a bidirectional WebTransport stream."
        VariableLengthIntegerUtil.write(FRAME_TYPE_WEBTRANSPORT_STREAM, httpStream.getOutputStream());
        VariableLengthIntegerUtil.write(sessionId, httpStream.getOutputStream());
        return wrap(httpStream);

    }

    @Override
    public void setUnidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler) {

    }

    @Override
    public void setBidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler) {

    }

    @Override
    public void close(long applicationErrorCode, String applicationErrorMessage) {

    }

    @Override
    public void close() {

    }

    @Override
    public void registerSessionTerminatedEventListener(BiConsumer<Long, String> listener) {

    }

    private WebTransportStream wrap(HttpStream httpStream) {
        return new WebTransportStream() {
            @Override
            public OutputStream getOutputStream() {
                return httpStream.getOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                return httpStream.getInputStream();
            }
        };
    }
}
