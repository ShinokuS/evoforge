# Continuum World Development Plan

## Purpose

This is the executable development plan for replacing the legacy full-world raster generation line with the Continuum architecture defined by [ADR-024](../../decisions/024-continuum-large-world-architecture.md).

The plan is intentionally incremental. No phase is allowed to hide months of untestable infrastructure behind a future visual result. Each accepted phase leaves the repository green, exposes a small stable semantic capability, records performance evidence and visualizes spatial meaning as soon as that meaning exists.

The historical conceptual reset point is `37e6e43c0461765e860f643ad6655b3cd5941aa6` (Genesis + stateless RNG only). New code nevertheless branches from current `develop` and keeps all accepted non-worldgen mechanics.

## Development laws

Every Continuum change follows these rules.

### One semantic responsibility per block

A block owns one explainable fact or transformation. Examples: continental structure, uplift structure, ridge topology, basin hierarchy, terrain sampling, page materialization.

Do not create a universal mutable generation context, universal world feature base class, global stage enum or central `switch` over all generated content.

### Contracts before alternate implementations, but only at real seams

A contract is justified when the output is independently meaningful or when a consumer must remain independent from the producing algorithm.

Do not add strategy/factory/plugin layers around private mathematics merely because an implementation might change someday.

### No numeric generator lineage

New Continuum classes and packages are named by meaning, not historical order. Do not introduce `V16`, `V17`, `NextGenerationTerrain`, `NewWorldGenerator2`, etc.

### Every spatial result is observable

If a generated fact has spatial meaning, the accepted slice should normally include a visual overlay. When a fact is not yet meaningful to render, the PR must state why and identify the first phase where it becomes visible.

### Every scaling-sensitive result is profiled immediately

A structure that might grow with world size is not accepted on a 64×64 fixture alone. Logical scale checks start at 10k and 100k as soon as the contract can express them; 1M logical dimensions are used once no full-area materialization is involved.

### Representation is replaceable

Tests assert semantic behavior, not private storage layout. Representation-specific tests are allowed only for explicit performance/storage components such as a cache or sparse page store.

### Legacy isolation

New Continuum domain packages do not import legacy V-numbered generation implementations. A temporary application-level adapter may expose both paths during migration. The goal is deletion of legacy worldgen after cutover, not permanent dual support.

---

# Verification model

Every phase uses the same six-gate checklist.

## Gate A — Semantic correctness

Headless tests cover invariants owned by the new fact/algorithm.

Examples:

- topology is valid;
- identifiers are stable;
- values stay inside declared semantic ranges;
- dependency boundaries are respected;
- no consumer depends on concrete algorithm type.

## Gate B — Determinism and locality

Where applicable, tests prove:

1. same authoritative inputs → same result;
2. query order does not change result;
3. adjacent requests agree at shared boundaries;
4. tiled and equivalent untiled requests agree;
5. cache eviction/rematerialization is semantically invisible;
6. rendering/inspection order cannot change generated facts;
7. adding an unrelated random purpose does not move existing samples.

## Gate C — Substitution

For every real algorithm seam, orchestration is tested with a tiny alternate implementation. The test proves consumers depend on the semantic fact/contract and do not contain concrete-class branches.

## Gate D — Performance and allocation

Relevant PRs report at least:

- logical world width/height/depth envelope;
- initialization time;
- cold query/materialization latency;
- warm query/materialization latency;
- peak resident heap;
- allocation traffic;
- cache resident bytes when applicable;
- count of structural features when useful;
- deterministic fingerprint/checksum.

Performance numbers are evidence, not hidden implementation semantics.

## Gate E — Visual diagnostics

Spatial phases add one or more overlays to the existing world-generation workspace. Debug rendering reads the same generated fact contracts as headless tests.

The visualizer must support:

- full-world overview;
- pan/zoom without regenerating the whole world;
- display of active query resolution/LOD;
- optional feature overlays;
- materialized-page/cache diagnostics once paging exists;
- seed and logical world dimensions in diagnostics.

## Gate F — Manual acceptance

Aesthetic/geographic phases are not merged merely because tests are green. Representative seeds are inspected at several scales and explicitly accepted.

The acceptance set grows over time but must include at least:

- a compact 10k world for quick inspection;
- a 100k world for scale behavior;
- several deterministic seeds;
- global overview and local zoom.

