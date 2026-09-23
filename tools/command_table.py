#!/usr/bin/env python3
"""Generate the command-surface table from CommandStrings.java.

The enum constants, wire text and source comments are parsed from the source.
The small role map is documentation metadata; the repository does not validate
command arity, ranges or replies.

    python3 tools/command_table.py
"""

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "src", "main", "java", "ch", "bissbert", "CommandStrings.java")

# Documentation roles keyed by the literal command word in the enum.
ROLES = {
    "command": "enter SDK mode",
    "takeoff": "control",
    "land": "control",
    "streamon": "video control",
    "streamoff": "video control",
    "emergency": "control",
    "up": "movement",
    "down": "movement",
    "left": "movement",
    "right": "movement",
    "forward": "movement",
    "back": "movement",
    "cw": "movement",
    "ccw": "movement",
    "go": "movement",
    "speed": "set value",
    "wifi ssid": "set value",
    "battery?": "read value",
}

# CONSTANT("literal"), optionally preceded by a /** ... */ block.
ENTRY = re.compile(
    r'(?:/\*\*(?P<doc>.*?)\*/\s*)?'
    r'^\s*(?P<name>[A-Z][A-Z0-9_]*)\s*\(\s*"(?P<cmd>[^"]*)"\s*\)\s*[,;]',
    re.S | re.M)


def clean_doc(raw):
    if not raw:
        return ""
    lines = []
    for line in raw.splitlines():
        line = line.strip().lstrip("*").strip()
        if line:
            lines.append(line)
    return " ".join(lines)


def parse():
    text = open(SRC, encoding="utf-8").read()
    # Only the enum body, i.e. everything before the first field declaration.
    body = text.split("String command;")[0]
    out = []
    for m in ENTRY.finditer(body):
        out.append({
            "name": m.group("name"),
            "cmd": m.group("cmd"),
            "doc": clean_doc(m.group("doc")),
        })
    return out


def main():
    entries = parse()
    if not entries:
        print("no enum constants parsed from " + SRC, file=sys.stderr)
        return 1

    missing = [e["cmd"] for e in entries if e["cmd"] not in ROLES]
    if missing:
        print("warning: no role entry for: " + ", ".join(missing),
              file=sys.stderr)

    print(f"{len(entries)} constants parsed from "
          f"src/main/java/ch/bissbert/CommandStrings.java")
    print()

    print("| Constant | Wire text | Role | Source comment |")
    print("|---|---|---|---|")
    for e in entries:
        role = ROLES.get(e["cmd"], "unclassified")
        comment = e["doc"].replace("|", "\\|")
        print(f"| `{e['name']}` | `{e['cmd']}` | {role} | {comment} |")
    print()
    print("The enum stores the wire prefix only. `ComplexCommand` appends "
          "caller-supplied parameters; no validation is performed here.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
