# Project Context

This page is the fastest way to reconstruct **what EvoForge is, what is already true in code, what must not be broken, and what should happen next**. It is written for humans returning after a long break and for future AI-assisted development sessions that do not have access to old chat history.

## EvoForge in one minute

EvoForge is a deterministic simulation of a persistent three-dimensional world. Complex behavior should emerge from explicit physical and behavioral rules instead of being faked in presentation code or hard-coded for particular content.

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

## Central architectural rules

Every mutable fact has one authoritative owner. Systems exchange narrow typed facts/capabilities rather than sharing one giant mutable world object.

Generation follows the same ownership discipline:

```text
semantic intent
      ↓
domain calibration
      +
versioned model recipe
      ↓
replaceable deterministic algorithm
      ↓
immutable generated fact
      ↓
preparation / materialization
      ↓
runtime owner
```

Meaning, calibration, model policy, spatial synthesis, generated facts and runtime ownership are deliberately separate concerns.

## Current stable capabilities

The integration line already contains working foundations for:

- deterministic discrete simulation time and scheduled processes;
- object identity, immutable definitions and explicit runtime assembly;
- finite optional world bounds and discrete XYZ spatial addressing;
- terrain geometry including full cells and cardinal ramps;
- navigation, occupancy, timed movement and deterministic 3D A* / `MoveTo`;
- autonomous agents with needs, perception, opportunities, utility and committed intents;
- finite consumables/regrowth;
- generic finite liquids, Water, Soil-retained liquid, precipitation, infiltration, local flow and evaporation;
- observer-only developer visualization and headless diagnostic/audit tooling;
- deterministic world provenance and typed replaceable generation/preparation algorithms;
- manually accepted V12 ordinary base terrain;
- manually accepted V13 structural mountains over V12.

The [Roadmap](roadmap.md) is the detailed status list. Individual mechanics are explained under [Systems](systems/).

## Accepted world-generation baseline

### Stage 0 — V12 ordinary morphology

```text
WorldGenerationIntent
        ↓
V12LandformCalibrator
        ↓
V12LandformCalibration + V12LandformRecipe
        ↓
V12LandformElevationAlgorithm
        ↓
ElevationField
```

V12 owns land/ocean membership, coherent landmasses/coasts, broad uplift, ordinary hills/depressions, rolling relief and rugged ridges. It intentionally remains the ordinary landscape baseline.

### Stage 1 — V13 dedicated mountains

```text
WorldGenerationIntent.mountains
        ↓
MountainCalibrator
        ↓
MountainCalibration + MountainRecipe
        ↓
MountainElevationAlgorithm
        ↓
ElevationField
```

V13 composes a capped V12 base with dedicated deterministic mountain uplift. Its important ownership is:

```text
Abundance  -> expected mountain coverage on V12 land
Scale      -> individual transverse size / source spacing
Height     -> bounded prominence
Chaininess -> elongation
Sharpness  -> readable geometric slope character
```

`V13MountainTerrainGenerator` composes replaceable base-generation, calibration and mountain-algorithm dependencies. The accepted standard implementation is `MountainMorphologyAlgorithm`; another compatible implementation can be substituted behind `MountainElevationAlgorithm` without changing orchestration or downstream fact consumers.

Mountain generation does not own geology, concrete runtime Shapes, navigation connectivity or Water. Generic shape fitting runs later from the final precise elevation.

See [V13 Mountain Generation](systems/world-generation/mountain-generation.md).

## World-generation direction

The causal milestone order is now:

```text
Stage 0  V12 architecture + ordinary base morphology       COMPLETE
   ↓
Stage 1  V13 mountain systems                              COMPLETE
   ↓
Stage 2  dry drainage / basins / river and lake carving   NEXT
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

Water remains intentionally late: river channels and lake bowls must first exist as real dry geometry. Stage 2 starts from the accepted V13 dry surface.

See [World Generation](systems/world-generation/overview.md) for the full contract.

## Important provisional components

Code can exist behind a useful typed seam without being the final accepted algorithm:

- current drainage/hydrography is useful analytical infrastructure, not final Stage 2 carving;
- current geology generation is a placeholder until Stage 3;
- current terrain-material slope/deposition behavior is an early causal slice until Stage 5;
- current generated initial-Water path is compatibility infrastructure; canonical generation fills finite Water only after complete dry-world acceptance.

Do not accumulate feature-specific patches in those placeholders. Replace or narrow the owning algorithm when its stage becomes active.

## Rules future work must preserve

1. **Determinism:** same authoritative inputs and compatible generation revision produce the same result.
2. **One owner per mutable fact:** no duplicate truth in visualizer, bootstrap or helpers.
3. **Observer independence:** camera/visibility never changes simulation fidelity or rules.
4. **Typed replaceable algorithms:** orchestration depends on contracts, not concrete implementations.
5. **Semantic authoring:** authors describe meaning; domain calibration resolves exact operating values.
6. **No concrete-content branching in generic code:** no `if mountain -> granite`, `if river -> sand`, material-name switches or similar shortcuts.
7. **Headless evidence + manual acceptance where needed:** tests prove invariants; visual quality is explicitly reviewed rather than inferred from numbers.
8. **No premature universal framework:** extract shared abstractions only after real consumers prove a common concept.
9. **Green checkpoints:** one independently meaningful concern is implemented and verified before the next semantic change is added.

See [Architecture](architecture.md) and [ADR-022: Green checkpoint development](decisions/022-green-checkpoint-development.md).

## Development rule

Production work advances in this order:

```text
state one contract / ownership question
        ↓
smallest meaningful implementation step
        ↓
focused evidence and check
        ↓
understood green checkpoint
        ↓
coherent commit
        ↓
next concern
```

An unexplained red checkpoint blocks further semantic work. Diagnose the earliest stage that emits the wrong fact before adding downstream repair. Refactors that claim to preserve behavior stay separate from semantic changes.

The detailed procedure lives in [Development Workflow](guides/development-workflow.md).

## How to recover context in five minutes

Read in this order:

1. **This page** — current state and direction.
2. [Architecture](architecture.md) — global rules.
3. [Roadmap](roadmap.md) — complete/next/deferred work.
4. The relevant group under [Systems](systems/) — current system semantics/algorithms.
5. [Decisions](decisions/) — durable rationale.
6. [Development Journal](journal/) only for historical investigation/acceptance records.

Then verify the owning production package and its tests before changing semantics.

## Documentation truth hierarchy

```text
production code + executable tests
        ↓
current normative docs (Architecture / Systems / Guides / Roadmap)
        ↓
accepted ADRs explaining why
        ↓
Journal records describing historical reasoning/acceptance
        ↓
chat history / prototypes
```

Historical notes never override current code and normative documentation merely because they are more detailed.

## Before starting Stage 2

Confirm all of the following:

- Stage 1 final PR is merged into `develop` and its final head is green;
- the accepted V13 surface is the explicit dry-morphology input;
- Stage 2 begins with one clear drainage/basin/carving ownership contract rather than extending provisional threshold hydrography blindly;
- each component is built as a green checkpoint with focused tests/diagnostics before the next component;
- river/lake geometry is observable while still completely dry;
- any durable pipeline/ownership change updates normative documentation and an ADR when required.

This checklist exists specifically to prevent future work from rediscovering or contradicting already accepted decisions.
