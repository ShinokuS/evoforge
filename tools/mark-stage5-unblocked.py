from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"expected text missing from {path}: {old!r}")
    file.write_text(text.replace(old, new), encoding="utf-8")


replace(
    "docs/project-context.md",
    "PR #133 completes the final post-reset semantic cleanup: ArchUnit now enforces production bytecode dependency direction and top-level world-module cycle freedom; the ambiguous `world/spatial`, umbrella `world/landscape`, generic `world/surface`, and consumer-owned `world/object/placement` boundaries are retired. Stage 5 remains blocked only until PR #133 merges.",
    "PR #133 completed the final post-reset semantic cleanup: ArchUnit enforces production bytecode dependency direction and top-level world-module cycle freedom; the ambiguous `world/spatial`, umbrella `world/landscape`, generic `world/surface`, and consumer-owned `world/object/placement` boundaries are retired. The architecture gate is complete.",
)
replace(
    "docs/project-context.md",
    "Stages 0–4 are complete. Stage 5 — Macro Ocean + Geophysical Skeleton has not started. It is the next substantive world-generation checkpoint immediately after final architecture cleanup PR #133 merges.",
    "Stages 0–4 are complete. **Stage 5 — Macro Ocean + Geophysical Skeleton is NEXT and has not started.** The architecture gate is complete, so Stage 5 is now the allowed substantive world-generation checkpoint.",
)
replace(
    "docs/roadmap.md",
    "## Current checkpoint — final semantic cleanup before Stage 5",
    "## Current checkpoint — Stage 5 ready to begin",
)
replace(
    "docs/roadmap.md",
    "PR #132 established the repository-wide ADR-026 architecture. PR #133 is the final cleanup gate before Stage 5: it removes residual ambiguous/umbrella boundaries and adds bytecode-level dependency/cycle enforcement.",
    "PR #132 established the repository-wide ADR-026 architecture and PR #133 completed its final semantic cleanup, removing residual ambiguous/umbrella boundaries and adding bytecode-level dependency/cycle enforcement.",
)
replace(
    "docs/roadmap.md",
    "Canonical Continuum Stages 0–4 are complete. The next stage is **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 has not started. Its work begins only after final architecture cleanup PR #133 is merged into `develop`, using ADR-026 plus the permanent ArchUnit/source fitness gates as hard boundaries.",
    "Canonical Continuum Stages 0–4 are complete. **Stage 5 — Macro Ocean + Geophysical Skeleton is NEXT and has not started.** PR #133 is merged, so Stage 5 is now allowed to begin under ADR-026 plus the permanent ArchUnit/source fitness gates.",
)
replace(
    "docs/systems/world-generation/continuum-development-plan.md",
    "- **Stage 5 — NEXT, not started.** Macro Ocean + Geophysical Skeleton begins after final architecture cleanup PR #133 merges.",
    "- **Stage 5 — NEXT, not started.** Macro Ocean + Geophysical Skeleton is now allowed to begin; final architecture cleanup PR #133 is merged.",
)
replace(
    "docs/systems/world-generation/continuum-development-plan.md",
    "Stage 4 is accepted. Final architecture cleanup PR #133 is the only remaining prerequisite before **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 remains not started until that PR merges; immediately afterward it is the next canonical implementation checkpoint.",
    "Stage 4 is accepted and final architecture cleanup PR #133 is merged. **Stage 5 — Macro Ocean + Geophysical Skeleton** is now the next canonical implementation checkpoint and has not started yet.",
)
