from __future__ import annotations

from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def mv(src: str, dst: str) -> None:
    source = ROOT / src
    if not source.exists():
        return
    target = ROOT / dst
    target.parent.mkdir(parents=True, exist_ok=True)
    run("git", "mv", src, dst)


def rm(path: str) -> None:
    target = ROOT / path
    if not target.exists():
        return
    run("git", "rm", "-r", path)


def replace_all(replacements: list[tuple[str, str]]) -> None:
    allowed = {".java", ".md", ".json", ".yml", ".yaml", ".gradle", ".properties"}
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts or path.suffix not in allowed:
            continue
        text = path.read_text(encoding="utf-8")
        updated = text
        for old, new in replacements:
            updated = updated.replace(old, new)
        if updated != text:
            path.write_text(updated, encoding="utf-8")


# 1. Merge the ambiguous world/spatial sibling into the semantic world/space/position capability.
main = "simulation/src/main/java/io/github/evoforge/simulation/world"
test = "simulation/src/test/java/io/github/evoforge/simulation/world"

mv(f"{main}/spatial/SpatialSystem.java", f"{main}/space/position/PositionSystem.java")
mv(f"{main}/spatial/TransformLookup.java", f"{main}/space/position/PositionLookup.java")
mv(f"{main}/spatial/TransformState.java", f"{main}/space/position/PositionState.java")
mv(f"{main}/spatial/ObjectSpatialIndex.java", f"{main}/space/position/ObjectPositionIndex.java")
mv(f"{main}/spatial/indexes/CellSpatialIndex.java", f"{main}/space/position/CellPositionIndex.java")

mv(f"{test}/spatial/SpatialSystemTest.java", f"{test}/space/position/PositionSystemTest.java")
mv(f"{test}/spatial/SpatialSystemIntegrationTest.java", f"{test}/space/position/PositionSystemIntegrationTest.java")
mv(f"{test}/spatial/TransformStateTest.java", f"{test}/space/position/PositionStateTest.java")
mv(f"{test}/spatial/indexes/CellSpatialIndexTest.java", f"{test}/space/position/CellPositionIndexTest.java")

# Generic placement is a space capability, not Object's consumer-owned workflow.
mv(f"{main}/object/placement/ObjectPlacementSystem.java", f"{main}/space/placement/ObjectPlacementSystem.java")

# 2. Replace the landscape umbrella with an authored material identity plus an explicit terrain-mutation mechanic.
mv(f"{main}/landscape/definition/LandscapeDefinitionId.java", f"{main}/material/MaterialDefinitionId.java")
mv(f"{main}/landscape/definition/LandscapeDefinitionBootstrap.java", f"{main}/material/MaterialDefinitionBootstrap.java")
mv(f"{main}/landscape/LandscapeMutations.java", "simulation/src/main/java/io/github/evoforge/simulation/mechanics/terrainmutation/TerrainMutations.java")
mv(f"{main}/landscape/LandscapeSystem.java", "simulation/src/main/java/io/github/evoforge/simulation/mechanics/terrainmutation/TerrainMutationWorkflow.java")
mv(f"{main}/terrain/command", "simulation/src/main/java/io/github/evoforge/simulation/mechanics/terrainmutation/command")

mv(f"{test}/landscape/definition/LandscapeDefinitionIdTest.java", f"{test}/material/MaterialDefinitionIdTest.java")
mv(f"{test}/landscape/definition/LandscapeDefinitionBootstrapTest.java", f"{test}/material/MaterialDefinitionBootstrapTest.java")
mv(f"{test}/landscape/LandscapeSystemTest.java", "simulation/src/test/java/io/github/evoforge/simulation/mechanics/terrainmutation/TerrainMutationWorkflowTest.java")
mv(f"{test}/landscape/LandscapeMutationBoundaryContractTest.java", "simulation/src/test/java/io/github/evoforge/simulation/mechanics/terrainmutation/TerrainMutationBoundaryContractTest.java")
mv(f"{test}/terrain/command", "simulation/src/test/java/io/github/evoforge/simulation/mechanics/terrainmutation/command")

mv("assets/definitions/landscape", "assets/definitions/material")

