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
package net.luminis.http3.impl;

import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InterfaceBoundDatagramSocketFactoryTest {

    @Test
    public void shouldThrowExceptionWhenInvalidLocalAddressIsProvided() {
        assertThatThrownBy(() -> {
            InetAddress localAddress = InetAddress.getByName("123.123.123.123");
            InterfaceBoundDatagramSocketFactory socketFactory = new InterfaceBoundDatagramSocketFactory(localAddress);
            InetAddress address = InetAddress.getByName("111.111.111.111");
            DatagramSocket socket = socketFactory.createSocket(address);
            socket.close();
        }).isInstanceOf(BindException.class);
    }
}
