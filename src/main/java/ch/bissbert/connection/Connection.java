package ch.bissbert.connection;

import ch.bissbert.connection.exception.NoConnectionException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class Connection implements AutoCloseable, CommandSender {

    private static final Logger logger = Logger.getLogger(Connection.class);

    /**
     * How long a read waits for a reply by default. The Tello answers a
     * movement command only once the movement ends, so this is generous.
     */
    public static final int DEFAULT_RECEIVE_TIMEOUT_MS = 15_000;

    private final int port;
    private final InetAddress address;
    private final DatagramSocket socket;

    /**
     * Opens a datagram socket connected to {@code host:port}.
     *
     * @throws UncheckedIOException if the host does not resolve or the socket
     *                              cannot be opened; the cause is the original
     *                              {@link UnknownHostException} or {@link SocketException}
     */
    public Connection(String host, int port) {
        this(host, port, DEFAULT_RECEIVE_TIMEOUT_MS);
    }

    /**
     * Like {@link #Connection(String, int)}, with a receive timeout in
     * milliseconds. {@code 0} waits forever.
     */
    public Connection(String host, int port, int receiveTimeoutMs) {
        this.port = port;
        try {
            this.address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new UncheckedIOException("Could not resolve Tello host " + host, e);
        }
        try {
            this.socket = new DatagramSocket();
            this.socket.setSoTimeout(receiveTimeoutMs);
        } catch (SocketException e) {
            throw new UncheckedIOException("Could not open a UDP socket for " + host + ":" + port, e);
        }
        this.connect();
    }

    /**
     * Sets how long a read waits for a reply, in milliseconds. {@code 0} waits
     * forever. A read that runs out of time throws
     * {@link java.net.SocketTimeoutException}; nothing is retried.
     */
    public void setReceiveTimeout(int timeoutMs) throws SocketException {
        socket.setSoTimeout(timeoutMs);
    }

    public int getReceiveTimeout() throws SocketException {
        return socket.getSoTimeout();
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
        if (socket.isConnected() && !socket.isClosed()) {
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
