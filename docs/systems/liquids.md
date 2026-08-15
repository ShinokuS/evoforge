# Liquids

## Purpose

Provide deterministic liquid foundations that are not defined by Water-specific gameplay or hydrology.

The current architecture separates two physical states:

```text
free liquid
    -> occupies world-cell free volume
    -> can move through Geometry

retained Soil liquid
    -> occupies pore capacity inside an absorbing terrain cell
    -> does not participate in free hydraulic flow while retained
```

Water is the first production liquid identity, but blood, wine or another future liquid can use the same free-liquid storage/flow and the same generic Soil-infiltration mechanism without copying Water code.

## Free-liquid ownership

`LiquidSystem` owns authoritative free-liquid state:

```text
XYZ -> dry
   or
XYZ -> one LiquidTypeId + finite volume
```

`LiquidLookup` is read-only. `LiquidStorage` is replaceable implementation detail. Current storage is sparse, so dry cells allocate no entry.

The normalized amount uses `CellVolume`:

```text
EMPTY = 0
FULL  = 1_000_000
```

The scale is a fraction of discrete cell volume, not litres.

## Identity

`LiquidTypeId` is an open semantic identifier, intentionally not a central enum/catalog. The Water integration owns its current identity as `WaterSystem.TYPE`; a future liquid integration may own another identity without modifying generic liquid code.

The foundation intentionally does not attach an omnibus property bag to every liquid. There is currently no authoritative meaning for hypothetical viscosity, density, flammability, toxicity or chemistry fields. The first mechanic that actually needs one should introduce a narrow typed definition capability.

## Current free-liquid composition invariant

An occupied **free-liquid** cell currently contains one liquid type.

```text
wine + dry cell   -> allowed
wine + wine       -> allowed within capacity
wine + blood      -> no implicit free-liquid transfer/mixing
```

`LiquidSystem.addAtMost(...)` returns zero when the target already contains another liquid type. It never overwrites the resident type.

The same rule is enforced by flow. If unlike free liquids are already adjacent, no cross-contact transfer is planned. If two unlike sources simultaneously target the same dry cell, all contested inflows are suppressed for that step. Deterministic iteration order therefore cannot become an accidental "mixing winner" rule.

This is an explicit temporary boundary. It is not a claim that real liquids cannot mix. Free-liquid mixing remains a separate future milestone.

## Geometry and free-liquid flow

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

A runtime containing several liquid identities must compose **one shared `LiquidSystem` and one shared `LiquidFlowSystem`** for that free-liquid state. Typed facades may filter or adapt that shared state, but must not create parallel hydraulic authorities for the same world.

`LiquidFlowProcess` owns generic scheduled cadence: one local solve per tick while the shared activity frontier contains work, with wakeups coalesced and no continuing schedule at dormancy.

A narrow `LiquidFlowPreparation` hook permits deterministic work immediately before transport. Current hydrology uses this position for Soil infiltration before the remaining free volume is allowed to flow.

## Generic Soil retention

Soil absorption is a generic liquid/material interaction, not a Water-only mechanic.

`SoilLiquidSystem` owns retained composition inside terrain pore volume:

```text
terrain XYZ
    shared pore capacity = 100_000

    retained water = 60_000
    retained blood = 25_000
    retained wine  =  5_000
    total          = 90_000
```

Unlike current free-liquid storage, retained Soil composition may already contain several liquid constituents. They **share one material-owned capacity**. Capacity is not duplicated per liquid.

This is intentionally not a full mixing model. The system records that several constituents occupy the same porous medium; it does not yet model molecular mixing, reactions, displacement, diffusion, phase separation or derived mixture properties.

`SparseSoilLiquidStorage` is the current sparse storage implementation. `SoilLiquidLookup` exposes both constituent amount and total retained amount. Deterministic retained-cell iteration is exposed separately through `SoilLiquidCellsLookup`.

### Infiltration

`SoilLiquidInfiltrationSystem` transfers active free liquid into supporting Soil before free-liquid transport:

```text
active free liquid
       |
       v
supporting terrain
       |
       v
SoilLiquidSystem
       |
       +--> retained constituent
       |
       `--> excess remains free
