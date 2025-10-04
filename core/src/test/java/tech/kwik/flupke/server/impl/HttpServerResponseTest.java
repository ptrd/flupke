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
package tech.kwik.flupke.server.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class HttpServerResponseTest {

    private HttpServerResponseImpl response;

    @BeforeEach
    void setupObjectUnderTest() {
        response = new HttpServerResponseImpl() {
            @Override
            public OutputStream getOutputStream() {
                return null;
            }
        };
    }

    @Test
    void statusCodeOfZeroShouldNotBeAccepted() {
        assertThatThrownBy(
                // When
                () -> response.setStatus(0))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void statusCodeOfFourDigitsShouldNotBeAccepted() {
        assertThatThrownBy(
                // When
                () -> response.setStatus(1234))
                // Then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void whenStatusNotSetGetStatusShouldThrow() {
        assertThatThrownBy(
                // When
                () -> response.status())
                // Then
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
    }
}