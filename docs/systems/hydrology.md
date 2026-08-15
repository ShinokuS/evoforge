# Surface Hydrology

## Purpose

Model the finite **Water** surface cycle without turning Weather, Terrain, Soil and free liquids into one mutable world-cell system.

Current production composition is:

```text
periodic / cyclic precipitation
        ↓
shared vertical sky surface
        ├─ exposed Water -> Water directly
        └─ exposed Terrain -> SoilMoisture first
                              ↓
                         excess free Water
                              ↓
                     WaterSoilExchange
                              ↓
                  shared free-liquid flow
                              ↓
                    dormant fixed point

periodic evaporation
        ↓
shared vertical sky surface revalidation
        ├─ exposed Water first
        └─ exposed SoilMoisture second
```

Precipitation and evaporation are simple deterministic Water forcings, not a complete Weather model and not generic rules for every future liquid.

## Ownership

Authoritative state remains independent:

```text
Terrain        XYZ -> landscape definition
SoilMoisture   terrain XYZ -> retained finite water moisture
LiquidSystem   XYZ -> LiquidTypeId + free liquid volume
Water          typed projection of LiquidSystem identity "water"
```

`SoilMoistureSystem` owns retained soil water. `LiquidSystem` owns free-liquid state and `WaterSystem` is its narrow Water facade. Precipitation, evaporation, Soil exchange and flow own no duplicate quantity.

Derived indexes are caches/read projections rather than additional world truth:

- `TerrainSurfaceLookup` — highest occupied terrain Z per XY column;
- generic `LiquidSurfaceLookup` — sparse free-liquid surfaces;
- Water-filtered `WaterSurfaceLookup` — Water columns for current hydrology consumers;
- `SoilMoistureCellsLookup` — terrain cells retaining positive moisture.

See [Liquids](liquids.md) for the generic transport/content boundary.

## Soil hydrology

Whether terrain absorbs Water is definition data. A landscape definition may declare finite `capacity` and `infiltrationLimit`; absence of Soil hydrology means non-absorbing terrain.

`SoilMoistureSystem` exposes bounded arithmetic mutation:

```java
int infiltrateAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

Infiltration is bounded by requested volume, the local transfer limit and remaining local capacity. Zero moisture is sparse absence.

A material may additionally declare `SoilHydrologyVariation(seed, capacityAmplitude)`. `TerrainSoilHydrologyLookup` combines the immutable material base with a deterministic coordinate hash. Only effective capacity varies; starting moisture is never randomized and no runtime RNG stream is consumed.

These are Water/Soil rules. Generic liquid transport does not infer Soil absorption for blood, wine or other future liquid identities.

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
SoilMoisture infiltration
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

## Run-on Water -> Soil exchange

Precipitation is not the only path from free Water into Soil.

`WaterSoilExchangeSystem` inspects active cells of the Water identity before the next shared liquid-flow solve. For each active Water cell it finds supporting terrain, asks `SoilMoistureSystem` to infiltrate at most the current Water and removes exactly that amount through `WaterSystem`.

Production order is:

```text
WaterFlowProcess resume
        ↓
WaterSoilExchangeSystem.update()
        ↓
WaterFlowSystem adapter
        ↓
LiquidFlowSystem.update()
```

So Water arriving from a neighbor can wet dry supporting soil before continuing downstream. Other liquid identities are ignored by this Water-specific exchange.

## Surface storage before horizontal runoff

A landscape definition may declare `SurfaceWaterStorage`: finite free Water retained on its supporting surface before horizontal runoff becomes mobile.

This volume remains authoritative free Water. It is not SoilMoisture and is not deleted.

The generic flow solver consumes a typed `LiquidSurfaceRetentionLookup`. Current Water composition adapts `SurfaceWaterStorage` into that capability for the Water identity only. This deliberately leaves future liquids free to define different material-retention behavior.

The reserve still applies only to same-Z horizontal transfers. Vertical falling through a valid opening does not subtract it. Multiple exits share one source reserve through aggregate outgoing limiting.

## Evaporation

Current evaporation is a finite absolute Water sink per exposed wet XY candidate, not percentage decay.

Candidates come from Water columns and positive SoilMoisture cells; the system does not scan the whole terrain. Every candidate is revalidated through the current sky-surface lookup before removal.

Removal order is:

```text
1. exposed free Water
2. exposed retained SoilMoisture
3. unfulfilled remainder
```

Exact accounting remains:

```text
requested = surfaceWaterRemoved + soilMoistureRemoved + unfulfilled
```

If precipitation occurs on the same simulation tick, periodic evaporation is suppressed for that tick independent of scheduler handler ordering.

No evaporation behavior is implied for arbitrary future liquids.

## Flow cadence and dormancy

Successful Water mutation wakes the shared liquid hydraulic frontier. `WaterFlowProcess` currently schedules the production Water/Soil preparation and the shared `LiquidFlowSystem` one local step per tick while work remains.

The underlying flow algorithm is liquid-generic; the process name reflects current production hydrology composition and can be generalized when a second runtime liquid needs independent scheduling semantics.

A settled lake/puddle therefore becomes dormant and costs no continuing flow work until a later mutation wakes it.

Latest Water transfers remain exposed through `WaterFlowLookup`; internally they are filtered from typed generic liquid-flow samples. A sample is actual latest-step transfer, not a persistent velocity field.

## Optional finite world bounds

A runtime may configure `SimulationAssembly.worldBounds(...)`. `WorldGeometryLookup` resolves coordinates outside the inclusive box as `FullShape`, so free-liquid flow sees a closed physical boundary through ordinary Geometry and needs no liquid-specific map-edge rule.

Without configured bounds, unbounded semantics remain intentionally available.

## Mixing boundary

Hydrology does not define liquid mixing. The current generic liquid representation is single-component per occupied cell and unlike-liquid contact is blocked explicitly rather than silently merged.

Mixtures, miscibility, reactions, phase separation and derived mixture properties are a separate future design problem. See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md).

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
- generic atmosphere/Soil rules for arbitrary liquids;
- liquid mixtures or reactions;
- generated/streamed world-bound semantics beyond the explicit finite runtime box;
- automatic hydraulic wake coordination for arbitrary runtime Geometry changes.

These should be added by their first real consumers rather than by expanding Terrain or creating a giant environment cell.

## Tests and acceptance

Headless Water coverage includes finite precipitation/evaporation accounting, shared sky targeting, exposed/covered behavior, deterministic local Soil variation, run-on infiltration, saturated Soil, SurfaceWaterStorage invariants, vertical falling, cyclic rain cadence, dormancy and finite-world containment.

Generic liquid coverage separately proves that non-Water identities reuse the same hydraulic solver and that unsupported unlike-liquid contact cannot accidentally mix through overwrite or deterministic ordering.

The visual Rain Cycle acceptance still proves dry start, Soil wetting while rain is visible, uneven puddle onset, finite lake evaporation and hydrology-aware inspection.

See [Liquids](liquids.md), [Water](water.md), [Water Traversal](water-traversal.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).