```

The transfer is exact: the amount successfully retained is the amount removed from free-liquid authority. If Soil has no remaining pore capacity, or the current infiltration step accepts only part of the incoming volume, the remainder stays free and can form/run off as a puddle.

That means a future battle does not need a special "make blood puddle" rule. Blood may first saturate available Soil retention; sufficient additional blood naturally remains as free liquid.

### Liquid × material interaction seam

`SoilHydrology` continues to provide the effective local material capacity and its default infiltration limit. `SoilLiquidInteractionLookup` resolves the effective infiltration limit for a particular liquid at a particular Soil cell.

The default resolver simply preserves the current material limit, so every liquid can use the mechanism immediately. A future definition-backed resolver can make, for example, blood and Water infiltrate the same terrain at different rates without adding liquid-name switches to `SoilLiquidSystem`.

The lookup is deliberately narrow. We do not pre-invent viscosity, density, chemistry or arbitrary property bags before a real mechanic needs them.

### Water moisture projection

Existing Water hydrology and presentation still consume `SoilMoistureSystem`. It is now a typed compatibility facade over retained Soil composition:

```text
SoilLiquidSystem
    water -> 30_000
    blood -> 20_000
       |
       `--> SoilMoistureSystem(WATER) -> 30_000
```

Water evaporation/visualization therefore continues to observe Water moisture only; retained blood does not become Water merely because it occupies the same Soil pore volume.

The current `WaterSoilExchangeSystem` remains as a Water-shaped composition adapter, but delegates the actual active-liquid infiltration pass to the generic Soil mechanism. Future multi-liquid production composition should wire the generic capability directly.

## Surface retention seam

Horizontal free-liquid runoff can consult:

```java
LiquidSurfaceRetentionLookup.capacityAt(type, x, y, z)
```

The liquid type is part of the query because a supporting material may eventually retain different free-liquid films differently. Current Water integration maps existing `SurfaceWaterStorage` data into this capability. Vertical falling remains unaffected by surface retention.

This surface-film capacity is distinct from retained Soil pore composition.

## Derived free-liquid surfaces

`LiquidSurfaceLookup` is a mutation-maintained sparse projection of positive free-liquid cells by XY column. It can resolve the top liquid overall or the top cell of a requested liquid type without scanning world space.

Derived surfaces own no quantity. They exist for exposure, presentation and other read-side consumers.

## Water integration

Water-specific semantics stay outside the generic liquid/Soil mechanisms:

```text
shared mechanics
  LiquidSystem
  LiquidFlowSystem
  SoilLiquidSystem
  SoilLiquidInfiltrationSystem
          |
          v
      WaterSystem
          |
          +-- precipitation source semantics
          +-- Water evaporation semantics
          +-- Water wading constraint
          `-- Water presentation
```

`WaterFlowSystem` may wrap the shared `LiquidFlowSystem` to expose Water-shaped diagnostics. Its Water-only constructors are convenience composition for the current runtime and fixtures, not a model for creating one solver per liquid type.

Reusing Soil infiltration does **not** imply that every retained liquid must evaporate on Water's schedule, satisfy Thirst, use Water traversal rules or share Water presentation.

See [Water](water.md) and [Surface Hydrology](hydrology.md).

## Future mixing/composition milestone

Free-liquid mixing remains deliberately deferred.

A future mixture milestone should focus on free-cell content/contact semantics rather than replacing the hydraulic world model. Likely questions include:

- how multiple free-liquid constituents and their quantities are represented;
- miscibility and phase separation;
- how composition is transferred with volume;
- density layering/buoyancy where required;
- how reactions create/remove constituents;
- how derived physical properties affect transport;
- how presentation and gameplay inspect mixtures.

Retained Soil composition gives later contamination/leaching/reaction work a real place to attach, but **none of those processes are implemented by this foundation**.

## Engineering rules

1. Free-liquid quantity has one authoritative owner.
2. One free-liquid world has one shared transport solver; typed liquid facades do not fork it.
3. Liquid identities are open and domain-owned; generic code does not maintain a catalog of concrete liquids.
4. Soil pore capacity belongs to the terrain/material and is shared by all retained constituents.
5. Generic Soil infiltration accepts arbitrary liquid identity; liquid/material differences enter through a narrow interaction capability.
6. Water-specific atmosphere, traversal and presentation rules remain explicit Water integrations.
7. Unsupported free-liquid composition must block explicitly rather than depend on collection ordering.
8. Retained multi-constituent state must not be mistaken for implemented chemistry/mixing.
9. Generic liquid code must not gain speculative properties without a real consumer.
10. Free-liquid flow remains deterministic and exactly volume-conserving; free-to-retained transfer removes exactly what Soil accepted.
11. Scheduling stops at hydraulic dormancy.
12. Presentation and diagnostics are observers, never liquid authority.

See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md) for the architectural boundary.