package net.luminis.http3.impl;

import net.luminis.quic.DatagramSocketFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class InterfaceBoundDatagramSocketFactory implements DatagramSocketFactory {
    private final InetAddress address;
    public InterfaceBoundDatagramSocketFactory(InetAddress address) {
        this.address = address;
    }
    private DatagramSocket bindToLocalAddress(InetAddress address) throws SocketException {
        return new DatagramSocket(new InetSocketAddress(address, 0));
    }
    @Override
    public DatagramSocket createSocket(InetAddress inetAddress) throws SocketException {
        DatagramSocket datagramSocket = bindToLocalAddress(address);
        datagramSocket.connect(new InetSocketAddress(inetAddress, 443));
        return datagramSocket;
    }
}