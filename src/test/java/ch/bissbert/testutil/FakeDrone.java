package ch.bissbert.testutil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A UDP endpoint on 127.0.0.1 that answers like the Tello command port. Replies
 * are set per command; a command mapped to {@code null} is never answered.
 * This is not a drone: every reply is synthetic.
 */
public final class FakeDrone implements AutoCloseable {

    public static final String HOST = "127.0.0.1";

    private final DatagramSocket socket;
    private final Map<String, String> replies = new ConcurrentHashMap<>();
    private final List<String> silent = new CopyOnWriteArrayList<>();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final Thread thread;
    private volatile String defaultReply = "ok";
    private volatile boolean running = true;

    public FakeDrone() throws SocketException {
        socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(100);
        thread = new Thread(this::serve, "fake-drone");
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return socket.getLocalPort();
    }

    public FakeDrone reply(String command, String answer) {
        replies.put(command, answer);
        return this;
    }

    public FakeDrone neverReplyTo(String command) {
        silent.add(command);
        return this;
    }

    public FakeDrone defaultReply(String answer) {
        this.defaultReply = answer;
        return this;
    }

    /** The commands received so far, in order. */
    public List<String> received() {
        return List.copyOf(received);
    }

    /** Waits until at least {@code count} datagrams arrived, up to two seconds. */
    public List<String> awaitReceived(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (received.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return received();
    }

    private void serve() {
        byte[] buf = new byte[2048];
        while (running) {
            DatagramPacket in = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(in);
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                return;
            }
            String text = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8);
            received.add(text);
            if (silent.contains(text)) {
                continue;
            }
            byte[] out = replies.getOrDefault(text, defaultReply).getBytes(StandardCharsets.UTF_8);
            try {
                socket.send(new DatagramPacket(out, out.length, in.getSocketAddress()));
            } catch (IOException e) {
                return;
            }
        }
    }

    @Override
    public void close() throws InterruptedException {
        running = false;
        socket.close();
        thread.join(1000);
    }
}