---

# Continuous benchmark ladder

Not every rung runs on every unit-test invocation. They are separate evidence tiers.

## Tier 1 — Micro correctness

Tiny synthetic worlds/regions. Runs in normal unit tests.

Purpose: exact topology, edge cases, substitution, deterministic vectors.

## Tier 2 — Routine logical scale

10,000 × 10,000 logical world.

Purpose: normal PR profiling and visual acceptance without full-world materialization.

## Tier 3 — Large logical scale

100,000 × 100,000 logical world.

Purpose: prove architecture does not fall back to `O(area)` resident storage and validate feature-aware overview/zoom.

## Tier 4 — Extreme address-space probe

1,000,000 × 1,000,000 logical coordinates, or larger when safe.

Purpose: architecture/scaling proof only. No requirement to generate every cell. Initialization and bounded queries must remain possible without allocation proportional to the trillion-cell address space.

## Failure rule

A phase that unexpectedly introduces resident memory proportional to total logical area fails the architecture gate even if a smaller benchmark happens to fit in RAM.

---

# Visual quality metrics

Automated metrics complement, not replace, manual inspection.

## Terrain quantization / noise

The discrete Z materialization suite will measure at least:

- isolated one-cell elevation islands;
- one-cell contour bands/terraces;
- minimum connected support width for a Z step where physically expected;
- slope discontinuity outliers;
- local curvature outliers;
- multi-resolution consistency.

A single-cell Z transition is not universally forbidden — cliffs and explicit sharp structures may justify one — but ordinary smooth geomorphology may not create widespread one-cell zebra/noise layers.

## Mountains

Metrics/diagnostics should include where meaningful:

- connected mountain-system length;
- principal ridge continuity;
- chain aspect ratio;
- branch count/order;
- peak spacing/distribution;
- pass/saddle structure;
- foothill transition width;
- relationship between drainage valleys and ridges.

A collection of round high-elevation blobs is not acceptable mountain morphology.

## Rivers and drainage

Check:

- acyclic downstream topology where the model requires it;
- stable receiver/catchment identity;
- tributary continuity;
- monotone hydrologic connectivity;
- river order/discharge consistency;
- no visual disappearance of major rivers solely due to coarse LOD.

## Lakes

Check:

- basin/spill topology;
- positive meaningful capacity;
- shoreline continuity at materialized detail;
- consistent water surface elevation;
- nesting/merge semantics where applicable;
- major lakes remain represented in coarse overview.

## Coasts and ocean

Check:

- coastline continuity;
- no pervasive cell-scale sawtooth;
- bays/peninsulas have plausible support widths;
- shelf/slope/deep-ocean transition continuity;
- deep-ocean structural features occur at appropriate scales.

---

# Phase 0 — Foundation harness: prove a world can be huge without existing as cells

## Goal

Create the minimum Continuum infrastructure needed to prove the architectural premise before implementing natural geography.

## New semantics

Introduce only the concrete concepts required for bounded deterministic spatial requests. Candidate responsibilities:

- immutable logical world address-space descriptor using existing world coordinate semantics;
- deterministic Continuum genesis/random scope adapter that does not require legacy `GenerationRevision` routing;
- a bounded 2D sample/materialization request carrying world-space extent and requested sample resolution;
- an immutable materialized scalar page/result used only by the proof field;
- a bounded page cache as a separate performance component, not authority.

Exact type names are chosen while coding based on the smallest real consumer. Do not create a universal query framework.

## Proof implementation

Use a deliberately simple deterministic analytic field whose value can be queried at arbitrary global coordinates without full-world allocation. It is not a terrain algorithm and will be deleted/replaced once structural geography exists.

## Tests

- 10k, 100k and 1M logical dimensions construct without area-sized allocation;
- point values are independent of query order;
- adjacent materialized pages agree at their boundary;
- equivalent one-page and multi-page requests produce equal samples;
- evict/reload reproduces identical content;
- cache has an explicit byte/page budget;
- alternate proof-field implementation can substitute through the real seam.

## Performance evidence

Record:

- construction memory/time for 10k / 100k / 1M logical worlds;
- cold/warm 256×256 materialization latency;
- cache peak resident bytes;
- allocation traffic.

No `O(world area)` arrays are allowed.

## Visualization

World Generation workspace gets a new Continuum mode:

