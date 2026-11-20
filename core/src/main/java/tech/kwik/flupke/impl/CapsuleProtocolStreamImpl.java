/*
 * Copyright © 2024, 2025 Peter Doornbosch
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

import tech.kwik.core.generic.VariableLengthInteger;
import tech.kwik.flupke.HttpStream;
import tech.kwik.flupke.core.Capsule;
import tech.kwik.flupke.core.CapsuleProtocolStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public class CapsuleProtocolStreamImpl implements CapsuleProtocolStream {

    private final OutputStream outputStream;
    private final Map<Long, Function<InputStream, Capsule>> capsuleParsers;
    private final PushbackInputStream inputStream;
    private final long streamId;

    public CapsuleProtocolStreamImpl(HttpStream httpStream) {
        this.streamId = httpStream.getStreamId();
        capsuleParsers = new ConcurrentHashMap<>();
        inputStream = new PushbackInputStream(httpStream.getInputStream(), 8);
        outputStream = httpStream.getOutputStream();
    }

    @Override
    public Capsule receive() throws IOException {
        long type = VariableLengthIntegerUtil.peekLong(inputStream);
        if (capsuleParsers.containsKey(type)) {
            try {
                return capsuleParsers.get(type).apply(inputStream);
            }
            catch (UncheckedIOException ioException) {
                throw ioException.getCause();
            }
        }
        else {
            return parseGenericCapsule();
        }
    }

    @Override
    public void send(Capsule capsule) throws IOException {
        capsule.write(outputStream);
        outputStream.flush();
    }

    @Override
    public void sendAndClose(Capsule capsule) throws IOException {
        capsule.write(outputStream);
        outputStream.close();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    @Override
    public long getStreamId() {
        return streamId;
    }

    @Override
    public void registerCapsuleParser(long type, Function<InputStream, Capsule> parser) {
        capsuleParsers.put(type, parser);
    }

    private Capsule parseGenericCapsule() throws IOException {
        long type = VariableLengthInteger.parseLong(inputStream);
        long length = VariableLengthInteger.parseLong(inputStream);
        inputStream.skip(length);
        return new UnknownCapsule(type);
    }
}
