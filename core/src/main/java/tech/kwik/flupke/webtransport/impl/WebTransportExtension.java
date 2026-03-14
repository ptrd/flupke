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

import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.impl.CapsuleProtocolStreamImpl;
import tech.kwik.flupke.server.Http3ServerConnection;
import tech.kwik.flupke.server.Http3ServerExtension;
import tech.kwik.flupke.webtransport.Session;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class WebTransportExtension implements Http3ServerExtension {

    private final ServerSessionFactoryImpl sessionFactory;
    private final Map<String, WebTransportHandlerRegistration> handlers;
    private final ExecutorService executor;

    public WebTransportExtension(Http3ServerConnection http3ServerConnection, Map<String, WebTransportHandlerRegistration> webTransportHandlers,
                                 ExecutorService executorService) {
        sessionFactory = new ServerSessionFactoryImpl(http3ServerConnection, executorService);
        this.handlers = webTransportHandlers;
        this.executor = executorService;
    }

    @Override
    public void handleExtendedConnect(HttpHeaders headers, String protocol, String authority, String pathAndQuery, BiConsumer<Integer, Map<String, List<String>>> statusCallback, HttpStream requestResponseSteam) {
        Optional<WebTransportHandlerRegistration> registration = findHandler(pathAndQuery);
        if (registration.isPresent()) {
            // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-15.html#section-3.3
            // "The client MAY include a WT-Available-Protocols header field in the CONNECT request. (...)
            //  If the server receives such a header, it MAY include a WT-Protocol field in a successful (2xx) response.
            //  If it does, the server MUST include a single choice from the client's list in that field. Servers MAY
            //  reject the request if the client did not include a suitable protocol."
            List<String> applicationProtocols = registration.get().applicationProtocols();
            Map<String, List<String>> responseHeaders = Map.of();
            String negotiatedProtocol = null;
            if (!applicationProtocols.isEmpty()) {
                Optional<String> match = headers.allValues("WT-Available-Protocols").stream()
                        .flatMap(v -> Stream.of(v.split(",")))
                        .map(String::trim)
                        .map(p -> p.startsWith("\"") && p.endsWith("\"") ? p.substring(1, p.length() - 1) : p)
                        .filter(applicationProtocols::contains)
                        .findFirst();
                if (match.isEmpty()) {
                    statusCallback.accept(404, Map.of());
                    return;
                }
                negotiatedProtocol = match.get();
                responseHeaders = Map.of("WT-Protocol", List.of("\"" + negotiatedProtocol + "\""));
            }
            sessionFactory.prepareServerSession();
            statusCallback.accept(200, responseHeaders);
            WebTransportContext context = new WebTransportContext(headers, authority, pathAndQuery, negotiatedProtocol);
            Session session = sessionFactory.createServerSession(context, new CapsuleProtocolStreamImpl(requestResponseSteam));
            async(() -> registration.get().handler().accept(session));
        }
        else {
            statusCallback.accept(404, Map.of());
        }
    }

    private void async(Runnable runnable) {
        executor.submit(runnable);
    }

    private Optional<WebTransportHandlerRegistration> findHandler(String pathAndQuery) {
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
