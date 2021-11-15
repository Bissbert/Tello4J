package ch.bissbert.connection;

import ch.bissbert.connection.controller.AdvancedDroneController;
import ch.bissbert.connection.controller.BasicDroneController;

public class Drone extends Connection {
    public final static String host = "192.168.10.1";
    public final static int port = 8889;

    private final BasicDroneController basicDroneController;
    private final AdvancedDroneController advancedDroneController;

    public Drone() {
        super(host, port);
        this.basicDroneController = new BasicDroneController(this);
        this.advancedDroneController = new AdvancedDroneController(this);
    }

    public BasicDroneController getBasicDroneController() {
        return basicDroneController;
    }

    public AdvancedDroneController getAdvancedDroneController() {
        return advancedDroneController;
    }
}
