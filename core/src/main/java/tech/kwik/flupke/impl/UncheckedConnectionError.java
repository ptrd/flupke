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
package tech.kwik.flupke.impl;

public class UncheckedConnectionError extends RuntimeException {

    private final long http3ErrorCode;
    private final ConnectionError checkedException;

    public UncheckedConnectionError(ConnectionError cause) {
        this.checkedException = cause;
        http3ErrorCode = cause.getHttp3ErrorCode();
    }

    public ConnectionError toChecked() {
        return checkedException;
    }
}
