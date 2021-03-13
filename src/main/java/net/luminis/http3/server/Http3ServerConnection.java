/*
 * Copyright © 2021 Peter Doornbosch
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
package net.luminis.http3.server;

import net.luminis.http3.impl.*;
import net.luminis.qpack.Decoder;
import net.luminis.qpack.Encoder;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.VariableLengthInteger;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.stream.QuicStream;

import java.io.*;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


/**
 * Http connection serving GET requests by returning the file from specified www dir.
 */
public class Http3ServerConnection extends ApplicationProtocolConnection implements Consumer<QuicStream> {

    public static int MAX_HEADER_SIZE = 10 * 1024;
    public static int MAX_DATA_SIZE = 10 * 1024 * 1024;
    public static int MAX_FRAME_SIZE = 10 * 1024 * 1024;

    private static AtomicInteger threadCount = new AtomicInteger();

    private final QuicConnection quicConnection;
    private final File wwwDir;
    private InputStream controlStream;
    private int peerQpackBlockedStreams;
    private int peerQpackMaxTableCapacity;
    private InputStream clientEncoderStream;
    private final Decoder qpackDecoder;


    public Http3ServerConnection(QuicConnection quicConnection, File wwwDir) {
        this.quicConnection = quicConnection;
        this.wwwDir = wwwDir;
        quicConnection.setPeerInitiatedStreamCallback(this);
        qpackDecoder = new Decoder();
        startControlStream();
    }

    @Override
    public void accept(QuicStream quicStream) {
        Thread thread = new Thread(() -> handleStream(quicStream));
        thread.setName("http-" + threadCount.getAndIncrement());
        thread.start();
    }

    void handleStream(QuicStream quicStream) {
        System.out.println("HTTP3 Server: client opening stream " + quicStream.getStreamId());

        if (quicStream.isUnidirectional()) {
            // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-6.2
            // "Unidirectional streams, in either direction, are used for a range of purposes. The purpose is
            //  indicated by a stream type, which is sent as a variable-length integer at the start of the stream. "
            try {
                int streamType = quicStream.getInputStream().read();
                if (streamType == 0x00) {
                    // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-6.2.1
                    // "A control stream is indicated by a stream type of "0x00"."
                    controlStream = quicStream.getInputStream();
                    processControlStream(controlStream);
                } else if (streamType == 0x02) {
                    // https://tools.ietf.org/html/draft-ietf-quic-qpack-21#section-4.2
                    // "An encoder stream is a unidirectional stream of type "0x02"."
                    clientEncoderStream = quicStream.getInputStream();
                }
            }
            catch (IOException ioError) {
                // "If either control stream is closed at any point, this MUST be treated as a connection error of
                //  type H3_CLOSED_CRITICAL_STREAM."
                quicConnection.close(); // TODO: with application error
            }
        }
        else {
            // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-6.1
            // "All client-initiated bidirectional streams are used for HTTP requests and responses."
            // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1
            // "A client sends an HTTP request on a request stream, which is a client-initiated bidirectional QUIC
            //  stream; see Section 6.1. A client MUST send only a single request on a given stream."
            InputStream requestStream = quicStream.getInputStream();
            try {
                List<Http3Frame> receivedFrames = parseHttp3Frames(requestStream);
                handleStream(receivedFrames, quicStream);
            }
            catch (IOException ioError) {
                sendHttpErrorResponse(500, "", quicStream);
            }
            catch (HttpError httpError) {
                sendHttpErrorResponse(httpError.getStatusCode(), httpError.getMessage(), quicStream);
            }
        }
    }

    private void sendHttpErrorResponse(int statusCode, String message, QuicStream quicStream) {
        try {
            sendStatus(statusCode, quicStream.getOutputStream());
        }
        catch (IOException e) {
        }
        finally {
            try {
                quicStream.getOutputStream().close();
            }
            catch (IOException e) {}
        }
    }

    List<Http3Frame> parseHttp3Frames(InputStream requestStream) throws IOException, HttpError {
        // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1
        // "An HTTP message (request or response) consists of:
        //   1.  the header section, sent as a single HEADERS frame (see Section 7.2.2),
        //   2.  optionally, the content, if present, sent as a series of DATA frames (see Section 7.2.1), and
        //   3.  optionally, the trailer section, if present, sent as a single HEADERS frame."
        List<Http3Frame> receivedFrames = new ArrayList<>();
        int headerSize = 0;
        int dataSize = 0;

        long frameType;
        while ((frameType = readFrameType(requestStream)) >= 0) {
            int payloadLength = VariableLengthInteger.parse(requestStream);

            switch ((int) frameType) {
                case 0x01:
                    if (headerSize + payloadLength > MAX_HEADER_SIZE) {
                        throw new HttpError("max frame size exceeded", 414);
                    }
                    byte[] payload = readExact(requestStream, payloadLength);
                    headerSize += payloadLength;
                    HeadersFrame responseHeadersFrame = new RequestHeadersFrame().parsePayload(payload, qpackDecoder);
                    receivedFrames.add(responseHeadersFrame);
                    break;
                case 0x00:
                    if (dataSize + payloadLength > MAX_DATA_SIZE) {
                        throw new HttpError("max frame size exceeded", 400);
                    }
                    payload = readExact(requestStream, payloadLength);
                    dataSize += payloadLength;
                    DataFrame dataFrame = new DataFrame().parsePayload(payload);
                    receivedFrames.add(dataFrame);
                    break;
                default:
                    // https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1
                    // "Frames of unknown types (Section 9), including reserved frames (Section 7.2.8) MAY be sent on a
                    //  request or push stream before, after, or interleaved with other frames described in this section."
                    if (payloadLength > MAX_FRAME_SIZE) {
                        throw new HttpError("max frame size exceeded", 400);
                    }
                    readExact(requestStream, payloadLength);
            }
        }
        return receivedFrames;
    }

