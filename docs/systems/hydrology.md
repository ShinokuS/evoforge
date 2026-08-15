# Surface Hydrology

## Purpose

Model the finite **Water** surface cycle while using generic free-liquid transport and generic Soil retention underneath Water-specific weather behavior.

Current production composition is Water-oriented:

```text
periodic / cyclic precipitation
        ↓
shared vertical sky surface
        ├─ exposed Water -> Water directly
        └─ exposed Terrain -> retained Water first
                              ↓
                         excess free Water
                              ↓
generic active-liquid Soil infiltration
                              ↓
                  shared free-liquid flow
                              ↓
                    dormant fixed point

periodic Water evaporation
        ↓
shared vertical sky surface revalidation
        ├─ exposed free Water first
        └─ exposed retained Water second
```

Precipitation and evaporation are deterministic Water forcings, not generic rules for every liquid. Soil infiltration itself is generic.

## Ownership

Authoritative state remains independent:

```text
Terrain          XYZ -> landscape definition
SoilLiquidSystem terrain XYZ -> retained constituent composition
LiquidSystem     XYZ -> one free LiquidTypeId + free volume
Water            typed projection of LiquidSystem identity "water"
SoilMoisture     typed Water projection of SoilLiquidSystem
```

One Soil cell has one material-owned pore capacity shared by all retained constituents. Water and blood do not each receive a separate full Soil capacity.

Derived indexes are caches/read projections rather than additional world truth:

- `TerrainSurfaceLookup` — highest occupied terrain Z per XY column;
- generic `LiquidSurfaceLookup` — sparse free-liquid surfaces;
- Water-filtered `WaterSurfaceLookup` — Water columns for current hydrology consumers;
- generic `SoilLiquidCellsLookup` — retained-liquid terrain cells;
- `SoilMoistureCellsLookup` — Water-retaining projection used by current Water evaporation/presentation.

See [Liquids](liquids.md) for the generic liquid/content boundaries.

## Soil hydrology

Whether terrain absorbs liquid remains definition data. A landscape definition may declare finite `capacity` and `infiltrationLimit`; absence of Soil hydrology means non-absorbing terrain.

`SoilLiquidSystem` owns retained composition and bounded arithmetic:

```java
infiltrateAtMost(type, x, y, z, requested)
removeAtMost(type, x, y, z, requested)
```

Infiltration is bounded by requested volume, the remaining **shared** local pore capacity and an effective liquid/material infiltration limit. `SoilLiquidInteractionLookup` is the narrow extension point for making different liquids infiltrate a material differently. Its current default preserves the existing material `infiltrationLimit`.

A material may additionally declare `SoilHydrologyVariation(seed, capacityAmplitude)`. `TerrainSoilHydrologyLookup` combines the immutable material base with a deterministic coordinate hash. Only effective capacity varies; starting retained content is never randomized and no runtime RNG stream is consumed.

`SoilMoistureSystem` remains a Water-oriented projection for existing hydrology consumers rather than a second retained-state authority.

## Shared vertical sky surface

`VerticalSkySurfaceSystem` currently combines cached Terrain and production Water surfaces. For one XY column it reports the current vertically exposed surface as Terrain or Water.

Important consequences remain:

- higher terrain shields lower terrain/Water from vertical atmosphere effects;
- Water above terrain becomes the exposed lake surface;
- a wet Water-only column remains vertically addressable;
- an empty column creates no hydrology state merely because atmosphere exists.

A Water/Terrain tie remains terrain-first under the current coarse-cell convention. Sub-cell atmospheric exposure is not modeled.

The current sky contract is deliberately Water-oriented. When another liquid becomes an atmosphere participant, sky/contact semantics must be extended explicitly rather than assuming Water behavior.

## Precipitation

`PrecipitationSystem` exposes two Water-cycle targets:

```java
applyTerrainSurface(x, y, terrainZ, amount)
applyWaterSurface(x, y, waterZ, amount)
```

Terrain precipitation follows:

```text
input
  ↓
retained Water infiltration
  ↓
remaining volume
  ↓
free Water in geometry-open surface space / cell above
  ↓
unplaced remainder
```

An exposed Water target bypasses Soil and adds directly to the Water column.

Every result preserves exact finite accounting:

```text
input = infiltrated + surfaceWater + unplaced
```

`PrecipitationSchedule` supports ordinary periodic pulses and cyclic active windows. The Rain Cycle acceptance scenario uses this so physical precipitation and visible rain begin/end from the same deterministic schedule.

## Run-on free liquid -> Soil infiltration

Precipitation is not the only path from free liquid into Soil.

`SoilLiquidInfiltrationSystem` inspects every currently active free-liquid cell before the next shared flow solve. It resolves supporting terrain, asks retained Soil authority to accept at most the current constituent and removes exactly that accepted amount from `LiquidSystem`.

