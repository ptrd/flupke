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
package tech.kwik.flupke.server;

import java.util.Map;

import static java.util.Collections.emptyMap;

public interface Http3ServerExtensionFactory {

    /**
     * Creates an extension that is bound to the given HTTP/3 connection.
     * @param http3ServerConnection
     * @return  the extension
     */
    Http3ServerExtension createExtension(Http3ServerConnection http3ServerConnection);

    /**
     * Return the extension specific HTTP3 settings for this extension.
     * @return
     */
    default Map<Long, Long> getExtensionSettings() {
        return emptyMap();
    }
}
