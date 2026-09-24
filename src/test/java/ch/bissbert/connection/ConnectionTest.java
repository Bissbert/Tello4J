package ch.bissbert.connection;

import ch.bissbert.connection.exception.NoConnectionException;
import ch.bissbert.testutil.FakeDrone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionTest {

    private FakeDrone drone;

    @BeforeEach
    void start() throws Exception {
        drone = new FakeDrone();
    }

    @AfterEach
    void stop() throws Exception {
        drone.close();
    }

    private Connection connect() {
        return new Connection(FakeDrone.HOST, drone.port());
    }

    @Test
    void sendsTheBareCommandAsOneDatagram() throws Exception {
        try (Connection c = connect()) {
            c.sendCommand("command");
        }
        assertEquals(List.of("command"), drone.awaitReceived(1));
    }

    @Test
    void sendsCommandsInOrder() throws Exception {
        try (Connection c = connect()) {
            c.sendCommand("command");
            c.sendCommand("takeoff");
            c.sendCommand("land");
        }
        assertEquals(List.of("command", "takeoff", "land"), drone.awaitReceived(3));
    }

    @Test
    void nullCommandIsRejected() {
        try (Connection c = connect()) {
            assertThrows(NullPointerException.class, () -> c.sendCommand((String) null));
        }
    }

    @Test
    void okReplyIsTrue() throws Exception {
        drone.reply("takeoff", "ok");
        try (Connection c = connect()) {
            assertTrue(c.sendCommandAndFetchStatus("takeoff"));
        }
    }

    @Test
    void okReplyIgnoresCase() throws Exception {
        drone.reply("takeoff", "OK");
        try (Connection c = connect()) {
            assertTrue(c.sendCommandAndFetchStatus("takeoff"));
        }
    }

    @Test
    void anyOtherReplyIsFalse() throws Exception {
        drone.reply("flip x", "error");
        try (Connection c = connect()) {
            assertFalse(c.sendCommandAndFetchStatus("flip x"));
        }
    }

    @Test
    void readReturnsThePayloadVerbatim() throws Exception {
        drone.reply("battery?", "87\r\n");
        try (Connection c = connect()) {
            assertEquals("87\r\n", c.sendCommandAndFetchData("battery?"));
        }
    }

    @Test
    void readIsCutToTheRequestedLength() throws Exception {
        drone.reply("sn?", "0TQDG44EDBNYXK");
        try (Connection c = connect()) {
            assertEquals("0TQD", c.sendCommandAndFetchData("sn?", 4));
        }
    }

    @Test
    void readDecodesUtf8() throws Exception {
        drone.reply("wifi?", "café");
        try (Connection c = connect()) {
            assertEquals("café", c.sendCommandAndFetchData("wifi?"));
        }
    }

    @Test
    void sendAfterCloseThrowsNoConnectionException() {
        Connection c = connect();
        c.close();
        assertThrows(NoConnectionException.class, () -> c.sendCommand("land"));
    }

    // Issue #5: a missing reply used to block forever.

    @Test
    void missingReplyTimesOut() {
        drone.neverReplyTo("stay-silent");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Connection c = new Connection(FakeDrone.HOST, drone.port(), 300)) {
                long t0 = System.nanoTime();
                assertThrows(SocketTimeoutException.class, () -> c.sendCommandAndFetchData("stay-silent"));
                long waitedMs = (System.nanoTime() - t0) / 1_000_000;
                assertTrue(waitedMs >= 250, "returned after " + waitedMs + " ms");
            }
        });
    }

    @Test
    void statusReadTimesOutToo() {
        drone.neverReplyTo("land");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Connection c = new Connection(FakeDrone.HOST, drone.port(), 200)) {
                assertThrows(SocketTimeoutException.class, () -> c.sendCommandAndFetchStatus("land"));
            }
        });
    }

    @Test
    void timeoutIsNotRetried() throws Exception {
        drone.neverReplyTo("forward 20");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Connection c = new Connection(FakeDrone.HOST, drone.port(), 200)) {
                assertThrows(SocketTimeoutException.class, () -> c.sendCommandAndFetchStatus("forward 20"));
            }
        });
        Thread.sleep(300);
        assertEquals(List.of("forward 20"), drone.received());
    }

    @Test
    void connectionIsUsableAfterATimeout() throws Exception {
        drone.neverReplyTo("stay-silent").reply("battery?", "50");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Connection c = new Connection(FakeDrone.HOST, drone.port(), 200)) {
                assertThrows(SocketTimeoutException.class, () -> c.sendCommandAndFetchData("stay-silent"));
                assertEquals("50", c.sendCommandAndFetchData("battery?"));
            }
        });
    }

    @Test
    void defaultConstructorSetsTheDefaultTimeout() throws Exception {
        try (Connection c = connect()) {
            assertEquals(Connection.DEFAULT_RECEIVE_TIMEOUT_MS, c.getReceiveTimeout());
        }
    }

    @Test
    void receiveTimeoutCanBeChanged() throws Exception {
        drone.neverReplyTo("stay-silent");
        try (Connection c = connect()) {
            c.setReceiveTimeout(150);
            assertEquals(150, c.getReceiveTimeout());
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(SocketTimeoutException.class, () -> c.sendCommandAndFetchData("stay-silent")));
        }
    }

    // Issue #6: a setup failure used to be printed, then dereferenced.

    @Test
    void unresolvableHostThrowsWithTheCause() {
        UncheckedIOException e = assertThrows(UncheckedIOException.class,
                () -> new Connection("does-not-exist.invalid", drone.port()));
        assertInstanceOf(UnknownHostException.class, e.getCause());
        assertTrue(e.getMessage().contains("does-not-exist.invalid"), e.getMessage());
    }

    @Test
    void unresolvableHostPrintsNoStackTrace() {
        java.io.PrintStream old = System.err;
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(err));
        try {
            assertThrows(UncheckedIOException.class, () -> new Connection("does-not-exist.invalid", drone.port()));
        } finally {
            System.setErr(old);
        }
        assertEquals("", err.toString());
    }

    @Test
    void invalidTimeoutIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Connection(FakeDrone.HOST, drone.port(), -1));
    }
}
