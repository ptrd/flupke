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

import net.luminis.http3.impl.CapsuleProtocolStreamImpl;
import net.luminis.http3.server.Http3ServerConnection;
import net.luminis.http3.server.Http3ServerExtension;
import net.luminis.http3.webtransport.Session;
import tech.kwik.core.QuicStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class WebTransportExtension implements Http3ServerExtension {

    private final ServerSessionFactoryImpl sessionFactory;
    private final Map<String, Consumer<Session>> handlers;
    private final ExecutorService executor;

    public WebTransportExtension(Http3ServerConnection http3ServerConnection, Map<String, Consumer<Session>> webTransportHandlers,
                                 ExecutorService executorService) {
        sessionFactory = new ServerSessionFactoryImpl(http3ServerConnection);
        this.handlers = webTransportHandlers;
        this.executor = executorService;
    }

    @Override
    public int handleExtendedConnect(HttpHeaders headers, String protocol, String authority, String pathAndQuery, QuicStream requestResponseSteam) {
        Optional<Consumer<Session>> handler = findHandler(pathAndQuery);
        if (handler.isPresent()) {
            Session session = sessionFactory.createServerSession(new CapsuleProtocolStreamImpl(requestResponseSteam));
            async(() -> handler.get().accept(session));
            return 200;
        }
        else {
            return 404;
        }
    }

    private void async(Runnable runnable) {
        executor.submit(runnable);
    }

    private Optional<Consumer<Session>> findHandler(String pathAndQuery) {
        try {
            String path = new URI(pathAndQuery).getPath();
            return handlers.keySet().stream()
                    .filter(path::equals)
                    .findFirst()
                    .map(handlers::get);
        }
        catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
