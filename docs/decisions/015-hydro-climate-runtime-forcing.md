# Decision 015 — Hydro-climate normals are realized analytically through existing hydrology

**Status:** Accepted

## Problem

`HydroClimateField` describes long-term precipitation supply and potential evaporation as exact rates, while the Water/Soil runtime owns actual hydrology. A generated world needs a bridge between those layers without turning Atlas into a second Water solver, inventing arbitrary pulse intervals, or storing fractional climate carry that later becomes persistence state.

The bridge must also preserve future spatial climate: atmospheric systems cannot assume that every XY column always receives the same amount.

## Decision

`HydroClimateForcingSystem` is a narrow runtime adapter:

```text
HydroClimateField
      ↓ exact rate realization at absolute simulation tick
potential evaporation demand
      ↓
EvaporationSystem
      ↓
pre-existing exposed Water / retained Water

HydroClimateField
      ↓ exact rate realization at the same absolute simulation tick
precipitation supply
      ↓
SkyPrecipitationSystem
      ↓
PrecipitationSystem
      ↓
SoilLiquidSystem / WaterSystem
```

The adapter consumes Atlas `HydroClimateField`, not Genesis `HydroClimateSpec`. It therefore does not know which user controls, calibration rules or generation model produced the climate facts.

For an exact rate `p / q`, the volume assigned to positive absolute tick `t` is:

```text
floor(p * t / q) - floor(p * (t - 1) / q)
```

This makes cumulative forcing over ticks `1..T` exactly `floor(p*T/q)` without mutable fractional carry. The phase is part of world time rather than adapter state, so there is no extra persistence contract.

Potential evaporation is evaluated against state present at the start of the interval. Precipitation supply is then added at the interval boundary. Freshly supplied rain is therefore not immediately removed by the same baseline-climate tick. This is an explicit deterministic discretization convention, not a claim about sub-tick meteorology.

Precipitation and evaporation remain separate forcings. They are not reduced to a synthetic `precipitation - evaporation` number because their physical mutation paths and unfulfilled accounting differ.

## Column capabilities

`SkyPrecipitationSystem` and `EvaporationSystem` accept narrow domain-specific column amount lookups. They do not import Atlas types.

Requests may exceed one cell volume. The atmospheric systems realize them through their existing bounded physics in `CellVolume.FULL` chunks. Precipitation re-resolves the sky surface between chunks so rising Water remains authoritative. Evaporation stops synthetic extra work once the current wet column cannot satisfy a chunk and accounts the unresolved tail as unfulfilled.

This is representation adaptation, not a new climate cap or balancing rule.

## Consequences

- generated climate can drive runtime Water without owning Water/Soil state;
- exact fractional rates require no mutable carry or arbitrary forcing interval;
- future spatial climate fields can vary by XY without redesigning hydrology ownership;
- existing periodic/cyclic atmosphere schedules remain valid for authored scenarios and tests;
- baseline normals are deterministic forcing, not full Weather;
- weather events may later redistribute the same long-term climatology while preserving the Atlas normal as the target condition;
- raw rates remain internal contracts and are not promoted to player-facing generation settings.

## Rejected directions

A single global rain interval was rejected because it would convert an implementation cadence into climate semantics and would not represent general rational rates exactly.

Mutable fractional carry was rejected because the same progression is derivable analytically from absolute world tick and would otherwise create avoidable authoritative persistence state.

Direct Water/Soil mutation from the climate adapter was rejected because those domains already own the physical state and accounting rules.

Netting precipitation and evaporation before mutation was rejected because an atmospheric source and a potential sink are not interchangeable quantities once exposure, Soil retention and finite Water are considered.

Reusing periodic-rain same-tick suppression as generated-climate semantics was rejected because scenario pulse scheduling and long-term baseline forcing are distinct concepts.
