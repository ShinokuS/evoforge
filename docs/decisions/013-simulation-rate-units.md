# Decision 013 — Long-term environmental rates use exact simulation dimensions

**Status:** Accepted

## Problem

World generation needs climate normals before it can derive meaningful water availability, initial hydrology or ecology. Existing precipitation and evaporation are operational pulse schedules expressed as a finite cell-volume amount plus an interval in simulation ticks.

Using an arbitrary reference period such as “amount per 1,000 ticks” for climate would make that chosen period part of world physics. Assigning one tick a real-world duration such as one second or one minute would also introduce a physical claim that existing simulation mechanics do not yet require or support.

## Decision

Long-term finite-volume rates are represented exactly as a rational number of `CellVolume` units per simulation tick.

`CellVolumeRate` stores:

```text
volumeUnitsNumerator / tickDenominator
```

in canonical reduced form. It is non-negative and uses integer arithmetic only. Event-derived rates reduce factors before multiplication so a large cycle does not overflow merely because an unreduced cumulative amount would be large.

The simulation tick is the current explicit time dimension. This decision does **not** assign a real-world duration to a tick and does not introduce calendar, day, season or year semantics.

Existing `PrecipitationSchedule` and `EvaporationSchedule` expose the exact long-run rate represented by their operational pulse timing. Their scheduling behavior is unchanged.

For cyclic precipitation, the mean supply is derived from the actual number of pulses in the active window:

```text
amountPerPulse * floor(activeTicks / intervalTicks) / cycleTicks
```

Evaporation's exposed rate is potential atmospheric removal forcing. Actual runtime removal remains limited by exposed Water/retained Water and existing hydrology rules.

## Consequences

- climate normals can use a stable dimension without inventing a reference period;
- runtime pulse schedules can be compared to generated long-term forcing without losing exactness;
- UI simulation speed remains unrelated to authoritative rates because throughput does not change tick semantics;
- no real-world seconds/minutes are claimed prematurely;
- future calendar or physical-time mapping can be added as a separate contract if a real consumer requires it;
- the rate type describes quantity over simulation time and does not own scheduling or mutable environmental state.

## Rejected directions

Fixed “per N ticks” climate constants were rejected because N would be an arbitrary hidden balance period.

Floating-point rates were rejected because exact deterministic ratios are simple here and avoid representation drift.

Declaring a tick equal to a real-world second/minute/day was rejected because current mechanics are calibrated in simulation ticks and no accepted physical-time scale exists yet.
