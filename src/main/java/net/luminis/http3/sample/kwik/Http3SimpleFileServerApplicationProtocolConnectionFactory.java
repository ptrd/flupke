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
package net.luminis.http3.sample.kwik;

import net.luminis.http3.sample.FileServer;
import net.luminis.http3.server.Http3ApplicationProtocolFactory;

import java.io.File;
import java.util.Objects;

/**
 * A simple HTTP3 file serving ApplicationProtocolConnectionFactory that serves files from a given 'www' directory.
 * Is used as a plugin by the Kwik sample server application.
 */
public class Http3SimpleFileServerApplicationProtocolConnectionFactory extends Http3ApplicationProtocolFactory {

    public Http3SimpleFileServerApplicationProtocolConnectionFactory(File wwwDir) {
        super(new FileServer(Objects.requireNonNull(wwwDir)));
    }
}
