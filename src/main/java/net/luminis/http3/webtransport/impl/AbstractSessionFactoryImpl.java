/*
 * Copyright © 2024 Peter Doornbosch
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

import net.luminis.http3.core.HttpStream;
import net.luminis.quic.generic.VariableLengthInteger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static net.luminis.http3.webtransport.Constants.WEBTRANSPORT_BUFFERED_STREAM_REJECTED;
import static net.luminis.http3.webtransport.Constants.WEBTRANSPORT_SESSION_GONE;

public abstract class AbstractSessionFactoryImpl implements SessionFactory {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-http-3-settings-parameter-r
    // "The SETTINGS_WEBTRANSPORT_MAX_SESSIONS parameter indicates that the specified HTTP/3 endpoint is
    //  WebTransport-capable and the number of concurrent sessions it is willing to receive."
    static final long SETTINGS_WEBTRANSPORT_MAX_SESSIONS = 0xc671706aL;

    protected final Map<Long, SessionImpl> sessionRegistry = new ConcurrentHashMap<>();
    private final ReentrantLock registrationLock = new ReentrantLock();
    private final Map<Long, List<HttpStream>> streamQueue = new ConcurrentHashMap<>();
    private volatile int streamsQueued;
    private final int maxStreamsQueued = 3;
    private volatile long latestSessionId = -1;

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
        registrationLock.lock();
        try {
            SessionImpl session = sessionRegistry.get(sessionId);
            if (session != null) {
                session.handleStream(httpStream);
            }
            else {
                if (sessionId <= latestSessionId) {
                    // Session already closed, ignore the stream
                    httpStream.abortReading(WEBTRANSPORT_SESSION_GONE);
                    if (httpStream.isBidirectional()) {
                        httpStream.resetStream(WEBTRANSPORT_SESSION_GONE);
                    }
                    return;
                }
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
        finally {
            registrationLock.unlock();
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

    @Override
    public void startSession(SessionImpl session) {
        registrationLock.lock();
        try {
            // Check queue for streams that are waiting for this session
            List<HttpStream> bufferedStreams = streamQueue.remove(session.getSessionId());
            if (bufferedStreams != null) {
                bufferedStreams.forEach(session::handleStream);
                streamsQueued -= bufferedStreams.size();
            }
        }
        finally {
            registrationLock.unlock();
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
