/*
 * Copyright © 2023, 2024 Peter Doornbosch
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
package net.luminis.http3.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * Stream for sending and receiving Capsules.
 * See https://www.rfc-editor.org/rfc/rfc9297.html#name-capsules
 */
public interface CapsuleProtocolStream {

    Capsule receive() throws IOException;

    void send(Capsule capsule) throws IOException;

    void sendAndClose(Capsule capsule) throws IOException;

    void close() throws IOException;

    /**
     * Returns the stream id of the underlying (QUIC) stream.
     * @return
     */
    long getStreamId();

    /**
     * Register a parser for a specific capsule type.
     * When the parser encounters an IOException, it should rethrow is as an UncheckedIOException.
     * @param type
     * @param parser
     */
    void registerCapsuleParser(long type, Function<InputStream, Capsule> parser);
}
