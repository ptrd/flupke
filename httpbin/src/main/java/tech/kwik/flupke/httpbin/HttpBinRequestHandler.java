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
package tech.kwik.flupke.httpbin;

import org.json.JSONObject;
import org.json.JSONTokener;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.http.HttpHeaders;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;


public class HttpBinRequestHandler implements HttpRequestHandler {

    private final Map<RequestKey, BiConsumer<HttpServerRequest, HttpServerResponse>> handlers = new LinkedHashMap<>();

    public HttpBinRequestHandler() {
        handlers.put(new RequestKey("GET", "/"), this::indexRequest);
        handlers.put(new RequestKey("GET", "/headers"), this::getHeadersRequest);
        handlers.put(new RequestKey("POST", "/headers"), this::postHeadersRequest);
        handlers.put(new RequestKey("POST", "/md5"), this::postForMd5);
    }

    @Override
    public void handleRequest(HttpServerRequest request, HttpServerResponse response) {
        BiConsumer<HttpServerRequest, HttpServerResponse> handler =
                handlers.get(new RequestKey(request.method(), request.path()));
        if (handler != null) {
            handler.accept(request, response);
        }
        else {
            response.setStatus(404);
        }
    }

    private void indexRequest(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse) {
        httpServerResponse.setStatus(200);
        try {
            String title = "httpbin powered by Flupke";
            String message = "Welcome to httpbin powered by Flupke!";
            OutputStreamWriter writer = new OutputStreamWriter(httpServerResponse.getOutputStream());
            writer.write("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>" + title + "</title>" +
                    "<style>body { font-family: Arial, sans-serif; background: #f7f7f7; margin: 40px; } h1 { color: #333; }" +
                    "table { border-collapse: collapse; width: 400px; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }" +
                    "th, td { padding: 12px 18px; border-bottom: 1px solid #eee; text-align: left; } " +
                    "th { background: #f0f0f0; color: #555; } tr:last-child td { border-bottom: none; }" +
                    "</style></head><body><h1>" + message + "</h1><table><tr><th>Method</th><th>Path</th></tr>\n");
            handlers.forEach((key, value) -> {
                try {
                    writer.write("<tr><td>" + key.method + "</td><td>" + key.path + "</td></tr>\n");
                }
                catch (IOException e) {
                    // Ignore
                }
            });
            writer.write("</table></body></html>");
            writer.flush();
        }
        catch (IOException e) {
            // Nothing we can do here, status is already set, can't be changed anymore
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

        JSONObject headersJson = new JSONObject(new JSONTokener(request.body()));
        if (headersJson.has("headers")) {
            Map<String, List<String>> headersMap = new HashMap<>();
            headersJson.getJSONObject("headers").toMap().forEach((key, value) -> {
                if (value instanceof List) {
                    headersMap.put(key, (List<String>) value);
                }
                else {
                    headersMap.put(key, List.of(value.toString()));
                }
            });
            response.setHeaders(HttpHeaders.of(headersMap, (s, s2) -> true));
        }
    }

    private void postForMd5(HttpServerRequest httpServerRequest, HttpServerResponse httpServerResponse) {
        httpServerResponse.setStatus(200);
        // TODO: httpServerResponse.setHeader("Content-Type", "application/json");

        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = httpServerRequest.body().read(buffer)) != -1) {
                md5.update(buffer, 0, bytesRead);
            }
            byte[] md5Hash = md5.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : md5Hash) {
                sb.append(String.format("%02x", b));
            }
            String md5HashString = sb.toString();
            JSONObject jsonOutput = new JSONObject().put("md5", md5HashString);

            OutputStreamWriter writer = new OutputStreamWriter(httpServerResponse.getOutputStream());
            jsonOutput.write(writer, 2, 0);
            writer.flush();
        }
        catch (Exception e) {
            httpServerResponse.setStatus(500);
        }
    }

    static private class RequestKey {
        String method;
        String path;

        RequestKey(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RequestKey that = (RequestKey) o;
            return method.equals(that.method) && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return 31 * method.hashCode() + path.hashCode();
        }
    }
}
