/*
 * Copyright © 2024, 2025 Peter Doornbosch
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

import tech.kwik.core.generic.VariableLengthInteger;
import tech.kwik.flupke.HttpStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import static tech.kwik.flupke.webtransport.Constants.WEBTRANSPORT_BUFFERED_STREAM_REJECTED;
import static tech.kwik.flupke.webtransport.Constants.WEBTRANSPORT_SESSION_GONE;

public abstract class AbstractSessionFactoryImpl implements SessionFactory {

    // WebTransport setting IDs — multiple variants for cross-draft compatibility.

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-13.html
    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-14.html
    public static final long SETTINGS_WT_MAX_SESSIONS_DRAFT_13_14 = 0x14e9cd29L;
    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-07.html
    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-12.html
    public static final long SETTINGS_WT_MAX_SESSIONS_DRAFT_07_12 = 0xc671706aL;
    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-06.html
    public static final long SETTINGS_WT_ENABLE_DRAFT_06 = 0x2b603742L;
    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-04.html
    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-05.html
    public static final long SETTINGS_WT_MAX_SESSIONS_DRAFT_04_05 = 0x2b603743L;

    private enum SessionState {
        NOT_CREATED, OPEN, CLOSED
    }

    protected final Map<Long, SessionImpl> sessionRegistry = new ConcurrentHashMap<>();
    private final ReentrantLock registrationLock = new ReentrantLock();
    private final Map<Long, List<HttpStream>> streamQueue = new ConcurrentHashMap<>();
    private volatile int streamsQueued;
    private final int maxStreamsQueued;
    private volatile long latestSessionId = -1;
    protected final ExecutorService executor;
    private final Map<Long, Queue<byte[]>> earlyDatagrams = new ConcurrentHashMap<>();

    public AbstractSessionFactoryImpl(ExecutorService executor, int maxStreamsQueued) {
        this.executor = executor;
        this.maxStreamsQueued = maxStreamsQueued;
    }

    protected void handleUnidirectionalStream(HttpStream httpStream) {
        try {
            InputStream inputStream = httpStream.getInputStream();
            long sessionId = VariableLengthInteger.parseLong(inputStream);

            attachStreamToSessionOrQueue(sessionId, httpStream);
        }
        catch (IOException e) {
            // Reading session id failed. Can only happen when QUIC connection is prematurely closed (and then it's all over).
        }
        catch (BufferedStreamsLimitExceededException e) {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-buffering-incoming-streams-
            // "When the number of buffered streams is exceeded, a stream SHALL be closed by sending a RESET_STREAM
            //  and/or STOP_SENDING with the WEBTRANSPORT_BUFFERED_STREAM_REJECTED error code."
            httpStream.abortReading(WEBTRANSPORT_BUFFERED_STREAM_REJECTED);
        }
    }

    protected void handleBidirectionalStream(HttpStream httpStream) {
        try {
            InputStream inputStream = httpStream.getInputStream();
            long signalValue = VariableLengthInteger.parseLong(inputStream);
            if (signalValue == 0x41) {
                long sessionId = VariableLengthInteger.parseLong(inputStream);
                attachStreamToSessionOrQueue(sessionId, httpStream);
            }
        }
        catch (IOException e) {
            // Reading session id failed. Can only happen when QUIC connection is prematurely closed (and then it's all over).
        }
        catch (BufferedStreamsLimitExceededException e) {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-buffering-incoming-streams-
            // "When the number of buffered streams is exceeded, a stream SHALL be closed by sending a RESET_STREAM
            //  and/or STOP_SENDING with the WEBTRANSPORT_BUFFERED_STREAM_REJECTED error code."
            httpStream.abortReading(WEBTRANSPORT_BUFFERED_STREAM_REJECTED);
            httpStream.resetStream(WEBTRANSPORT_BUFFERED_STREAM_REJECTED);
        }
    }

    private void attachStreamToSessionOrQueue(long sessionId, HttpStream httpStream) throws BufferedStreamsLimitExceededException {
        SessionImpl session;
        SessionState sessionState;
        registrationLock.lock();
        try {
            session = sessionRegistry.get(sessionId);
            if (session != null && session.isOpen()) {
                sessionState = SessionState.OPEN;
            }
            else {
                if (session == null && sessionId <= latestSessionId) {
                    sessionState = SessionState.CLOSED;
                }
                else {
                    sessionState = SessionState.NOT_CREATED;
                }
            }
        }
        finally {
            registrationLock.unlock();
        }
        if (sessionState == SessionState.OPEN) {
            session.handleStream(httpStream);
        }
        else if (sessionState == SessionState.CLOSED) {
            // Session already closed, ignore the stream
            httpStream.abortReading(WEBTRANSPORT_SESSION_GONE);
            if (httpStream.isBidirectional()) {
                httpStream.resetStream(WEBTRANSPORT_SESSION_GONE);
            }
        }
        else if (sessionState == SessionState.NOT_CREATED) {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-buffering-incoming-streams-
            // "Similarly, a client may receive a server-initiated stream or a datagram before receiving the CONNECT
            //  response headers from the server. To handle this case, WebTransport endpoints SHOULD buffer streams
            //  and datagrams until those can be associated with an established session. To avoid resource exhaustion,
            //  the endpoints MUST limit the number of buffered streams and datagrams."
            if (streamsQueued >= maxStreamsQueued) {
                throw new BufferedStreamsLimitExceededException();
            }
            // Session not yet created, queue the stream
            streamQueue.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(httpStream);
            streamsQueued++;
        }
    }

    protected void registerSession(SessionImpl session) {
        registrationLock.lock();
        try {
            latestSessionId = session.getSessionId();
            sessionRegistry.put(session.getSessionId(), session);
        }
        finally {
            registrationLock.unlock();
        }
    }

    protected boolean handleEarlyDatagram(long streamId, byte[] data) {
        // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.html#name-buffering-incoming-streams-
        // "To handle this case, WebTransport endpoints SHOULD buffer streams and datagrams until they can be associated
        //  with an established session. To avoid resource exhaustion, endpoints MUST limit the number of buffered streams
        //  and datagrams."
        // "When the number of buffered datagrams is exceeded, a datagram SHALL be dropped. It is up to an implementation
        //  to choose what stream or datagram to discard."
        // TODO: implement a limit on buffered datagrams and drop datagrams when the limit is exceeded.
        earlyDatagrams.computeIfAbsent(streamId, k -> new ConcurrentLinkedQueue<>()).add(data);
        return true;
    }

    @Override
    public void startSession(SessionImpl session) {
        List<HttpStream> bufferedStreams;
        registrationLock.lock();
        try {
            // Check queue for streams that are waiting for this session
            bufferedStreams = streamQueue.remove(session.getSessionId());
            if (bufferedStreams != null) {
                streamsQueued -= bufferedStreams.size();
            }
        }
        finally {
            registrationLock.unlock();
        }
        if (bufferedStreams != null) {
            bufferedStreams.forEach(stream -> executor.submit(() -> session.handleStream(stream)));
        }
        // Flush any datagrams that arrived before the session-specific handler was registered in the SessionImpl constructor.
        Queue<byte[]> bufferedDatagrams = earlyDatagrams.remove(session.getSessionId());
        if (bufferedDatagrams != null) {
            bufferedDatagrams.forEach(datagram -> executor.submit(() -> session.handleDatagram(datagram)));
        }
    }

    @Override
    public void removeSession(SessionImpl session) {
        registrationLock.lock();
        try {
            sessionRegistry.remove(session.getSessionId());
            streamQueue.remove(session.getSessionId());
        }
        finally {
            registrationLock.unlock();
        }
    }
}
