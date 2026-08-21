# Roadmap

The Roadmap answers two questions: **what is already real in EvoForge, and what is intentionally next?** Exact mechanic behavior belongs in [Systems](systems/); global rules belong in [Architecture](architecture.md).

## Current position

Completed world-generation slices:

- **Stage 0 / V12** — architecture stabilization and accepted ordinary terrain;
- **Stage 1 / V13** — accepted structural mountains;
- **Stage 2A / V14** — accepted standing-water bathymetry;
- **Stage 2B / V14–V15** — accepted organic continental domain and terrain-derived inland lakes.

Stage 2B is closed as a self-contained generation slice. The experimental drainage/river topology and F4 diagnostic scaffolding explored during development were deliberately removed before completion; they are not production commitments.

Before new river semantics, the accepted generation baseline will receive two engineering passes:

1. **large-world generation performance** — target practical generation of at least `10,000 × 10,000` worlds without visible quality loss, with larger worlds kept possible by architecture;
2. **world-generation preview UI** — scenario-style controls, grouped/collapsible settings, tooltips, generation hotkey and non-blocking progress reporting.

Those passes optimize/present the accepted model; they do not reopen its visual semantics without a new explicit acceptance decision.

For current algorithms see [World Generation](systems/world-generation/overview.md) and [Continental Domain and Inland Lakes](systems/world-generation/landmass-and-inland-lakes.md).

## Completed simulation foundations

The wider project already has deterministic simulation/runtime foundations, typed Definitions, scheduled processes, finite world bounds, Terrain/Geometry/Navigation, deterministic movement/pathfinding, autonomous-agent foundations, finite Water/Soil/surface hydrology, and observer-only diagnostic tooling.

Those runtime systems remain separate from world generation. A generator authors initial facts; it does not remain a second runtime owner.

## World-generation architecture law

```text
semantic intent
    ↓
world-specific calibration
    +
versioned recipe
    ↓
replaceable spatial algorithm
    ↓
immutable generated fact
```

A later stage may consume an earlier fact but must not silently steal its responsibility.

## Stage 0 — V12 ordinary terrain **[COMPLETE]**

V12 established the generation architecture and accepted ordinary land-surface morphology: broad uplift, ordinary hills/depressions, rolling relief, rugged structures and bounded readable slopes.

## Stage 1 — V13 Mountain Systems **[COMPLETE]**

V13 added independently calibrated/replaceable mountain morphology. Mountain semantic controls retain distinct meanings, and mountains do not own water/coast membership or rock identity.

See [V13 Mountain Generation](systems/world-generation/mountain-generation.md).

## Stage 2A — V14 Standing-Water Bathymetry **[COMPLETE]**

V14 preserved wet/dry membership while re-authoring submerged depth. Accepted properties include smooth coast/littoral descent, broad deep structure, no cell-scale depth noise, bounded slopes and independent near-shore/deep-interior responsibilities.

See [V14 Standing-Water Bathymetry](systems/world-generation/bathymetry-generation.md).

## Stage 2B — Continental Domain and Inland Lakes **[COMPLETE]**

Stage 2B separated two responsibilities that had previously been coupled accidentally.

### Continental domain

- `RegularizedGraphLandmassSilhouetteAlgorithm` is the accepted standard owner;
- external ocean/continent topology is produced by an irregular geometric graph + regularized land phase;
- `Fragmentation` owns macro land-mass separation/connectivity only;
- local V12 relief cannot reopen the accepted shoreline;
- obvious grid/Voronoi structure, thin tendrils and sharp coarse appendages are rejected by the model rather than painted over downstream.

### Inland lakes

- lakes are selected from real broad continental lowlands;
- lake existence is independent of `Fragmentation` as a direct control;
- current generated water surface is `Z = 0`;
- a geographic lake is rejected unless its geometry can support a meaningful `>= 5 Z` depth profile;
- lake shoreline membership exists before mountains;
- mountains therefore naturally respect lakes instead of being carved after the fact;
- lake-bottom refinement uses broad shoreline-distance morphology and deterministic asymmetry, not random pits;
- lake bathymetry cannot change the accepted lake footprint;
- no synthetic one-block dry shoreline ridge is authored.

See [Continental Domain and Inland Lakes](systems/world-generation/landmass-and-inland-lakes.md).

## Engineering pass A — Large-world performance **[NEXT]**

Target: make at least `10,000 × 10,000` worlds a practical default-scale workload and keep larger worlds architecturally possible.

This is not a blind micro-optimization pass. It will first identify asymptotic and memory costs across the complete accepted pipeline, research appropriate mathematical/algorithmic alternatives, and then replace expensive representations/algorithms only when visual equivalence can be demonstrated.

Required acceptance:

- deterministic output semantics remain stable or receive an explicit revision if intentionally changed;
- no loss of accepted landmass/lake/mountain/bathymetry quality;
- measured time and memory budgets on representative large worlds;
- no hidden full-world duplicate passes where local/chunked/streaming computation is sufficient;
- documentation of complexity, memory ownership and any approximations introduced.

## Engineering pass B — World-generation preview UI **[AFTER PERFORMANCE]**

Target: make generation inspection pleasant without changing world semantics.

Planned work:

- align styling with scenario UI;
- group settings into clear collapsible sections;
- provide concise tooltips for each semantic control;
- remove large explanatory prose from the control panel;
- move Generate to a prominent top position;
- bind generation to `G`;
- move expensive generation off the render thread;
- show progress and current generation stage while work is running.

Presentation remains observer-only; UI code never becomes a generation owner.

## Future Stage 2 hydrography **[DEFERRED UNTIL AFTER ENGINEERING PASSES]**

Hydrography will restart from the accepted final elevation instead of reviving the deleted experiment.

The intended responsibility chain is:

```text
accepted final terrain
        ↓
drainage / catchment topology
        ↓
river network
        ↓
channel / valley morphology
```

Important planned boundary:

```text
river routing owns where the river goes
channel morphology owns how terrain is incised
```

A river must become real generated geometry, not a visual blue line. Future drainage analysis must not silently regenerate accepted continents/lakes merely to make routing easier.

## Later milestones

### Coherent geology

Replace placeholder geology with coherent formations/strata and only the deposit bodies genuinely required by the model. Rock identity remains separate from mountain shape.

### Caves

Generate coherent underground voids behind a replaceable algorithm using available morphology/geology causes.

### Causal surface/subsurface material synthesis

Combine final morphology, hydrographic/depositional facts, geology and calibrated semantic material/Soil definitions. Avoid permanent feature-name shortcuts such as `river -> sand` or `mountain -> granite`.

### Complete dry-world acceptance

Accept terrain, mountains, standing-water geometry, rivers/valleys, geology/deposits, caves and surface/subsurface materials before initial finite Water is authored.

### Finite initial Water fill

Fill already-created oceans/lakes/channels with finite Water. Generation owns initial placement only; normal runtime liquid/hydrology systems own subsequent Water behavior.

### Runtime handoff audit

Verify generated facts materialize exactly once and no generator/preparation/bootstrap object remains a second runtime owner.

## Documentation rule

The repository [Documentation Guide](guides/documentation.md) is already the canonical rule requested for future work: documentation must serve both a non-programmer and an implementer. Non-trivial system pages explain purpose in plain language, ownership, diagrams/lifecycle, exact algorithms, formulas with every symbol defined, invariants, limitations, representative code/tests and classified sources. Project-specific algorithms are labelled as such rather than given misleading academic citations.

A feature is not complete merely because code/tests are green if its normative documentation still requires chat history to understand the implementation.
