[← back to the overview](../README.md)

# Connection and transport

The connection layer is a thin wrapper over `java.net.DatagramSocket`. The
main source files are [`Connection.java`](../src/main/java/ch/bissbert/connection/Connection.java),
[`Drone.java`](../src/main/java/ch/bissbert/connection/Drone.java),
[`CommandSender.java`](../src/main/java/ch/bissbert/connection/CommandSender.java),
and [`DroneController.java`](../src/main/java/ch/bissbert/connection/controller/DroneController.java).

## Lifecycle and failure paths

```mermaid
stateDiagram-v2
    [*] --> Constructing
    Constructing --> Connected: resolve host, create socket, connect
    Constructing --> SetupFailure: lookup or socket setup throws
    SetupFailure --> SetupFailure: prints stack trace; connect still follows
    Connected --> Connected: send command datagram
    Connected --> Waiting: receive reply
    Waiting --> Connected: reply datagram arrives
    Waiting --> Waiting: no socket timeout; caller blocks
    Connected --> Closed: close()
    Closed --> SendFailure: later send reaches closed socket
    SendFailure --> Closed: SocketException
```

This is an implementation state model, not a state machine maintained by the
library. There is no public `ConnectionState` value. In particular, the
constructor catches `UnknownHostException` and `SocketException`, prints the
stack trace, and then calls its private `connect()` method; setup failure is not
converted into a library-specific result.

## What `Connection` puts on the wire

`sendCommand(String)` rejects a null string, encodes the text as UTF-8, creates
one `DatagramPacket`, and sends it to the address and port captured by the
constructor. It does not add a newline, length prefix, retry, or sequence
number. `sendCommand(Command)` calls `compose()` first.

`fetchDataByte()` allocates a receive buffer and calls `socket.receive()`. The
default helper in `CommandSender` asks for a buffer of `1024` bytes. The actual
datagram length is used when copying the result, so the returned byte array is
trimmed to the received payload. `fetchDataString()` decodes that payload as
UTF-8 without trimming it.

The status helper is intentionally small:

```mermaid
flowchart LR
    A["sendCommandAndFetchStatus(command)"] --> B["send command"]
    B --> C["receive UTF-8 reply"]
    C --> D{"reply equalsIgnoreCase ok?"}
    D -->|yes| E["true"]
    D -->|no| F["false"]

    style E fill:#238636,stroke:#3fb950,color:#fff
    style F fill:#da3633,stroke:#f85149,color:#fff
```

Any reply other than `ok` maps to `false`; the original reply text is not
returned by this helper. A read operation should use
`sendCommandAndFetchData()` instead.

## The `Drone` convenience type

[`Drone.java`](../src/main/java/ch/bissbert/connection/Drone.java) fixes the
host to `192.168.10.1` and the command port to `8889`. Its constructor also
creates a `BasicDroneController` and an `AdvancedDroneController`. At present
both controller classes inherit the same one-operation base:

```java
public void enterSDKMode() throws IOException {
    connection.sendCommand("command");
}
```

Constructing `Drone` connects the local UDP socket, but it does not call
`enterSDKMode()`. The application must send `command` before other SDK
commands, as shown in the [protocol write-up](protocol.md).

The [Tello SDK 2.0 User Guide][sdk] documents the aircraft's command,
state-telemetry, and video interfaces. Tello4J implements only the command
side. It has no UDP server for state packets and no video receiver.

## Important boundaries

| Boundary | What the source does |
|---|---|
| Missing reply | Blocks in `DatagramSocket.receive()` because no timeout is set |
| Closed socket | `close()` closes the socket; a later send can throw `SocketException` |
| Failed command | Status helper returns `false` for any reply other than `ok` |
| Failed setup | Constructor prints the caught exception rather than exposing a connection result |
| Custom endpoint | Use `Connection(host, port)`; `Drone` has only its fixed defaults |
| Telemetry | Not bound, decoded, stored, or exposed |

[sdk]: https://dl-cdn.ryzerobotics.com/downloads/Tello/Tello%20SDK%202.0%20User%20Guide.pdf
