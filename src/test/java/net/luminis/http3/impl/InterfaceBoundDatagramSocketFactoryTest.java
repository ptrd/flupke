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
