# ADR-015: Prepared climate is realized through existing hydrology

- Status: Superseded
- Scope: Runtime atmospheric forcing bridge
- Decision: The original `HydroClimateForcingSystem` representation has been superseded by current `AtmosphericRuntimePlan` composition, while the durable rule remains: prepared climate/Weather forcing must feed existing Water/Soil owners instead of becoming a second hydrology engine.

## Context

A generated long-term climate fact needs a runtime realization. The original `HydroClimateField` slice needed exact fractional forcing without arbitrary pulse intervals, mutable carry or direct Atlas ownership of Water.

## Decision

Historically an exact rational rate `p/q` assigned amount at positive absolute tick `t` as:

```text
floor(p*t/q) - floor(p*(t-1)/q)
```

so cumulative forcing over ticks `1..T` was exactly `floor(p*T/q)` without mutable fractional carry.

Potential evaporation was evaluated against interval-start state, then precipitation was added at the interval boundary. Source and sink remained separate because exposure/unfulfilled accounting differs.

The concrete `HydroClimateForcingSystem/HydroClimateField` path was later replaced by the broader physical-climate/weather composition model. The canonical principle is now expressed through `AtmosphericRuntimePlan`/`AtmosphericWaterForcing`: one-shot composition reads immutable prepared climate/weather facts and runtime forcing calls the existing precipitation/evaporation/Water/Soil mechanics.

## Why

Forcing should adapt prepared environmental meaning to runtime owners; it must not duplicate physical Water accounting or introduce avoidable persistence state solely to realize fractions.

## Consequences

- Existing Water/Soil systems remain the only physical quantity owners.
- Runtime atmosphere can be replaced without changing hydrology ownership.
- Prepared climate remains independent from event realization.
- Exact analytical rate realization remains a valid technique where a current rate contract uses it, but it is no longer the universal generated-climate representation.

## Alternatives considered

A single arbitrary global rain interval, mutable fractional carry, direct climate-adapter mutation of internal Water/Soil storage, and netting precipitation minus evaporation before physical mutation were rejected.

## Current implementation

`AtmosphericRuntimePlan.compose(...)` produces runtime atmospheric composition/forcing from immutable generated facts. Current generated physical climate uses `ClimateNormalsField` and explicit physical time/space scale where needed; scenario pulse forcing and generated climate forcing remain distinct realization paths over the same Water/Soil mechanics.

## Related documentation

- [Generated World Runtime](../systems/world-generation/generated-world-runtime.md)
- [Surface Hydrology](../systems/environment/hydrology.md)
- [Rainfall Regime Calibration](../systems/environment/rainfall-calibration.md)
- [ADR-021](021-world-preparation-and-calibration-boundary.md)
