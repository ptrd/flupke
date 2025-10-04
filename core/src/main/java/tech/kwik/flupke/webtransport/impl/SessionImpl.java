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
package tech.kwik.flupke.webtransport.impl;

import tech.kwik.flupke.core.Capsule;
import tech.kwik.flupke.core.CapsuleProtocolStream;
import tech.kwik.flupke.core.Http3Connection;
import tech.kwik.flupke.core.HttpStream;
import tech.kwik.flupke.impl.VariableLengthIntegerUtil;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static tech.kwik.flupke.webtransport.Constants.*;

public class SessionImpl implements Session {

    private enum State {
        CREATED,
        OPEN,
        CLOSING,
        CLOSED
    }

    private final Http3Connection http3Connection;
    private final WebTransportContext context;
    private final CapsuleProtocolStream connectStream;
    private final long sessionId;
    private final SessionFactory sessionFactory;
    private volatile State state;
    private final ReentrantLock stateChangeLock = new ReentrantLock();
    private final Thread capsuleProcessorThread;
    private Consumer<WebTransportStream> unidirectionalStreamReceiveHandler;
    private Consumer<WebTransportStream> bidirectionalStreamReceiveHandler;
    private BiConsumer<Long, String> sessionTerminatedEventListener;
    private Queue<HttpStream> sendingStreams = new ConcurrentLinkedQueue();
    private Queue<HttpStream> receivingStreams = new ConcurrentLinkedQueue();

    SessionImpl(Http3Connection http3Connection, WebTransportContext context, CapsuleProtocolStream connectStream, SessionFactory sessionFactory) {
        this(http3Connection, context, connectStream, s -> {}, s -> {}, sessionFactory);
    }