# Material-owned traversal aspects should say what the authored identity actually represents.
nav = f"{main}/navigation/traversal"
nav_test = f"{test}/navigation/traversal"
mv(f"{nav}/LandscapeTraversalDefinitions.java", f"{nav}/MaterialTraversalDefinitions.java")
mv(f"{nav}/LandscapeTraversalDefinitionCompiler.java", f"{nav}/MaterialTraversalDefinitionCompiler.java")
mv(f"{nav_test}/LandscapeTraversalDefinitionCompilerTest.java", f"{nav_test}/MaterialTraversalDefinitionCompilerTest.java")

# 3. `surface` was a generic root containing only sky-exposure projections.
mv(f"{main}/surface", f"{main}/sky")
mv(f"{test}/surface", f"{test}/sky")

replace_all([
    ("io.github.evoforge.simulation.world.spatial.indexes", "io.github.evoforge.simulation.world.space.position"),
    ("io.github.evoforge.simulation.world.spatial", "io.github.evoforge.simulation.world.space.position"),
    ("SpatialSystemIntegrationTest", "PositionSystemIntegrationTest"),
    ("SpatialSystemTest", "PositionSystemTest"),
    ("SpatialSystem", "PositionSystem"),
    ("TransformStateTest", "PositionStateTest"),
    ("TransformState", "PositionState"),
    ("TransformLookup", "PositionLookup"),
    ("ObjectSpatialIndex", "ObjectPositionIndex"),
    ("CellSpatialIndexTest", "CellPositionIndexTest"),
    ("CellSpatialIndex", "CellPositionIndex"),
    ("io.github.evoforge.simulation.world.object.placement", "io.github.evoforge.simulation.world.space.placement"),
    ("io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId", "io.github.evoforge.simulation.world.material.MaterialDefinitionId"),
    ("io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionBootstrap", "io.github.evoforge.simulation.world.material.MaterialDefinitionBootstrap"),
    ("io.github.evoforge.simulation.world.landscape.LandscapeMutations", "io.github.evoforge.simulation.mechanics.terrainmutation.TerrainMutations"),
    ("io.github.evoforge.simulation.world.landscape.LandscapeSystem", "io.github.evoforge.simulation.mechanics.terrainmutation.TerrainMutationWorkflow"),
    ("io.github.evoforge.simulation.world.terrain.command", "io.github.evoforge.simulation.mechanics.terrainmutation.command"),
    ("io.github.evoforge.simulation.world.landscape.definition", "io.github.evoforge.simulation.world.material"),
    ("io.github.evoforge.simulation.world.landscape", "io.github.evoforge.simulation.mechanics.terrainmutation"),
    ("LandscapeDefinitionBootstrapTest", "MaterialDefinitionBootstrapTest"),
    ("LandscapeDefinitionBootstrap", "MaterialDefinitionBootstrap"),
    ("LandscapeDefinitionIdTest", "MaterialDefinitionIdTest"),
    ("LandscapeDefinitionId", "MaterialDefinitionId"),
    ("LandscapeTraversalDefinitionCompilerTest", "MaterialTraversalDefinitionCompilerTest"),
    ("LandscapeTraversalDefinitionCompiler", "MaterialTraversalDefinitionCompiler"),
    ("LandscapeTraversalDefinitions", "MaterialTraversalDefinitions"),
    ("LandscapeMutationBoundaryContractTest", "TerrainMutationBoundaryContractTest"),
    ("LandscapeSystemTest", "TerrainMutationWorkflowTest"),
    ("LandscapeSystem", "TerrainMutationWorkflow"),
    ("LandscapeMutations", "TerrainMutations"),
    ("assets/definitions/landscape", "assets/definitions/material"),
    ("world/landscape/definition", "world/material"),
    ("world/landscape", "mechanics/terrainmutation"),
    ("io.github.evoforge.simulation.world.surface", "io.github.evoforge.simulation.world.sky"),
    ("world/surface", "world/sky"),
])

