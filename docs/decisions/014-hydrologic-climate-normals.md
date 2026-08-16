# Decision 014 — Hydrologic climate normals are generated facts, not runtime weather

**Status:** Accepted

## Problem

Elevation and drainage define where Water can move, but not how much atmospheric Water the world receives or how strongly exposed Water can be removed. Deriving initial hydrology or later ecological potential from drainage alone would require arbitrary channel thresholds and quantities.

Existing precipitation and evaporation schedules are operational runtime pulse timing. Treating those schedules as climate would mix long-term environmental conditions with the mechanism used to realize them during simulation.

## Decision

`WorldSpec` includes `HydroClimateSpec`, containing two exact long-term rates:

- precipitation supply;
- potential evaporative demand.

Both use `CellVolumeRate` (`cell-volume units / simulation tick`).

`WorldAtlas` owns the resulting immutable `HydroClimateField`. The first authoring algorithm is deliberately uniform: each XY column receives the requested normal unchanged. This is not a claim that climate must remain spatially uniform. It avoids inventing latitude, wind, temperature or random climate noise before those have causal models and consumers.

The compatibility `WorldSpec(WorldBounds)` constructor uses `HydroClimateSpec.UNFORCED` (zero supply and zero demand). It introduces no hidden baseline climate into existing callers.

Climate normals are not weather events. They do not schedule rain, remove Water or mutate Soil. A later runtime-forcing bridge may realize these normals through precipitation/evaporation processes while preserving the existing authoritative hydrology systems.

## Consequences

- hydrologic supply/demand become explicit generation inputs and provenance through `WorldSpec`;
- Atlas can reason about long-term water availability without owning runtime Water;
- no arbitrary reference period or real-world tick duration is needed;
- old `WorldSpec(bounds)` callers remain deterministic and explicitly unforced;
- spatial climate variation is deferred until a real causal input such as atmospheric circulation, temperature or another accepted model exists;
- temperature, seasonality, wind, biome and weather anomalies remain separate future semantics.

## Rejected directions

Random climate noise was rejected because variation without a causal model would make visual texture into simulation physics.

Embedding `PrecipitationSchedule` or `EvaporationSchedule` in Atlas was rejected because schedules are runtime realization, not long-term generated facts.

Inferring water supply from drainage contributing area was rejected because drainage routes supplied Water; it does not create atmospheric Water.
