from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"expected text missing from {path}: {old[:100]!r}")
    file.write_text(text.replace(old, new), encoding="utf-8")


replace(
    "docs/project-context.md",
    "The architecture reset is no longer a feature-development blocker after PR #132 is merged. The next world-generation checkpoint is Stage 5 — Macro Ocean + Geophysical Skeleton.",
    "PR #133 completes the final post-reset semantic cleanup: ArchUnit now enforces production bytecode dependency direction and top-level world-module cycle freedom; the ambiguous `world/spatial`, umbrella `world/landscape`, generic `world/surface`, and consumer-owned `world/object/placement` boundaries are retired. Stage 5 remains blocked only until PR #133 merges.",
)
replace(
    "docs/project-context.md",
    """│   ├── continuum/\n│   ├── space/\n│   ├── geometry/\n│   ├── geology/\n│   ├── terrain/\n│   ├── liquid/\n│   ├── soil/\n│   ├── atmosphere/\n│   └── object/\n├── mechanics/         true cross-owner laws such as Movement""",
    """│   ├── continuum/\n│   ├── material/\n│   ├── object/\n│   ├── space/          position, orientation, occupancy, placement, measurement\n│   ├── geometry/\n│   ├── navigation/\n│   ├── geology/\n│   ├── terrain/\n│   ├── liquid/\n│   ├── soil/\n│   ├── atmosphere/\n│   ├── sky/\n│   └── interaction/\n├── mechanics/         true cross-concept workflows: Movement, Hydrology, TerrainMutation""",
)
replace(
    "docs/project-context.md",
    "Stages 0–4 are complete. After the architecture reset merges, the next substantive Continuum/world-generation checkpoint is Stage 5 — Macro Ocean + Geophysical Skeleton; Stage 5 has not started yet.",
    "Stages 0–4 are complete. Stage 5 — Macro Ocean + Geophysical Skeleton has not started. It is the next substantive world-generation checkpoint immediately after final architecture cleanup PR #133 merges.",
)

replace(
    "docs/roadmap.md",
    "## Current checkpoint — semantic capability architecture accepted",
    "## Current checkpoint — final semantic cleanup before Stage 5",
)
replace(
    "docs/roadmap.md",
    "PR #132 completes the repository-wide architecture reset defined by ADR-026. The repository now uses one authoritative `:simulation` Gradle module decomposed by independent semantic concepts and consumer-neutral capabilities.",
    "PR #132 established the repository-wide ADR-026 architecture. PR #133 is the final cleanup gate before Stage 5: it removes residual ambiguous/umbrella boundaries and adds bytecode-level dependency/cycle enforcement.",
)
replace(
    "docs/roadmap.md",
    "- legacy `world/mechanics` and forbidden generic technical roots are mechanically rejected;",
    "- legacy `world/mechanics` plus retired `world/spatial`, `world/landscape` and generic `world/surface` roots are mechanically rejected;\n- object Position is the explicit `world/space/position` authority/capability; generic placement is `world/space/placement`; authored material identity is `world/material`; cross-concept terrain mutation is `mechanics/terrainmutation`; sky exposure is `world/sky`;\n- ArchUnit checks production bytecode for world-module cycles and forbidden dependency direction;",
)
replace(
    "docs/roadmap.md",
    "Canonical Continuum Stages 0–4 are complete. The next stage is **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 has not started. Its work begins only after PR #132 is merged into `develop`, using the semantic-capability laws as a hard boundary.",
    "Canonical Continuum Stages 0–4 are complete. The next stage is **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 has not started. Its work begins only after final architecture cleanup PR #133 is merged into `develop`, using ADR-026 plus the permanent ArchUnit/source fitness gates as hard boundaries.",
)

replace(
    "docs/architecture.md",
    """│   ├── continuum/              neutral large-world addressing/materialization\n│   ├── object/                 object identity/existence semantics\n│   ├── space/\n│   │   ├── position/           position authority/capabilities as justified by audit\n│   │   └── occupancy/          consumer-neutral admission/reservation semantics\n│   ├── geometry/               objective physical geometry\n│   ├── navigation/             consumer-neutral connectivity/traversability/path capability\n│   ├── visibility/             only when independent visibility semantics exist\n│   ├── geology/\n│   ├── terrain/\n│   ├── liquid/\n│   ├── soil/\n│   └── atmosphere/\n├── mechanics/\n│   ├── movement/               movement-specific workflow only\n│   ├── <drop/build/etc>/       only when real mechanics exist\n│   └── <cross-concept law>/""",
    """│   ├── continuum/              neutral large-world addressing/materialization\n│   ├── material/               authored material identity shared by semantic aspects\n│   ├── object/                 object identity/existence semantics\n│   ├── space/\n│   │   ├── position/           object position authority + rebuildable cell index\n│   │   ├── orientation/        independent facing/orientation semantics\n│   │   ├── occupancy/          consumer-neutral admission/reservation semantics\n│   │   ├── placement/          generic placement over admission + position mutation\n│   │   └── measurement/        physical space/volume units\n│   ├── geometry/               objective physical geometry\n│   ├── navigation/             connectivity/traversal/pathfinding capability\n│   ├── geology/\n│   ├── terrain/\n│   ├── liquid/\n│   ├── soil/\n│   ├── atmosphere/\n│   ├── sky/                    derived sky-exposure/surface capability\n│   └── interaction/            interaction-access semantics\n├── mechanics/\n│   ├── movement/               movement-specific workflow only\n│   ├── hydrology/              cross-concept environmental water workflows\n│   └── terrainmutation/        coordinated Terrain + Geometry + Traversal invalidation""",
)
replace(
    "docs/architecture.md",
    "- no semantic dependency cycles;",
    "- no semantic dependency cycles (ArchUnit checks top-level `world/*` slices from production bytecode);",
)

replace(
    "docs/guides/testing.md",
    "ArchUnit is the preferred Java-level enforcement tool when a rule is naturally expressible through classes/packages. Lightweight source/build tests may cover repository topology or conventions ArchUnit cannot see.",
    "ArchUnit is the active Java-level dependency enforcement tool. It imports production bytecode (excluding test classes), rejects cycles between top-level `world/*` semantic modules, protects Kernel neutrality, and prevents `world` semantics from depending on Mechanics/Agents. Lightweight source/build tests remain complementary for repository topology and naming laws that bytecode cannot see.",
)

replace(
    "docs/systems/world-generation/continuum-development-plan.md",
    "- **Stage 5 — NEXT, not started.** Macro Ocean + Geophysical Skeleton begins after architecture reset PR #132 merges.",
    "- **Stage 5 — NEXT, not started.** Macro Ocean + Geophysical Skeleton begins after final architecture cleanup PR #133 merges.",
)
replace(
    "docs/systems/world-generation/continuum-development-plan.md",
    "Stage 4 is accepted. The architecture reset is the final prerequisite before **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 remains not started until PR #132 is merged; after that merge it is the next canonical implementation checkpoint.",
    "Stage 4 is accepted. Final architecture cleanup PR #133 is the only remaining prerequisite before **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 remains not started until that PR merges; immediately afterward it is the next canonical implementation checkpoint.",
)
