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
import java.util.regex.Pattern;

class WebTransportHandlerRegistration {

    private final Consumer<Session> handler;
    private final List<String> applicationProtocols;
    private final String staticPath;
    private final Pattern pathPattern;

    /**
     * Constructor for static-path registrations.
     * @param staticPath
     * @param handler
     * @param applicationProtocols
     */
    WebTransportHandlerRegistration(String staticPath, Consumer<Session> handler, List<String> applicationProtocols) {
        this.handler = handler;
        this.applicationProtocols = applicationProtocols;
        this.staticPath = staticPath;
        this.pathPattern = null;
    }

    /**
     * Constructor for regex-based registrations.
     * @param pathPattern
     * @param handler
     * @param applicationProtocols
     */
    WebTransportHandlerRegistration(Pattern pathPattern, Consumer<Session> handler, List<String> applicationProtocols) {
        this.handler = handler;
        this.applicationProtocols = applicationProtocols;
        this.staticPath = null;
        this.pathPattern = pathPattern;
    }

    Consumer<Session> handler() {
        return handler;
    }

    List<String> applicationProtocols() {
        return applicationProtocols;
    }

    String staticPath() {
        return staticPath;
    }

    /**
     * Returns the compiled path regex pattern, or {@code null} for static-path registrations.
     */
    Pattern pathPattern() {
        return pathPattern;
    }

    boolean isRegexBased() {
        return pathPattern != null;
    }
}
