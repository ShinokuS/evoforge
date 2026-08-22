#!/usr/bin/env python3
"""Repair imports that were implicit before semantic-capability package moves.

The migration moves Java types and rewrites fully-qualified names. Java source files
that previously referenced a peer from the same package did not need an import;
after the two peers move to different semantic packages, that implicit reference is
lost. This pass reconstructs the old package membership from the migration map and
adds only those imports that became necessary because of the move.
"""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path
import importlib.util
import re

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "tools" / "semantic-capability-migration.py"
PACKAGE_RE = re.compile(r"(?m)^package\s+([A-Za-z_][\w.]*)\s*;")
IMPORT_RE = re.compile(r"(?m)^import\s+(?:static\s+)?([A-Za-z_][\w.]*)\s*;")


def load_pairs() -> list[tuple[str, str]]:
    spec = importlib.util.spec_from_file_location("semantic_capability_migration", MIGRATION)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load semantic capability migration")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return list(module.PAIRS) + list(module.MANUAL)


def fqcn_from_path(path: str) -> str:
    return path.split("/java/", 1)[1][:-5].replace("/", ".")


def package_of(fqcn: str) -> str:
    return fqcn.rsplit(".", 1)[0]


def simple_name(fqcn: str) -> str:
    return fqcn.rsplit(".", 1)[1]


def current_fqcn(path: Path, text: str) -> str | None:
    match = PACKAGE_RE.search(text)
    if match is None:
        return None
    return f"{match.group(1)}.{path.stem}"


def add_imports(text: str, imports: set[str]) -> str:
    if not imports:
        return text
    package_match = PACKAGE_RE.search(text)
    if package_match is None:
        raise RuntimeError("Java source has no package declaration")

    existing = set(IMPORT_RE.findall(text))
    missing = sorted(imports - existing)
    if not missing:
        return text

    import_matches = list(IMPORT_RE.finditer(text))
    if import_matches:
        insert_at = import_matches[-1].end()
        block = "\n" + "\n".join(f"import {fqcn};" for fqcn in missing)
    else:
        insert_at = package_match.end()
        block = "\n\n" + "\n".join(f"import {fqcn};" for fqcn in missing)
    return text[:insert_at] + block + text[insert_at:]


def main() -> None:
    pairs = [
        (fqcn_from_path(old), fqcn_from_path(new))
        for old, new in load_pairs()
        if old.endswith(".java")
        and new.endswith(".java")
        and "/java/" in old
        and "/java/" in new
    ]
    old_by_new = {new: old for old, new in pairs}

    sources: list[tuple[Path, str, str, str]] = []
    peers_by_old_package: dict[str, list[tuple[str, str]]] = defaultdict(list)

    for module in ("simulation", "core"):
        for path in (ROOT / module).rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            current = current_fqcn(path, text)
            if current is None:
                continue
            old = old_by_new.get(current, current)
            sources.append((path, text, current, old))
            peers_by_old_package[package_of(old)].append((simple_name(current), current))

    changed = 0
    added = 0
    for path, text, current, old in sources:
        old_package = package_of(old)
        current_package = package_of(current)
        own_name = simple_name(current)
        explicit_imports = set(IMPORT_RE.findall(text))
        imported_by_simple = {
            simple_name(imported): imported
            for imported in explicit_imports
            if not imported.endswith(".*")
        }
        required: set[str] = set()

        for name, target in peers_by_old_package.get(old_package, ()): 
            if name == own_name or package_of(target) == current_package:
                continue
            if re.search(rf"\b{re.escape(name)}\b", text) is None:
                continue
            conflicting = imported_by_simple.get(name)
            if conflicting is not None and conflicting != target:
                raise RuntimeError(
                    f"ambiguous import repair in {path.relative_to(ROOT)}: "
                    f"{name} already imports {conflicting}, migration needs {target}"
                )
            required.add(target)

        updated = add_imports(text, required)
        if updated != text:
            path.write_text(updated, encoding="utf-8")
            changed += 1
            added += len(required - explicit_imports)

    print(f"semantic-capability import repair: {added} imports across {changed} files")


if __name__ == "__main__":
    main()
