#!/usr/bin/env python3
"""Finish the semantic-capability move and remove transitional package debt."""
from __future__ import annotations

from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]

MOVES = {
    "simulation/src/main/java/io/github/evoforge/simulation/world/mechanics/occupancy/OccupancyLookup.java":
        "simulation/src/main/java/io/github/evoforge/simulation/world/space/occupancy/OccupancyLookup.java",
    "simulation/src/main/java/io/github/evoforge/simulation/world/mechanics/geometry/ShapeTraversalFactor.java":
        "simulation/src/main/java/io/github/evoforge/simulation/world/geometry/ShapeTraversalFactor.java",
    "simulation/src/main/java/io/github/evoforge/simulation/world/mechanics/geometry/ShapeTraversalLowerBoundLookup.java":
        "simulation/src/main/java/io/github/evoforge/simulation/world/geometry/ShapeTraversalLowerBoundLookup.java",
    "simulation/src/main/java/io/github/evoforge/simulation/world/mechanics/traversal/LandscapeTraversalDefinitionCompiler.java":
        "simulation/src/main/java/io/github/evoforge/simulation/world/navigation/traversal/LandscapeTraversalDefinitionCompiler.java",
    "simulation/src/main/java/io/github/evoforge/simulation/world/mechanics/traversal/LandscapeTraversalDefinitions.java":
        "simulation/src/main/java/io/github/evoforge/simulation/world/navigation/traversal/LandscapeTraversalDefinitions.java",
    "simulation/src/test/java/io/github/evoforge/simulation/world/mechanics/geometry/GeometryTraversalLowerBoundTest.java":
        "simulation/src/test/java/io/github/evoforge/simulation/world/geometry/GeometryTraversalLowerBoundTest.java",
    "simulation/src/test/java/io/github/evoforge/simulation/world/mechanics/traversal/LandscapeTraversalDefinitionCompilerTest.java":
        "simulation/src/test/java/io/github/evoforge/simulation/world/navigation/traversal/LandscapeTraversalDefinitionCompilerTest.java",
    "simulation/src/test/java/io/github/evoforge/simulation/control/core/ControlDependencyContractTest.java":
        "simulation/src/test/java/io/github/evoforge/simulation/kernel/command/ControlDependencyContractTest.java",
}

REPLACEMENTS = {
    "io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyLookup":
        "io.github.evoforge.simulation.world.space.occupancy.OccupancyLookup",
    "io.github.evoforge.simulation.world.mechanics.geometry.ShapeTraversalFactor":
        "io.github.evoforge.simulation.world.geometry.ShapeTraversalFactor",
    "io.github.evoforge.simulation.world.mechanics.geometry.ShapeTraversalLowerBoundLookup":
        "io.github.evoforge.simulation.world.geometry.ShapeTraversalLowerBoundLookup",
    "io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitionCompiler":
        "io.github.evoforge.simulation.world.navigation.traversal.LandscapeTraversalDefinitionCompiler",
    "io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions":
        "io.github.evoforge.simulation.world.navigation.traversal.LandscapeTraversalDefinitions",
    "package io.github.evoforge.simulation.world.mechanics.occupancy;":
        "package io.github.evoforge.simulation.world.space.occupancy;",
    "package io.github.evoforge.simulation.world.mechanics.geometry;":
        "package io.github.evoforge.simulation.world.geometry;",
    "package io.github.evoforge.simulation.world.mechanics.traversal;":
        "package io.github.evoforge.simulation.world.navigation.traversal;",
    "package io.github.evoforge.simulation.control.core;":
        "package io.github.evoforge.simulation.kernel.command;",
}

DOC_REPLACEMENTS = {
    "simulation/.../world/mechanics/geometry/": "simulation/.../world/geometry/",
    "simulation/.../world/mechanics/occupancy/": "simulation/.../world/space/occupancy/",
    "simulation/.../world/mechanics/traversal/": "simulation/.../world/navigation/traversal/",
    "simulation/.../world/pathfinding/": "simulation/.../world/navigation/pathfinding/",
    "simulation/.../world/agent/": "simulation/.../agents/",
}

