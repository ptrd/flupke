package net.luminis.http3.sample;

import net.luminis.http3.impl.FlupkeVersion;
import net.luminis.quic.QuicConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Flupke {

    public static void main(String[] args) {
        System.out.println("Flupke version (build id): " + FlupkeVersion.getVersion());
    }
}
