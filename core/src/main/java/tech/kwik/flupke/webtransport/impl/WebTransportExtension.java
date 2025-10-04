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
package tech.kwik.flupke.webtransport.impl;

import tech.kwik.flupke.core.HttpStream;
import tech.kwik.flupke.impl.CapsuleProtocolStreamImpl;
import tech.kwik.flupke.server.Http3ServerConnection;
import tech.kwik.flupke.server.Http3ServerExtension;
import tech.kwik.flupke.webtransport.Session;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

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
    public void handleExtendedConnect(HttpHeaders headers, String protocol, String authority, String pathAndQuery, IntConsumer statusCallback, HttpStream requestResponseSteam) {
        Optional<Consumer<Session>> handler = findHandler(pathAndQuery);
        if (handler.isPresent()) {
            statusCallback.accept(200);
            WebTransportContext context = new WebTransportContext(headers, authority, pathAndQuery);
            Session session = sessionFactory.createServerSession(context, new CapsuleProtocolStreamImpl(requestResponseSteam));
            async(() -> handler.get().accept(session));
        }
        else {
            statusCallback.accept(404);
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
