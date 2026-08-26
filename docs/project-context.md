# Project Context

This page is the fastest current-state recovery path for EvoForge. It answers: **what the project is, what architecture is authoritative, what is currently being changed, and what work is blocked until that change is accepted**.

Repository-root `AGENTS.md` is mandatory reading before this page for any code/documentation action.

## EvoForge in one minute

EvoForge is a deterministic simulation of a persistent three-dimensional world. Complex behavior should emerge from explicit physical and behavioral rules rather than presentation tricks or content-specific hard-coding.

```text
authored semantic meaning
        ↓
deterministic owner-local Genesis
        ↓
authoritative semantic owners
        ↓
explicit cross-owner mechanics
        ↓
observable world state
        ↓
visualizer / diagnostics
```

The renderer is an observer. Camera/render state never decides what is physically true.

A finite EvoForge world also has one permanent geographic boundary law: **the logical world is surrounded by ocean.** Continents, islands and all exposed Genesis land exist inside that ocean; they never terminate because the rectangular coordinate domain ended.

## Repository map

```text
simulation/   all authoritative simulation, Genesis, world state, physics, mechanics and agents
core/         libGDX visualizer, debug scenarios and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definitions and presentation data
docs/         canonical rules, systems, decisions, guides and history
```

Only `simulation`, `core` and `lwjgl3` are code/Gradle modules under the current architecture.

## Architecture reset accepted

PR #132 establishes [ADR-026: Semantic capability architecture](decisions/026-semantic-capability-architecture.md) as the authoritative repository architecture. PR #133 completed the final post-reset semantic cleanup and ArchUnit enforcement.

Current laws include:

- one authoritative source per mutable fact;
- the primary unit is an independent semantic concept, not a technical layer or first consumer;
- reusable capabilities live with the concept they express;
- mechanics/workflows coordinate independent semantic capabilities;
- public semantic surfaces are narrow, consumer-neutral and acyclic;
- pages/chunks/caches are representation, not world truth;
- camera/visibility cannot change simulation truth;
- deterministic results cannot depend on rendering, query order, cache history or incidental thread scheduling;
- performance optimization must preserve semantic results rather than replace unseen parts of the world with different rules;
- finite Genesis geography must preserve the surrounding-ocean boundary condition through every later terrain refinement/materialization stage.

See [Architecture](architecture.md) and ADR-026 for the full laws.

## Current architecture map

```text
simulation
├── kernel/            time, scheduling and other neutral execution services
├── definition/        neutral authored-definition infrastructure only
├── genesis/           global initial-world composition only
├── world/             objective semantic owners
│   ├── continuum/      neutral large-world addressing/materialization
│   ├── geophysics/     macro-geophysical causes + finite-world ocean boundary
│   ├── material/
│   ├── object/
│   ├── space/
│   ├── geometry/
│   ├── navigation/
│   ├── geology/
│   ├── terrain/
│   ├── liquid/
│   ├── soil/
│   ├── atmosphere/
│   ├── sky/
│   └── interaction/
├── mechanics/         true cross-concept workflows
├── agents/            autonomous cognition/needs/perception/decision
└── composition/       only where a real composition area is justified
```

Do not create empty packages merely to match this diagram.

## Continuum/world-generation status

The dense V12–V15 whole-world architecture remains retired. Continuum remains the canonical large-world foundation.

Accepted Continuum work includes deterministic addressable sampling, bounded local/shared region work, infinite-time foundations, multi-resolution reads, bounded map/cache infrastructure and representative scale evidence.

Stages 0–5 are complete. **Stage 5 — Macro Ocean + Geophysical Skeleton was manually accepted in PR #135.** `world/geophysics` owns the macro-geophysical cause; ocean/land is derived from the same signed macro elevation relative to the shared sea datum.

PR #136 attempted Stage 6 as a noise/refinement-driven continuous heightfield plus tile-LOD repair. Manual visual inspection rejected that implementation. The PR is closed and archive-only.

### Immediate work before replacement Stage 6

The accepted Stage 5 intrinsic macro elevation remains valid, but future structure-first Terrain needs more geophysical cause than one scalar `elevationAt(x,y)`.

