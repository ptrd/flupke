/*
 * Copyright © 2024 Peter Doornbosch
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
package net.luminis.http3.webtransport;

public class Constants {

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-stream-type-registration
    // "Code: 0x54
    //  Stream Type: WebTransport stream"
    public static final int STREAM_TYPE_WEBTRANSPORT = 0x54;

    // https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html#name-frame-type-registration
    // "Code: 0x41
    //  Frame Type: WEBTRANSPORT_STREAM"
    public static final int FRAME_TYPE_WEBTRANSPORT_STREAM = 0x41;
}
