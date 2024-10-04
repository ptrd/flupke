/*
 * Copyright © 2021, 2022, 2023, 2024 Peter Doornbosch
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
package net.luminis.http3.server;

import java.io.OutputStream;

public abstract class HttpServerResponse {

    private int status = -1;

    public abstract OutputStream getOutputStream();

    /**
     * https://www.rfc-editor.org/rfc/rfc9110.html#name-status-codes
     * "All valid status codes are within the range of 100 to 599, inclusive."
     * "Values outside the range 100..599 are invalid. Implementations often use three-digit integer values outside of
     *  that range (i.e., 600..999) for internal communication of non-HTTP status (e.g., library errors). "
     * @param status
     */
    public void setStatus(int status) {
        if (status < 100 || status > 1000) {
            throw new IllegalArgumentException("invalid status code: " + status);
        }
        this.status = status;
    }

    public int status() {
        if (status == -1) {
            throw new IllegalStateException("status not set");
        }
        return status;
    }

    public boolean isStatusSet() {
        return status != -1;
    }

    public long size() {
        return 0;
    }
}
