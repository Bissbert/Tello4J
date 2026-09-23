[← back to the overview](../README.md)

# Bugs found

This file records defects found while documenting the current source. None of
the fixes below were applied. The line numbers refer to the source inspected
for this pass.

## `ComplexCommand.parameters` is never initialized

**File and line:** `src/main/java/ch/bissbert/command/model/ComplexCommand.java:11`

**What happens:** `addParam()` calls `parameters.add(param)` while
`parameters` is null. `compose()` has the same problem when it evaluates
`parameters.size()`.

**How to reproduce:** Build the project, then run
`python3 tools/protocol_probe.py`. The `ComplexCommand.addParam()` check reports
`NullPointerException` for `addParam(20)`.

**Fix I would have made:** initialize the collection when the command is
constructed.

```diff
-    LinkedList<Object> parameters;
+    LinkedList<Object> parameters = new LinkedList<>();
```

## A terminal `CommandExecutor` always calls a null successor

**File and line:** `src/main/java/ch/bissbert/command/creator/CommandExecutor.java:59`

**What happens:** `run()` sends or reads its own command, then unconditionally
calls `after.run()`. A command with no `andThen()` successor therefore throws
`NullPointerException` after its own operation.

**How to reproduce:** Build the project, then run
`python3 tools/protocol_probe.py`. The `executor-chain-tail` check reports that
the command was sent and the tail then threw `NullPointerException`.

**Fix I would have made:** treat a missing successor as the end of the chain.

```diff
-        after.run();
+        if (after != null) {
+            after.run();
+        }
```

## A missing reply blocks forever

**File and line:** `src/main/java/ch/bissbert/connection/Connection.java:59`

**What happens:** `fetchDataByte()` calls `DatagramSocket.receive()` without
configuring `SO_TIMEOUT`. A command whose peer never replies leaves the caller
blocked.

**How to reproduce:** Build the project, then run
`python3 tools/protocol_probe.py`. Its local stub deliberately stays silent for
`stay-silent`; the probe reports that the receive thread is still blocked after
`5004` milliseconds.

**Fix I would have made:** configure a documented, caller-selectable receive
timeout before waiting for the datagram and surface `SocketTimeoutException`.

```diff
-        socket.receive(answerPacket);
+        socket.setSoTimeout(RECEIVE_TIMEOUT_MILLIS);
+        socket.receive(answerPacket);
```

## The closed-socket guard checks the wrong condition

**File and line:** `src/main/java/ch/bissbert/connection/Connection.java:45`

**What happens:** `sendCommand()` checks `socket.isConnected()`, but a
`DatagramSocket` can remain connected after `close()`. A later send therefore
passes the guard and throws the socket's `SocketException` instead of the
library's `NoConnectionException` path.

**How to reproduce:** Build the project, then run
`python3 tools/protocol_probe.py`. The `send-after-close` check closes a
connection and observes `java.net.SocketException: Socket closed` on the next
send.

**Fix I would have made:** include the closed state in the guard.

```diff
-        if (socket.isConnected()) {
+        if (socket.isConnected() && !socket.isClosed()) {
```

## Constructor setup failure is caught, then dereferenced

**Files and lines:** `src/main/java/ch/bissbert/connection/Connection.java:22-30`

**What happens:** the constructor catches `UnknownHostException` or
`SocketException`, prints the stack trace, and then calls `connect()` anyway.
If address or socket creation failed, `connect()` dereferences the null field
instead of returning a clear construction failure.

**How to reproduce:** Construct `new Connection("does-not-exist.invalid", 8889)`
with a host that cannot be resolved, or run in an environment where the
datagram socket cannot be created.
The catch block is followed unconditionally by `this.connect()` in the source.
This path was not exercised by the localhost probe.

**Fix I would have made:** make construction fail explicitly and only connect
after both required fields were created.

```diff
-        try {
+        try {
             this.address = InetAddress.getByName(host);
             this.socket = new DatagramSocket();
-        } catch (UnknownHostException e) {
-            e.printStackTrace();
-        } catch (SocketException e) {
-            e.printStackTrace();
+            this.connect();
+        } catch (UnknownHostException | SocketException e) {
+            throw new IllegalStateException("Could not create Tello connection", e);
         }
-        this.connect();
```
