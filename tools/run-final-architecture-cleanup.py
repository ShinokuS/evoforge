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

# The original migration intentionally renames PositionSystem's internal state,
# but its first implementation over-matched ordinary consumer fields named
# `transforms`. Restore those consumer references while keeping PositionSystem
# itself on the precise `positions` terminology.
position_system = root / "simulation/src/main/java/io/github/evoforge/simulation/world/space/position/PositionSystem.java"
for path in (root / "simulation").rglob("*.java"):
    if path == position_system:
        continue
    text = path.read_text(encoding="utf-8")
    fixed = text
    for member in ("x", "y", "z", "add", "move", "remove"):
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
