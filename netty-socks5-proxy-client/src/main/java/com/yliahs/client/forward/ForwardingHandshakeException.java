package com.yliahs.client.forward;

public class ForwardingHandshakeException extends Exception {

    private final byte status;

    public ForwardingHandshakeException(byte status, String message) {
        super(message);
        this.status = status;
    }

    public byte getStatus() {
        return status;
    }
}

