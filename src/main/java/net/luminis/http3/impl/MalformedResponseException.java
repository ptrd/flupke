package net.luminis.http3.impl;

// https://www.rfc-editor.org/rfc/rfc9114.html#name-malformed-requests-and-resp
public class MalformedResponseException extends Throwable {

    public MalformedResponseException(String reason) {
    }
}