    SessionImpl(Http3Connection http3Connection, WebTransportContext context, CapsuleProtocolStream connectStream,
                Consumer<WebTransportStream> unidirectionalStreamHandler, Consumer<WebTransportStream> bidirectionalStreamHandler,
                SessionFactory sessionFactory) {
        this.http3Connection = http3Connection;
        this.context = context;
        this.connectStream = connectStream;
        sessionId = connectStream.getStreamId();
        this.sessionFactory = sessionFactory;

        state = State.CREATED;

        unidirectionalStreamReceiveHandler = Optional.ofNullable(unidirectionalStreamHandler).orElse(stream -> {
            System.err.println("No handler set for incoming unidirectional stream.");
        });
        bidirectionalStreamReceiveHandler = Optional.ofNullable(bidirectionalStreamHandler).orElse(stream -> {
            System.err.println("No handler set for incoming bidirectional stream.");
        });
        sessionTerminatedEventListener = (errorCode, errorMessage) -> {};

        connectStream.registerCapsuleParser(CLOSE_WEBTRANSPORT_SESSION, inputStream -> {
            try {
                return new CloseWebtransportSessionCapsule(inputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        capsuleProcessorThread = new Thread(() -> receiveAndProcessCapsules(connectStream), "webtransport-connectstream-" + sessionId);
        capsuleProcessorThread.start();
    }

    @Override
    public void open() {
        changeState(State.OPEN, state -> state == State.CREATED, state -> state == State.OPEN);

        sessionFactory.startSession(this);
    }

    private void receiveAndProcessCapsules(CapsuleProtocolStream connectStream) {
        try {
            boolean closed = false;
            while (! closed) {
                Capsule receivedCapsule = connectStream.receive();
                if (receivedCapsule.getType() == CLOSE_WEBTRANSPORT_SESSION) {
                    CloseWebtransportSessionCapsule webtransportClose = (CloseWebtransportSessionCapsule) receivedCapsule;
                    closedByPeer(webtransportClose.getApplicationErrorCode(), webtransportClose.getApplicationErrorMessage());
                    closed = true;
                }
            }
        }
        catch (IOException e) {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
            // "Cleanly terminating a CONNECT stream without a CLOSE_WEBTRANSPORT_SESSION capsule SHALL be
            //  semantically equivalent to terminating it with a CLOSE_WEBTRANSPORT_SESSION capsule that has an error
            //  code of 0 and an empty error string."
            closedByPeer(0, "");
        }
    }

    @Override
    public WebTransportStream createUnidirectionalStream() throws IOException {
        checkState();

        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-unidirectional-streams
        // "The HTTP/3 unidirectional stream type SHALL be 0x54. The body of the stream SHALL be the stream type,
        //  followed by the session ID, encoded as a variable-length integer, followed by the user-specified stream data."
        HttpStream httpStream = http3Connection.createUnidirectionalStream(STREAM_TYPE_WEBTRANSPORT);
        VariableLengthIntegerUtil.write(sessionId, httpStream.getOutputStream());
        sendingStreams.add(httpStream);
        return wrap(httpStream);
    }

    @Override
    public WebTransportStream createBidirectionalStream() throws IOException {
        checkState();

        HttpStream httpStream = http3Connection.createBidirectionalStream();
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-bidirectional-streams
        // "The signal value, 0x41, is used by clients and servers to open a bidirectional WebTransport stream."
        VariableLengthIntegerUtil.write(FRAME_TYPE_WEBTRANSPORT_STREAM, httpStream.getOutputStream());
        VariableLengthIntegerUtil.write(sessionId, httpStream.getOutputStream());
        sendingStreams.add(httpStream);
        receivingStreams.add(httpStream);
        return wrap(httpStream);
    }

    private void checkState() throws IOException {
        if (state == State.CREATED) {
            throw new IllegalStateException("Session is not opened yet");
        }
        if (state != State.OPEN) {
            throw new IOException("Session is closed");
        }
    }

    @Override
    public void setUnidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler) {
        unidirectionalStreamReceiveHandler = Objects.requireNonNull(handler);
    }

    @Override
    public void setBidirectionalStreamReceiveHandler(Consumer<WebTransportStream> handler) {
        bidirectionalStreamReceiveHandler = Objects.requireNonNull(handler);
    }

    @Override
    public void close(long applicationErrorCode, String applicationErrorMessage) throws IOException {
        if (! changeState(State.CLOSING, state -> state == State.OPEN, state -> state == State.CLOSING || state == State.CLOSED)) {
            return;
        }

        // https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-07.html#name-session-wide-features
        // "Terminate the session while communicating to the peer an unsigned 32-bit error code and an error reason
        //  string of at most 1024 bytes."
        if (applicationErrorCode < 0 || applicationErrorCode > 0xffffffffL) {
            throw new IllegalArgumentException("Application error code must be a 32-bit unsigned integer");
        }
        if (applicationErrorMessage.getBytes().length > 1024) {
            throw new IllegalArgumentException("Error message must not be longer than 1024 bytes");
        }

        CloseWebtransportSessionCapsule capsule = new CloseWebtransportSessionCapsule((int) applicationErrorCode, applicationErrorMessage);
        connectStream.sendAndClose(capsule);
        stopSending();
        resetSenders();
        abortReading();
        stopReceivingCapsules();
        sessionTerminatedEventListener.accept(applicationErrorCode, applicationErrorMessage);
        sessionFactory.removeSession(this);
    }

    private void stopReceivingCapsules() {
        capsuleProcessorThread.interrupt();
    }

    @Override
    public void close() throws IOException {
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
        // "Cleanly terminating a CONNECT stream without a CLOSE_WEBTRANSPORT_SESSION capsule SHALL be semantically
        //  equivalent to terminating it with a CLOSE_WEBTRANSPORT_SESSION capsule that has an error code of 0 and an
        //  empty error string."
        close(0, "");
    }

    @Override
    public void registerSessionTerminatedEventListener(BiConsumer<Long, String> listener) {
        sessionTerminatedEventListener = Objects.requireNonNull(listener);
    }

    void handleStream(HttpStream httpStream) {
        if (httpStream.isUnidirectional()) {
            handleUnidirectionalStream(httpStream);
        }
        else {
            handleBidirectionalStream(httpStream);
        }
    }

    void handleUnidirectionalStream(HttpStream inputStream) {
        if (state == State.OPEN) {
            receivingStreams.add(inputStream);
            unidirectionalStreamReceiveHandler.accept(wrapInputOnly(inputStream));
        }
        else {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
            // "Upon learning that the session has been terminated, the endpoint MUST reset the send side and abort
            //  reading on the receive side of all of the streams associated with the session"
            inputStream.abortReading(WEBTRANSPORT_SESSION_GONE);
        }
    }

    void handleBidirectionalStream(HttpStream httpStream) {
        if (state == State.OPEN) {
            sendingStreams.add(httpStream);
            receivingStreams.add(httpStream);
            bidirectionalStreamReceiveHandler.accept(wrap(httpStream));
        }
        else {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
            // "Upon learning that the session has been terminated, the endpoint MUST reset the send side and abort
            //  reading on the receive side of all of the streams associated with the session"
            httpStream.abortReading(WEBTRANSPORT_SESSION_GONE);
            httpStream.resetStream(WEBTRANSPORT_SESSION_GONE);
        }
    }

    void closedByPeer(long applicationErrorCode, String applicationErrorMessage) {
        if (! changeState(State.CLOSING, state -> true, state -> state == State.CLOSING || state == State.CLOSED)) {
            return;
        }

        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
        // "Upon learning that the session has been terminated, the endpoint MUST reset the send side and abort reading
        //  on the receive side of all of the streams associated with the session (see Section 2.4 of [RFC9000]) using
        //  the WEBTRANSPORT_SESSION_GONE error code; it MUST NOT send any new datagrams or open any new streams."
        stopSending();
        resetSenders();
        abortReading();
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-session-termination
        // "The recipient MUST close the stream upon receiving a FIN. "
        try {
            connectStream.close();
        }
        catch (IOException e) {}

        sessionFactory.removeSession(this);
        sessionTerminatedEventListener.accept(applicationErrorCode, applicationErrorMessage);
    }

    private void stopSending() {
        changeState(State.CLOSED);
    }

    private void resetSenders() {
        sendingStreams.forEach(stream -> stream.resetStream(WEBTRANSPORT_SESSION_GONE));
    }

    private void abortReading() {
        receivingStreams.forEach(stream -> stream.abortReading(WEBTRANSPORT_SESSION_GONE));
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

    private void changeState(State newState) {
        changeState(newState, state -> true, state -> false);
    }

    private void changeState(State newState, Predicate<State> precondition) {
        changeState(newState, precondition, state -> false);
    }

    private boolean changeState(State newState, Predicate<State> precondition, Predicate<State> ignoreCondition) {
        stateChangeLock.lock();
        try {
            if (ignoreCondition.test(state)) {
                return false;
            }
            if (!precondition.test(state)) {
                throw new IllegalStateException("Invalid state transition from " + state + " to " + newState);
            }
            state = newState;
            return true;
        }
        finally {
            stateChangeLock.unlock();
        }
    }

    @Override
    public long getSessionId() {
        return sessionId;
    }

    @Override
    public String getPath() {
        return context.getPathAndQuery();
    }

    @Override
    public boolean isOpen() {
        return state == State.OPEN;
    }
}
