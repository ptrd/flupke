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

import tech.kwik.core.DatagramSocketFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class InterfaceBoundDatagramSocketFactory implements DatagramSocketFactory {

    private final InetAddress localAddress;

    public InterfaceBoundDatagramSocketFactory(InetAddress localAddress) {
        this.localAddress = localAddress;
    }

    private DatagramSocket bindToLocalAddress(InetAddress address) throws SocketException {
        return new DatagramSocket(new InetSocketAddress(address, 0));
    }

    @Override
    public DatagramSocket createSocket(InetAddress inetAddress) throws SocketException {
        return bindToLocalAddress(localAddress);
    }
}