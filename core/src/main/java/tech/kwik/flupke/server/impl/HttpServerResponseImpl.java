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
package tech.kwik.flupke.server.impl;

import tech.kwik.core.QuicStream;
import tech.kwik.flupke.impl.HeadersFrame;
import tech.kwik.flupke.server.HttpServerResponse;
import tech.kwik.qpack.Encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpHeaders;
import java.util.Map;

class HttpServerResponseImpl implements HttpServerResponse {

    private final Encoder qpackEncoder;
    private final OutputStream quicOutputStream;
    private int status = -1;
    private boolean outputStarted;
    private DataFrameWriter dataFrameWriter;
    private HttpHeaders httpHeaders;

    public HttpServerResponseImpl(QuicStream quicStream, Encoder qpackEncoder) {
        this.qpackEncoder = qpackEncoder;
        this.quicOutputStream = quicStream.getOutputStream();
        this.httpHeaders = HttpHeaders.of(Map.of(), (a, b) -> true);
    }

    /**
     * https://www.rfc-editor.org/rfc/rfc9110.html#name-status-codes
     * "All valid status codes are within the range of 100 to 599, inclusive."
     * "Values outside the range 100..599 are invalid. Implementations often use three-digit integer values outside of
     *  that range (i.e., 600..999) for internal communication of non-HTTP status (e.g., library errors). "
     * @param status
     */
    @Override
    public void setStatus(int status) {
        if (status < 100 || status > 999) {
            throw new IllegalArgumentException("invalid status code: " + status);
        }
        this.status = status;
    }

    @Override
    public void setHeaders(HttpHeaders headers) {
        if (outputStarted) {
            throw new IllegalStateException("Cannot set headers after getOutputStream has been called");
        }

        httpHeaders = headers;
    }

    @Override
    public OutputStream getOutputStream() {
        if (!outputStarted) {
            HeadersFrame headersFrame = new HeadersFrame(httpHeaders, Map.of(HeadersFrame.PSEUDO_HEADER_STATUS, Integer.toString(status())));
            OutputStream outputStream = quicOutputStream;
            try {
                outputStream.write(headersFrame.toBytes(qpackEncoder));
            }
            catch (IOException e) {
                // Ignore, there is nothing we can do. Note Kwik will not throw exception when writing to stream.
            }
            outputStarted = true;
            dataFrameWriter = new DataFrameWriter(quicOutputStream);
        }
        return dataFrameWriter;
    }

    @Override
    public int status() {
        if (status == -1) {
            throw new IllegalStateException("status not set");
        }
        return status;
    }

    @Override
    public boolean isStatusSet() {
        return status != -1;
    }

    @Override
    public long size() {
        if (dataFrameWriter != null) {
            return dataFrameWriter.getBytesWritten();
        }
        else {
            return 0;
        }
    }
}
