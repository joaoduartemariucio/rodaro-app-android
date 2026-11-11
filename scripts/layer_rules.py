#!/usr/bin/env python3
"""Fails when a source file crosses a layer boundary.

Mirrors the custom lint rules of the iOS app: Domain is plain Kotlin and Presentation never
touches the Data layer. Comments and string literals are ignored. Every rule is an error.
"""

import pathlib
import re
import sys

SOURCE = pathlib.Path("app/src/main/java/br/com/joaoduartemariucio/rodaro")

RULES = [
    (
        "domain_no_android",
        "domain/**/*.kt",
        r"^\s*import\s+(android|androidx|com\.google\.android)\.",
        "Domain is plain Kotlin. A type that needs Android or Jetpack belongs in Presentation or Data.",
    ),
    (
        "domain_no_outer_layers",
        "domain/**/*.kt",
        r"^\s*import\s+br\.com\.joaoduartemariucio\.rodaro\.(data|presentation|app)\.",
        "Domain depends on nothing. Move the shared type into Domain or invert the dependency.",
    ),
    (
        "presentation_no_data_types",
        "presentation/**/*.kt",
        r"(\bimport\s+br\.com\.joaoduartemariucio\.rodaro\.data\.|"
        r"\b(VehicleModel|FuelEntryModel|ServiceEntryModel|ServiceRuleModel|TripRouteModel|RodaroDatabase|VehicleDao|FuelEntryDao|"
        r"ServiceDao|TripRouteDao|BackupDao|BackupTables|RoomDatabase|Room[A-Za-z]+Repository|WorkManager[A-Za-z]*)\b)",
        "Presentation talks to Domain interfaces only. This type belongs to the Data layer.",
    ),
]


def strip(line, in_block):
    code = []
    i = 0
    while i < len(line):
        if in_block:
            end = line.find("*/", i)
            if end == -1:
                return "".join(code), True
            i, in_block = end + 2, False
        elif line.startswith("/*", i):
            in_block, i = True, i + 2
        elif line.startswith("//", i):
            break
        elif line[i] == '"':
            end = line.find('"', i + 1)
            while end != -1 and line[end - 1] == "\\":
                end = line.find('"', end + 1)
            code.append('""')
            i = len(line) if end == -1 else end + 1
        else:
            code.append(line[i])
            i += 1
    return "".join(code), in_block


def main():
    violations = 0
    for name, pattern, regex, message in RULES:
        expression = re.compile(regex)
        for path in sorted(SOURCE.glob(pattern)):
            in_block = False
            for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
                code, in_block = strip(raw, in_block)
                if expression.search(code):
                    violations += 1
                    print(f"{path}:{number}: error: {message} ({name})")
    if violations:
        sys.exit(f"{violations} layer violation(s).")
    print("Layer rules: no violations.")


if __name__ == "__main__":
    main()
