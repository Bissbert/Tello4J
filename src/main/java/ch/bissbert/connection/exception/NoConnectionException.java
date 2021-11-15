package ch.bissbert.connection.exception;

import java.net.SocketException;

public class NoConnectionException extends SocketException {
    public NoConnectionException() {
        super("The Socket you are trying to use is not connected");
    }
}
