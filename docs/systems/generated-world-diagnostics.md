# Generated World Diagnostics

## Purpose

Provide one deterministic audit vocabulary for generated-world runs in headless CI and interactive desktop sessions.

Diagnostics observe existing generated facts and runtime state. They do not alter generation, scheduling, Water, Soil, Terrain or balancing rules.

## Audit boundary

```text
WorldGenesis
    ↓
WorldAtlasGenerator
    ↓
WorldAtlas ──────────────┐
                        │
production runtime ─────┼─> GeneratedWorldDiagnosticsProbe
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
- number of cells containing retained Water.

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

No Water storage representation is exposed. The diagnostic scans semantic world coordinates only.

## Logging

`GeneratedWorldDiagnosticsLog.info(snapshot)` emits one compact structured SLF4J event:

```text
event=world.generated.audit ...
```

The existing desktop logging configuration therefore automatically supplies session/scenario context, persistence and rotation. Headless tests assert the immutable snapshot directly rather than parsing log text.

This keeps logs observational: enabling, disabling or redirecting logging cannot alter simulation authority.

## CI baseline

The first headless integration checks intentionally use small bounded worlds and the production runtime path.

Current scenarios prove:

1. a generated world with no configured water source does not spontaneously create free or retained Water while the production scheduler runs;
2. existing precipitation, infiltration and Water mechanics operate on generated Terrain;
3. replaying the same seed, setup and tick count produces the same complete diagnostic snapshot;
4. generated Atlas surfaces and runtime Terrain surfaces remain identical when no terrain-changing runtime mechanic is active.

These are correctness gates, not performance thresholds.

## Next use

As generated-world bootstrap composition becomes a first-class runtime entry point, the same probe and log event should be invoked at deliberate checkpoints such as:

- immediately after materialization;
- after deterministic warmup;
- after a user-requested diagnostic capture;
- before/after calibration candidates in development tooling.

New metrics should be added only when they represent an existing semantic fact or a concrete invariant we need to diagnose. The diagnostic object must not become a second world model.

See [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Surface Hydrology](hydrology.md), and [Decision 017](../decisions/017-generated-world-diagnostic-audits.md).
