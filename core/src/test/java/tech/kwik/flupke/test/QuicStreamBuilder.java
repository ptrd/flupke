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
package tech.kwik.flupke.test;

import tech.kwik.core.QuicStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuicStreamBuilder {

    private byte[] inputData;
    private OutputStream outputStream;

    public QuicStreamBuilder withInputData(byte[] inputData) {
        this.inputData = inputData;
        return this;
    }
    public QuicStreamBuilder withOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
        return this;
    }

    public QuicStream build() {
        ByteArrayInputStream byteArrayInputStream = null;
        if (inputData == null) {
            byteArrayInputStream = new ByteArrayInputStream(new byte[0]);
        }
        else {
            byteArrayInputStream = new ByteArrayInputStream(inputData);
        }
        if (outputStream == null) {
            outputStream = new ByteArrayOutputStream();
        }

        QuicStream stream = mock(QuicStream.class);
        when(stream.getOutputStream()).thenReturn(outputStream);
        when(stream.getInputStream()).thenReturn(byteArrayInputStream);
        return stream;

    }
}
