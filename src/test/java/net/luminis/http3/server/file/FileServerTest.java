/*
 * Copyright © 2021, 2022, 2023 Peter Doornbosch
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

import net.luminis.http3.server.HttpServerRequest;
import net.luminis.http3.server.HttpServerResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

public class FileServerTest {

    private FileServer fileServer;
    private File wwwDir;

    @Before
    public void initFileServer() throws IOException {
        wwwDir = Files.createTempDirectory("www").toFile();
        fileServer = new FileServer(wwwDir);
    }

    @Test
    public void handleSimpleFileRequest() throws IOException {
        // Given
        Files.write(new File(wwwDir, "file").toPath(), "Hello World!".getBytes());
        HttpServerResponse response = mock(HttpServerResponse.class);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(byteArrayOutputStream);

        // When
        fileServer.handleRequest(createGetRequest("file"), response);

        // Then
        assertThat(byteArrayOutputStream.toString()).isEqualTo("Hello World!");
    }

    @Test
    public void testFileNotFoundRequest() throws IOException {
        // Given
        HttpServerResponse response = mock(HttpServerResponse.class);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(byteArrayOutputStream);

        // When
        fileServer.handleRequest(createGetRequest("doesnotexist"), response);

        // Then
        assertThat(byteArrayOutputStream.size()).isEqualTo(0);
        verify(response).setStatus(intThat(arg -> arg == 404));
    }

    private HttpServerRequest createGetRequest(String filename) throws IOException {
        return new HttpServerRequest("GET", filename, null, Inet4Address.getByAddress(new byte[] { 10, 0, 0, 58 }));
    }
}