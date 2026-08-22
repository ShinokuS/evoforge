from pathlib import Path
import runpy
import shutil

root = Path(__file__).resolve().parents[1]

try:
    runpy.run_path(root / "tools/apply-final-architecture-cleanup.py", run_name="__main__")
except RuntimeError as exc:
    if "retired architecture path still exists:" not in str(exc):
        raise

    # git tracks files rather than directories; after moving every Java source,
    # empty legacy directories may remain physically on the Actions runner.
    for relative in [
        "simulation/src/main/java/io/github/evoforge/simulation/world/spatial",
        "simulation/src/main/java/io/github/evoforge/simulation/world/landscape",
        "simulation/src/main/java/io/github/evoforge/simulation/world/surface",
        "simulation/src/main/java/io/github/evoforge/simulation/world/object/placement",
    ]:
        path = root / relative
        if path.exists() and not any(path.rglob("*.java")):
            shutil.rmtree(path)

position_system = root / "simulation/src/main/java/io/github/evoforge/simulation/world/space/position/PositionSystem.java"
simulation_view = root / "simulation/src/main/java/io/github/evoforge/simulation/runtime/SimulationView.java"

# Position is the semantic concept. Keep the public runtime view aligned with
# that terminology instead of preserving the old transform vocabulary.
view_text = simulation_view.read_text(encoding="utf-8")
view_text = view_text.replace("PositionLookup transforms,", "PositionLookup positions,")
view_text = view_text.replace("transforms == null", "positions == null")
simulation_view.write_text(view_text, encoding="utf-8")

# The first mechanical pass renamed member calls globally. Ordinary consumers
# may still intentionally use a local/field variable named `transforms`; keep
# those references internally consistent without undoing view.positions().
for source_root in [root / "simulation", root / "core"]:
    for path in source_root.rglob("*.java"):
        if path == position_system:
            continue
        text = path.read_text(encoding="utf-8")
        fixed = text
        if "PositionLookup transforms" in fixed:
            for member in ("x", "y", "z", "has"):
                fixed = fixed.replace(f"positions.{member}(", f"transforms.{member}(")
        if fixed != text:
            path.write_text(fixed, encoding="utf-8")

for relative in [
    "simulation/src/main/java/io/github/evoforge/simulation/world/spatial",
    "simulation/src/main/java/io/github/evoforge/simulation/world/landscape",
    "simulation/src/main/java/io/github/evoforge/simulation/world/surface",
    "simulation/src/main/java/io/github/evoforge/simulation/world/object/placement",
]:
    path = root / relative
    if path.exists() and any(path.rglob("*.java")):
        raise RuntimeError(f"retired architecture Java sources remain: {relative}")
