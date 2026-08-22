#!/usr/bin/env python3
"""Repair Java imports exposed by semantic-capability package moves.

Two deterministic cases are handled after the audited file migration:
1. references that used to resolve implicitly because two types shared a package;
2. explicit project imports whose source type moved but whose import was not covered
   by the exact migration replacement map.

The repair never guesses between multiple project types with the same simple name.
"""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path
import importlib.util
import re

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "tools" / "semantic-capability-migration.py"
PACKAGE_RE = re.compile(r"(?m)^package\s+([A-Za-z_][\w.]*)\s*;")
IMPORT_LINE_RE = re.compile(r"(?m)^import\s+(static\s+)?([A-Za-z_][\w.]*)\s*;")


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


def explicit_imports(text: str) -> set[str]:
    return {match.group(2) for match in IMPORT_LINE_RE.finditer(text)}


def repair_stale_project_imports(
        text: str,
        project_types: set[str],
        project_types_by_simple: dict[str, list[str]]) -> tuple[str, int]:
    replacements: list[tuple[int, int, str]] = []
    repaired = 0
    for match in IMPORT_LINE_RE.finditer(text):
        if match.group(1) is not None:
            continue
        imported = match.group(2)
        if imported in project_types or not imported.startswith("io.github.evoforge."):
            continue
        candidates = project_types_by_simple.get(simple_name(imported), [])
        if len(candidates) == 1:
            target = candidates[0]
            replacements.append((match.start(2), match.end(2), target))
            repaired += 1
        elif len(candidates) > 1:
            raise RuntimeError(
                f"ambiguous stale project import {imported}: "
                + ", ".join(sorted(candidates))
            )

    for start, end, target in reversed(replacements):
        text = text[:start] + target + text[end:]
    return text, repaired


def add_imports(text: str, imports: set[str]) -> tuple[str, int]:
    if not imports:
        return text, 0
    package_match = PACKAGE_RE.search(text)
    if package_match is None:
        raise RuntimeError("Java source has no package declaration")

    existing = explicit_imports(text)
    missing = sorted(imports - existing)
    if not missing:
        return text, 0

    import_matches = list(IMPORT_LINE_RE.finditer(text))
    if import_matches:
        insert_at = import_matches[-1].end()
        block = "\n" + "\n".join(f"import {fqcn};" for fqcn in missing)
    else:
        insert_at = package_match.end()
        block = "\n\n" + "\n".join(f"import {fqcn};" for fqcn in missing)
    return text[:insert_at] + block + text[insert_at:], len(missing)


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
    project_types: set[str] = set()
    project_types_by_simple: dict[str, list[str]] = defaultdict(list)

    for module in ("simulation", "core"):
        for path in (ROOT / module).rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            current = current_fqcn(path, text)
            if current is None:
                continue
            old = old_by_new.get(current, current)
            sources.append((path, text, current, old))
            peers_by_old_package[package_of(old)].append((simple_name(current), current))
            project_types.add(current)
            project_types_by_simple[simple_name(current)].append(current)

    changed_files = 0
    added_imports = 0
    repaired_imports = 0

    for path, original_text, current, old in sources:
        text, repaired = repair_stale_project_imports(
            original_text, project_types, project_types_by_simple)
        repaired_imports += repaired

        old_package = package_of(old)
        current_package = package_of(current)
        own_name = simple_name(current)
        imports_now = explicit_imports(text)
        imported_by_simple = {
            simple_name(imported): imported
            for imported in imports_now
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

        text, added = add_imports(text, required)
        added_imports += added
        if text != original_text:
            path.write_text(text, encoding="utf-8")
            changed_files += 1

    print(
        "semantic-capability import repair: "
        f"{repaired_imports} stale imports repaired, "
        f"{added_imports} implicit imports added across {changed_files} files"
    )


if __name__ == "__main__":
    main()
