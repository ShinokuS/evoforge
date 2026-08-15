# Water

## Purpose

Define Water-specific behavior on top of EvoForge's shared liquid foundations.

Water was the first implemented liquid. Storage, hydraulic transport, surface retention and porous-Soil retention are now generic mechanics described in [Liquids](liquids.md). `WaterSystem` is a narrow typed facade for the open `WaterSystem.TYPE` identity; precipitation, Water evaporation, wading and presentation remain explicit Water consumers.

## Ownership

Authoritative free-liquid quantity belongs to `LiquidSystem`:

```text
XYZ -> dry
   or
XYZ -> LiquidTypeId + finite free volume
```

Water owns the `water` identity. `WaterLookup` projects only that constituent:

```java
int amount(int x, int y, int z);
```

The accepted normalized scale remains:

```text
CellVolume.EMPTY = 0
CellVolume.FULL  = 1_000_000
```

Water does not own a separate storage implementation or hydraulic solver.

## Mutation facade

`WaterSystem` delegates Water mutations to the shared `LiquidSystem`:

```java
int addAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

The returned amount is the volume actually added or removed. Geometry capacity, liquid-type collision rules, sparse state, surface projection and hydraulic wakeup remain generic responsibilities.

Water-specific sources/sinks use this typed facade instead of mutating generic storage directly.

## Shared hydraulic transport

One runtime composes one `LiquidFlowSystem` for every free-liquid identity in that world.

The accepted Water mechanics remain:

- Shape-derived free capacity;
- physical face openings and vertical falling;
- hydraulic head from world Z plus local fill height;
- deterministic plan / limit / commit updates;
- exact finite-volume conservation;
- proportional simultaneous-transfer limiting;
- fixed-point relaxation;
- sparse active frontier and dormancy;
- actual latest-step transfer diagnostics.

Water uses `LiquidTransportProperties.reference()`, so the generic viscosity model preserves the previous reference-Water cadence.

There is no `WaterFlowSystem` transport authority. Water-facing diagnostics are obtained through `WaterFlowLookup.from(liquidFlow.flowLookup())`, which filters generic flow samples by `WaterSystem.TYPE`.

See [Liquids](liquids.md) for the current single-component free-cell contact rule and transport-property model.

## Water surface projection

`WaterSurfaceLookup` is a Water-filtered projection over generic `LiquidSurfaceLookup`. It exposes Water columns for atmosphere, traversal and presentation consumers without owning quantity.

## Surface retention

Surface microtopographic retention is now generic material data:

```text
SurfaceRetentionDefinitions
        ↓
TerrainSurfaceRetentionLookup
        ↓
LiquidFlowSystem
```

The capacity is free-liquid volume retained on a supporting material before same-Z horizontal runoff. It is distinct from retained Soil pore volume and remains part of authoritative free-liquid state.

The former Water-specific `SurfaceWaterStorageDefinitions`, `SurfaceWaterStorageLookup` and `TerrainSurfaceWaterStorageLookup` have been removed. Water uses the same generic material capability as every other free liquid.

The current retention value is material-owned and liquid-neutral. Future liquid-dependent wetting must be introduced through real physical properties such as surface tension/contact angle if a consumer requires them, not through Water-specific parallel storage definitions.

## Soil infiltration and retained Water

Soil infiltration is generic. `SoilLiquidSystem` owns retained composition and one shared material pore capacity.

Water participates as one constituent:

```text
free Water
    ↓
SoilLiquidInfiltrationSystem
    ↓
SoilLiquidSystem
    ├─ retained Water
    └─ other retained constituents
```

There is no separate `SoilMoistureSystem` authority or Water-only soil-exchange adapter.

Porous terrain declares `SoilProperties(capacity, permeability)`. Water's reference viscosity converts permeability to the same nominal uptake rate used to configure the material. A more viscous future liquid receives a lower effective uptake rate through the generic transport math without changing Water or Soil storage code.

Water retained in Soil is read explicitly through:

```java
soilLiquids.amountOf(WaterSystem.TYPE, x, y, z)
```

## Atmosphere interaction

Current precipitation and evaporation remain Water-specific integrations.

Rain behavior:

- rain onto exposed Terrain attempts generic Soil retention as Water first;
- excess becomes free Water in available surface volume;
- rain onto exposed Water adds directly to the Water column.

Water evaporation:

- removes exposed free Water first;
- then removes exposed retained Water;
- does not remove other retained constituents;
- reports `surfaceWaterRemoved` and `retainedWaterRemoved` separately.

Another liquid does not automatically receive Water's atmosphere semantics merely because it shares the transport foundation.

See [Surface Hydrology](hydrology.md).

## Water-aware traversal

`WaterWadingConstraint` remains Water-specific gameplay behavior.

A mover with `WaterWadingProfile(maxDepth)` evaluates current Water depth during path planning and authoritative Movement revalidation. A non-Water liquid does not automatically inherit Water's wading profile or hazard semantics.

See [Water Traversal](water-traversal.md).

## Geometry changes

Geometry owns Shape; liquids own quantity. Geometry changes therefore do not silently delete Water.

Hydraulic wakeup belongs to the shared `LiquidFlowSystem.activateAt(...)` capability. General runtime coordination of displaced liquid after arbitrary Geometry changes remains future work.

## Mixing boundary

Water can coexist with other liquid identities in one generic liquid world, but current free-liquid cells are single-component. Unlike-liquid contact blocks explicitly instead of being silently merged.

Retained Soil composition may contain Water alongside other constituents because all share porous capacity. That does not implement free-liquid mixing, chemistry, diffusion or phase separation.

See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md).

## Deliberately absent

Current Water integration does not implement:

- drinking/Thirst interactions;
- shallow-Water speed penalties beyond current wading admissibility;
- swimming or waterborne locomotion;
- current forces, knockback or drowning;
- Water-body identity;
- detailed pressure/inertia/turbulence/erosion;
- surface tension/contact-angle wetting;
- object displacement volume;
- deep groundwater/drainage;
- automatic wake/displacement coordination for arbitrary Geometry mutation;
- arbitrary-liquid atmosphere or traversal semantics;
- free-liquid mixtures/reactions;
- retained-liquid diffusion, leaching or reactions.

Kinematic viscosity is part of the generic liquid foundation and is already active for Water/free-flow/Soil uptake calculations.

## Tests and acceptance

Water headless coverage remains the regression contract for Water semantics: finite mutations, Geometry capacity/openings, conservation, vertical falling, surface retention, Soil infiltration, saturation, precipitation/evaporation accounting, Water surfaces, actual-flow diagnostics, Water wading and finite-world containment.

Generic liquid tests additionally verify non-Water transport, explicit transport definitions, viscosity-dependent mobility, shared Soil pore capacity, retained constituent identity and deterministic no-mix contact behavior.

Visual Water acceptance remains Rain Cycle, stacked Z flow, Geometry/Ramp stress, optical depth and calm/active Water presentation.

See [Liquids](liquids.md), [Surface Hydrology](hydrology.md), [Geometry and Shape](geometry.md), [Water Traversal](water-traversal.md), and the historical [Water Foundation note](../notes/water-foundation.md).
