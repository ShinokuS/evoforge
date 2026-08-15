# Surface Hydrology

## Purpose

Model a finite surface-water cycle without turning Weather, Terrain, Soil and Water into one mutable world-cell system.

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
                       local Water flow
                              ↓
                    dormant fixed point

periodic evaporation
        ↓
shared vertical sky surface revalidation
        ├─ exposed Water first
        └─ exposed SoilMoisture second
```

Precipitation and evaporation are still simple deterministic forcings. They are not a complete Weather model.

## Ownership

Authoritative state remains independent:

```text
Terrain        XYZ -> landscape definition
SoilMoisture   terrain XYZ -> retained finite moisture
Water          XYZ -> free liquid volume
```

`SoilMoistureSystem` owns retained soil water. `WaterSystem` owns free liquid. Precipitation, evaporation and flow own no duplicate quantity.

Derived indexes are caches/read projections rather than additional world truth:

- `TerrainSurfaceLookup` — highest occupied terrain Z per XY column;
- `WaterSurfaceLookup` — highest positive-Water Z per wet XY column;
- `SoilMoistureCellsLookup` — terrain cells retaining positive moisture.

## Soil hydrology

Whether terrain absorbs Water is definition data. A landscape definition may declare finite `capacity` and `infiltrationLimit`; absence of Soil hydrology means non-absorbing terrain.

`SoilMoistureSystem` exposes bounded arithmetic mutation:

```java
int infiltrateAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

Infiltration is bounded by requested volume, the local transfer limit and remaining local capacity. Zero moisture is sparse absence.

### Deterministic local capacity variation

A material may additionally declare `SoilHydrologyVariation(seed, capacityAmplitude)`.

`TerrainSoilHydrologyLookup` combines the immutable material base with a deterministic hash of seed + definition + XYZ. Only the effective capacity varies; starting moisture is never randomized and no runtime RNG stream is consumed.

The same seed, definition and coordinate therefore always resolve to the same local hydrology. This supports natural-looking uneven saturation/puddle onset without turning random draws into hidden simulation state.

## Shared vertical sky surface

`VerticalSkySurfaceSystem` combines cached Terrain and Water surfaces. For one XY column it reports the current vertically exposed surface as either Terrain or Water.

Important consequences:

- higher terrain shields lower terrain/Water from vertical atmosphere effects;
- Water above terrain becomes the exposed lake surface;
- a wet Water-only column remains vertically addressable;
- an empty column creates no hydrology state merely because atmosphere exists.

A Water/Terrain tie remains terrain-first under the current coarse-cell convention. Sub-cell atmospheric exposure is not modeled yet.

Precipitation and evaporation both consume this shared exposure rule rather than implementing separate notions of "sky".

## Precipitation

`PrecipitationSystem` exposes two objective targets:

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

An exposed Water target bypasses Soil and adds to the exposed liquid column.

Every result preserves exact finite accounting:

```text
input = infiltrated + surfaceWater + unplaced
```

`PrecipitationSchedule` supports both ordinary periodic pulses and a cyclic active window. A cyclic schedule can therefore model a visible shower as many small deterministic pulses across its duration rather than one delayed artificial dump.

The Rain Cycle acceptance scenario uses this to make physical precipitation and visible rain begin/end from the same schedule.

## Run-on Water -> Soil exchange

Precipitation is not the only path from free Water into Soil.

`WaterSoilExchangeSystem` inspects the existing sparse active-Water frontier before the next flow solve. For each active Water cell it finds supporting terrain (same anchor cell when partial terrain shares space, otherwise immediately below), asks that terrain's `SoilMoistureSystem` to infiltrate at most the current free Water, and removes exactly the infiltrated volume from Water.

Production order is:

```text
WaterFlowProcess resume
        ↓
WaterSoilExchangeSystem.update()
        ↓
WaterFlowSystem.update()
```

So Water arriving from a neighbor can wet dry supporting soil before continuing downstream. This preserves the ownership split while preventing precipitation from being a privileged one-off Soil path.

## Surface storage before horizontal runoff

A landscape definition may declare `SurfaceWaterStorage`: finite free Water retained on its supporting surface before horizontal runoff becomes mobile.

This volume remains real authoritative Water and is conserved. It is not SoilMoisture and is not deleted.

The flow solver applies the reserve only to same-Z horizontal transfers. Vertical falling through a valid physical opening does **not** subtract the surface-storage reserve. Multiple horizontal exits share one source reserve through the aggregate outgoing limiter, so they cannot each independently drain below it.

This gives shallow puddle/micro-storage behavior without a special "puddle" object or a thin film that expands forever across an open plane.

## Evaporation

Current evaporation is a finite absolute sink per exposed wet XY candidate, not percentage decay.

Candidates come only from wet Water columns and positive SoilMoisture cells; the system does not scan the whole terrain. Every candidate is revalidated through `SkySurfaceLookup` before removal.

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

## Flow cadence and dormancy

Successful Water mutation wakes the local hydraulic frontier through `WaterSystem`. `WaterFlowProcess` advances one local solve per scheduled tick and reschedules itself only while work remains.

A settled lake/puddle therefore becomes dormant and costs no continuing flow work until a later mutation wakes it.

The latest actual transfer step is also exposed through sparse `WaterFlowLookup` for diagnostics/presentation. No transfer sample is treated as calm; it is not a persistent velocity field.

## Optional finite world bounds

A runtime may configure `SimulationAssembly.worldBounds(...)`. The shared `WorldGeometryLookup` resolves coordinates outside the inclusive box as `FullShape`, so Water sees a physically closed boundary through ordinary Geometry and needs no Water-specific map-edge rule.

An assembly with no configured bounds deliberately keeps unbounded semantics. Explicit runtime bounds do not yet define generated/unloaded/streamed world state.

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
- generated/streamed world-bound semantics beyond the explicit finite runtime box;
- automatic hydraulic wake coordination for arbitrary runtime Geometry changes.

These should be added by their first real consumers rather than by expanding Terrain or creating a giant environment cell.

## Tests and acceptance

Headless coverage includes finite precipitation/evaporation accounting, shared sky targeting, exposed/covered behavior, deterministic local Soil capacity variation, run-on infiltration, saturated Soil behavior, horizontal SurfaceWaterStorage invariants, vertical falling through the reserve, cyclic rain cadence, solver dormancy and explicit world-bound containment.

The visual Rain Cycle acceptance separately proves dry start, Soil wetting while rain is visible, uneven puddle onset, finite lake evaporation and hydrology-aware inspection.

See [Water](water.md), [Water Traversal](water-traversal.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).
