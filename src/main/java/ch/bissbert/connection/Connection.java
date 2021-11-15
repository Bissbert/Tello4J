package ch.bissbert.connection;

import ch.bissbert.connection.exception.NoConnectionException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class Connection implements AutoCloseable, CommandSender {

    private static final Logger logger = Logger.getLogger(Connection.class);

    private final int port;
    private InetAddress address;
    private DatagramSocket socket;

    public Connection(String host, int port) {
        this.port = port;
        try {
            this.address = InetAddress.getByName(host);
            this.socket = new DatagramSocket();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.connect();
    }

    private void connect() {
        socket.connect(address, port);
    }

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public synchronized void sendCommand(String command) throws IOException {
        Objects.requireNonNull(command);
        if (socket.isConnected()) {
            logger.info("executing command '"+command+"'");
            final byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
            final DatagramPacket commandPacket = new DatagramPacket(commandBytes, commandBytes.length, address, port);
            socket.send(commandPacket);
        } else {
            throw new NoConnectionException();
        }
    }

    @Override
    public byte[] fetchDataByte(int lengthInByte) throws IOException {
        byte[] fetchedData = new byte[lengthInByte];
        final DatagramPacket answerPacket = new DatagramPacket(fetchedData, fetchedData.length);
        socket.receive(answerPacket);
        return Arrays.copyOf(fetchedData, answerPacket.getLength());
    }

    @Override
    public synchronized String fetchDataString(int lengthInByte) throws IOException {
        return new String(fetchDataByte(lengthInByte), StandardCharsets.UTF_8);
    }

    @Override
    public boolean sendCommandAndFetchStatus(String command) throws IOException {
        return sendCommandAndFetchData(command).equalsIgnoreCase("ok");
    }
}
