# Generated World Diagnostics

## Purpose

Provide one deterministic audit vocabulary for generated-world runs in headless CI and interactive desktop sessions.

Diagnostics observe existing generated facts and runtime state. They do not alter generation, scheduling, Water, Soil, Terrain or balancing rules.

## Audit boundary

```text
WorldGenesis
    ↓
GeneratedWorldBootstrap
    ↓
WorldAtlas ──────────────┐
                        │
SimulationRuntime ──────┼─> GeneratedWorldDiagnosticsProbe
                        │             ↓
SimulationView ─────────┘     immutable audit snapshot
                                      ↓
                            CI assertions / SLF4J summary
```

`GeneratedWorldDiagnosticsProbe` is intentionally on-demand. It may scan the complete finite `WorldBounds` volume to produce exact totals. It is not registered in the scheduler and does not run every tick.

## Current facts

Each snapshot records:

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

## Surface invariant

For every generated XY column the audit compares:

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

The audit independently sums Water through the existing read capabilities:

```text
SimulationView.water()
SimulationView.soilLiquids()
```

Free and retained volumes remain separate and are also exposed as `totalWaterVolume()` for convenient conservation checks.

The audit also accumulates each XY column before discarding local detail. This distinguishes **spread** from **vertical concentration** without exposing Water storage:

```text
wetWaterColumns
wetSoilColumns
maximumFreeWaterColumnVolume
maximumRetainedWaterColumnVolume
maximumWetWaterCellsPerColumn
```

`maximumWetWaterCellsPerColumn` is intentionally a representation-independent count of occupied world cells, not a claim about meters of physical depth. Exact column volume is reported separately. A later physical measurement layer may derive human units when its scale contract exists.

These distribution facts were added after the representative `32×32` audit showed that worlds with identical total Water mass could differ strongly in `wetWaterCells`. Cell count alone could not distinguish broad shallow spreading from concentration in deeper columns.

No Water storage representation is exposed. The diagnostic scans semantic world coordinates only and computes all current Water totals/distribution facts in the same deliberate full-world audit pass.

## Logging

`GeneratedWorldDiagnosticsLog.info(snapshot)` emits one compact structured SLF4J event:

```text
event=world.generated.audit ...
```

The existing desktop logging configuration therefore automatically supplies session/scenario context, persistence and rotation. Headless tests assert the immutable snapshot directly rather than parsing log text.

This keeps logs observational: enabling, disabling or redirecting logging cannot alter simulation authority.

## CI baseline

Headless generated-world integration uses the same `GeneratedWorldBootstrap` and ordinary `SimulationRuntime` path intended for future desktop generation.

Current scenarios prove:

1. a generated world with no configured Water source does not spontaneously create free or retained Water while the production scheduler runs;
2. Atlas-driven HydroClimate precipitation, infiltration and Water mechanics operate on generated Terrain through the production scheduler;
3. replaying the same seed, content setup and tick count produces the same complete diagnostic snapshot;
4. generated Atlas surfaces and runtime Terrain surfaces remain identical when no terrain-changing runtime mechanic is active;
5. generated HydroClimate cannot accidentally stack with legacy periodic atmospheric forcing;
6. Water spread/concentration diagnostics are themselves deterministic across replay.

These are correctness gates, not performance thresholds.

## Representative evidence

The first `32×32` audit showed non-trivial seed-dependent terrain/drainage diversity while preserving exact Water mass under the same closed-world uniform forcing. At tick `100`, for example, the same total Water volume was distributed across substantially different counts of wet cells between seeds.

That observation justifies measuring Water distribution. It does **not** by itself justify declaring any one distribution healthy, flooded or preferable. Diagnostics report facts; future evaluators own interpretation.

## Checkpoints

The same probe and log event are intended for deliberate generated-world checkpoints such as:

- immediately after bootstrap/materialization (`tick=0`);
- during deterministic warmup;
- at warmup completion;
- after a user-requested diagnostic capture;
- before/after calibration candidates in development tooling.

Warmup policy is separate from diagnostics. A snapshot reports facts at the requested tick; it does not decide how long a world should warm up or whether the result is viable.

New metrics should be added only when they represent an existing semantic fact or a concrete invariant we need to diagnose. The diagnostic object must not become a second world model.

See [Generated World Runtime](generated-world-runtime.md), [Generated World Warmup](generated-world-warmup.md), [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Surface Hydrology](hydrology.md), and [Decision 017](../decisions/017-generated-world-diagnostic-audits.md).
