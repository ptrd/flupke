/*
 * Copyright © 2025 Peter Doornbosch
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
package tech.kwik.flupke.sample;

import org.json.JSONObject;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

public class HttpBinRequestHandler implements HttpRequestHandler {

    @Override
    public void handleRequest(HttpServerRequest request, HttpServerResponse response) {
        if (request.method().equals("GET") && request.path().equals("/headers")) {
            getHeadersRequest(request, response);
        } else if (request.method().equals("POST") && request.path().equals("/headers")) {
            postHeadersRequest(request, response);
        }
    }

    private void getHeadersRequest(HttpServerRequest request, HttpServerResponse response) {
        response.setStatus(200);

        JSONObject jsonOuput = new JSONObject()
                .put("headers", new JSONObject(request.headers().map()));

        OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream());
        jsonOuput.write(writer, 2, 0);
        try {
            writer.flush();
        }
        catch (IOException e) {
            // Nothing we can do here, status is already set, can't be changed anymore
        }
    }

    private void postHeadersRequest(HttpServerRequest request, HttpServerResponse response) {
        response.setStatus(200);
        HttpHeaders headers = HttpHeaders.of(
            Map.of("Content-Type", List.of("application/json")),
            (s, s2) -> true
        );
        response.setHeaders(headers);
    }

}

