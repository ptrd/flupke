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

import net.luminis.http3.core.CapsuleProtocolStream;
import net.luminis.http3.server.Http3ServerConnection;
import net.luminis.http3.webtransport.Session;

import static net.luminis.http3.webtransport.Constants.FRAME_TYPE_WEBTRANSPORT_STREAM;
import static net.luminis.http3.webtransport.Constants.STREAM_TYPE_WEBTRANSPORT;

public class ServerSessionFactoryImpl extends AbstractSessionFactoryImpl {

    private final Http3ServerConnection http3ServerConnection;

    public ServerSessionFactoryImpl(Http3ServerConnection http3ServerConnection) {
        this.http3ServerConnection = http3ServerConnection;
    }

    public Session createServerSession(CapsuleProtocolStream connectStream) {
        http3ServerConnection.registerBidirectionalStreamHandler(FRAME_TYPE_WEBTRANSPORT_STREAM, this::handleBidirectionalStream);
        http3ServerConnection.registerUnidirectionalStreamType(STREAM_TYPE_WEBTRANSPORT, this::handleUnidirectionalStream);
        SessionImpl session = new SessionImpl(http3ServerConnection, connectStream, s -> {}, s -> {}, this);
        registerSession(session);
        return session;
    }
}