# Tighten Position naming now that it is no longer a vague Spatial/Transform bucket.
replace_all([
    ("private final PositionState transforms = new PositionState();", "private final PositionState positions = new PositionState();"),
    ("return transforms;", "return positions;"),
    ("transforms.add(", "positions.add("),
    ("transforms.move(", "positions.move("),
    ("transforms.remove(", "positions.remove("),
    ("transforms.x(", "positions.x("),
    ("transforms.y(", "positions.y("),
    ("transforms.z(", "positions.z("),
    ("public PositionLookup transforms()", "public PositionLookup positions()"),
    (".transforms()", ".positions()"),
])

# Add narrow consumer-neutral mutation/admission capabilities so generic placement does not require concrete systems.
position_dir = ROOT / f"{main}/space/position"
(position_dir / "PositionMutations.java").write_text(
    """package io.github.evoforge.simulation.world.space.position;\n\nimport io.github.evoforge.simulation.world.object.ObjectId;\n\n/** Consumer-neutral authoritative position mutation capability. */\npublic interface PositionMutations {\n\n    void place(ObjectId id, int x, int y, int z);\n\n    void move(ObjectId id, int x, int y, int z);\n\n    void remove(ObjectId id);\n}\n""",
    encoding="utf-8",
)

position_system = position_dir / "PositionSystem.java"
text = position_system.read_text(encoding="utf-8")
text = text.replace("public final class PositionSystem {", "public final class PositionSystem implements PositionMutations {")
text = text.replace("    public void place(\n", "    @Override\n    public void place(\n")
text = text.replace("    public void move(\n", "    @Override\n    public void move(\n")
text = text.replace("    public void remove(\n", "    @Override\n    public void remove(\n")
position_system.write_text(text, encoding="utf-8")

occupancy_dir = ROOT / f"{main}/space/occupancy"
(occupancy_dir / "CellAdmission.java").write_text(
    """package io.github.evoforge.simulation.world.space.occupancy;\n\nimport io.github.evoforge.simulation.world.object.ObjectId;\n\n/** Consumer-neutral query for whether an object may enter a discrete cell. */\npublic interface CellAdmission {\n\n    OccupancyState admissionState(ObjectId objectId, int x, int y, int z);\n}\n""",
    encoding="utf-8",
)

occupancy_system = occupancy_dir / "OccupancySystem.java"
text = occupancy_system.read_text(encoding="utf-8")
text = text.replace("public final class OccupancySystem implements OccupancyLookup {", "public final class OccupancySystem implements OccupancyLookup, CellAdmission {")
text = text.replace("    public OccupancyState admissionState(\n", "    @Override\n    public OccupancyState admissionState(\n")
occupancy_system.write_text(text, encoding="utf-8")

placement = ROOT / f"{main}/space/placement/ObjectPlacementSystem.java"
placement.write_text(
    """package io.github.evoforge.simulation.world.space.placement;\n\nimport io.github.evoforge.simulation.world.object.ObjectId;\nimport io.github.evoforge.simulation.world.object.ObjectLookup;\nimport io.github.evoforge.simulation.world.space.occupancy.CellAdmission;\nimport io.github.evoforge.simulation.world.space.occupancy.OccupancyState;\nimport io.github.evoforge.simulation.world.space.position.PositionMutations;\n\n/**\n * Consumer-neutral object placement capability coordinating admission with\n * authoritative position mutation.\n */\npublic final class ObjectPlacementSystem {\n\n    private final ObjectLookup objects;\n    private final CellAdmission admission;\n    private final PositionMutations positions;\n\n    public ObjectPlacementSystem(\n            ObjectLookup objects,\n            CellAdmission admission,\n            PositionMutations positions) {\n\n        if (objects == null) {\n            throw new IllegalArgumentException(\"objects must not be null\");\n        }\n        if (admission == null) {\n            throw new IllegalArgumentException(\"admission must not be null\");\n        }\n        if (positions == null) {\n            throw new IllegalArgumentException(\"positions must not be null\");\n        }\n\n        this.objects = objects;\n        this.admission = admission;\n        this.positions = positions;\n    }\n\n    public ObjectPlacementResult place(\n            ObjectId objectId,\n            int x,\n            int y,\n            int z) {\n\n        if (objectId == null) {\n            throw new IllegalArgumentException(\"objectId must not be null\");\n        }\n        if (!objects.isAlive(objectId)) {\n            throw new IllegalArgumentException(\"unknown object: \" + objectId);\n        }\n\n        OccupancyState state = admission.admissionState(objectId, x, y, z);\n        if (state == OccupancyState.OCCUPIED) {\n            return ObjectPlacementResult.DESTINATION_OCCUPIED;\n        }\n        if (state == OccupancyState.RESERVED) {\n            return ObjectPlacementResult.DESTINATION_RESERVED;\n        }\n\n        positions.place(objectId, x, y, z);\n        return ObjectPlacementResult.PLACED;\n    }\n}\n""",
    encoding="utf-8",
)

