# Generated World Diagnostics

## Purpose

Provide one deterministic audit vocabulary for generated-world runs in headless CI and interactive desktop sessions.

Diagnostics observe existing generated facts and runtime state. They do not alter generation, scheduling, Water, Soil, Terrain or balancing rules.

## Audit boundaries

Runtime state is observed through the existing generated-world probe:

```text
WorldGenesis
    ↓
GeneratedWorldBootstrap
    ↓
WorldAtlas ──────────────┐
                        │
SimulationRuntime ──────┼─> GeneratedWorldDiagnosticsProbe
                        │             ↓
SimulationView ─────────┘     immutable runtime snapshot
```

Initial generated material composition has a separate semantic-key snapshot:

```text
WorldAtlas + TerrainPalette
          ↓
TerrainMaterialField
          ↓
GeneratedTerrainMaterialDiagnosticsProbe
          ↓
initial surface / solid-volume material counts
```

Both probes are intentionally on-demand. They are not registered in the scheduler and may scan the finite world when an explicit diagnostic checkpoint requests exact totals.

## Runtime facts

`GeneratedWorldDiagnostics` records:

- simulation tick;
- master seed;
- generation and RNG revisions;
- finite world bounds;
- concrete Terrain cell count;
- occupied Terrain column count;
- Atlas/runtime surface mismatch count;
- minimum and maximum generated surface Z;
- number of terminal drainage basins;
- maximum drainage contributing area;
- total free Water volume;
- total retained Water volume in Soil;
- number of cells containing free Water;
- number of cells containing retained Water;
- number of XY columns containing free Water;
- number of XY columns containing retained Water;
- maximum free-Water volume accumulated in one XY column;
- maximum retained-Water volume accumulated in one XY column;
- maximum number of Z cells containing free Water in one XY column.

The snapshot deliberately excludes wall-clock duration, renderer/camera state and logging configuration. Two deterministic runs can therefore compare the record directly.

## Generated Terrain material facts

`GeneratedTerrainMaterialDiagnostics` records the immutable pre-runtime material composition using stable semantic keys rather than runtime integer ids.

It reports exact counts for:

- each material exposed on generated XY surfaces;
- each material across the complete generated solid Terrain volume;
- terrain cells and columns;
- palette key and generation provenance.

The canonical text event is:

```text
event=world.generated.terrain-materials ...
```

This makes `topsoil`, `soil`, `sand` and `granite` distributions inspectable without exposing the compact per-column representation used by `TerrainMaterialGenerationStage`.

## Surface invariant

For every generated XY column the runtime audit compares:

```text
WorldAtlas.elevation().elevationAt(x, y)
```

with:

```text
SimulationView.terrainSurfaces().topZ(x, y)
```

`surfaceMismatches == 0` means the runtime surface still exactly represents the generated discrete Atlas surface at the audit checkpoint.

A later terrain-changing runtime system may intentionally make this value non-zero. The diagnostic reports that fact; it does not assume Atlas must continuously mirror lived Terrain.

## Water audit

The runtime audit independently sums Water through the existing read capabilities:

```text
SimulationView.water()
SimulationView.soilLiquids()
```

Free and retained volumes remain separate and are also exposed as `totalWaterVolume()` for convenient conservation checks.

The audit accumulates each XY column before discarding local detail. This distinguishes **spread** from **vertical concentration** without exposing Water storage:

```text
wetWaterColumns
wetSoilColumns
maximumFreeWaterColumnVolume
maximumRetainedWaterColumnVolume
maximumWetWaterCellsPerColumn
```

`maximumWetWaterCellsPerColumn` is intentionally a representation-independent count of occupied world cells, not a claim about meters of physical depth. Exact column volume is reported separately. A later physical measurement layer may derive human units when its scale contract exists.

No Water storage representation is exposed. The diagnostic scans semantic world coordinates only and computes all current Water totals/distribution facts in the same deliberate full-world audit pass.

## Logging

`GeneratedWorldDiagnosticsLog.info(snapshot)` emits the runtime event:

```text
event=world.generated.audit ...
```

`GeneratedTerrainMaterialDiagnosticsFormat` emits the initial material event shown above for developer/CI audits.

The existing desktop logging configuration supplies session/scenario context, persistence and rotation where runtime logging is used. Headless correctness tests assert immutable snapshots directly rather than parsing log text.

This keeps logs observational: enabling, disabling or redirecting logging cannot alter simulation authority.

## CI baseline

Headless generated-world integration uses the same `GeneratedWorldBootstrap` and ordinary `SimulationRuntime` path intended for future desktop generation. The current warmup fixtures use the canonical generated terrain palette rather than a synthetic uniform porous material.

Current scenarios prove:

1. a generated world with no configured Water source does not spontaneously create free or retained Water while the production scheduler runs;
2. Atlas-driven HydroClimate precipitation, infiltration and Water mechanics operate on generated multi-material Terrain through the production scheduler;
3. replaying the same seed, content setup and tick count produces the same complete diagnostic snapshot;
4. generated Atlas surfaces and runtime Terrain surfaces remain identical when no terrain-changing runtime mechanic is active;
5. generated HydroClimate cannot accidentally stack with legacy periodic atmospheric forcing;
6. Water spread/concentration diagnostics are deterministic across replay;
7. semantic terrain-material generation is independent from runtime registry ids and can be audited before materialization.

These are correctness gates, not performance thresholds.

## Representative evidence

The representative `32×32` workload now emits both material composition and runtime hydrology for the same fixed seed set.

Under the same climate forcing, total Water mass at tick `100` remained identical across seeds while retained Water varied strongly with generated material composition. Rock-exposed worlds retained less Water because granite has no Soil-retention aspect; soil-dominated worlds retained more. The free-Water remainder changed by exactly the corresponding amount.

This is useful causal evidence:

```text
relief / drainage
      ↓
generated Terrain materials
      ↓
existing material Soil properties
      ↓
retained vs free Water
```

It does **not** establish that any current material percentage or Water distribution is the desired balance. Diagnostics report facts; future evaluation/calibration owns interpretation.

## Checkpoints

The probes are intended for deliberate generated-world checkpoints such as:

- immediately after generation/materialization (`tick=0`);
- during deterministic warmup;
- at warmup completion;
- after a user-requested diagnostic capture;
- before/after calibration candidates in development tooling.

Warmup policy is separate from diagnostics. A snapshot reports facts at the requested tick; it does not decide how long a world should warm up or whether the result is viable.

New metrics should be added only when they represent an existing semantic fact or a concrete invariant we need to diagnose. Diagnostic objects must not become a second world model.

See [Terrain Generation](terrain-generation.md), [Generated World Runtime](generated-world-runtime.md), [Generated World Warmup](generated-world-warmup.md), [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Surface Hydrology](hydrology.md), and [Decision 017](../decisions/017-generated-world-diagnostic-audits.md).
