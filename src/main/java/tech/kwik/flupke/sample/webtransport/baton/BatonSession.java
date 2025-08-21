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
package tech.kwik.flupke.sample.webtransport.baton;

import tech.kwik.core.generic.VariableLengthInteger;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BatonSession {

    enum ArrivalType {
        UniDirectional,
        PeerInitiatedBidirectional,
        SelfInitiatedBidirectional
    };

    private boolean debug = false;
    private Session webTransportSession;

    public BatonSession(Session session) {
        this.webTransportSession = session;
    }

    public BatonSession() {
    }

    public void setWebTransportSession(Session session) {
        this.webTransportSession = session;
        this.webTransportSession.open();
    }

    public void start(int initialBaton) throws IOException {
        writeBaton(initialBaton, webTransportSession.createUnidirectionalStream().getOutputStream());
    }

    void unidirectionalStreamHandler(WebTransportStream stream) {
        debug("Received unidirectional stream " + stream);
        try {
            int baton = readBaton(stream.getInputStream());
            processBaton(baton, ArrivalType.UniDirectional, null);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void bidirectionalStreamHandler(WebTransportStream stream) {
        debug("Received bidirectional Baton stream " + stream);
        try {
            int baton = readBaton(stream.getInputStream());
            processBaton(baton, ArrivalType.PeerInitiatedBidirectional, stream);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processBaton(int baton, ArrivalType arrivalType, WebTransportStream receivingStream) throws IOException {
        if (baton == 0) {
            closed();
        }
        else {
            int newBaton = (baton + 1) % 256;
            switch (arrivalType) {
                case UniDirectional:
                    WebTransportStream bidirectionalStream = webTransportSession.createBidirectionalStream();
                    writeBaton(newBaton, bidirectionalStream.getOutputStream());
                    if (newBaton == 0) {
                        close();
                        return;
                    }
                    int nextBaton = readBaton(bidirectionalStream.getInputStream());
                    debug("Received baton " + baton + " on unidirectional on thread " + Thread.currentThread().getName());
                    processBaton(nextBaton, ArrivalType.SelfInitiatedBidirectional, bidirectionalStream);
                    break;
                case PeerInitiatedBidirectional:
                    debug("Received baton " + baton + " on peerinitiatedbiderctional on thread " + Thread.currentThread().getName());
                    writeBaton(newBaton, receivingStream.getOutputStream());
                    break;
                case SelfInitiatedBidirectional:
                    debug("Received baton " + baton + " on self-initiatedbiderctional on thread " + Thread.currentThread().getName());
                    WebTransportStream unidirectionalStream = webTransportSession.createUnidirectionalStream();
                    writeBaton(newBaton, unidirectionalStream.getOutputStream());
            }
        }
    }

    private void writeBaton(int newBaton, OutputStream outputStream) throws IOException {
        out(">> " + newBaton);
        byte paddingLength = 0;
        outputStream.write(paddingLength);
        if (paddingLength > 0) {
            outputStream.write(new byte[paddingLength]);
        }
        outputStream.write(newBaton);
        outputStream.close();
    }

    private int readBaton(InputStream inputStream) throws IOException {
        int paddingLength = VariableLengthInteger.parse(inputStream);
        debug("Padding length: " + paddingLength);
        inputStream.skip(paddingLength);
        int baton = inputStream.read();
        int eof = inputStream.read();
        out("<< " + baton);
        return baton;
    }

    private void close() throws IOException {
        out("Closing Baton session");
        webTransportSession.close();
    }

    private void closed() throws IOException {
        out("Closed Baton session");
        webTransportSession.close();
    }

    private void out(String message) {
        System.out.println("[" + (webTransportSession != null? webTransportSession.getSessionId(): "?") + "]: " + message);
    }

    private void debug(String message) {
        if (debug) {
            System.out.println("D " + message);
        }
    }
}

