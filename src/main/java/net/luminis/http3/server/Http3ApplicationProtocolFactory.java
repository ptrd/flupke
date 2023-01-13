/*
 * Copyright © 2021, 2022, 2023 Peter Doornbosch
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
package net.luminis.http3.server;


import net.luminis.http3.server.file.FileServer;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.server.ApplicationProtocolConnectionFactory;


import java.io.File;

public class Http3ApplicationProtocolFactory implements ApplicationProtocolConnectionFactory {

    private File wwwDir;
    private final FileServer fileServer;

    public Http3ApplicationProtocolFactory(File wwwDir) {
        if (wwwDir == null) {
            throw new IllegalArgumentException();
        }
        this.wwwDir = wwwDir;
        fileServer = new FileServer(wwwDir);
    }

    @Override
    public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
        return new Http3ServerConnection(quicConnection, fileServer);
    }
}
