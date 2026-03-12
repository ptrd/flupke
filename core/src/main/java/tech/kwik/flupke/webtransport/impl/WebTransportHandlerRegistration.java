/*
 * Copyright © 2026 Peter Doornbosch
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

import tech.kwik.flupke.webtransport.Session;

import java.util.List;
import java.util.function.Consumer;

class WebTransportHandlerRegistration {

    private final Consumer<Session> handler;
    private final List<String> applicationProtocols;

    WebTransportHandlerRegistration(Consumer<Session> handler) {
        this(handler, List.of());
    }

    WebTransportHandlerRegistration(Consumer<Session> handler, List<String> applicationProtocols) {
        this.handler = handler;
        this.applicationProtocols = applicationProtocols;
    }

    Consumer<Session> handler() {
        return handler;
    }

    List<String> applicationProtocols() {
        return applicationProtocols;
    }
}
