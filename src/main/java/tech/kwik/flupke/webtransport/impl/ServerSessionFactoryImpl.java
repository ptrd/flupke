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
package tech.kwik.flupke.webtransport.impl;

import tech.kwik.flupke.core.CapsuleProtocolStream;
import tech.kwik.flupke.server.Http3ServerConnection;
import tech.kwik.flupke.webtransport.Session;

import static tech.kwik.flupke.webtransport.Constants.FRAME_TYPE_WEBTRANSPORT_STREAM;
import static tech.kwik.flupke.webtransport.Constants.STREAM_TYPE_WEBTRANSPORT;

public class ServerSessionFactoryImpl extends AbstractSessionFactoryImpl {

    private final Http3ServerConnection http3ServerConnection;

    public ServerSessionFactoryImpl(Http3ServerConnection http3ServerConnection) {
        this.http3ServerConnection = http3ServerConnection;
    }

    public Session createServerSession(WebTransportContext context, CapsuleProtocolStream connectStream) {
        http3ServerConnection.registerBidirectionalStreamHandler(FRAME_TYPE_WEBTRANSPORT_STREAM, this::handleBidirectionalStream);
        http3ServerConnection.registerUnidirectionalStreamType(STREAM_TYPE_WEBTRANSPORT, this::handleUnidirectionalStream);
        SessionImpl session = new SessionImpl(http3ServerConnection, context, connectStream, this);
        registerSession(session);
        return session;
    }
}
