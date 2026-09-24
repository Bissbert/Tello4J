# Tello4J

Tello4J is a small Java wrapper around the Ryze Tello SDK's UDP command
protocol. It opens a datagram connection to the Tello's default command
endpoint, turns enum values and command objects into wire text, and exposes
helpers for sending commands and reading replies. It is useful as a thin
starting point for a Java flight program; it is not a complete flight-control,
telemetry, or video SDK.

```mermaid
sequenceDiagram
    participant App as Java application
    participant Lib as Tello4J
    participant T as Ryze Tello
    participant State as External state listener

    App->>Lib: new Drone()
    Lib->>Lib: resolve host, connect DatagramSocket
    App->>Lib: send "command"
    Lib->>T: UDP command datagram
    T-->>Lib: "ok" or "error"
    App->>Lib: send "takeoff"
    Lib->>T: UDP command datagram
    T-->>Lib: "ok" or "error"
    App->>Lib: send movement command
    Lib->>T: UDP command datagram
    T-->>Lib: "ok" or "error"
    T-->>State: state telemetry datagrams
    Note over Lib,State: Tello4J does not bind or parse the state stream
    App->>Lib: send "land"
    Lib->>T: UDP command datagram
    T-->>Lib: "ok" or "error"
    App->>Lib: close()
```

## Quick start

Build the library from the repository root:

```sh
mvn -B clean package
```

The build also runs the JUnit suite. To run only the tests in a Linux
container, with no JDK on the host:

```sh
sh tools/docker-test.sh
```

The tests use a fake drone on `127.0.0.1`; no aircraft is needed. This build runs in the Linux container used for the
[measurements](docs/measurement.md). To fly, put the built jar and its Log4j dependency on your application's
classpath, then use the smallest direct API path:

```java
import ch.bissbert.CommandStrings;
import ch.bissbert.connection.Drone;

import java.io.IOException;

public final class QuickStart {
    private static void requireOk(Drone drone, String command) throws IOException {
        if (!drone.sendCommandAndFetchStatus(command)) {
            throw new IOException("Tello rejected: " + command);
        }
    }

    public static void main(String[] args) throws IOException {
        try (Drone drone = new Drone()) {
            requireOk(drone, CommandStrings.COMMAND.getCommand());
            requireOk(drone, CommandStrings.TAKE_OFF.getCommand());
            requireOk(drone, CommandStrings.LAND.getCommand());
        }
    }
}
```

This flight example was not run: this environment has no Tello aircraft. The
class opens the endpoint hard-coded by `Drone`, sends `command`, then sends
takeoff and land. Test only with a charged aircraft, a clear area, and a
deliberate safety plan.

## Architecture

```mermaid
flowchart TD
    A["Application"] --> D["Drone"]
    D --> C["Connection<br/>DatagramSocket"]
    D --> BC["BasicDroneController"]
    D --> AC["AdvancedDroneController"]
    BC --> C
    AC --> C

    E["CommandExecutor"] --> M["Command model"]
    M --> B["BasicCommand"]
    M --> X["ComplexCommand"]
    B --> C
    X --> C
    S["CommandStrings"] --> B
    S --> X

    C -->|"UDP command port"| T["Tello"]
    T -->|"reply datagram"| C
    T -.->|"state packets<br/>not consumed"| U["External listener"]

    style C fill:#1f6feb,stroke:#58a6ff,color:#fff
    style M fill:#8250df,stroke:#bc8cff,color:#fff
    style T fill:#238636,stroke:#3fb950,color:#fff
    style U fill:#9e6a03,stroke:#d29922,color:#fff
```

`Drone` is a convenience subclass of `Connection`. Its source constants point
to `192.168.10.1` on UDP port `8889`, the Tello command endpoint. Constructing
the object creates and connects a Java `DatagramSocket`; it does not send the
SDK-mode command automatically. `DroneController.enterSDKMode()` is the one
controller operation currently implemented.

The official [Tello SDK 2.0 User Guide][sdk] describes the separate state and
video UDP interfaces. Tello4J currently models neither listener, parser, nor
frame decoder.

The library sends bare UTF-8 command text. `sendCommandAndFetchStatus()` treats
only a case-insensitive `ok` reply as success; a read command returns the
received UTF-8 payload instead.

## Capabilities

| Area | Present | Boundary |
|---|---|---|
| Command transport | UDP datagrams and UTF-8 text | Fixed default endpoint in `Drone` |
| SDK mode | `command` via controller or direct send | No automatic handshake |
| Control commands | Enum values for takeoff, land, stream, emergency | Caller sequences and checks replies |
| Movement commands | Enum values and arbitrary command parameters | No range or arity validation |
| Read commands | `sendCommandAndFetchData()` and status helper | One receive, 15 s timeout by default (`SocketTimeoutException`, no retry) |
| Telemetry | No listener or state model | Use a separate UDP listener |
| Video | `streamon` / `streamoff` wire strings | No video receiver or decoder |
| Command chain | `CommandExecutor` and `Reference<T>` | Needs `createConnection()` before `run()`; one shared static connection |

## Results

These values come from `tools/linux-run.sh`, which builds the library and runs
the scripts in a Linux container. See [`docs/measurement.md`](docs/measurement.md).

| Check | Result |
|---|---:|
| `mvn -B clean package` | exit `0` |
| JUnit tests | `39` run, `0` failures, `0` errors |
| Java sources under `src/main/java` | `14` files, `553` lines, `14,876` bytes |
| Build output | `16` class files; jar `16,243` bytes |
| `CommandStrings` entries parsed | `18` |
| Local UDP-stub probe | `15/15` checks passed |
| No-reply receive probe | `SocketTimeoutException` after `512` ms with a `500` ms timeout |
| Java compiler | `javac 11.0.32` (Temurin, Linux aarch64) |

The probe uses synthetic localhost replies such as `ok`, `error`, and
`stub-reply`. It does not measure flight, radio range, telemetry, battery,
latency to an aircraft, or video.

## Repository layout

```text
src/main/java/ch/bissbert/
├── CommandStrings.java          wire command vocabulary
├── command/model/               command objects and composition
├── command/creator/             command execution and references
└── connection/                  UDP transport, drone, controllers
docs/                            subsystem write-ups and measurement method
tools/                           inventory, table generator, local probe, Linux run
```

Start with [`docs/README.md`](docs/README.md) for the write-ups. The command
table in [`docs/protocol.md`](docs/protocol.md) is emitted by
`python3 tools/command_table.py` from `CommandStrings.java`.

## Known limitations

- The repository has no telemetry listener, telemetry parser, video receiver,
  or frame decoder.
- A read waits 15 seconds for a reply by default, then throws
  `SocketTimeoutException`. Nothing is retried. Choose another value with
  `new Connection(host, port, timeoutMs)` or `setReceiveTimeout()`; `Drone`
  uses the default.
- A failed `Connection` setup throws `UncheckedIOException` with the original
  `UnknownHostException` or `SocketException` as its cause.
- `Drone` has no constructor for a custom host or port. Tests and alternate
  Tello network layouts must use `Connection` directly.
- No flight program was run against a real aircraft. The diagrams are protocol
  documentation, not a flight recording.

Three earlier bugs are fixed: `ComplexCommand` parameters, the null successor
at the end of an executor chain, and the send-after-close guard. See
[`docs/BUGS-FOUND.md`](docs/BUGS-FOUND.md).

[sdk]: https://dl-cdn.ryzerobotics.com/downloads/Tello/Tello%20SDK%202.0%20User%20Guide.pdf