The current `WaterSoilExchangeSystem` is a compatibility adapter for existing Water-oriented wiring; its actual pass delegates to this generic mechanism.

Conceptual order is:

```text
LiquidFlowProcess resume
        ↓
generic SoilLiquidInfiltrationSystem
        ↓
LiquidFlowSystem.update()
```

So run-on Water still wets dry supporting Soil before continuing downstream, while a future blood spill can use the same physical mechanism. If Soil reaches its shared capacity or accepts only part of the incoming amount this step, the excess remains free and can form/run off as a puddle.

## Surface storage before horizontal runoff

A landscape definition may declare `SurfaceWaterStorage`: finite free Water retained on its supporting surface before horizontal runoff becomes mobile.

This volume remains authoritative **free Water**. It is distinct from retained Soil pore composition.

The generic flow solver consumes a typed `LiquidSurfaceRetentionLookup`. Current Water composition adapts `SurfaceWaterStorage` into that capability for the Water identity only. This leaves future liquids free to define different surface-film retention behavior.

The reserve applies only to same-Z horizontal transfers. Vertical falling through a valid opening does not subtract it. Multiple exits share one source reserve through aggregate outgoing limiting.

## Evaporation

Current evaporation is a finite absolute **Water** sink per exposed wet XY candidate, not percentage decay.

Candidates come from Water columns and positive Water-moisture cells; the system does not scan the whole terrain. Every candidate is revalidated through the current sky-surface lookup before removal.

Removal order is:

```text
1. exposed free Water
2. exposed retained Water
3. unfulfilled remainder
```

Exact accounting remains:

```text
requested = surfaceWaterRemoved + soilMoistureRemoved + unfulfilled
```

If precipitation occurs on the same simulation tick, periodic evaporation is suppressed for that tick independent of scheduler handler ordering.

No evaporation behavior is implied for another free or retained liquid constituent.

## Flow cadence and dormancy

Successful free-liquid mutation wakes the shared hydraulic frontier. `WaterFlowProcess` currently remains a Water-shaped composition adapter around the generic scheduled liquid process; the underlying flow cadence and dormancy belong to `LiquidFlowProcess`/`LiquidFlowSystem`.

A settled lake/puddle therefore becomes dormant and costs no continuing flow work until a later mutation wakes it.

Latest Water transfers remain exposed through `WaterFlowLookup`; internally they are filtered from typed generic liquid-flow samples. A sample is actual latest-step transfer, not a persistent velocity field.

## Optional finite world bounds

A runtime may configure `SimulationAssembly.worldBounds(...)`. `WorldGeometryLookup` resolves coordinates outside the inclusive box as `FullShape`, so free-liquid flow sees a closed physical boundary through ordinary Geometry and needs no liquid-specific map-edge rule.

Without configured bounds, unbounded semantics remain intentionally available.

## Mixing boundary

Hydrology does not define free-liquid mixing. Current free-liquid cells are single-component and unlike-liquid contact is blocked explicitly rather than silently merged.

Retained Soil state may contain multiple constituents sharing pore capacity. This is composition bookkeeping inside porous material, not implemented miscibility, chemistry, diffusion, reactions or phase separation.

A future free-liquid mixture milestone remains separate. See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md).

## Deliberately absent

The current hydrology/environment foundation does not implement:

- full Weather state, moving storm fronts or spatial rainfall fields;
- temperature, humidity, solar radiation or wind-driven evaporation rates;
- object/canopy atmospheric occlusion;
- deep drainage/groundwater;
- plant uptake;
- terrain erosion;
- derived water-body identity;
- detailed pressure, inertia, viscosity or turbulence;
- generic atmosphere/traversal rules for arbitrary liquids;
- free-liquid mixtures or reactions;
- retained-liquid diffusion, displacement, leaching or reactions;
- generated/streamed world-bound semantics beyond the explicit finite runtime box;
- automatic hydraulic wake coordination for arbitrary runtime Geometry changes.

These should be added by their first real consumers rather than by expanding Terrain or creating a giant environment cell.

## Tests and acceptance

Headless Water coverage includes finite precipitation/evaporation accounting, shared sky targeting, exposed/covered behavior, deterministic local Soil variation, run-on infiltration, saturated Soil, SurfaceWaterStorage invariants, vertical falling, cyclic rain cadence, dormancy and finite-world containment.

Generic liquid/Soil coverage additionally proves that non-Water liquid identities reuse the same hydraulic solver, use the same Soil-infiltration mechanism, can coexist as separate retained constituents sharing one pore capacity, and leave excess free when uptake is bounded.

The visual Rain Cycle acceptance still proves dry start, Water-retained Soil wetting while rain is visible, uneven puddle onset, finite lake evaporation and hydrology-aware inspection.

See [Liquids](liquids.md), [Water](water.md), [Water Traversal](water-traversal.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).