[← back to the overview](../README.md)

# Bugs found

Each entry below was confirmed by reading the source and, where possible,
reproduced with `tools/protocol_probe.py` in a Linux container (see
[How this was measured](measurement.md)). Three are fixed on `main`. Two are
still open because each fix needs a public API decision.

| # | Entry | Status |
|---|---|---|
| 1 | `ComplexCommand.parameters` is never initialized | Fixed in [`42c9dff`](https://github.com/Bissbert/Tello4J/commit/42c9dff) |
| 2 | A terminal `CommandExecutor` calls a null successor | Fixed in [`35081a4`](https://github.com/Bissbert/Tello4J/commit/35081a4) |
| 3 | A missing reply blocks forever | Open |
| 4 | The closed-socket guard checks the wrong condition | Fixed in [`8970c18`](https://github.com/Bissbert/Tello4J/commit/8970c18) |
| 5 | Constructor setup failure is caught, then dereferenced | Open |

## 1. `ComplexCommand.parameters` is never initialized

**Status:** fixed in [`42c9dff`](https://github.com/Bissbert/Tello4J/commit/42c9dff).

**File:** `src/main/java/ch/bissbert/command/model/ComplexCommand.java:11`

**What happened:** `parameters` was declared but never assigned, so
`addParam()` and `compose()` threw `NullPointerException`.

**What changed:** each command now gets its own list:

```diff
-    LinkedList<Object> parameters;
+    LinkedList<Object> parameters = new LinkedList<>();
```

**Check:** the probe's `complex-addparam` line reads
`addParam(20), compose() = "forward 20"`.

## 2. A terminal `CommandExecutor` calls a null successor

**Status:** fixed in [`35081a4`](https://github.com/Bissbert/Tello4J/commit/35081a4).

**File:** `src/main/java/ch/bissbert/command/creator/CommandExecutor.java:59`

**What happened:** `run()` sent or read its own command and then always called
`after.run()`. The last executor in a chain has no successor, so it threw
`NullPointerException` after its own operation.

**What changed:** the successor is called only when it exists:

```diff
-        after.run();
+        if (after != null) {
+            after.run();
+        }
```

**Check:** the probe's `executor-chain-tail` line reads
`run() sent the command and returned`, and the stub receives the `command`
datagram.

## 3. A missing reply blocks forever

**Status:** open. A finite timeout is a public behaviour change: the default
value and the caller contract for `SocketTimeoutException` both have to be
chosen, and retries of movement commands must not be added implicitly.

**File:** `src/main/java/ch/bissbert/connection/Connection.java:59`

**What happens:** `fetchDataByte()` calls `DatagramSocket.receive()` and no
code sets `SO_TIMEOUT`. If the peer never replies, the caller stays blocked.

**Reproduce:** the probe's stub never answers `stay-silent`:

```
fetchDataString() never times out when nothing replies       PASS  fetchDataString() still blocked after 5002 ms with no reply
```

**Possible fix:** a caller-selectable receive timeout, set before
`receive()`, with `SocketTimeoutException` passed to the caller.

## 4. The closed-socket guard checks the wrong condition

**Status:** fixed in [`8970c18`](https://github.com/Bissbert/Tello4J/commit/8970c18).

**File:** `src/main/java/ch/bissbert/connection/Connection.java:45`

**What happened:** `sendCommand()` checked only `socket.isConnected()`, which
stays true after `close()`. A send after `close()` threw the socket's
`SocketException: Socket closed` instead of the library's
`NoConnectionException`.

**What changed:**

```diff
-        if (socket.isConnected()) {
+        if (socket.isConnected() && !socket.isClosed()) {
```

**Check:** the probe's `send-after-close` line reads
`threw ch.bissbert.connection.exception.NoConnectionException`.

## 5. Constructor setup failure is caught, then dereferenced

**Status:** open. The fix needs a decision on whether a setup failure is a
checked or an unchecked exception, which changes the constructor's contract.

**File:** `src/main/java/ch/bissbert/connection/Connection.java:20-31`

**What happens:** the constructor catches `UnknownHostException` or
`SocketException`, prints the stack trace, and calls `connect()` anyway. If
the host did not resolve, `socket` is still null and `connect()` throws
`NullPointerException`.

**Reproduce:** the probe constructs `new Connection("does-not-exist.invalid", port)`:

```
unresolvable host: constructor throws NPE                    PASS  constructor threw java.lang.NullPointerException
```

**Possible fix:** connect inside the `try`, and throw with the original cause:

```diff
         try {
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
