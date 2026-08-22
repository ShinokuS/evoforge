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

    for relative in [
        "simulation/src/main/java/io/github/evoforge/simulation/world/spatial",
        "simulation/src/main/java/io/github/evoforge/simulation/world/landscape",
        "simulation/src/main/java/io/github/evoforge/simulation/world/surface",
        "simulation/src/main/java/io/github/evoforge/simulation/world/object/placement",
    ]:
        path = root / relative
        if path.exists() and any(path.rglob("*.java")):
            raise RuntimeError(f"retired architecture Java sources remain: {relative}")
