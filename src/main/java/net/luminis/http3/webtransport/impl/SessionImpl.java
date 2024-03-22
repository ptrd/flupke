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

import net.luminis.http3.core.Capsule;
import net.luminis.http3.core.CapsuleProtocolStream;
import net.luminis.http3.core.Http3Connection;
import net.luminis.http3.core.HttpStream;
import net.luminis.http3.impl.VariableLengthIntegerUtil;
import net.luminis.http3.webtransport.Session;
import net.luminis.http3.webtransport.WebTransportStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.luminis.http3.webtransport.Constants.CLOSE_WEBTRANSPORT_SESSION;
import static net.luminis.http3.webtransport.Constants.FRAME_TYPE_WEBTRANSPORT_STREAM;
import static net.luminis.http3.webtransport.Constants.STREAM_TYPE_WEBTRANSPORT;

public class SessionImpl implements Session {

    private final Http3Connection http3Connection;
    private final long sessionId;
    private Consumer<WebTransportStream> unidirectionalStreamReceiveHandler;
    private Consumer<WebTransportStream> bidirectionalStreamReceiveHandler;
    private BiConsumer<Long, String> sessionTerminatedEventListener;

    SessionImpl(Http3Connection http3Connection, CapsuleProtocolStream connectStream,
                Consumer<WebTransportStream> unidirectionalStreamHandler, Consumer<WebTransportStream> bidirectionalStreamHandler) {
        this.http3Connection = http3Connection;
        sessionId = connectStream.getStreamId();

        unidirectionalStreamReceiveHandler = unidirectionalStreamHandler;
        bidirectionalStreamReceiveHandler = bidirectionalStreamHandler;
        sessionTerminatedEventListener = (errorCode, errorMessage) -> {};

        connectStream.registerCapsuleParser(CLOSE_WEBTRANSPORT_SESSION, inputStream -> {
            try {
                return new CloseWebtransportSessionCapsule(inputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        new Thread(() -> {
            try {
                boolean closed = false;
                while (! closed) {
                    Capsule receivedCapsule = connectStream.receive();
                    if (receivedCapsule.getType() == CLOSE_WEBTRANSPORT_SESSION) {
                        CloseWebtransportSessionCapsule webtransportClose = (CloseWebtransportSessionCapsule) receivedCapsule;
                        closed(webtransportClose.getApplicationErrorCode(), webtransportClose.getApplicationErrorMessage());
                        closed = true;
                    }
                }
            }
            catch (IOException e) {
                // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
                // "Cleanly terminating a CONNECT stream without a CLOSE_WEBTRANSPORT_SESSION capsule SHALL be
                //  semantically equivalent to terminating it with a CLOSE_WEBTRANSPORT_SESSION capsule that has an error
                //  code of 0 and an empty error string."
                closed(0, "");
            }
        }, "webtransport-connectstream-" + sessionId).start();
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
        unidirectionalStreamReceiveHandler = handler;
    }

    @Override
    public void setBidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler) {
        bidirectionalStreamReceiveHandler = handler;
    }

    @Override
    public void close(long applicationErrorCode, String applicationErrorMessage) {

    }

    @Override
    public void close() {

    }

    @Override
    public void registerSessionTerminatedEventListener(BiConsumer<Long, String> listener) {
        sessionTerminatedEventListener = Objects.requireNonNull(listener);
    }

    void handleUnidirectionalStream(HttpStream inputStream) {
        unidirectionalStreamReceiveHandler.accept(wrapInputOnly(inputStream));
    }

    void handleBidirectionalStream(HttpStream httpStream) {
        bidirectionalStreamReceiveHandler.accept(wrap(httpStream));
    }

    void closed(long applicationErrorCode, String applicationErrorMessage) {
        sessionTerminatedEventListener.accept(applicationErrorCode, applicationErrorMessage);
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

    private WebTransportStream wrapInputOnly(HttpStream inputStream) {
        return new WebTransportStream() {
            @Override
            public OutputStream getOutputStream() {
                return null;
            }

            @Override
            public InputStream getInputStream() {
                return inputStream.getInputStream();
            }
        };
    }
}
