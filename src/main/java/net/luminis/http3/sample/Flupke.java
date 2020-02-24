package net.luminis.http3.sample;

import net.luminis.quic.QuicConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Flupke {

    public static void main(String[] args) {
        System.out.println("Flupke version (build id): " + getVersion());
    }

    static String getVersion() {
        InputStream in = Flupke.class.getResourceAsStream("version.properties");
        if (in != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        }
        else return "dev";
    }

}