# Rename the moved material/workflow declarations and packages after file moves.
replace_all([
    ("package io.github.evoforge.simulation.world.material;\n\npublic final class Landscape", "package io.github.evoforge.simulation.world.material;\n\npublic final class Material"),
])

# The broad replacements above already rename the declarations in normal Java syntax.

# Explicitly forbid the retired ambiguous/umbrella roots from silently returning.
fitness = ROOT / "simulation/src/test/java/io/github/evoforge/simulation/architecture/SemanticCapabilityArchitectureContractTest.java"
text = fitness.read_text(encoding="utf-8")
text = text.replace(
    "    void legacyWorldMechanicsTreeCannotReappear() {\n        Path legacy = simulationPackageRoot().resolve(\"world/mechanics\");\n        assertFalse(\n                Files.exists(legacy),\n                () -> \"legacy consumer-owned world capability tree reappeared: \" + legacy);\n    }",
    """    void retiredAmbiguousAndUmbrellaWorldRootsCannotReappear() {\n        Path root = simulationPackageRoot().resolve(\"world\");\n        for (String retired : List.of(\"mechanics\", \"landscape\", \"spatial\", \"surface\")) {\n            Path path = root.resolve(retired);\n            assertFalse(\n                    Files.exists(path),\n                    () -> \"retired ambiguous/umbrella world root reappeared: \" + path);\n        }\n    }""",
)
fitness.write_text(text, encoding="utf-8")

# Reconcile the top-level world navigation page with the actual semantic roots.
package_info = ROOT / f"{main}/package-info.java"
package_info.write_text(
    """/**\n * Objective world semantics for EvoForge.\n *\n * <p>Top-level packages are independent semantic concepts, not technical\n * lifecycle layers and not the first mechanics that consume them. Current\n * roots include Continuum infrastructure, authored material identity, terrain,\n * geometry, space (position/orientation/occupancy/placement/measurement),\n * navigation (including traversal/pathfinding), object identity/state, liquid\n * and water, soil, atmosphere, geology, sky exposure, and interaction access.\n * Cross-concept causal orchestration belongs under {@code simulation.mechanics},\n * while domain-neutral execution belongs under {@code simulation.kernel}.</p>\n *\n * <p>See ADR-026 and repository-root {@code AGENTS.md}. A new independent\n * capability must receive its own semantic home rather than being nested under\n * its first consumer.</p>\n */\npackage io.github.evoforge.simulation.world;\n""",
    encoding="utf-8",
)

# Remove branch-only scratch notes; accepted documentation is updated separately.
for scratch in [
    "docs/journal/architecture-final-cleanup-2026-08-23.md",
    "docs/journal/architecture-final-cleanup-plan.md",
    "docs/journal/architecture-final-cleanup-note.md",
    "docs/journal/.keep",
]:
    path = ROOT / scratch
    if path.exists():
        path.unlink()

# Sanity checks: old roots and key old identifiers must be gone from production Java.
for retired in [f"{main}/spatial", f"{main}/landscape", f"{main}/surface", f"{main}/object/placement"]:
    if (ROOT / retired).exists():
        raise RuntimeError(f"retired architecture path still exists: {retired}")

production_text = "\n".join(
    path.read_text(encoding="utf-8")
    for path in (ROOT / "simulation/src/main/java").rglob("*.java")
)
for stale in [
    "world.spatial",
    "world.landscape",
    "LandscapeDefinitionId",
    "LandscapeSystem",
    "SpatialSystem",
    "TransformLookup",
    "TransformState",
]:
    if stale in production_text:
        raise RuntimeError(f"stale production architecture identifier remains: {stale}")
