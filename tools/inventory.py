#!/usr/bin/env python3
"""Counts what is actually in the repository: sources, lines, bytes, and the
build output if the project has been built.

    python3 tools/inventory.py
"""

import os
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "src", "main", "java")
CLASSES = os.path.join(REPO, "target", "classes")

# Which write-up covers which source file.
DOCS = {
    "connection/Connection.java":       "docs/connection.md",
    "connection/CommandSender.java":    "docs/connection.md",
    "connection/Drone.java":            "docs/connection.md",
    "connection/exception/NoConnectionException.java": "docs/connection.md",
    "connection/controller/DroneController.java":      "docs/connection.md",
    "connection/controller/BasicDroneController.java": "docs/connection.md",
    "connection/controller/AdvancedDroneController.java": "docs/connection.md",
    "CommandStrings.java":              "docs/protocol.md",
    "command/model/Command.java":       "docs/commands.md",
    "command/model/AbstractCommand.java": "docs/commands.md",
    "command/model/BasicCommand.java":  "docs/commands.md",
    "command/model/ComplexCommand.java": "docs/commands.md",
    "command/creator/CommandExecutor.java": "docs/commands.md",
    "command/creator/Reference.java":   "docs/commands.md",
}


def java_files():
    out = []
    for root, _, files in os.walk(SRC):
        for f in sorted(files):
            if f.endswith(".java"):
                full = os.path.join(root, f)
                out.append((os.path.relpath(full, os.path.join(SRC, "ch", "bissbert")), full))
    return sorted(out)


def main():
    files = java_files()
    if not files:
        print("no sources found under " + SRC, file=sys.stderr)
        return 1

    total_lines = total_bytes = 0
    rows = []
    for rel, full in files:
        data = open(full, "rb").read()
        lines = data.decode("utf-8").count("\n")
        rows.append((rel, lines, len(data)))
        total_lines += lines
        total_bytes += len(data)

    width = max(len(r[0]) for r in rows)
    print("Sources under src/main/java/ch/bissbert/")
    print()
    print(f"{'file':<{width}}  {'lines':>6}  {'bytes':>6}  write-up")
    print("-" * (width + 30))
    for rel, lines, size in rows:
        print(f"{rel:<{width}}  {lines:>6}  {size:>6,}  {DOCS.get(rel, '-')}")
    print("-" * (width + 30))
    print(f"{'total (' + str(len(rows)) + ' files)':<{width}}  {total_lines:>6}  {total_bytes:>6,}")
    print()

    print("Markdown table:")
    print()
    print("| Source | Lines | Bytes | Write-up |")
    print("|---|---:|---:|---|")
    for rel, lines, size in rows:
        doc = DOCS.get(rel)
        link = f"[→]({doc})" if doc else "-"
        print(f"| `{rel}` | {lines} | {size:,} | {link} |")
    print(f"| **total** | **{total_lines}** | **{total_bytes:,}** | |")
    print()

    if os.path.isdir(CLASSES):
        classes = []
        for root, _, fs in os.walk(CLASSES):
            classes += [f for f in fs if f.endswith(".class")]
        print(f"Build output: {len(classes)} .class files in target/classes")
        jars = [f for f in os.listdir(os.path.join(REPO, "target"))
                if f.endswith(".jar")] if os.path.isdir(os.path.join(REPO, "target")) else []
        for j in jars:
            size = os.path.getsize(os.path.join(REPO, "target", j))
            print(f"              target/{j}  {size:,} bytes")
    else:
        print("Build output: not present (run 'mvn -B package')")

    print()
    try:
        v = subprocess.run(["javac", "-version"], capture_output=True, text=True)
        print("javac: " + (v.stdout or v.stderr).strip())
    except OSError:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
