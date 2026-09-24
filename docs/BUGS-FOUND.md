[← back to the overview](../README.md)

# Bugs found

Each entry below was confirmed by reading the source and, where possible,
reproduced with `tools/protocol_probe.py` in a Linux container (see
[How this was measured](measurement.md)). All six are fixed: three on `main`,
and three in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad), which also adds a JUnit
suite with a regression test for each (`sh tools/docker-test.sh`).

| # | Entry | Status |
|---|---|---|
| 1 | `ComplexCommand.parameters` is never initialized | Fixed in [`42c9dff`](https://github.com/Bissbert/Tello4J/commit/42c9dff) |
| 2 | A terminal `CommandExecutor` calls a null successor | Fixed in [`35081a4`](https://github.com/Bissbert/Tello4J/commit/35081a4) |
| 3 | A missing reply blocks forever | Fixed in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad) ([#5](https://github.com/Bissbert/Tello4J/issues/5)) |
| 4 | The closed-socket guard checks the wrong condition | Fixed in [`8970c18`](https://github.com/Bissbert/Tello4J/commit/8970c18) |
| 5 | Constructor setup failure is caught, then dereferenced | Fixed in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad) ([#6](https://github.com/Bissbert/Tello4J/issues/6)) |
| 6 | An executor with parameters throws `NullPointerException` on `run()` | Fixed in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad) ([#7](https://github.com/Bissbert/Tello4J/issues/7)) |

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

**Status:** fixed in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad) ([#5](https://github.com/Bissbert/Tello4J/issues/5)).

**File:** `src/main/java/ch/bissbert/connection/Connection.java`

**What happened:** `fetchDataByte()` called `DatagramSocket.receive()` and no
code set `SO_TIMEOUT`. If the peer never replied, the caller stayed blocked.

**What changed:** the constructor sets a receive timeout, 15 seconds by
default. The Tello replies to a movement command only when the move is done,
so the default leaves room for slow commands. A read that runs out of time
throws `SocketTimeoutException`. Nothing is retried, so a movement command is
never sent twice. The timeout can be chosen per connection:

```java
new Connection(host, port, 2_000);   // 2 s; 0 waits forever
connection.setReceiveTimeout(5_000);
```

**Check:** the probe's stub never answers `stay-silent`:

```
fetchDataString() times out when nothing replies             PASS  SocketTimeoutException after 512 ms (timeout 500 ms)
```

`ConnectionTest` covers the timeout, that it is not retried, and that the
connection still works afterwards. With `setSoTimeout` removed, 6 of its
tests fail.

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

**Status:** fixed in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad) ([#6](https://github.com/Bissbert/Tello4J/issues/6)).

**File:** `src/main/java/ch/bissbert/connection/Connection.java`

**What happened:** the constructor caught `UnknownHostException` or
`SocketException`, printed the stack trace, and called `connect()` anyway. If
the host did not resolve, `socket` was still null and `connect()` threw
`NullPointerException`.

**What changed:** a setup failure throws `UncheckedIOException` with the
original exception as its cause, and nothing is printed. It is unchecked so
that the constructor signature stays the same, and so do the callers in `Drone`
and `CommandExecutor.createConnection()`.

```diff
         } catch (UnknownHostException e) {
-            e.printStackTrace();
+            throw new UncheckedIOException("Could not resolve Tello host " + host, e);
         }
```

`SocketException` is handled the same way.

**Check:** the probe constructs `new Connection("does-not-exist.invalid", port)`:

```
unresolvable host: constructor throws with the cause         PASS  constructor threw UncheckedIOException, cause java.net.UnknownHostException
```

With the old catch-and-print restored, 2 tests in `ConnectionTest` fail.

## 6. An executor with parameters throws `NullPointerException` on `run()`

**Status:** fixed in [`71a99ad`](https://github.com/Bissbert/Tello4J/commit/71a99ad) ([#7](https://github.com/Bissbert/Tello4J/issues/7)). Found by the new JUnit suite.

**File:** `src/main/java/ch/bissbert/command/creator/CommandExecutor.java` (`ComplexExecutor`)

**What happened:** `ComplexExecutor` declared its own `command` field, which
hid the inherited one. `withParam()` wrote to the hidden field, but the
inherited `run()` read the parent's field, which was still null. Any
executor with a parameter failed before sending:

```java
CommandExecutor.execute("forward").withParam(20).run();   // NullPointerException
```

The conversion from `BasicExecutor` also dropped any successor set earlier
with `andThen()` and any read target set with `withReadAndSaveIn()`.

**What changed:** the hiding field is gone. The constructor sets the inherited
`command` and copies the successor and read target:

```diff
-        private ComplexCommand command;
-
         public ComplexExecutor(BasicExecutor basicExecutor) {
             this.command = new ComplexCommand((BasicCommand) basicExecutor.command);
+            this.after = basicExecutor.after;
+            this.reference = basicExecutor.reference;
         }
```

**Check:** the probe's `executor-with-param` line passes, and the stub
receives the datagram `forward 20`. With the hiding field restored, 5 tests in
`CommandExecutorTest` fail.
