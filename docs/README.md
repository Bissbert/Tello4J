# Documentation

[← back to the overview](../README.md)

The write-ups follow the path from the aircraft-facing UDP socket to the
command vocabulary and the higher-level builders. [`measurement.md`](measurement.md)
records how the repository numbers and the localhost probe results were
produced.

| Subsystem | Source covered | Write-up |
|---|---|---|
| Connection and transport | `Connection`, `Drone`, controllers, `CommandSender` | [`connection.md`](connection.md) |
| Command model and execution | `Command`, `BasicCommand`, `ComplexCommand`, `CommandExecutor`, `Reference` | [`commands.md`](commands.md) |
| SDK command vocabulary | `CommandStrings` and the wire sequence | [`protocol.md`](protocol.md) |
| Measurement method | build, inventory, command table, local UDP probe | [`measurement.md`](measurement.md) |
| Bugs found | current-source defects and unapplied fixes | [`BUGS-FOUND.md`](BUGS-FOUND.md) |

The code has no telemetry or video consumer. The protocol diagrams therefore
show those streams as external responsibilities rather than pretending that
Tello4J handles them.
