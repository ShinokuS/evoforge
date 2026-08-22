#!/usr/bin/env python3
"""One-shot EvoForge semantic-capability package migration.

Generated from the audited exact source-tree delta between PR #132's pre-migration
snapshot and the accepted semantic-capability target. It performs only deterministic
file moves and Java namespace rewrites.
"""
from pathlib import Path
import base64
import json
import re
import shutil
import zlib

ROOT = Path(__file__).resolve().parents[1]
PAIRS = json.loads(zlib.decompress(base64.b64decode("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")).decode())
MANUAL = [("simulation/src/main/java/io/github/evoforge/simulation/control/core/CommandResult.java", "simulation/src/main/java/io/github/evoforge/simulation/kernel/command/CommandResult.java")]

def fqcn(path: str) -> str:
    return path.split("/java/", 1)[1][:-5].replace("/", ".")

def main() -> None:
    all_pairs = PAIRS + MANUAL
    if all(not (ROOT / old).exists() and (ROOT / new).exists() for old, new in all_pairs):
        print("semantic-capability migration already applied")
        return
    temp_root = ROOT / ".semantic-migration-tmp"
    if temp_root.exists():
        shutil.rmtree(temp_root)
    staged = []
    for old, new in all_pairs:
        source = ROOT / old
        destination = ROOT / new
        if not source.exists():
            raise RuntimeError(f"missing migration source: {old}")
        temporary = temp_root / old
        temporary.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(source, temporary)
        staged.append((temporary, destination, old, new))
    for temporary, destination, old, new in staged:
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            raise RuntimeError(f"migration destination already exists: {new}")
        shutil.move(temporary, destination)
    shutil.rmtree(temp_root, ignore_errors=True)
    fqn_moves = {}
    for old, new in all_pairs:
        if old.endswith(".java") and new.endswith(".java") and "/java/" in old and "/java/" in new:
            fqn_moves[fqcn(old)] = fqcn(new)
    replacements = sorted(fqn_moves.items(), key=lambda item: len(item[0]), reverse=True)
    for module in ("simulation", "core"):
        for path in (ROOT / module).rglob("*.java"):
            text = path.read_text()
            updated = text
            for old, new in replacements:
                updated = updated.replace(old, new)
            normalized = path.as_posix()
            marker = None
            for candidate in ("/src/main/java/", "/src/test/java/"):
                if candidate in normalized:
                    marker = candidate
                    break
            if marker is not None:
                relative = normalized.split(marker, 1)[1]
                package_name = ".".join(relative.split("/")[:-1])
                updated = re.sub(r"^package\s+[\w.]+;", f"package {package_name};", updated, count=1, flags=re.MULTILINE)
            if updated != text:
                path.write_text(updated)
    for old, new in all_pairs:
        if (ROOT / old).exists():
            raise RuntimeError(f"old path survived migration: {old}")
        if not (ROOT / new).exists():
            raise RuntimeError(f"new path missing after migration: {new}")
    for path in sorted(ROOT.rglob("*"), reverse=True):
        if path.is_dir():
            try:
                path.rmdir()
            except OSError:
                pass
    print(f"semantic-capability migration applied: {len(all_pairs)} moves")

if __name__ == "__main__":
    main()
