# ADR-014: Hydrologic climate normals are prepared facts, not runtime weather

- Status: Superseded
- Scope: Generated climate representation
- Decision: The durable principle that long-term climate conditions are prepared/generated facts distinct from current runtime Weather remains; the historical `HydroClimateSpec/HydroClimateField` representation has been superseded by current `ClimateSpec/ClimateNormalsField` physical climate contracts.

## Context

Elevation/drainage alone cannot determine how much atmospheric Water a world receives or how strongly exposed Water can be removed. Runtime precipitation/evaporation schedules also describe operational event timing, not long-term climate meaning.

## Decision

The historical slice introduced explicit long-term precipitation supply and potential evaporation as generated climate-normal facts, separate from Weather/runtime mutation. The important architectural decision was the separation:

```text
long-term prepared climate condition
        ≠
current weather/event schedule
        ≠
mutable Water/Soil state
```

The original concrete types (`HydroClimateSpec`, `HydroClimateField`, exact cell-volume/tick rates) were later replaced by a physical climate model based on `ClimateSpec`, `ClimateNormalsField`, `WaterDepthRate` and explicit physical-space/time conversion when runtime quantities are required.

## Why

Climate is environmental provenance/input; Weather is current process/state; Water/Soil are physical runtime owners. Keeping those layers separate allows eventful weather algorithms to change without rewriting long-term climate facts or Water ownership.

## Consequences

- Generated climate remains immutable preparation data.
- Runtime atmosphere realizes prepared conditions through its own replaceable plan/forcing seam.
- No climate-normal field directly mutates Water/Soil.
- User-facing generation should remain semantic rather than exposing internal pulse/rate solver knobs.
- Spatial climate variation can be added through explicit causal models instead of arbitrary noise.

## Alternatives considered

Random climate noise without causal inputs was rejected. Embedding runtime precipitation/evaporation schedules directly in Atlas was rejected because schedules are one realization, not climate meaning. Inferring atmospheric Water supply from drainage was rejected because drainage routes Water; it does not create it.

## Current implementation

`WorldSpec` currently carries `ClimateSpec`; `WorldAtlas` carries `ClimateNormalsField`; `AtmosphericRuntimePlan` composes runtime Weather/Water forcing from immutable prepared facts. Rainfall occurrence/regime calibration is a separate prepared layer. Historical `HydroClimate*` terminology should be treated as superseded design history rather than current normative vocabulary.

## Related documentation

- [World Atlas](../systems/world-generation/world-atlas.md)
- [Generated World Runtime](../systems/world-generation/generated-world-runtime.md)
- [Rainfall Regime Calibration](../systems/environment/rainfall-calibration.md)
- [Surface Hydrology](../systems/environment/hydrology.md)
