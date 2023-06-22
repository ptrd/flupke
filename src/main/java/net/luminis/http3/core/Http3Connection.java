/*
 * Copyright © 2023 Peter Doornbosch
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
package net.luminis.http3.core;

import java.util.function.Consumer;

public interface Http3Connection {

    /**
     * HTTP/3 extension method: allow registration of a new unidirectional stream type.
     * https://www.rfc-editor.org/rfc/rfc9114.html#name-extensions-to-http-3
     * "Extensions are permitted to use (...) new unidirectional stream types (Section 6.2)."
     * @param streamType
     * @param handler
     */
    void registerUnidirectionalStreamType(long streamType, Consumer<HttpStream> handler);
}
