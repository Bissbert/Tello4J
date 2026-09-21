#!/usr/bin/env python3
"""Drives the Tello4J library against a local UDP stub and reports what the
library actually puts on the wire.

This is NOT a drone. The stub answers on 127.0.0.1 with the shape of reply the
Tello SDK specifies ("ok", "error", a payload line) so that the library's own
send/receive/parse path can be exercised end to end. Every reply is synthetic
and is labelled as such; no telemetry is claimed.

Requires: python3 (stdlib only), javac + java, and a built library. It builds
the library with Maven if target/classes is missing.

    python3 tools/protocol_probe.py
"""

import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLASSES = os.path.join(REPO, "target", "classes")
PROBE_SRC = os.path.join(REPO, "tools", "ProtocolProbe.java")
PROBE_CLASS = "ch.bissbert.command.creator.ProtocolProbe"

# What the stub answers, keyed by the command text it receives.
# "ok" / "error" are the two status words the Tello SDK defines. "stub-reply"
# is deliberately not a plausible telemetry value.
REPLIES = {
    "command": b"ok",
    "takeoff": b"ok",
    "provoke-error": b"error",
    "battery?": b"stub-reply",
    "stay-silent": None,          # never answered, on purpose
}
DEFAULT_REPLY = b"ok"

CHECK_LABELS = {
    "construct":            "Connection(host, port) constructs and connects",
    "send-plain":           "sendCommand() emits the bare command as one datagram",
    "status-ok":            '"ok" reply maps to sendCommandAndFetchStatus() == true',
    "status-error":         "any other reply maps to false",
    "read-payload":         "a read command returns the reply payload verbatim",
    "compose-basic":        'BasicCommand("up").compose() == "up"',
    "compose-enum":         "CommandStrings-backed command composes to its SDK word",
    "compose-wifi":         'SET_WIFI composes to "wifi ssid", placeholder included',
    "complex-addparam":     "ComplexCommand.addParam() throws NullPointerException",
    "executor-no-connection": "CommandExecutor.run() throws NPE without createConnection()",
    "executor-chain-tail":  "CommandExecutor.run() throws NPE at the end of a chain",
    "send-after-close":     "sendCommand() after close() throws",
    "no-so-timeout":        "fetchDataString() never times out when nothing replies",
}


def find_log4j():
    home = os.path.expanduser("~")
    jar = os.path.join(home, ".m2", "repository", "log4j", "log4j",
                       "1.2.17", "log4j-1.2.17.jar")
    return jar if os.path.exists(jar) else None


def ensure_built():
    if os.path.isdir(CLASSES):
        return
    print("target/classes missing - running: mvn -B -q clean package")
    subprocess.run(["mvn", "-B", "-q", "clean", "package"], cwd=REPO, check=True)


class Stub(threading.Thread):
    """A single UDP socket that answers like the SDK's command port."""

    def __init__(self):
        super().__init__(daemon=True)
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("127.0.0.1", 0))
        self.port = self.sock.getsockname()[1]
        self.received = []
        self._halt = threading.Event()

    def run(self):
        self.sock.settimeout(0.25)
        while not self._halt.is_set():
            try:
                data, addr = self.sock.recvfrom(2048)
            except socket.timeout:
                continue
            except OSError:
                break
            text = data.decode("utf-8", "replace")
            self.received.append(data)
            reply = REPLIES.get(text, DEFAULT_REPLY)
            if reply is not None:
                try:
                    self.sock.sendto(reply, addr)
                except OSError:
                    pass

    def shutdown(self):
        self._halt.set()
        self.join(timeout=2)
        self.sock.close()


def main():
    ensure_built()
    log4j = find_log4j()
    if not log4j:
        print("log4j-1.2.17.jar not found in ~/.m2 - run 'mvn -B package' first",
              file=sys.stderr)
        return 2

    outdir = tempfile.mkdtemp(prefix="tello4j-probe-")
    try:
        cp = os.pathsep.join([CLASSES, log4j])
        subprocess.run(["javac", "-cp", cp, "-d", outdir, PROBE_SRC], check=True)

        stub = Stub()
        stub.start()

        run = subprocess.run(
            ["java", "-cp", os.pathsep.join([outdir, cp]), PROBE_CLASS, str(stub.port)],
            capture_output=True, text=True, timeout=120)
        stub.shutdown()
    finally:
        shutil.rmtree(outdir, ignore_errors=True)

    checks = []
    for line in run.stdout.splitlines():
        if line.startswith("CHECK|"):
            _, cid, verdict, detail = line.split("|", 3)
            checks.append((cid, verdict, detail))

    if not checks:
        print("probe produced no checks", file=sys.stderr)
        print(run.stdout, run.stderr, file=sys.stderr)
        return 1

    width = max(len(CHECK_LABELS.get(c, c)) for c, _, _ in checks)
    print()
    print("Library behaviour against the local UDP stub")
    print("=" * (width + 40))
    for cid, verdict, detail in checks:
        print(f"{CHECK_LABELS.get(cid, cid):<{width}}  {verdict:<4}  {detail}")

    print()
    print("Datagrams the stub received, in order:")
    for i, data in enumerate(stub.received, 1):
        print(f"  {i:>2}. {len(data):>2} bytes  {data!r}")

    passed = sum(1 for _, v, _ in checks if v == "PASS")
    print()
    print(f"{passed}/{len(checks)} checks passed")

    print()
    print("Markdown table:")
    print()
    print("| Check | Result | Observed |")
    print("|---|:--:|---|")
    for cid, verdict, detail in checks:
        mark = "yes" if verdict == "PASS" else "no"
        print(f"| {CHECK_LABELS.get(cid, cid)} | {mark} | `{detail}` |")

    return 0 if passed == len(checks) else 1


if __name__ == "__main__":
    sys.exit(main())
