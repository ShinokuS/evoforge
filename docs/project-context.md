# Project Context

This page is the fastest way to reconstruct **what EvoForge is, what is already true in code, what must not be broken, and what should happen next**. It is written for humans returning after a long break and for future AI-assisted development sessions that do not have access to old chat history.

## EvoForge in one minute

EvoForge is a deterministic simulation of a persistent three-dimensional world. The project is trying to make complex behavior emerge from a small number of explicit physical and behavioral rules instead of faking results in the renderer or hard-coding special cases for particular content.

A useful mental model is:

```text
authored meaning
      ↓
validated / calibrated data
      ↓
authoritative simulation systems
      ↓
observable world state
      ↓
visualizer and diagnostics
```

The renderer is an observer. It never decides what is physically true.

## Repository map

```text
simulation/   pure Java authoritative simulation and generation code
core/         libGDX visualizer, scenarios and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definition/presentation data
docs/         canonical explanations, decisions, guides and journal
```

The most important dependency rule is one-way: `simulation` must not depend on libGDX or presentation code.

## The central architectural idea

Every mutable fact has one authoritative owner. Other systems receive narrow read or mutation capabilities instead of sharing one giant mutable world object.

For example, an object's identity, position, movement reservation, Water volume and Soil-held liquid are different facts with different owners. They may describe the same place or actor, but they are not collapsed into one mutable record.

This makes the simulation easier to test, replay and extend without accidental hidden coupling.

## Current stable capabilities

The integration line already contains working foundations for:

- deterministic discrete simulation time and scheduled processes;
- object identity, immutable definitions and explicit runtime assembly;
- finite optional world bounds and discrete XYZ spatial addressing;
- terrain geometry including full cells and cardinal ramps;
- navigation, transition cost, occupancy, timed movement and deterministic 3D A* pathfinding;
- autonomous agents with needs, perception, opportunities, deterministic utility and committed intents;
- finite consumable resources, regrowth and Need progression;
- generic free liquids, finite Water, Soil-retained liquid, precipitation, infiltration, local flow and evaporation;
- Water-aware terrestrial traversal;
- an observer-only developer visualizer and headless diagnostic/audit tooling;
- deterministic world provenance, typed generated facts and replaceable generation algorithms;
- the manually accepted V12 base-terrain generator.

The [Roadmap](roadmap.md) is the detailed status list. Individual mechanics are explained under [Systems](systems/).

## Protected Stage 0 baseline

Stage 0 established the world-generation architecture that future stages must build on.

The accepted V12 terrain appearance is a protected baseline. Its implementation is intentionally split as:

```text
WorldGenerationIntent
        ↓
V12LandformCalibrator
        ↓
V12LandformCalibration
        +
V12LandformRecipe
        ↓
V12LandformElevationAlgorithm
        ↓
ElevationField
```

The important distinction is **meaning versus implementation detail**:

- authored controls express semantic intent such as land coverage, relief or ruggedness;
- calibration converts that intent into exact values appropriate for the world dimensions;
- a versioned recipe owns V12-specific model constants;
- the spatial algorithm consumes only calibrated values + recipe + deterministic generation randomness.

Do not add mountain, river, geology or material special cases directly into V12 just because it is convenient. Later stages have their own typed outputs and replaceable algorithms.

## World-generation direction

After Stage 0, the world-generation sequence is deliberately ordered so physical causes exist before their consequences:

```text
Stage 1  mountain systems
   ↓
Stage 2  dry drainage / basins / river and lake carving
   ↓
Stage 3  coherent layered geology and required deposits
   ↓
Stage 4  caves
   ↓
Stage 5  causal surface/subsurface material synthesis
   ↓
Stage 6  complete dry-world acceptance
   ↓
Stage 7  finite initial Water fill
   ↓
Stage 8  runtime handoff audit
```

The next implementation stage is therefore **Stage 1 — Mountain Systems**. Water is intentionally late: river channels and lake bowls must first exist as real dry geometry.

See [World Generation](systems/world-generation/overview.md) for the full contract and stage acceptance rules.

## Important things that are provisional

Code can exist without being the final accepted algorithm. In particular, Stage 0 deliberately preserves typed seams around several provisional implementations:

- current analytical drainage/hydrography is useful infrastructure, not the final carved river/lake system;
- current geology generation is a placeholder behind a useful contract, not the final coherent geology model;
- current terrain-material slope/deposition behavior is an early vertical slice, not final causal surface synthesis;
- current generated initial-Water path is reusable infrastructure, but canonical world generation fills Water only after the dry world is accepted.

Do not accumulate special cases in these placeholders. Replace or narrow them in the stage that owns the real model.

## Rules future work must preserve

1. **Determinism:** same authoritative inputs and generation revision produce the same result.
2. **One owner per mutable fact:** no duplicate truth in visualizer, bootstrap or helper systems.
3. **Observer independence:** camera distance or visibility never changes simulation fidelity or rules.
4. **Typed replaceable algorithms:** orchestration depends on semantic contracts, not concrete implementation classes.
5. **Semantic authoring:** content authors describe meaning/character; domain calibration resolves exact physical or operational values.
6. **No concrete-content branching in generic code:** avoid `if river -> sand`, `if mountain -> granite`, material-name switches, or similar shortcuts.
7. **Headless evidence:** fundamental mechanics require deterministic tests/diagnostics; visual quality also requires explicit manual acceptance when appropriate.
8. **No premature universal framework:** extract shared abstractions only after real consumers demonstrate the common concept.

The full global rules live in [Architecture](architecture.md).

## How to recover context in five minutes

Read in this order:

1. **This page** — current state and direction.
2. [Architecture](architecture.md) — rules that all systems must obey.
3. [Roadmap](roadmap.md) — completed work, active/next milestones and deliberately deferred scope.
4. The relevant group under [Systems](systems/) — exact current semantics and algorithms.
5. [Decisions](decisions/) — why durable choices were made.
6. [Development Journal](journal/) only when historical reasoning, an audit or an acceptance record is needed.

Then verify the owning production package and its tests before changing semantics. Markdown explains the model; code/tests prove the exact current implementation.

## Documentation truth hierarchy

When sources appear to disagree, use this order:

```text
production code + executable tests
        ↓
current normative docs (Architecture / Systems / Guides / Roadmap)
        ↓
accepted ADRs explaining why
        ↓
Journal records describing historical thinking
        ↓
chat history / prototypes
```

A Journal entry is never allowed to override current code or normative documentation merely because it is more detailed.

## Before starting a new stage

Confirm all of the following:

- `develop` is the intended baseline and the previous stage is merged;
- the relevant System page states the current accepted baseline;
- the new stage has one clear owner, input facts, output facts and replaceable algorithm boundary;
- the authored controls remain semantic rather than low-level implementation knobs;
- deterministic tests and an observable diagnostic/preview plan are defined before visual acceptance;
- any architecture or pipeline change updates documentation and, when durable, an ADR in the same PR.

This checklist exists specifically to prevent future sessions from rediscovering or contradicting decisions already made.
