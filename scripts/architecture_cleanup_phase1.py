from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def move_dir(old: str, new: str) -> None:
    src = ROOT / old
    dst = ROOT / new
    if not src.exists():
        return
    if dst.exists():
        raise RuntimeError(f"destination already exists: {dst}")
    shutil.move(str(src), str(dst))


def rewrite_text_files() -> None:
    replacements = [
        ("io.github.evoforge.simulation.world.continuum", "io.github.evoforge.world.continuum"),
        ("io.github.evoforge.simulation.world.genesis", "io.github.evoforge.generator"),
        ("io.github.evoforge.simulation.definition", "io.github.evoforge.world.definition"),
        ("io.github.evoforge.simulation", "io.github.evoforge.physics"),
    ]
    suffixes = {".java", ".gradle", ".yml", ".yaml"}
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in suffixes:
            continue
        if ".git" in path.parts or "build" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        changed = text
        for old, new in replacements:
            changed = changed.replace(old, new)
        if changed != text:
            path.write_text(changed, encoding="utf-8")


def relocate_java_sources(module: str) -> None:
    module_root = ROOT / module
    if not module_root.exists():
        return
    for source_root_rel in ("src/main/java", "src/test/java"):
        source_root = module_root / source_root_rel
        if not source_root.exists():
            continue
        files = list(source_root.rglob("*.java"))
        for path in files:
            text = path.read_text(encoding="utf-8")
            match = re.search(r"^package\s+([A-Za-z0-9_.]+);", text, re.MULTILINE)
            if not match:
                continue
            expected = source_root / Path(*match.group(1).split(".")) / path.name
            if expected == path:
                continue
            expected.parent.mkdir(parents=True, exist_ok=True)
            if expected.exists():
                raise RuntimeError(f"duplicate Java destination: {expected}")
            shutil.move(str(path), str(expected))


def move_definition_package(source_module: str, source_set: str) -> None:
    src = ROOT / source_module / source_set / "io/github/evoforge/world/definition"
    if not src.exists():
        return
    dst = ROOT / "world" / source_set / "io/github/evoforge/world/definition"
    dst.mkdir(parents=True, exist_ok=True)
    for path in list(src.rglob("*.java")):
        target = dst / path.name
        if target.exists():
            raise RuntimeError(f"duplicate definition class: {target}")
        shutil.move(str(path), str(target))


def move_foundation_definition(source_set: str) -> None:
    base = ROOT / "foundation" / source_set
    if not base.exists():
        return
    candidates = list(base.rglob("*.java"))
    if not candidates:
        return
    dst = ROOT / "world" / source_set / "io/github/evoforge/world/definition"
    dst.mkdir(parents=True, exist_ok=True)
    for path in candidates:
        text = path.read_text(encoding="utf-8")
        text = text.replace(
            "io.github.evoforge.simulation.definition",
            "io.github.evoforge.world.definition",
        )
        target = dst / path.name
        if target.exists():
            raise RuntimeError(f"duplicate foundation definition: {target}")
        target.write_text(text, encoding="utf-8")


def write_build_files() -> None:
    settings = (
        "plugins {\n"
        "  id(\"org.gradle.toolchains.foojay-resolver-convention\") version \"1.0.0\"\n"
        "}\n"
        "\n"
        "// generator creates initial state; world owns current state/access;\n"
        "// physics advances that state; core/lwjgl3 are presentation and launch only.\n"
        "include 'lwjgl3', 'core', 'world', 'generator', 'physics'\n"
    )
    (ROOT / "settings.gradle").write_text(settings, encoding="utf-8")

    generator_build = (
        "[compileJava, compileTestJava]*.options*.encoding = 'UTF-8'\n\n"
        "eclipse.project.name = appName + '-generator'\n\n"
        "dependencies {\n"
        "    implementation project(':world')\n"
        "    testImplementation \"org.junit.jupiter:junit-jupiter:$junitVersion\"\n"
        "    testRuntimeOnly \"org.junit.platform:junit-platform-launcher\"\n"
        "}\n\n"
        "test {\n"
        "    useJUnitPlatform()\n"
        "    testLogging {\n"
        "        events \"passed\", \"skipped\", \"failed\"\n"
        "        exceptionFormat = \"full\"\n"
        "    }\n"
        "}\n"
    )
    (ROOT / "generator" / "build.gradle").write_text(generator_build, encoding="utf-8")

    physics_build = ROOT / "physics" / "build.gradle"
    text = physics_build.read_text(encoding="utf-8")
    text = text.replace("appName + '-simulation'", "appName + '-physics'")
    text = text.replace("    implementation project(':foundation')\n", "")
    text = text.replace("representative living-world scale profile", "representative runtime-physics scale profile")
    physics_build.write_text(text, encoding="utf-8")

    world_build = ROOT / "world" / "build.gradle"
    text = world_build.read_text(encoding="utf-8")
    text = text.replace("':generation', ':simulation'", "':generator', ':physics'")
    text = text.replace("Generation/Simulation/Presentation", "Generator/Physics/Presentation")
    text = text.replace("generation/simulation/presentation", "generator/physics/presentation")
    text = text.replace("Foundation may be consumed by World;\n// ", "")
    world_build.write_text(text, encoding="utf-8")

    core_build = ROOT / "core" / "build.gradle"
    text = core_build.read_text(encoding="utf-8")
    text = text.replace("implementation project(':simulation')", "implementation project(':physics')")
    core_build.write_text(text, encoding="utf-8")


def update_gradle_task_references() -> None:
    for path in (ROOT / ".github" / "workflows").glob("*.yml"):
        if path.name.startswith("architecture-cleanup-phase1"):
            continue
        text = path.read_text(encoding="utf-8")
        changed = text.replace(":simulation:", ":physics:")
        changed = changed.replace("simulation/build/", "physics/build/")
        changed = changed.replace(":generation:", ":generator:")
        changed = changed.replace("generation/build/", "generator/build/")
        if changed != text:
            path.write_text(changed, encoding="utf-8")


def ensure_no_old_module_package_roots() -> None:
    failures: list[str] = []
    for module in ("world", "generator", "physics"):
        for path in (ROOT / module).rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if "package io.github.evoforge.simulation" in text:
                failures.append(str(path.relative_to(ROOT)))
    if failures:
        raise RuntimeError("old simulation package declarations remain:\n" + "\n".join(failures))


def main() -> None:
    move_dir("simulation", "physics")
    move_dir("generation", "generator")

    # Rename package roots first, then make the directory tree match package declarations.
    rewrite_text_files()
    for module in ("world", "generator", "physics", "core", "lwjgl3"):
        relocate_java_sources(module)

    # Reunite the generic definition infrastructure. A separate one-file foundation module
    # adds no useful responsibility boundary here.
    move_definition_package("physics", "src/main/java")
    move_definition_package("physics", "src/test/java")
    move_foundation_definition("src/main/java")
    move_foundation_definition("src/test/java")

    foundation = ROOT / "foundation"
    if foundation.exists():
        shutil.rmtree(foundation)

    write_build_files()
    update_gradle_task_references()
    ensure_no_old_module_package_roots()


if __name__ == "__main__":
    main()
