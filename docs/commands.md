[← back to the overview](../README.md)

# Command model and execution

The command layer separates command text from the socket that sends it. The
source is under [`command/model`](../src/main/java/ch/bissbert/command/model/)
and [`command/creator`](../src/main/java/ch/bissbert/command/creator/).

## Composition path

```mermaid
flowchart LR
    A["Application"] --> E["CommandExecutor.execute(...)"]
    A --> B["BasicCommand"]
    A --> X["ComplexCommand"]
    S["CommandStrings"] --> B
    S --> X
    B -->|"compose() = literal"| C["CommandSender"]
    X -->|"compose() = literal + params"| C
    E --> B
    E --> X
    C --> U["UDP command socket"]

    style S fill:#8250df,stroke:#bc8cff,color:#fff
    style C fill:#1f6feb,stroke:#58a6ff,color:#fff
    style U fill:#238636,stroke:#3fb950,color:#fff
```

`AbstractCommand` stores the wire text and a boolean read flag. `BasicCommand`
returns that text unchanged. `ComplexCommand` is intended to append parameters
with spaces, but its `parameters` field is declared and never initialized. As
a result, calling `addParam()` currently throws before a parameterized command
can be composed. The local probe in `tools/` records this as an observed
behavior; it is documented here rather than hidden behind a workaround.

## `CommandExecutor`

`CommandExecutor.execute(CommandStrings)` and
`CommandExecutor.execute(String)` each create a basic executor. Calling
`withParam()` replaces that path with a complex executor and adds the first
parameter. `withReadAndSaveIn(Reference<String>)` marks the command as a read
and stores the eventual reply in the supplied reference.

The intended run path is:

```mermaid
sequenceDiagram
    participant App as Application
    participant Exec as CommandExecutor
    participant Cmd as Command
    participant Conn as Connection

    App->>Exec: execute(command)
    App->>Exec: createConnection()
    App->>Exec: run()
    Exec->>Cmd: isRead()
    alt read command
        Exec->>Conn: sendCommandAndFetchData(command)
        Conn-->>Exec: reply text
        Exec->>App: Reference.setReference(reply)
    else write command
        Exec->>Conn: sendCommand(command)
    end
    Exec->>Exec: after.run()
```

The last call is unconditional. `andThen()` stores only one successor, so a
tail executor with no successor throws after its own send/read operation. The
executor also uses one static connection shared by all executor instances.
For predictable behavior, the direct `Connection` API is easier to audit.

## Class responsibilities

| Type | Responsibility |
|---|---|
| `Command` | Compose wire text and expose the read flag |
| `AbstractCommand` | Store command text and read state |
| `BasicCommand` | Return one literal command |
| `ComplexCommand` | Append object parameters to a command |
| `CommandExecutor` | Send commands and optionally save a read reply |
| `Reference<T>` | Mutable holder for a saved reply |

The command model does not validate that a command accepts a parameter, that a
parameter has the right type, or that a value is safe for a real aircraft.
