package net.luminis.http3.impl;

import net.luminis.quic.DatagramSocketFactory;

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