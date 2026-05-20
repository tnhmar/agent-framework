package com.agentruntime.protocols.anp;

public class AnpTransportException extends Exception {
    public AnpTransportException(String message) { super(message); }
    public AnpTransportException(String message, Throwable cause) { super(message, cause); }
}
