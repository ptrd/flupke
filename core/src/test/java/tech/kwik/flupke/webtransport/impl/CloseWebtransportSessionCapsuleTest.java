/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
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
package tech.kwik.flupke.webtransport.impl;

import org.junit.jupiter.api.Test;
import tech.kwik.flupke.test.ByteUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloseWebtransportSessionCapsuleTest {

    @Test
    void capsuleCanBeReadFromInputStream() throws IOException {
        // Given
        // capsule type = 0x2843, encoded as variable length integer, 0x40 must be added to the first byte
        InputStream data = new ByteArrayInputStream(ByteUtils.hexToBytes("6843" + "07" + "87654321" + "627965"));

        // When
        CloseWebtransportSessionCapsule closeCapsule = new CloseWebtransportSessionCapsule(data);

        // Then
        assertThat(closeCapsule.getApplicationErrorCode()).isEqualTo(0x87654321);
        assertThat(closeCapsule.getApplicationErrorMessage()).isEqualTo("bye");
    }

    @Test
    void whenReadingCapsuleFailsWithEof() throws IOException {
        // Given
        // capsule type = 0x2843, encoded as variable length integer, 0x40 must be added to the first byte
        InputStream data = new ByteArrayInputStream(ByteUtils.hexToBytes("6843" + "07" + "87"));

        assertThatThrownBy(() ->
                // When
                new CloseWebtransportSessionCapsule(data)
                // Then
        ).isInstanceOf(IOException.class);
    }

    @Test
    void creatingCapsuleWithVeryLongErrorMessageFails() {
        // Given
        String msg = "*".repeat(1025);

        // Then
        assertThatThrownBy(() ->
                // When
                new CloseWebtransportSessionCapsule(0x87654321, msg)
        );
    }

    @Test
    void testSerializingCapsule() throws IOException {
        // Given
        CloseWebtransportSessionCapsule closeCapsule = new CloseWebtransportSessionCapsule(1234, "err");

        // When
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        closeCapsule.write(output);

        // Then
        assertThat(output.toByteArray()).isEqualTo(ByteUtils.hexToBytes("6843" + "07" + "000004d2" + "657272"));
    }
}