- logical width/height including 100k+;
- immediate full-world overview generated at viewport resolution;
- zoom/pan requests bounded pages;
- overlay for requested page boundaries, sample resolution and cache residency.

## Acceptance

A 100,000×100,000 and 1,000,000×1,000,000 logical proof world can be opened and navigated without a full-world generation pause or memory growth proportional to area.

---

# Phase 1 — Structural spatial index / World Atlas skeleton

## Goal

Create a compact immutable container/index for scale-aware geographic features without deciding final geology yet.

## Semantics

Introduce stable feature identity and area/scale queries only as required by the first structural feature consumer.

Avoid committing prematurely to quadtree, R-tree or another public representation. The implementation may use one internally and change later.

## Tests

- deterministic feature lookup;
- insertion/build order independence where promised;
- query does not miss intersecting features;
- no duplicate semantic features across tile boundaries;
- alternate index implementation substitution if a stable index seam is genuinely required.

## Performance

Profile lookup cost and memory against number of features, not number of world cells.

## Visualization

Overlay feature bounds/IDs and LOD filtering over 10k/100k/1M logical worlds.

---

# Phase 2 — Continental and oceanic structure

## Goal

Author global land/ocean structure without a full-resolution height map.

## Candidate model direction

Use a compact graph/region process capable of expressing:

- continental and oceanic provinces;
- large land masses;
- meaningful separation/connectivity;
- coast-support structure at several scales.

The first implementation is replaceable. It must not expose its graph tessellation as geography semantics unless a real downstream fact requires it.

## Required properties

- deterministic global identity;
- scale-aware shape, not 1-cell noise;
- no mandatory full-world raster;
- bounded query/materialization;
- stable shared boundaries between requests.

## Tests

- requested land/ocean semantic intent is respected within defined tolerances;
- connected-component topology metrics;
- feature minimum-width checks;
- overlap/page-boundary consistency;
- scale independence over 10k and 100k worlds.

## Visualization

Immediately render:

- continental/oceanic regions;
- structural control features;
- resulting coarse coast;
- component/width diagnostics.

Manual acceptance focuses on natural large-scale silhouettes, bays, peninsulas and absence of noisy appendages.

---

# Phase 3 — Geological provinces and tectonic structure

## Goal

Give large terrain forms causal geological support rather than adding independent noise layers.

## Structural facts

Only after concrete model selection, introduce facts such as:

- crust/geological province;
- tectonic boundary/fault structure;
- boundary interaction type/strength;
- uplift/subsidence influence.

## Model research direction

Evaluate compact plate/province graph approaches and physically inspired but computationally bounded tectonic approximations. Full plate-physics simulation is not required unless evidence shows it is the best trade-off.

## Tests

- province coverage/topology;
- boundary continuity;
- deterministic interaction fields;
- uplift support width/continuity;
- no dependence on viewport/materialization order.

## Visualization

Overlays:

- provinces;
- boundaries/faults;
- interaction type;
- uplift/subsidence heat map.

Large worlds are inspected at continental scale before any mountain surface is accepted.

---

# Phase 4 — Orography: mountain systems, ridges, passes and foothills

## Goal

Replace mountain blobs with structural mountain systems.

## Model direction

Generate orography from uplift/geological causes, then form connected ridge networks and secondary structures. Evaluate stream-power/erosion-inspired methods and feature-network terrain synthesis rather than independent radial mountain stamps.

## Facts

Potential independent facts include:

- mountain-system identity;
- principal/secondary ridge network;
- ridge prominence/support scale;
- passes/saddles where meaningful;
- broad foothill influence.

Do not invent a fact merely to mirror an implementation array.

## Tests

- chain continuity and aspect ratio;
- deterministic branching;
- no isolated blob-only system under normal mountain intent;
- support width appropriate to requested scale;
- boundaries identical across local materializations.

## Performance

Scale with number/complexity of mountain features. No continent-sized dense mountain raster required for resident authority.

## Visualization

Mandatory overlays:

- uplift;
- ridge skeleton;
- system IDs;
- influence/prominence;
- first coarse reconstructed relief.

Manual acceptance occurs on 10k and 100k worlds before proceeding.

---

# Phase 5 — Drainage, watersheds and erosion coupling

## Goal

Make valleys and water routing part of the same large-scale geomorphology rather than a late painted effect.