CONTROL_TEST = '''package io.github.evoforge.simulation.kernel.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ControlDependencyContractTest {

    private static final String WORLD_IMPORT =
            "import io.github.evoforge.simulation.world.";
    private static final String LEGACY_CONTROL_IMPORT =
            "import io.github.evoforge.simulation.control.";

    @Test
    void genericCommandInfrastructureDoesNotDependOnWorldDomains()
            throws IOException {

        assertNoImport(
                mainJava().resolve(
                        "io/github/evoforge/simulation/kernel/command"),
                WORLD_IMPORT);
    }

    @Test
    void worldDomainsDoNotDependOnLegacyControlPackages()
            throws IOException {

        assertNoImport(
                mainJava().resolve(
                        "io/github/evoforge/simulation/world"),
                LEGACY_CONTROL_IMPORT);
    }

    @Test
    void moveToOrchestrationDoesNotBypassMovementExecutionBoundary()
            throws IOException {

        Path source = uniqueSource(mainJava(), "MoveToSystem.java");
        String text = Files.readString(source);

        // MoveTo may consume read-only transform/pathfinding capabilities, but
        // authoritative physical mutation must still go through MovementSystem.
        assertFalse(text.matches("(?s).*import\\\\s+[^;]*\\\\.SpatialSystem;.*"));
        assertFalse(text.matches("(?s).*import\\\\s+[^;]*\\\\.OccupancySystem;.*"));
        assertFalse(text.matches("(?s).*import\\\\s+[^;]*\\\\.NavigationSystem;.*"));
    }

    private static Path uniqueSource(Path root, String fileName)
            throws IOException {

        try (var paths = Files.walk(root)) {
            List<Path> matches = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .toList();
            assertEquals(1, matches.size(), "expected one source named " + fileName);
            return matches.getFirst();
        }
    }

    private static void assertNoImport(
            Path root,
            String forbiddenImport)
            throws IOException {

        assertTrue(Files.isDirectory(root), "missing source directory: " + root);

        try (var paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile);
                assertFalse(
                        source.contains(forbiddenImport),
                        () -> javaFile + " contains forbidden dependency " + forbiddenImport);
            }
        }
    }

    private static Path mainJava() {
        Path moduleLocal = Path.of("src/main/java");
        if (Files.isDirectory(moduleLocal)) return moduleLocal;

        Path repositoryRelative = Path.of("simulation/src/main/java");
        if (Files.isDirectory(repositoryRelative)) return repositoryRelative;

        throw new IllegalStateException("cannot locate simulation main Java sources");
    }
}
'''


def move(old: str, new: str) -> None:
    source = ROOT / old
    target = ROOT / new
    if not source.is_file():
        raise RuntimeError(f"missing cleanup source: {old}")
    if target.exists():
        raise RuntimeError(f"cleanup target already exists: {new}")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(source, target)


def replace_in_tree(base: Path, suffixes: set[str], replacements: dict[str, str]) -> None:
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix not in suffixes:
            continue
        text = path.read_text(encoding="utf-8")
        updated = text
        for old, new in replacements.items():
            updated = updated.replace(old, new)
        if updated != text:
            path.write_text(updated, encoding="utf-8")


def main() -> None:
    for old, new in MOVES.items():
        move(old, new)

    replace_in_tree(ROOT / "simulation", {".java"}, REPLACEMENTS)
    replace_in_tree(ROOT / "core", {".java"}, REPLACEMENTS)
    replace_in_tree(ROOT / "docs", {".md"}, DOC_REPLACEMENTS)

    control = ROOT / "simulation/src/test/java/io/github/evoforge/simulation/kernel/command/ControlDependencyContractTest.java"
    control.write_text(CONTROL_TEST, encoding="utf-8")

    liquid = ROOT / "simulation/src/test/java/io/github/evoforge/simulation/world/liquid/LiquidDependencyContractTest.java"
    text = liquid.read_text(encoding="utf-8")
    text = text.replace(
        '    private static final String LEGACY_WATER_IMPORT =\n            "import io.github.evoforge.simulation.world.landscape.water.";\n',
        '')
    text = text.replace(
        '    private static final String OWNER_LOCAL_WATER_IMPORT =',
        '    private static final String WATER_IMPORT =')
    text = text.replace(
        'source.contains(LEGACY_WATER_IMPORT)\n                                || source.contains(OWNER_LOCAL_WATER_IMPORT)',
        'source.contains(WATER_IMPORT)')
    liquid.write_text(text, encoding="utf-8")

    soil = ROOT / "simulation/src/test/java/io/github/evoforge/simulation/world/soil/SoilLiquidDependencyContractTest.java"
    text = soil.read_text(encoding="utf-8").replace(
        '"import io.github.evoforge.simulation.world.landscape.water."',
        '"import io.github.evoforge.simulation.world.liquid.water."')
    soil.write_text(text, encoding="utf-8")

    stale_root = ROOT / "simulation/src/main/java/io/github/evoforge/simulation/world/mechanics"
    stale_files = list(stale_root.rglob("*.java")) if stale_root.exists() else []
    if stale_files:
        raise RuntimeError("production world/mechanics still contains: " + ", ".join(map(str, stale_files)))

    stale_refs = (
        "io.github.evoforge.simulation.world.mechanics.geometry",
        "io.github.evoforge.simulation.world.mechanics.occupancy",
        "io.github.evoforge.simulation.world.mechanics.traversal",
    )
    offenders: list[str] = []
    for base in (ROOT / "simulation", ROOT / "core"):
        for path in base.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if any(ref in text for ref in stale_refs):
                offenders.append(str(path.relative_to(ROOT)))
    if offenders:
        raise RuntimeError("stale semantic-package references: " + ", ".join(offenders))

    print("post-migration semantic cleanup applied")


if __name__ == "__main__":
    main()
