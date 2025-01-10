/*
 * Copyright © 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package net.luminis.http3.sample;

import net.luminis.http3.impl.FlupkeVersion;
import net.luminis.http3.server.HttpRequestHandler;
import net.luminis.http3.server.HttpServerRequest;
import net.luminis.http3.server.HttpServerResponse;
import tech.kwik.core.KwikVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A simple file serving http request handler. It serves files from a given 'www' directory.
 * And when a given request path is a number followed by 'K' or 'M', and is not found in the www directory,
 * it will respond with a file of the specified size.
 * Each request is logged in the standard Apache Access Log format to standard out.
 */
public class FileServer implements HttpRequestHandler {

    private static final int MAX_DOWNLOAD_SIZE = 100 * 1024 * 1024;
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
            if (path.equals("/version")) {
                response.setStatus(200);
                String versionLine = "Kwik version: " + KwikVersion.getVersion() + "\n"
                        + "Flupke version: " + FlupkeVersion.getVersion() + "\n";
                response.getOutputStream().write(versionLine.getBytes());
                response.getOutputStream().close();
                log(request, response);
                return;
            }

            if (path.isBlank() || path.equals("/")) {
                path = "index.html";
            }
            File fileInWwwDir = getFileInWwwDir(path);
            if (fileInWwwDir != null && fileInWwwDir.exists() && fileInWwwDir.isFile() && fileInWwwDir.canRead()) {
                response.setStatus(200);
                try (FileInputStream fileIn = new FileInputStream(fileInWwwDir); OutputStream out = response.getOutputStream()) {
                    fileIn.transferTo(out);
                }
            }
            else {
                Matcher sizeNameMacher = Pattern.compile("/{0,1}(\\d+)([kmKM])").matcher(path);
                if (sizeNameMacher.matches()) {
                    int size = Integer.parseInt(sizeNameMacher.group(1));
                    String unit = sizeNameMacher.group(2).toLowerCase();
                    long sizeInBytes = size * (unit.equals("k")? 1024: unit.equals("m")? 1024 * 1024: 1);
                    if (sizeInBytes > MAX_DOWNLOAD_SIZE) {
                        response.setStatus(509); // Bandwidth Limit Exceeded
                        return;
                    }
                    transferFileOfSize(sizeInBytes, response.getOutputStream());
                }
                else {
                    response.setStatus(404);
                }
            }
        }
        else {
            response.setStatus(405);
        }
        log(request, response);
    }

    private void transferFileOfSize(long size, OutputStream outputStream) throws IOException {
        long remaining = size;
        int blockSize = 1000;
        byte[] dummmyData = new byte[blockSize];
        try {
            while (remaining >= blockSize) {
                outputStream.write(dummmyData);
                remaining -= blockSize;
            }
            outputStream.write(new byte[(int) remaining]);
        }
        finally {
            outputStream.close();
        }
    }

    private void log(HttpServerRequest request, HttpServerResponse response) {
        // Using standard Apache Access Log format
        String logLine = request.clientAddress().getHostAddress() + " " +
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