## Model direction

Evaluate hierarchical drainage generation and stream-power erosion coupling. The accepted model must preserve closed/declared world-boundary semantics and work without a permanently resident full DEM.

Global analysis that genuinely needs raster-like work must be tile-streaming/out-of-core with bounded memory.

## Facts

- drainage/receiver topology;
- catchment/watershed identity;
- accumulation/discharge potential;
- stream hierarchy.

## Tests

- receiver/topology validity;
- watershed partition consistency;
- deterministic accumulation;
- boundary semantics;
- local/global query agreement;
- no materialization-order dependence.

## Visualization

- watershed coloring;
- flow/receiver graph;
- accumulation heat map;
- stream hierarchy over orography.

---

# Phase 6 — Depression hierarchy and lakes

## Goal

Lakes become consequences of real basins, spill topology and water inventory rather than selected negative-elevation patches.

## Model direction

Use a Depression-Hierarchy-style structural representation and evaluate Fill–Spill–Merge semantics for later runtime hydrology.

## Facts

As justified by consumers:

- basin identity;
- pit/minimum;
- spill point/elevation;
- parent/receiver relation;
- capacity/area-volume relationship;
- current generated initial water level/volume if initial climate/hydrology already supplies it.

## Tests

- hierarchy acyclic/valid;
- spill relations deterministic;
- water level produces one consistent shoreline;
- basin capacity non-negative and monotone;
- nested/merged basin cases;
- major lake feature remains discoverable at coarse LOD.

## Visualization

- basin IDs/bounds;
- depression tree;
- spill points;
- candidate/final lake surface;
- shoreline at several zoom levels.

This phase explicitly closes the regression class where large-world lakes exist but disappear from 2D presentation.

---

# Phase 7 — River network as geographic features

## Goal

Promote significant drainage paths into scale-aware river features before detailed channel cells exist.

## Facts

- river/segment identity;
- source/receiver/tributary relationships;
- order;
- discharge potential;
- width/depth envelope or model inputs when physically justified.

## Tests

- tributary graph consistency;
- continuity through page boundaries;
- river scale/order monotonicity;
- major river discoverability at global LOD.

## Visualization

Feature-aware river rendering from world overview down to local channels.

---

# Phase 8 — Continuous terrain surface reconstruction

## Goal

Produce a high-quality continuous/fixed-point surface from accepted structural geography.

## Inputs

Continental support, geology/uplift, orography, drainage/valleys, basins and other already accepted facts only as causally required.

## Model requirements

- deterministic arbitrary-coordinate query;
- bounded regional materialization;
- continuous/high-precision representation before integer Z;
- scale-dependent feature amplitude/support;
- no pervasive cell-scale random height texture;
- exact boundary agreement between independently requested regions.

Local fine detail may use deterministic procedural texture only when constrained by geomorphic scale and upstream structure. Noise is a detail tool, never the owner of macro geography.

## Tests

- point-query determinism;
- tiled/untiled equivalence;
- boundary continuity;
- slope/curvature bounds where model promises them;
- multi-resolution consistency;
- structural alignment of valleys/ridges/basins.

## Visualization

- continuous elevation;
- slope;
- curvature;
- structural contribution overlays;
- 3D sampled mesh at requested resolution.

---

# Phase 9 — Discrete Z geometry materialization and anti-noise gate

## Goal

Translate the continuous surface into EvoForge's discrete Shape/Z geometry without reintroducing one-cell contour noise.

## Rules

Quantization policy is an independently replaceable materialization concern. It may not rewrite large-scale geography to hide upstream defects.

## Tests

Automated fixtures and representative generated regions measure:

- isolated Z cells/components;
- one-cell terraces and contour bands;
- support widths;
- local slope transitions;
- Shape continuity;
- equivalence after page eviction/rematerialization.

Tests distinguish intentionally sharp cliffs from ordinary smooth terrain.

## Visualization

A diagnostic mode switches among:

- continuous surface;
- integer Z;
- Shape geometry;
- highlighted one-cell bands/outliers.

Manual acceptance is mandatory before this becomes runtime terrain.

---

# Phase 10 — Coasts and ocean bathymetry

## Goal

Create realistic water boundaries and ocean-floor structure from continental/geological causes.

## Structural direction

Support meaningful concepts such as:

- shelf;
- continental slope;
- abyssal regions;
- ridges;
- trenches;
- seamount/basin structures where justified.

Do not generate deep ocean as a uniform radial descent to one center.

## Tests/visualization

Measure and show coastline/shelf width, depth continuity and structural ocean-floor features at multiple scales.

---

# Phase 11 — Bounded terrain pages and residency

## Goal

Make exact detailed terrain available to runtime/presentation consumers with memory bounded by an explicit cache budget.

## Components

Separate responsibilities:

- immutable source/materializer;
- page key/request semantics;
- resident cache;
- asynchronous presentation preparation where useful;
- diagnostics.

The cache never owns world truth.

## Tests

- hard memory/page budget;
- deterministic eviction;
- reload equality;
- concurrent/background preparation cannot mutate authority;
- locality/query patterns do not alter output;
- 100k/1M worlds show similar resident detailed-terrain memory at the same cache budget.

## Visualization

- page boundaries;
- resident/cold pages;
- request queue;
- LOD/sample density;
- generation time per page.

---

# Phase 12 — Feature-aware world overview / clipmap-style presentation

## Goal

Make the world inspectable from complete overview to exact cells without materializing full resolution.

## Rendering model

Use viewport-resolution terrain sampling combined with direct rendering of important structural features. Consider clipmap-style nested levels for efficient camera movement.

## Requirements

- 10k, 100k and larger dimensions selectable in GUI;
- immediate low-detail overview;
- progressive refinement on zoom;
- major coasts/ridges/rivers/lakes remain visible at meaningful LOD;
- rendering cache/prefetch cannot change generated/simulation truth.

## Acceptance

Manual navigation across a 100k world must feel like viewing one continuous world rather than triggering full regeneration jobs.

---

# Phase 13 — Sparse generated terrain deltas

## Goal

Persist and query lived terrain change without copying the immutable generated baseline.

## Model

```text
authoritative terrain query
    = generated baseline
    overlaid by domain-owned runtime delta
```

The exact delta representation is chosen from measured mutation patterns. It may be paged/sparse; no assumption that every world cell needs a runtime record.

## Tests

- generated baseline is unchanged by runtime edits;
- edited query wins over baseline;
- unedited coordinates reconstruct from Genesis/structural facts;
- save/load/evict/reload preserves edits exactly;
- sparse storage growth correlates with actual mutations, not logical world volume.

## Visualization

Overlay generated baseline vs runtime modified regions/pages.

---

# Phase 14 — Sparse subsurface geology and caves

## Goal

Expand Z dramatically without dense XYZ allocation.

## Model direction

Start from compact structural facts:

- strata/volumes;
- cave graph/chambers/passages;
- faults/fractures where already meaningful;
- implicit local geometry.

Materialize sparse 3D bricks/cells only where a consumer requires detailed topology. Borrow hierarchical sparse-volume principles rather than implementing a universal dense voxel world.

## Tests

- increasing legal Z depth with no added subsurface features causes negligible resident-memory growth;
- cave topology deterministic across local materialization boundaries;
- sparse brick eviction/reload equality;
- surface and subsurface interfaces remain consistent.

## Visualization

- cave graph;
- geological cross-section;
- Z slices;
- sparse brick residency.

---

# Phase 15 — Runtime entity/storage scalability

## Goal

Ensure hundreds of thousands or millions of runtime objects do not require heavyweight Java-object graphs or per-frame updates.

## Direction

Preserve EvoForge fact ownership while allowing each hot owner to choose compact primitive/paged storage.

This is not a universal ECS migration. `Spatial`, needs, growth, inventory and other domains continue to own their facts independently.

## Tests/performance

Representative entity counts grow by powers of ten. Measure:

- resident bytes/entity per owner;
- lookup/mutation latency;
- paging/residency cost;
- creation/removal cost;
- deterministic iteration semantics where required.

---

# Phase 16 — Temporal Continuum / event-driven distant simulation

## Goal

Keep every object causally alive regardless of player location without updating every object every tick.

## Direction

Evolve existing scheduling behind domain semantics toward:

- next meaningful transition scheduling;
- exact analytical progression;
- deterministic compact timer/event storage;
- invalidation/rescheduling when causes change;
- potentially hierarchical timing-wheel or another measured scheduler representation.

## Critical invariant

There is one simulation model. Distance/visibility never selects a cheaper authoritative rule.