    private void handleStream(List<Http3Frame> receivedFrames, QuicStream quicStream) throws HttpError {
        RequestHeadersFrame headersFrame = (RequestHeadersFrame) receivedFrames.stream()
                .filter(f -> f instanceof HeadersFrame)
                .findFirst()
                .orElseThrow(() -> new HttpError("", 400));

        if (headersFrame.getMethod() != null && headersFrame.getPath() != null) {
            if (headersFrame.getMethod().equals("GET")) {
                get(headersFrame.getPath(), quicStream.getOutputStream());
            }
            else {
                throw new HttpError("http method not supported", 405);
            }
        }
    }

    private void sendStatus(int statusCode, OutputStream outputStream) throws IOException {
        ResponseHeadersFrame headersFrame = new ResponseHeadersFrame();
        headersFrame.setStatus(statusCode);
        outputStream.write(headersFrame.toBytes(new Encoder()));
    }

    private void get(String path, OutputStream outputStream) throws HttpError {
        System.out.println("GET " + path);
        if (path.trim().equals("/")) {
            path = "/index.html";
        }
        try {
            File file = getFileInWwwDir(path);
            if (file != null && file.exists() && file.isFile()) {
                DataFrame dataFrame = new DataFrame(Files.readAllBytes(file.toPath()));
                sendStatus(200, outputStream);
                outputStream.write(dataFrame.toBytes());
                outputStream.close();
            } else {
                throw new HttpError("file not found", 404);
            }
        }
        catch (IOException e){
            throw new HttpError("internal server error", 500);
        }
    }

    /**
     * Check that file specified by argument is actually in the www dir (to prevent file traversal).
     * @param fileName
     * @return
     * @throws IOException
     */
    private File getFileInWwwDir(String fileName) throws IOException {
        String requestedFilePath = new File(wwwDir, fileName).getCanonicalPath();
        if (requestedFilePath.startsWith(wwwDir.getCanonicalPath())) {
            return new File(requestedFilePath);
        }
        else {
            return null;
        }
    }

    long readFrameType(InputStream inputStream) {
        try {
            return VariableLengthInteger.parseLong(inputStream);
        }
        catch (IOException eof) {
            return -1;
        }
    }
    
    void startControlStream() {
        try {
            // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
            // "Each side MUST initiate a single control stream at the beginning of
            //   the connection and send its SETTINGS frame as the first frame on this
            //   stream."
            QuicStream clientControlStream = quicConnection.createStream(false);
            OutputStream clientControlOutput = clientControlStream.getOutputStream();
            // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
            // "A control stream is indicated by a stream type of "0x00"."
            clientControlOutput.write(0x00);

            // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-2.3
            // "After the QUIC connection is
            //   established, a SETTINGS frame (Section 4.2.5) MUST be sent by each
            //   endpoint as the initial frame of their respective HTTP control stream
            //   (see Section 3.2.1)."
            ByteBuffer settingsFrame = new SettingsFrame(0, 0).getBytes();
            clientControlStream.getOutputStream().write(settingsFrame.array(), 0, settingsFrame.limit());
            // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
            // " The sender MUST NOT close the control stream."
        }
        catch (IOException e) {
            // QuicStream's output stream will never throw an IOException, unless stream is already closed.
        }
    }

    private void processControlStream(InputStream controlStream) throws IOException {
        int frameType = VariableLengthInteger.parse(controlStream);
        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-3.2.1
        // "Each side MUST initiate a single control stream at the beginning of
        //   the connection and send its SETTINGS frame as the first frame on this
        //   stream. "
        // https://tools.ietf.org/html/draft-ietf-quic-http-20#section-4.2.5
        // "The SETTINGS frame (type=0x4)..."
        if (frameType != 0x04) {
            throw new RuntimeException("Invalid frame on control stream");
        }
        int frameLength = VariableLengthInteger.parse(controlStream);
        byte[] payload = readExact(controlStream, frameLength);

        SettingsFrame settingsFrame = new SettingsFrame().parsePayload(ByteBuffer.wrap(payload));
        peerQpackMaxTableCapacity = settingsFrame.getQpackMaxTableCapacity();
        peerQpackBlockedStreams = settingsFrame.getQpackBlockedStreams();
    }

    private byte[] readExact(InputStream inputStream, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = inputStream.read(data, offset, length - offset);
            if (read > 0) {
                offset += read;
            }
            else {
                throw new EOFException("Stream closed by peer");
            }
        }
        return data;
    }
}
