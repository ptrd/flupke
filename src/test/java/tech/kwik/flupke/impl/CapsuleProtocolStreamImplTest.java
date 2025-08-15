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


import org.junit.jupiter.api.Test;
import tech.kwik.flupke.core.CapsuleProtocolStream;
import tech.kwik.flupke.core.GenericCapsule;
import tech.kwik.flupke.core.HttpStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CapsuleProtocolStreamImplTest {

    @Test
    public void testCapsuleParsingOnConnectStream() throws Exception {
        // Given
        ByteArrayInputStream quicInputStream = new ByteArrayInputStream(new byte[] {
                0x40, 0x68, 0x03, 0x75, 0x49, (byte) 0xde  // 0x4068: capsule type (2-byte var int), 0x03: capsule length,  0x75, 0x49, 0xde: capsule data
        });
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getInputStream()).thenReturn(quicInputStream);
        CapsuleProtocolStream capsuleProtocolStream = new CapsuleProtocolStreamImpl(httpStream);

        // When
        capsuleProtocolStream.registerCapsuleParser(0x68, input -> {
            try {
                byte[] data = input.readNBytes(6);
                return new TestCapsule(Arrays.copyOfRange(data, 3, 6));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        GenericCapsule received = (GenericCapsule) capsuleProtocolStream.receive();

        // Then
        assertThat(received).isInstanceOf(TestCapsule.class);
        assertThat(received.getType()).isEqualTo(0x68);
        assertThat(received.getData()).isEqualTo(new byte[] { 0x75, 0x49, (byte) 0xde });
    }

    @Test
    public void whenCapsuleParserThrowsIORuntimeExceptionThisIsConvertedToIoException() throws Exception {
        // Given
        ByteArrayInputStream quicInputStream = new ByteArrayInputStream(new byte[] { 0x2f });
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getInputStream()).thenReturn(quicInputStream);
        CapsuleProtocolStream capsuleProtocolStream = new CapsuleProtocolStreamImpl(httpStream);

        // When
        capsuleProtocolStream.registerCapsuleParser(0x2f, input -> {
            throw new UncheckedIOException(new IOException("missing data"));
        });

        // Then
        assertThatThrownBy(() -> capsuleProtocolStream.receive())
                .isInstanceOf(IOException.class)
                .hasMessage("missing data");
    }

    @Test
    public void closeOnCapsuleStreamClosesOutputStreamOfUnderlyingQuicStream() throws Exception {
        // Given
        OutputStream quicOutputStream = mock(OutputStream.class);
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getOutputStream()).thenReturn(quicOutputStream);
        CapsuleProtocolStream capsuleProtocolStream = new CapsuleProtocolStreamImpl(httpStream);

        // When
        capsuleProtocolStream.close();

        // Then
        verify(quicOutputStream).close();
    }

    @Test
    public void sendAndcloseOnCapsuleStreamClosesOutputStreamOfUnderlyingQuicStream() throws Exception {
        // Given
        OutputStream quicOutputStream = mock(OutputStream.class);
        HttpStream httpStream = mock(HttpStream.class);
        when(httpStream.getOutputStream()).thenReturn(quicOutputStream);
        CapsuleProtocolStream capsuleProtocolStream = new CapsuleProtocolStreamImpl(httpStream);

        // When
        capsuleProtocolStream.sendAndClose(new GenericCapsule(0x68, new byte[] { 0x01, 0x02, 0x03 }));

        // Then
        verify(quicOutputStream, atLeast(1)).write(any(), anyInt(), anyInt());
        verify(quicOutputStream).close();
    }

    static class TestCapsule extends GenericCapsule {
        public TestCapsule(byte[] data) {
            super(0x68, data);
        }
    }


}