## Tests

For mechanics migrated to analytical/event-driven progression:

- per-tick reference fixture and optimized progression reach identical authoritative checkpoints;
- observing/teleporting camera does not alter result;
- object on near and far coordinates behaves identically under equal causes;
- event ordering/tie-breaking deterministic;
- page residency does not suppress due events.

## Performance

Benchmark 10k, 100k, 500k and eventually 1M scheduled subjects as mechanics permit. Measure due-event cost separately from dormant-state cost.

## Visualization

Developer overlays show scheduled next event/time and resident/cold owner pages without changing scheduling.

---

# Phase 17 — Generated-world runtime cutover

## Goal

Use Continuum as the sole production generated-world path.

## Preconditions

The new path must have accepted:

- structural geography;
- high-quality local terrain;
- hydrology features;
- bounded materialization;
- large-world visualizer;
- runtime terrain handoff/query semantics;
- persistence/provenance decisions required by the then-current save model.

## Cutover procedure

1. composition root selects Continuum;
2. scenarios/tests migrate through semantic adapters;
3. compare runtime owners for identical contract expectations;
4. remove legacy V-numbered generator implementations and revision routing that have no persistence consumer;
5. delete temporary compatibility adapters;
6. reconcile canonical docs.

The legacy implementation is deleted, not left as an alternate branch that every future feature must support.

---

# PR structure

Do not implement Phases 0–17 in one PR.

Preferred sequence:

```text
PR A — Continuum constitution + executable plan
PR B — Phase 0 query/materialization proof + visualizer
PR C — structural spatial index when justified
PR D — continental structure
PR E — geology/tectonics
PR F — orography
...
```

A larger semantic phase may itself use multiple PRs when its contract, first algorithm and visual tooling can be independently accepted.

Each PR should consist of small green commits. A commit should normally introduce one of:

- a semantic fact/contract + tests;
- one implementation + tests;
- one performance harness;
- one visual adapter;
- one documentation reconciliation.

Do not mix several unproven geographic models into one commit.

---

# Branch and merge policy

Continuum work branches from `develop`.

Spatial/aesthetic PRs remain Draft until automated gates are green and manual visual acceptance has occurred. Performance-sensitive PRs remain Draft until representative scale evidence is recorded.

A failed model may be replaced inside its isolated semantic seam without rewriting accepted downstream consumers. If a semantic contract itself proves wrong, revise the smallest owning contract explicitly rather than hiding the mismatch behind adapters.

---

# Research policy

Each major natural-world model is researched immediately before implementation, using primary literature and proven large-scale implementations where possible.

Research notes must distinguish:

- physical/geoscience model;
- graphics/procedural synthesis technique;
- data structure/storage technique;
- EvoForge-specific inference/design choice.

A citation is not evidence that EvoForge implements full physical fidelity. The owning system document records exactly which equations/ideas were adopted, approximated or rejected.

Expected research checkpoints include:

- continental/plate/province synthesis;
- tectonic uplift and erosion;
- drainage/catchment algorithms;
- depression hierarchy and Fill–Spill–Merge;
- out-of-core DEM processing;
- feature-network terrain synthesis;
- multi-resolution terrain/clipmaps;
- sparse volumetric structures;
- event scheduling at very large entity counts.

---

# Definition of success

The Continuum migration is successful when all of the following are true:

1. A 100k+ logical world can be opened, inspected and queried without full-world cell generation.
2. Increasing empty logical world dimensions does not cause resident terrain memory to grow with total cell count.
3. Global overview and local exact materialization represent the same structural geography.
4. Mountains are connected geomorphic systems rather than independent height blobs.
5. Lakes arise from basin topology and remain visible/meaningful at large scale.
6. Rivers arise from drainage structure and remain continuous across scale/page boundaries.
7. Ordinary 2D terrain does not exhibit pervasive one-cell Z noise/terraces.
8. Ocean/coast/bathymetry have coherent scale-aware structure.
9. Large Z depth is sparse rather than a dense volume cost.
10. Runtime edits grow sparse state according to actual change, not total world volume.
11. Distant objects obey the same authoritative mechanics as nearby objects without mandatory per-frame/per-tick polling.
12. Every major semantic block is independently testable, replaceable and visually diagnosable.
13. The legacy V-numbered generation line can be deleted without changing the new architecture.
