from pathlib import Path


def replace_between(path: str, start: str, end: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    start_index = text.index(start)
    end_index = text.index(end, start_index)
    file.write_text(text[:start_index] + replacement + text[end_index:], encoding="utf-8")


def replace_exact(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"expected text not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new), encoding="utf-8")


replace_between(
    "docs/project-context.md",
    "## Architecture reset is the current blocking work\n",
    "## Global simulation laws\n",
    """## Architecture reset accepted\n\nPR #132 establishes [ADR-026: Semantic capability architecture](decisions/026-semantic-capability-architecture.md) as the authoritative repository architecture. The rejected horizontal `foundation` / `world` / `generation` split and the superseded exclusive owner-first taxonomy are historical only.\n\nCurrent laws:\n\n- one authoritative source per mutable fact;\n- the primary unit is an independent semantic concept, not a technical layer or first consumer;\n- reusable capabilities live with the concept they express and never inside Movement/Agent/another consumer merely because that consumer appeared first;\n- mechanics/workflows coordinate independent semantic capabilities and own only workflow-specific process/policy state;\n- authority, capability, algorithm, projection, process and Genesis are orthogonal roles within/around semantic modules;\n- Kernel is domain-neutral execution infrastructure;\n- public semantic surfaces are narrow, consumer-neutral and acyclic; foreign `internal` access is forbidden;\n- mechanically decidable architecture laws, determinism, coverage and representative scale gates are enforced by tests/CI;\n- package placement must pass the reuse test in root `AGENTS.md`.\n\nThe architecture reset is no longer a feature-development blocker after PR #132 is merged. The next world-generation checkpoint is Stage 5 — Macro Ocean + Geophysical Skeleton.\n\n""",
)

replace_exact(
    "docs/project-context.md",
    "The exact package tree is being migrated, but the canonical semantic map is:",
    "The canonical semantic map is:",
)
replace_exact(
    "docs/project-context.md",
    "The next substantive Continuum/world-generation stage is intentionally blocked until PR #132 finishes and the new package/dependency/testing/documentation laws are green.",
    "Stages 0–4 are complete. After the architecture reset merges, the next substantive Continuum/world-generation checkpoint is Stage 5 — Macro Ocean + Geophysical Skeleton; Stage 5 has not started yet.",
)
replace_exact(
    "docs/project-context.md",
    "docs/decisions/025-owner-first-modular-simulation.md",
    "docs/decisions/026-semantic-capability-architecture.md",
)
replace_exact(
    "docs/project-context.md",
    "Then inspect the current owner package and its tests. During architecture PR #132, inspect the PR branch rather than assuming `develop` package paths are already final.",
    "Then inspect the current semantic module, its public capabilities, dependencies and tests.",
)

replace_between(
    "docs/roadmap.md",
    "## Current blocking milestone — owner-first architecture reset\n",
    "## Accepted Continuum foundation before reset\n",
    """## Current checkpoint — semantic capability architecture accepted\n\nPR #132 completes the repository-wide architecture reset defined by ADR-026. The repository now uses one authoritative `:simulation` Gradle module decomposed by independent semantic concepts and consumer-neutral capabilities.\n\nAccepted reset guarantees:\n\n- `:simulation`, `:core` and `:lwjgl3` are the only code/Gradle modules;\n- reusable capabilities such as Occupancy, Navigation, Pathfinding and Geometry are independent semantic units rather than Movement/Agent internals;\n- mechanics contain workflow-specific orchestration only;\n- lower-level world semantics do not depend on mechanics or agents;\n- legacy `world/mechanics` and forbidden generic technical roots are mechanically rejected;\n- the mandatory reuse test for new concepts lives in root `AGENTS.md`;\n- architecture fitness tests, deterministic tests, measured JaCoCo coverage floors and representative scale profiles are CI gates;\n- canonical documentation points at ADR-026 and the final package ownership;\n- temporary migration/refactor workflows are removed before merge.\n\n## Next allowed world-generation work\n\nCanonical Continuum Stages 0–4 are complete. The next stage is **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 has not started. Its work begins only after PR #132 is merged into `develop`, using the semantic-capability laws as a hard boundary.\n\n""",
)
replace_exact(
    "docs/roadmap.md",
    "See [Project Context](project-context.md), [Architecture](architecture.md), [ADR-025](decisions/025-owner-first-modular-simulation.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).",
    "See [Project Context](project-context.md), [Architecture](architecture.md), [ADR-026](decisions/026-semantic-capability-architecture.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).",
)

replace_exact(
    "docs/systems/world-generation/continuum-development-plan.md",
    "- **Stage 4 — CURRENT.** Map / Zoom Performance Proof; draft PR #125, awaiting exact-head gates and manual inspection.\n- **Stage 5+ — not started.**",
    "- **Stage 4 — complete and manually accepted.** Map / Zoom Performance Proof; PR #125 merged. Revalidated during the architecture reset.\n- **Stage 5 — NEXT, not started.** Macro Ocean + Geophysical Skeleton begins after architecture reset PR #132 merges.\n- **Stage 6+ — not started.**",
)
replace_exact(
    "docs/systems/world-generation/continuum-development-plan.md",
    "After Stage 4 passes automated gates and manual inspection, stop. Do not start **Stage 5 — Macro Ocean + Geophysical Skeleton** until the user explicitly accepts Stage 4 and asks to continue.",
    "Stage 4 is accepted. The architecture reset is the final prerequisite before **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 remains not started until PR #132 is merged; after that merge it is the next canonical implementation checkpoint.",
)

replace_exact(
    "docs/guides/development-workflow.md",
    """Owner: <semantic owner>\nType: OWNER | MECHANIC | KERNEL | PROJECTION | COMPOSITION\nFacts changed: <authoritative facts or none>\nPublic contracts touched: <exact capabilities>\nDependencies: <public contracts only>\nInvariants: <what must remain true>\nScale/performance risk: <expected workload/memory behavior>\nEvidence: <tests/profile/manual acceptance>\nDocs: <normative pages affected>""",
    """Semantic concept: <independent concept being changed>\nRoles: <AUTHORITY / CAPABILITY / ALGORITHM / PROJECTION / PROCESS / GENESIS / WORKFLOW / KERNEL / COMPOSITION as applicable>\nConsumers: <current consumers; none if lower-level>\nFacts changed: <authoritative facts or none>\nPublic capabilities touched: <consumer-neutral contracts>\nDependencies: <public semantic contracts only>\nReuse test: <could another plausible consumer reuse this without moving it?>\nInvariants: <what must remain true>\nScale/performance risk: <expected workload/memory behavior>\nEvidence: <tests/profile/manual acceptance>\nDocs: <normative pages affected>""",
)
replace_between(
    "docs/guides/development-workflow.md",
    "### Placement decision\n",
    "## 3. Branch model\n",
    """### Placement decision\n\nStart with the semantic concept, then assign roles inside/around it:\n\n```text\nmeaning exists without current consumer?  -> independent semantic module/capability\nconsumer-neutral mutable fact?             -> authority inside that semantic module\nreplaceable rule for that concept?         -> algorithm/policy beside the concept\nrebuildable derived representation?        -> projection beside the concept\ncausal process intrinsic to one concept?   -> process inside that concept\ncoordinates independent concepts?          -> mechanic/workflow\ndomain-neutral execution?                  -> kernel\nwiring/lifecycle selection only?           -> composition\notherwise                                  -> resolve responsibility first\n```\n\nNever put Occupancy/Navigation/Geometry/Visibility/placement or another independently meaningful capability under Movement, Agent, Build, Drop or whichever feature first needs it. If a plausible second consumer would force extraction, the placement is wrong now.\n\n""",
)
replace_exact(
    "docs/guides/development-workflow.md",
    "Before adding an import across semantic blocks, ask whether the consumer needs the concrete type or only a narrower semantic capability.",
    "Before adding an import across semantic modules, ask whether the consumer needs the concrete type or only a narrower semantic capability, and verify that the dependency points from consumer to the more reusable concept rather than back toward the consumer.",
)
replace_exact(
    "docs/guides/development-workflow.md",
    "- final diff still matches declared owner/block type;",
    "- final diff still matches the declared independent semantic concept and applicable orthogonal roles;",
)
replace_exact(
    "docs/guides/development-workflow.md",
    "See [ADR-022: Green checkpoint development](../decisions/022-green-checkpoint-development.md) and [ADR-025: Owner-first modular simulation architecture](../decisions/025-owner-first-modular-simulation.md).",
    "See [ADR-022: Green checkpoint development](../decisions/022-green-checkpoint-development.md) and [ADR-026: Semantic capability architecture](../decisions/026-semantic-capability-architecture.md).",
)

snapshot = Path(".github/workflows/refactor-snapshot.yml")
if snapshot.exists():
    snapshot.unlink()
