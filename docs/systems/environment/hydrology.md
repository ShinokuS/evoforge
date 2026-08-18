# Surface Hydrology

## Purpose

Model the finite Water surface cycle while using generic free-liquid transport and generic retained-Soil mechanics underneath Water-specific atmosphere behavior.

Current production composition is:

```text
periodic / cyclic Water precipitation
        ↓
shared vertical sky surface
        ├─ exposed Water -> add to free Water
        └─ exposed Terrain -> retain Water in Soil first
                              ↓
                         excess free Water
                              ↓
generic SoilLiquidInfiltrationSystem
                              ↓
                  shared LiquidFlowSystem
                              ↓
                    dormant fixed point

periodic Water evaporation
        ↓
shared vertical sky surface revalidation
        ├─ exposed free Water first
        └─ exposed retained Water second
```

Precipitation and evaporation are Water-specific forcings. Soil retention and free-liquid transport are generic.

## Ownership

Authoritative state remains separate:

```text
Terrain           XYZ -> landscape definition
SoilLiquidSystem  terrain XYZ -> retained constituent composition
LiquidSystem      XYZ -> one free LiquidTypeId + free volume
WaterSystem       typed facade for LiquidTypeId "water"
```

There is no separate authoritative `SoilMoistureSystem`. Water retained in Soil is read directly as the Water constituent of `SoilLiquidSystem`.

One porous terrain cell has one material-owned capacity shared by every retained constituent.

Derived read-side indexes include:

- `TerrainSurfaceLookup` — highest terrain Z by XY column;
- `LiquidSurfaceLookup` — sparse generic free-liquid surfaces;
- `WaterSurfaceLookup` — Water-filtered surface projection;
- `SoilLiquidCellsLookup` — retained-liquid terrain cells.

See [Liquids](liquids.md) for the generic liquid/content boundary.

## Soil properties

Absorption is definition-driven through `SoilProperties`:

```text
capacity      total shared pore volume
permeability  nominal uptake per tick for the reference-viscosity liquid
```

A landscape definition without Soil properties is non-absorbing.

`TerrainSoilPropertiesLookup` resolves the local material properties. Optional `SoilPropertiesVariation(seed, capacityAmplitude)` deterministically varies capacity by coordinates and definition id; it does not consume runtime RNG, alter permeability or pre-fill retained state.

For a free liquid touching porous Soil, the effective one-step uptake rate is material permeability adjusted by that liquid's kinematic viscosity:

```text
effectiveRate
    = permeability * referenceViscosity / liquidViscosity
```

`SoilLiquidSystem.infiltrateAtMost(...)` then bounds accepted volume by requested volume, remaining shared pore capacity and that effective rate.

This replaces the previous `SoilHydrology(infiltrationLimit)` and Water-only soil-moisture abstraction. Liquid/material differences are produced from independent physical properties rather than identity pair tables.

## Shared vertical sky surface

`VerticalSkySurfaceSystem` combines cached Terrain and Water surface projections. For one XY column it reports the currently exposed Terrain or Water surface.

Consequences:

- higher terrain shields lower terrain/Water from vertical atmosphere effects;
- Water above terrain becomes the exposed lake surface;
- a Water-only column remains addressable;
- an empty column creates no hydrology state merely because atmosphere exists.

A Water/Terrain tie is terrain-first under the current coarse-cell convention. Sub-cell atmospheric exposure remains outside the model.

The sky contract is intentionally Water-oriented today. Other atmosphere-participating liquids require explicit future semantics.

## Precipitation

`PrecipitationSystem` exposes Water-cycle operations:

```java
applyTerrainSurface(x, y, terrainZ, amount)
applyWaterSurface(x, y, waterZ, amount)
```

Rain onto Terrain follows:

```text
input Water
    ↓
SoilLiquidSystem.infiltrateAtMost(WATER, ...)
    ↓
remaining Water
    ↓
free Water in geometry-open surface space / cell above
    ↓
unplaced remainder
```

Rain onto an exposed Water surface bypasses Soil and adds directly to the Water column.

Accounting is exact:

```text
input = infiltrated + surfaceWater + unplaced
```

`PrecipitationSchedule` supports periodic pulses and cyclic active windows. The Rain Cycle scenario uses the same schedule for physical rain and visible weather timing.

## Run-on free liquid -> Soil

Rain is not the only path into retained Soil state.

`SoilLiquidInfiltrationSystem` inspects active free-liquid cells immediately before the next shared hydraulic solve. It resolves supporting terrain, asks `SoilLiquidSystem` to retain that cell's actual constituent, and removes exactly the accepted volume from `LiquidSystem`.

```text
LiquidFlowProcess resume
        ↓
SoilLiquidInfiltrationSystem.update()
        ↓
LiquidFlowSystem.update()
```

This mechanism is liquid-agnostic. Water run-on wets Soil before continuing downstream; blood or another future constituent uses the same process. Excess after capacity/rate limits stays free.

No Water-only exchange wrapper participates in runtime composition.

## Surface retention before horizontal runoff

A landscape material may declare generic surface-retention capacity through `SurfaceRetentionDefinitions`.

`TerrainSurfaceRetentionLookup` resolves this finite material-owned microtopographic reserve. It remains authoritative **free liquid**, distinct from retained Soil pore composition.

The reserve applies to same-Z horizontal runoff only. Valid vertical falling is unaffected. Multiple horizontal exits share one source reserve through aggregate limiting.

