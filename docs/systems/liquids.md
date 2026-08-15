# Liquids

## Purpose

Provide one deterministic free-liquid transport foundation that is not defined by Water-specific hydrology.

The foundation answers a deliberately small set of questions:

```text
what liquid is in this cell?
how much free volume is present?
how much fits current Geometry?
where can that volume flow?
when has the local region reached a dormant fixed point?
```

Water is the first production liquid identity, but blood, wine or another future liquid can use the same storage, hydraulic solver and flow scheduling without copying Water code.

## Ownership

`LiquidSystem` owns authoritative free-liquid state:

```text
XYZ -> dry
   or
XYZ -> one LiquidTypeId + finite volume
```

`LiquidLookup` is read-only. `LiquidStorage` is replaceable implementation detail. Current storage is sparse, so dry cells allocate no entry.

The normalized amount still uses `CellVolume`:

```text
EMPTY = 0
FULL  = 1_000_000
```

The scale is a fraction of discrete cell volume, not litres.

## Identity

`LiquidTypeId` is an open semantic identifier. `StandardLiquidTypes.WATER` defines the built-in Water identity.

The foundation intentionally does not attach an omnibus property bag to every liquid. There is currently no authoritative meaning for hypothetical viscosity, density, flammability, toxicity or chemistry fields. The first mechanic that actually needs such a property should introduce a narrow typed definition capability.

## Current composition invariant

An occupied cell currently contains one liquid type.

```text
wine + dry cell   -> allowed
wine + wine       -> allowed within capacity
wine + blood      -> no implicit transfer/mixing
```

`LiquidSystem.addAtMost(...)` returns zero when the target already contains another liquid type. It never overwrites the resident type.

The same rule is enforced by flow. If unlike liquids are already adjacent, no cross-contact transfer is planned. If two unlike sources simultaneously target the same dry cell, all contested inflows are suppressed for that step. Deterministic iteration order therefore cannot become an accidental "mixing winner" rule.

This is an explicit temporary boundary. It is not a claim that real liquids cannot mix.

## Geometry and flow

`LiquidFlowSystem` is the generic form of the original Water solver. It retains the accepted Water mechanics:

- Shape-derived free capacity;
- physical face opening floors;
- hydraulic head from world Z plus local liquid surface height;
- two-phase plan/limit/commit updates;
- exact finite-volume conservation;
- deterministic proportional limiting;
- fixed-point relaxation;
- sparse active frontier and dormancy;
- latest actual-flow diagnostics.

The solver reads liquid identity only to preserve content and enforce the current no-mixing boundary. Hydraulic Geometry does not switch on names such as `water`, `blood` or `wine`.

`LiquidFlowProcess` owns the generic scheduled cadence: one local solve per tick while the shared activity frontier contains work, with wakeups coalesced and no continuing schedule at dormancy.

A narrow `LiquidFlowPreparation` hook exists because a real current consumer needs deterministic work immediately before transport: Water can infiltrate Soil before remaining free liquid flows. The hook does not own liquid state and is not a general event bus.

## Surface retention seam

Horizontal runoff can consult:

```java
LiquidSurfaceRetentionLookup.capacityAt(type, x, y, z)
```

The liquid type is part of the query because a future material may retain different liquids differently. Current Water integration maps existing `SurfaceWaterStorage` data into this capability. Vertical falling remains unaffected by surface retention.

## Derived surfaces

`LiquidSurfaceLookup` is a mutation-maintained sparse projection of positive-liquid cells by XY column. It can resolve the top liquid overall or the top cell of a requested liquid type without scanning world space.

Derived surfaces own no quantity. They exist for exposure, presentation and other read-side consumers.

## Water integration

Water-specific semantics stay outside the generic transport owner:

```text
LiquidSystem / LiquidFlowSystem / LiquidFlowProcess
                    ↓ typed WATER facade
                 WaterSystem
                    ├─ Water -> SoilMoisture exchange
                    ├─ precipitation / evaporation
                    ├─ Water wading constraint
                    └─ Water presentation
```

`WaterFlowProcess` is now only the production hydrology adapter: it supplies Water's pre-flow Soil exchange to the generic scheduled process.

This distinction is important. Reusing the same hydraulic solver does not imply that wine should infiltrate Soil like rainwater, blood should evaporate on the same schedule, or every liquid should share Water traversal rules.

See [Water](water.md) and [Surface Hydrology](hydrology.md).

## Future mixing/composition

A future mixture milestone should focus on cell content and contact semantics rather than replacing the hydraulic world model.

Likely questions for that milestone include:

- how multiple constituents and their quantities are represented;
- miscibility and phase separation;
- how composition is transferred with volume;
- how reactions create/remove constituents;
- how derived physical properties affect flow or other systems;
- how presentation and gameplay inspect mixtures.

Those questions are intentionally unanswered today. The current single-component invariant makes the unsupported contact explicit and gives that future work a bounded replacement point.

## Engineering rules

1. Free-liquid quantity has one authoritative owner.
2. Liquid-specific mechanics consume narrow typed capabilities; they do not fork the transport solver.
3. No system may silently reinterpret one liquid identity as another.
4. Contact between unsupported compositions must fail/block explicitly rather than depend on collection ordering.
5. Generic liquid code must not gain speculative properties without a real consumer.
6. Flow remains deterministic and exactly volume-conserving.
7. Scheduling stops at hydraulic dormancy.
8. Presentation and diagnostics are observers, never liquid authority.

See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md) for the architectural boundary.