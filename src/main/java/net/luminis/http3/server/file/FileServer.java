/*
 * Copyright © 2021 Peter Doornbosch
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
package net.luminis.http3.server.file;

import net.luminis.http3.server.HttpRequestHandler;
import net.luminis.http3.server.HttpServerRequest;
import net.luminis.http3.server.HttpServerResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


/**
 * A simple file serving http request handler.
 */
public class FileServer implements HttpRequestHandler {

    private final DateTimeFormatter timeFormatter;
    private final File wwwDir;

    public FileServer(File wwwDir) {
        this.wwwDir = wwwDir;
        timeFormatter = DateTimeFormatter.ofPattern("d/MMM/yyyy:HH:mm:ss Z").withZone(ZoneId.systemDefault());
    }

    @Override
    public void handleRequest(HttpServerRequest request, HttpServerResponse response) throws IOException {
        if (request.method().equals("GET")) {
            String path = request.path();
            if (path.isBlank() || path.equals("/")) {
                path = "index.html";
            }
            File fileInWwwDir = getFileInWwwDir(path);
            if (fileInWwwDir != null && fileInWwwDir.exists() && fileInWwwDir.isFile() && fileInWwwDir.canRead()) {
                response.setStatus(200);
                response.getOutputStream().write(Files.readAllBytes(fileInWwwDir.toPath()));
                response.getOutputStream().close();
            }
            else {
                response.setStatus(404);
            }
        }
        else {
            response.setStatus(405);
        }
        log(request, response);
    }

    private void log(HttpServerRequest request, HttpServerResponse response) {
        // Using standard Apache Access Log format
        String logLine = request.clientAddress() + " " +
                // client identity
                "- " +
                // client userid
                "- " +
                // time that the request was received
                "[" + timeFormatter.format(request.time()) + "] " +
                // request line
                request.method() + " " + request.path() + " " + "HTTP/3 " +
                // status code
                response.status() + " " +
                // size of the response
                response.size();

        System.out.println(logLine);
    }

    /**
     * Check that file specified by argument is actually in the www dir (to prevent file traversal).
     * @param fileName
     * @return
     * @throws IOException
     */
    private File getFileInWwwDir(String fileName) throws IOException {
        String requestedFilePath = new File(wwwDir, fileName).getCanonicalPath();
        if (requestedFilePath.startsWith(wwwDir.getCanonicalPath())) {
            return new File(requestedFilePath);
        }
        else {
            return null;
        }
    }
}
