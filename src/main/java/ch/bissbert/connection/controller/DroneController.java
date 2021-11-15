package ch.bissbert.connection.controller;

import ch.bissbert.connection.Connection;

import java.io.IOException;

public abstract class DroneController {
    protected final Connection connection;

    public DroneController(Connection connection) {
        this.connection = connection;
    }

    public void enterSDKMode() throws IOException {
        connection.sendCommand("command");
    }
}
