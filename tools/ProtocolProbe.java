// Drives the Tello4J library against a local UDP stub (tools/protocol_probe.py).
// It is deliberately in ch.bissbert.command.creator so it can reach the
// package-visible static CommandExecutor.connection field without changing the
// library. Nothing here talks to a real drone.
//
// Usage: java -cp <library-classes>:<log4j>:<this> ch.bissbert.command.creator.ProtocolProbe <port>
// Prints one "CHECK|<id>|<PASS|FAIL>|<detail>" line per check.

package ch.bissbert.command.creator;

import ch.bissbert.CommandStrings;
import ch.bissbert.command.model.BasicCommand;
import ch.bissbert.command.model.ComplexCommand;
import ch.bissbert.connection.Connection;

public class ProtocolProbe {

    private static final String HOST = "127.0.0.1";
    private static int port;

    public static void main(String[] args) throws Exception {
        port = Integer.parseInt(args[0]);

        constructs();
        sendsPlainDatagram();
        statusOk();
        statusNotOk();
        readReturnsPayload();
        composeBasic();
        composeEnumBacked();
        composeWifiLiteral();
        complexCommandAddParam();
        executorRunWithoutConnection();
        executorChainTail();
        sendAfterClose();
        receiveHasNoTimeout();

        System.out.flush();
        // A probe thread is still parked in receive(); do not wait for it.
        Runtime.getRuntime().halt(0);
    }

    private static void check(String id, boolean pass, String detail) {
        System.out.println("CHECK|" + id + "|" + (pass ? "PASS" : "FAIL") + "|" + detail);
        System.out.flush();
    }

    /** The constructor resolves the host and connects the datagram socket. */
    private static void constructs() {
        try (Connection c = new Connection(HOST, port)) {
            check("construct", c != null, "Connection(" + HOST + ", " + port + ") built");
        } catch (Exception e) {
            check("construct", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** sendCommand writes the command text as one UTF-8 datagram, no framing added. */
    private static void sendsPlainDatagram() {
        try (Connection c = new Connection(HOST, port)) {
            c.sendCommand("command");
            check("send-plain", true, "sent \"command\"");
        } catch (Exception e) {
            check("send-plain", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** sendCommandAndFetchStatus maps an "ok" reply to true. */
    private static void statusOk() {
        try (Connection c = new Connection(HOST, port)) {
            boolean r = c.sendCommandAndFetchStatus("takeoff");
            check("status-ok", r, "sendCommandAndFetchStatus(\"takeoff\") -> " + r);
        } catch (Exception e) {
            check("status-ok", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** ...and any other reply to false. */
    private static void statusNotOk() {
        try (Connection c = new Connection(HOST, port)) {
            boolean r = c.sendCommandAndFetchStatus("provoke-error");
            check("status-error", !r, "sendCommandAndFetchStatus(\"provoke-error\") -> " + r);
        } catch (Exception e) {
            check("status-error", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** A read command hands back the reply payload verbatim, untrimmed. */
    private static void readReturnsPayload() {
        try (Connection c = new Connection(HOST, port)) {
            String r = c.sendCommandAndFetchData(CommandStrings.GET_BATTERY.getCommand());
            check("read-payload", r != null && !r.isEmpty(), "reply=" + quote(r) + " length=" + r.length());
        } catch (Exception e) {
            check("read-payload", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void composeBasic() {
        String composed = new BasicCommand("up", false).compose();
        check("compose-basic", "up".equals(composed), "BasicCommand(\"up\").compose() = " + quote(composed));
    }

    private static void composeEnumBacked() {
        String composed = new BasicCommand(CommandStrings.TAKE_OFF, false).compose();
        check("compose-enum", "takeoff".equals(composed),
                "BasicCommand(CommandStrings.TAKE_OFF).compose() = " + quote(composed));
    }

    /** SET_WIFI bakes the SDK's placeholder word "ssid" into the command text. */
    private static void composeWifiLiteral() {
        String composed = new BasicCommand(CommandStrings.SET_WIFI, false).compose();
        check("compose-wifi", "wifi ssid".equals(composed),
                "BasicCommand(CommandStrings.SET_WIFI).compose() = " + quote(composed));
    }

    /** ComplexCommand.parameters is declared but never assigned. */
    private static void complexCommandAddParam() {
        try {
            new ComplexCommand("go", false).addParam(20);
            check("complex-addparam", false, "addParam(20) returned normally");
        } catch (Throwable t) {
            check("complex-addparam", t instanceof NullPointerException,
                    "addParam(20) threw " + t.getClass().getName());
        }
    }

    /** run() never calls createConnection(), so the static field is still null. */
    private static void executorRunWithoutConnection() {
        CommandExecutor.connection = null;
        try {
            CommandExecutor.execute(CommandStrings.COMMAND).run();
            check("executor-no-connection", false, "run() returned normally");
        } catch (Throwable t) {
            check("executor-no-connection", t instanceof NullPointerException,
                    "run() threw " + t.getClass().getName());
        }
    }

    /** run() always calls after.run(); the last executor in a chain has no successor. */
    private static void executorChainTail() {
        Connection c = new Connection(HOST, port);
        CommandExecutor.connection = c;
        try {
            CommandExecutor.execute(CommandStrings.COMMAND).run();
            check("executor-chain-tail", false, "run() returned normally");
        } catch (Throwable t) {
            check("executor-chain-tail", t instanceof NullPointerException,
                    "run() sent the command, then threw " + t.getClass().getName());
        } finally {
            CommandExecutor.connection = null;
            c.close();
        }
    }

    /** DatagramSocket.isConnected() stays true after close(), so the guard misses. */
    private static void sendAfterClose() {
        Connection c = new Connection(HOST, port);
        c.close();
        try {
            c.sendCommand("land");
            check("send-after-close", false, "sendCommand() returned normally");
        } catch (Throwable t) {
            check("send-after-close", true, t.getClass().getName() + ": " + t.getMessage());
        }
    }

    /** No SO_TIMEOUT is ever set, so receive() parks forever when nothing replies. */
    private static void receiveHasNoTimeout() throws Exception {
        final Connection c = new Connection(HOST, port);
        final long budgetMs = 5000;
        Thread t = new Thread(() -> {
            try {
                c.sendCommandAndFetchData("stay-silent");
            } catch (Throwable ignored) {
                // the probe exits via halt() while this thread is parked
            }
        });
        t.setDaemon(true);
        long t0 = System.nanoTime();
        t.start();
        t.join(budgetMs);
        long waitedMs = (System.nanoTime() - t0) / 1_000_000;
        boolean stillBlocked = t.isAlive();
        check("no-so-timeout", stillBlocked,
                "fetchDataString() still blocked after " + waitedMs + " ms with no reply");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