A separate Stage 5 follow-up PR therefore expands `world/geophysics` with bounded deterministic structural context and the finite-world surrounding-ocean constraint. Intended facts include continental/deep-ocean support, macro-margin influence, structural-region identity, local boundary orientation/regime/strength and boundary-ocean influence.

The boundary model is not a renderer mask: a non-zero hard belt around all four logical edges is always ocean, with a broader smooth transition toward the unaffected intrinsic macro field. Later Stage 6+ terrain receives this fact and must not raise the hard belt above sea datum.

This preparation does not generate Terrain, mountains, rivers or lakes.

### Replacement Stage 6

Stage 6 remains `Continuous Surface Evolution Prototype`, but its implementation direction is reset by [ADR-027](decisions/027-hierarchical-geomorphic-geography.md).

The new pipeline is:

```text
Stage 5 geophysical causes + surrounding-ocean constraint
        ↓
regional geomorphic structures
        ↓
mountain belts / plateaus / basins / plains
        ↓
V12-informed local morphology
        ↓
continuous world/terrain surface
```

The useful local ideas from old V12 are deliberately reused as algorithmic lineage: balanced hills/depressions, rolling relief, physical cell-scale feature sizes and explicit prevention of one-block Z chatter. The old dense architecture and global V-number generator lineage are not restored.

Stage 6 must preserve the hard Stage 5 boundary ocean. Whole-world acceptance therefore requires visible ocean on every side and no landmass/mountain system clipped by the finite world rectangle.

Stage 7 drainage and all later river/lake work remain blocked until this replacement Stage 6 is manually accepted.

See the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md) and [Stage 6 replacement plan](systems/world-generation/stage6-hierarchical-geomorphic-geography.md).

## Genesis versus Runtime terrain

Genesis generation and later mutable Terrain are separate concerns.

Stages 7–8 may use erosion-like/relaxation mathematics as **finite Genesis construction solvers** to create coherent channel/lake geometry. This does not mean simulating years of runtime erosion or water history.

Future runtime terrain changes such as digging, construction, landslides or real water erosion belong to later mechanics. Conceptually, current Terrain then becomes reconstructable Genesis terrain plus authoritative sparse persistent changes. The exact storage representation is postponed until those mechanics/persistence stages require it.

## Simulation-scale observer independence

An individual living simulation entity does not become a different kind of simulation because it is far from the camera.

Future large-object-count optimization may use data-oriented storage, event/wake scheduling, exact elapsed-time advancement where mathematically valid, batching and sparse indexes. It may not replace an existing distant individual animal with a statistically different surrogate solely because it is unobserved.

Some concepts may genuinely be fields/aggregates by ontology (for example grass biomass rather than every blade as an object). That is a semantic modeling decision, not camera LOD.

## Definitions policy

Definitions describe immutable authored semantic meaning. Root definition infrastructure is neutral; domain-specific definition types/compilers belong with the owner/mechanic that consumes them.

Human-facing semantic controls normally use normalized meaning where appropriate. Solver coefficients/thresholds/tuning constants remain hidden implementation policy unless they are genuinely authored semantic content.

The surrounding-ocean rule is not a Definition slider. It is a world invariant shared by every finite-world profile.

## Performance policy

For a persistent enormous world, performance is architectural:

```text
representative workload
      ↓
measure work/memory/latency
      ↓
identify hot path
      ↓
optimize hidden implementation
      ↓
prove same semantic result
      ↓
retain regression evidence
```

Visibility/camera distance may optimize presentation/cache only. It may not select cheaper authoritative world rules.

## Fast recovery path

Read in this order:

```text
AGENTS.md
docs/project-context.md
docs/architecture.md
docs/roadmap.md
docs/decisions/026-semantic-capability-architecture.md
docs/decisions/027-hierarchical-geomorphic-geography.md
relevant docs/systems/** page
```

Then inspect the current semantic module, its public capabilities, dependencies and tests.

If current normative docs conflict with executable code/tests, reconcile the contradiction in the same change. Do not use chat history as the missing source of truth.
