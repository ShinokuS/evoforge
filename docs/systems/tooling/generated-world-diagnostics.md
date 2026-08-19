# Generated World Diagnostics

## In plain language

Generated-world diagnostics are EvoForge's **measuring instruments**. They answer questions such as:

- Did the generated Terrain materialize exactly as intended?
- How many drainage basins exist?
- How much free Water versus Soil-retained Water exists after 100 ticks?
- Does replaying the same seed produce exactly the same observations?

Diagnostics never “fix” a world and never decide whether the result is aesthetically good. They only observe authoritative generated/runtime facts at deliberate checkpoints.

## Current status

There are two complementary diagnostic families:

```text
WorldAtlas + prepared/generated material facts
        ↓
GeneratedTerrainMaterialDiagnosticsProbe
        ↓
immutable initial material-composition snapshot

WorldAtlas + started SimulationRuntime/SimulationView
        ↓
GeneratedWorldDiagnosticsProbe
        ↓
immutable runtime checkpoint snapshot
```

Both are on-demand developer/audit tools. They are not scheduled simulation processes and may deliberately scan the finite world when an exact checkpoint is requested.

## Runtime snapshot

`GeneratedWorldDiagnostics` currently records deterministic facts including:

- simulation tick;
- master seed;
- generation/RNG revisions;
- finite world bounds;
- concrete Terrain cells and occupied XY columns;
- Atlas/runtime surface mismatch count;
- minimum/maximum generated surface Z;
- terminal drainage basin count and maximum contributing area;
- geology province/unit summaries where present;
- generated initial Water volume/column/shoreline facts;
- current total free Water and retained Soil Water;
- wet free-Water/retained-Soil cell and XY-column counts;
- maximum free/retained Water volume in one XY column;
- maximum vertical count of Water-occupied cells in one XY column.

The snapshot deliberately excludes wall-clock time, camera state, FPS and logging configuration so deterministic replays can compare records directly.

## Surface invariant

For each generated XY column the runtime probe compares:

```text
Atlas discrete surface = atlas.elevation().elevationAt(x,y)
Runtime surface        = simulationView.terrainSurfaces().topZ(x,y)
```

Then:

```text
surfaceMismatches == 0
```

means runtime Terrain still matches the original generated discrete surface at that checkpoint.

A future legitimate Terrain-changing mechanic may make this nonzero. The diagnostic reports the divergence; it does not assert that lived Terrain must eternally mirror the Atlas.

## Water accounting

Water totals are independently read from existing semantic capabilities:

```text
SimulationView.water()
SimulationView.soilLiquids()
```

The probe keeps free and retained Water separate and also exposes their sum for convenient conservation/accounting checks.

Per-column aggregation distinguishes spread from vertical concentration without exposing storage internals:

```text
wet Water columns
wet Soil columns
maximum free-Water column volume
maximum retained-Water column volume
maximum Water-occupied Z cells in one column
```

These are simulation units. A cell count is not silently labeled as metres.

## Terrain-material diagnostics

`GeneratedTerrainMaterialDiagnostics` observes immutable pre-runtime material composition using stable `TerrainMaterialKey`s rather than runtime integer definition IDs.

It counts:

- material exposure on generated XY surfaces;
- material occurrences through the complete generated solid volume;
- Terrain cell/column totals;
- profile/palette provenance and generation provenance.

The compact developer event is:

```text
event=world.generated.terrain-materials ...
```

This lets a developer inspect composition without exposing/depending on the compact internal per-column representation of `TerrainMaterialField`.

## Logging

Runtime snapshot formatting/logging can emit:

```text
event=world.generated.audit ...
```

Log text exists for human/CI inspection. Tests compare typed immutable diagnostics rather than treating logs as an authoritative database.

Enabling/disabling/redirecting logs cannot change simulation behavior.

## What diagnostics can prove

Diagnostics are useful for exact claims such as:

```text
same seed + same revision + same inputs
→ same checkpoint snapshot
```

or:

```text
no runtime Terrain-changing mechanic
→ Atlas/runtime surface mismatch = 0
```

They can also expose causal evidence. For example, representative generated worlds with more non-porous exposed rock can retain less Water in Soil under the same forcing.

That observation does **not** automatically mean the current material distribution is balanced/desirable. Measurement and acceptance policy remain separate.

## Checkpoints

Typical deliberate checkpoints:

```text
tick 0 after materialization/bootstrap
deterministic warm-up ticks
after a targeted scenario/action
a calibration comparison point
```

A probe does not decide which checkpoint is “equilibrium” or whether a world is viable.

## Invariants

- Diagnostics read existing authoritative/generated facts; they own no simulation state.
- Diagnostic capture never advances time or mutates the world.
- Snapshots contain deterministic simulation facts, not wall-clock/presentation facts.
- Stable semantic material keys are reported instead of runtime registry IDs.
- Full-world scans are deliberate checkpoint cost, not background per-tick behavior.
- Adding a metric requires an existing semantic fact/concrete diagnostic need; diagnostics do not become a second world model.

## Current limitations

There is no universal generated-world “quality score”, viability oracle, balance threshold database or visual-aesthetic metric.

Those would require concrete acceptance criteria in the stage that owns them. Manual visual acceptance remains necessary for aesthetics such as terrain readability.

## Code and tests

Primary code lives under:

```text
simulation/.../world/diagnostics/
simulation/.../world/diagnostics/warmup/
```

Generated World Audit tests/workflows use these same probes and production runtime paths.

## Sources

**Internal EvoForge tooling design.** Diagnostics are observer-only project infrastructure, not a scientific model.

See [Generated World Warm-up](generated-world-warmup.md), [World Atlas](../world-generation/world-atlas.md), [Generated World Runtime](../world-generation/generated-world-runtime.md), [Terrain Generation](../world-generation/terrain-generation.md), and [ADR-017](../../decisions/017-generated-world-diagnostic-audits.md).
