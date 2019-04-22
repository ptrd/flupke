/*
 * Copyright © 2019 Peter Doornbosch
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

import net.luminis.quic.QuicConnection;
import net.luminis.quic.SysOutLogger;
import net.luminis.quic.Version;

import java.io.IOException;
import java.net.http.HttpRequest;


public class Http3Connection {

    private final QuicConnection quicConnection;
    private final String host;
    private final int port;

    public Http3Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        SysOutLogger logger = new SysOutLogger();
        logger.logInfo(true);
        logger.logPackets(true);
        logger.useRelativeTime(true);

        quicConnection = new QuicConnection(host, port, Version.IETF_draft_18, logger);
    }

    public void connect(int connectTimeoutInMillis) throws IOException {
        quicConnection.connect(connectTimeoutInMillis, "h3-19");
    }

    public void send(HttpRequest request) {
        System.out.println("Sending HTTP3 request to " + host + ":" + port);
    }

}