The current surface-retention capability is liquid-neutral. The former Water-only `SurfaceWaterStorage*` model has been removed rather than retained as a parallel definition path.

## Evaporation

Current evaporation is a finite absolute Water sink per exposed wet XY candidate, not percentage decay.

Candidate columns come from Water surfaces and retained Soil cells that contain the Water constituent. The system does not scan all terrain.

Removal order is:

```text
1. exposed free Water
2. exposed retained Water
3. unfulfilled remainder
```

Exact accounting is:

```text
requested = surfaceWaterRemoved + retainedWaterRemoved + unfulfilled
```

If precipitation occurs on the same simulation tick, periodic evaporation is suppressed independent of scheduler handler ordering.

Other retained constituents are neither candidates nor sinks of this Water-specific process.

## Generated hydro-climate forcing

Generated worlds have a separate adapter from immutable Atlas climate normals into the same atmosphere mechanics:

```text
human generation intent
        ↓ future balancer / calibration
HydroClimateSpec
        ↓ generation
HydroClimateField
        ↓ HydroClimateForcingSystem
EvaporationSystem + SkyPrecipitationSystem
        ↓
existing SoilLiquidSystem / WaterSystem
```

`HydroClimateForcingSystem` reads `HydroClimateField`, never the normalized Genesis spec or future user controls. It owns no Water, Soil, weather state or fractional accumulator.

For one exact `CellVolumeRate p/q`, forcing assigned to positive absolute tick `t` is derived analytically as:

```text
floor(p*t/q) - floor(p*(t-1)/q)
```

The cumulative amount over ticks `1..T` is therefore exactly `floor(p*T/q)` without persistent carry or an arbitrary pulse interval.

Generated baseline forcing evaluates potential evaporation against state that existed at the start of the interval, then adds precipitation at the interval boundary. Fresh generated rain is not immediately removed by that same baseline tick. This convention is separate from the periodic scenario rule that suppresses periodic evaporation on a precipitation-event tick.

The atmosphere systems expose narrow column-specific amount capabilities so a future causal climate field can vary across XY without giving Atlas access to Water/Soil mutation. Requests larger than one cell volume are adapted through bounded `CellVolume.FULL` physics calls; precipitation re-resolves the exposed surface between chunks as Water rises.

This bridge is a generated-world composition capability, not yet an automatic replacement for the existing periodic/cyclic scenario systems. Eventful weather, storms and dry spells remain future runtime semantics that may redistribute a long-term climate normal without redefining it.

Raw climate rates are internal normalized facts. Player-facing world creation should use a small set of semantic controls; balancing/calibration translates those intentions into the technical rates used here.

## Flow cadence and diagnostics

Successful free-liquid mutation wakes the shared `LiquidFlowSystem` activity frontier. `LiquidFlowProcess` schedules one local solve per tick while work remains and stops at dormancy.

There is no separate Water transport process. `WaterFlowLookup.from(genericFlowLookup)` filters actual latest-step generic transfer diagnostics by Water identity for presentation/debug consumers.

A flow sample is a latest-step transfer observation, not a persistent velocity field.

## Optional finite world bounds

`SimulationAssembly.worldBounds(...)` may configure inclusive finite bounds. `WorldGeometryLookup` resolves coordinates outside them as `FullShape`, so free-liquid flow sees the same closed physical boundary as other Geometry consumers.

Without configured bounds, unbounded coordinate semantics remain available.

## Mixing boundary

Hydrology does not define free-liquid mixing. Current free cells are single-component and unlike contact is explicitly blocked rather than silently merged.

Retained Soil may contain multiple constituent quantities sharing pore capacity. That is porous composition bookkeeping, not implemented miscibility, chemistry, diffusion, reactions or phase separation.

See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md).

## Deliberately absent

The current hydrology/environment foundation does not implement:

- full Weather state, moving storm fronts or spatial rainfall fields;
- temperature, humidity, solar radiation or wind-driven evaporation;
- object/canopy atmospheric occlusion;
- deep drainage/groundwater;
- plant uptake;
- terrain erosion;
- derived water-body identity;
- pressure/inertia/turbulence;
- surface tension/contact-angle wetting physics;
- generic atmosphere/traversal rules for arbitrary liquids;
- free-liquid mixtures or reactions;
- retained-liquid diffusion, displacement, leaching or reactions;
- generated/streamed bounds beyond explicit runtime `WorldBounds`;
- automatic hydraulic wake coordination for arbitrary runtime Geometry changes.

Kinematic viscosity **is implemented** as a generic liquid transport property and affects both free-liquid mobility and Soil infiltration rate.

## Tests and acceptance

Headless Water coverage includes finite precipitation/evaporation accounting, shared sky targeting, exposed/covered behavior, deterministic local Soil-capacity variation, run-on infiltration, saturated Soil, surface-retention invariants, vertical falling, cyclic rain cadence, dormancy, Water wading and finite-world containment.

Generated hydro-climate coverage additionally locks exact fractional rate realization, spatially distinct rates, large-volume chunking, baseline evaporation/precipitation ordering and zero-forcing behavior without creating hydrology state.

Generic liquid/Soil coverage proves that non-Water identities reuse the shared solver, preserve identity, use the same Soil-retention mechanism, compete for one pore capacity, respond to viscosity, and leave excess free when uptake is bounded.

The visual Rain Cycle acceptance remains the manual parity gate for dry start, retained-Water wetting, uneven puddling, evaporation and inspection.

See [Liquids](liquids.md), [Water](water.md), [Water Traversal](water-traversal.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).
