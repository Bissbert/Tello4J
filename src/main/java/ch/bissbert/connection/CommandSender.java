package ch.bissbert.connection;

import ch.bissbert.command.model.Command;

import java.io.IOException;

public interface CommandSender {

    void sendCommand(String command) throws IOException;

    default void sendCommand(Command command) throws IOException {
        this.sendCommand(command.compose());
    }

    String fetchDataString(int lengthInByte) throws IOException;

    byte[] fetchDataByte(int length) throws IOException;
    default byte[] fetchDataByte() throws IOException{
        return this.fetchDataByte(1024);
    }

    default String fetchDataString() throws IOException {
        return this.fetchDataString(1024);
    }

    default String sendCommandAndFetchData(String command) throws IOException {
        sendCommand(command);
        return fetchDataString();
    }

    default String sendCommandAndFetchData(Command command) throws IOException {
        return sendCommandAndFetchData(command.compose());
    }

    default String sendCommandAndFetchData(String command, int lengthInByte) throws IOException {
        sendCommand(command);
        return fetchDataString(lengthInByte);
    }

    default String sendCommandAndFetchData(Command command, int lengthInByte) throws IOException {
        return sendCommandAndFetchData(command.compose(), lengthInByte);
    }

    boolean sendCommandAndFetchStatus(String command) throws IOException;

    default boolean sendCommandAndFetchStatus(Command command) throws IOException {
        return sendCommandAndFetchStatus(command.compose());
    }
}
