# Surface Hydrology

## Purpose

Model the finite surface-water cycle without turning Weather, Terrain, Soil and Water into one mutable world-cell system.

The current hydrology path is:

```text
periodic external precipitation
        |
        v
shared vertical sky surfaces
        |
        +--> exposed Water -> Water directly
        |
        v
exposed terrain
        |
        +--> retained SoilMoisture
        |
        v
remaining volume
        |
        v
authoritative WaterSystem
        |
        v
local WaterFlowProcess until dormancy

periodic evaporation
        |
        v
wet state-bearing columns only
        |
        v
shared vertical sky surface revalidation
        |
        +--> exposed Water first
        |
        +--> exposed SoilMoisture second
```

Precipitation and evaporation are deliberately simple uniform forcings. They are not yet a complete Weather model.

## Ownership

The authoritative states remain independent:

```text
Terrain        XYZ -> landscape definition
SoilMoisture   terrain XYZ -> retained finite moisture
Water          XYZ -> free liquid volume
```

`SoilMoistureSystem` owns retained soil water. `WaterSystem` owns free liquid water. Precipitation and evaporation own no stored water; they route finite source/sink requests through the public mutation boundaries of those owners.

Derived indexes are caches, not additional authoritative world state:

- `TerrainSurfaceLookup` tracks the highest occupied terrain Z per XY column;
- `WaterSurfaceLookup` tracks the highest positive-water Z per wet XY column;
- `SoilMoistureCellsLookup` tracks cells retaining positive soil moisture.

## Landscape soil aspect

Whether terrain absorbs water is definition data rather than a concrete terrain-class check.

A landscape definition may declare:

```json
{
  "aspects": {
    "soil": {
      "capacity": 600000,
      "infiltrationLimit": 125000
    }
  }
}
```

Both values use the deterministic `0..CellVolume.FULL` volume scale shared with Water.

`capacity` is the retained-moisture capacity of one terrain cell. `infiltrationLimit` bounds one infiltration request. It is intentionally not expressed as a per-tick rate because `SoilMoistureSystem` does not own simulation time.

A landscape definition without a `soil` aspect is non-absorbing. No concrete dirt/rock/material switch exists.

## Finite retained moisture

`SoilMoistureSystem` exposes finite arithmetic mutation:

```java
int infiltrateAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

`infiltrateAtMost` is bounded by the requested amount, material infiltration limit and remaining capacity.

`removeAtMost` is the symmetric authoritative drain primitive used by later consumers such as evaporation or plant uptake.

Storage is sparse. Zero moisture is absence. Positive-moisture transitions also maintain `SoilMoistureCellsLookup`, allowing environmental sinks to inspect wet state without scanning all terrain.

A terrain/material change never silently deletes already retained moisture.

## Shared vertical sky surface

Precipitation was the first consumer of vertical exposure. Evaporation is now the second, so the resolution rule lives in a shared environment capability rather than being duplicated inside either effect.

`VerticalSkySurfaceSystem` combines cached Terrain and Water surfaces and exposes:

```java
SkySurface find(x, y);
void forEach(SkySurfaceConsumer consumer);
```

For one XY column:

```text
no Terrain, wet Water
        -> WATER at highest wet Z
Terrain and highest wet Z > highest terrain Z
        -> WATER at highest wet Z
Terrain otherwise
        -> TERRAIN at highest terrain Z
neither
        -> no sky surface
```

A Water/Terrain tie remains terrain-first. This is a deliberate coarse-cell convention: the model does not yet resolve sub-cell exposed areas of a partially occupied Shape.

The same rule gives useful behavior without special lake/cave/roof object types:

- higher terrain shields lower terrain and Water from vertical atmosphere effects;
- Water above terrain is the exposed surface of a lake;
- a wet Water-only column remains vertically sky-addressable;
- a dry, empty column creates no hydrology state merely because a global atmosphere exists.

`SkyPrecipitationSystem` now consumes this shared resolver. Evaporation uses the same resolver to revalidate sparse wet candidates before removing anything.

## Precipitation

`PrecipitationSystem` has two objective target operations:

```java
PrecipitationResult applyTerrainSurface(x, y, terrainZ, amount);
PrecipitationResult applyWaterSurface(x, y, waterZ, amount);
```

A terrain target infiltrates soil first. Remaining volume is offered to terrain-anchor free space when neutral Geometry opens from above, then to the cell directly above.

A Water target bypasses soil and enters exposed free liquid directly.

The terrain-anchor step remains geometry-driven. A Ramp may contain liquid in its free wedge while a full solid terrain cell cannot. No concrete Shape branch is used.

`PrecipitationResult` enforces:

```text
input = infiltrated + surfaceWater + unplaced
```

`PrecipitationBatchResult` applies the same invariant across one uniform sky pass.

## Simple evaporation placeholder

Evaporation is a finite sink rather than a percentage decay.

One configured event requests an absolute volume per wet candidate XY column:

```text
evaporation amount per column = fixed volume
```

This intentionally approximates equal exposed cell surface area. A shallow puddle and a deep lake with the same exposed XY area lose the same volume during one event; depth only changes how many events are required to dry them.

`EvaporationSystem` builds its candidate set only from:

- currently wet Water columns;
- currently positive SoilMoisture cells.

It does not scan every terrain column. Each candidate column is then revalidated through `SkySurfaceLookup`, so covered/cave moisture is not removed.

Removal order for one exposed column is:

```text
1. exposed free Water
2. retained SoilMoisture on the exposed terrain surface
3. unfulfilled remainder
```

If a small puddle dries before the configured amount is exhausted, the remainder may continue into exposed soil moisture in the same event.

Water stored in a top-open partial terrain anchor can evaporate before retained moisture in that same anchor. This uses neutral Geometry top-opening facts rather than a concrete Ramp check.

`EvaporationResult` enforces:

```text
requested = surfaceWaterRemoved + soilMoistureRemoved + unfulfilled
```

`EvaporationBatchResult` enforces the same invariant with `long` totals.

## Periodic environment cadence

`PeriodicPrecipitationSystem` owns recurrence for the simple precipitation source.

`PeriodicEvaporationSystem` owns recurrence for the simple evaporation sink.

Evaporation is suppressed on a simulation tick that also contains a configured precipitation event. `PrecipitationEventLookup` reports both the next scheduled precipitation tick and the just-completed precipitation tick, so suppression does not depend on scheduler handler order.

The current periodic systems are temporary forcing mechanisms. Future Weather state, fronts, humidity, temperature, wind and solar exposure should determine their rates without moving Water or SoilMoisture ownership into Weather.

## Water flow interaction

Successful Water additions/removals wake Water's local hydraulic frontier through `WaterSystem` itself.

`WaterFlowProcess` advances one local solver update per scheduled simulation tick until the active frontier reaches dormancy. Weather effects do not import or manipulate the flow solver directly; production composition is responsible only for ensuring a pending flow process exists after an external Water mutation.

## Current world-boundary limitation

The current test/sandbox world has no authoritative finite world bounds. Therefore a sufficiently open setup can allow Water flow to continue into coordinates containing no supporting generated landscape.

This slice deliberately does **not** invent an artificial invisible wall, delete escaping Water, or add a temporary edge rule.

World containment belongs to the future world-generation/world-bounds architecture. Generated scenarios are expected to provide physical enclosing terrain where containment is required. Until that system exists, hydrology acceptance scenarios should avoid maps where Water can escape indefinitely into ungenerated empty space.

This is a known scenario constraint, not a Water conservation exception: the solver still conserves finite Water while redistributing it between represented cells.

## Deliberately absent

The current hydrology/environment foundation still does not implement:

- full Weather state or weather transitions;
- spatial rainfall/evaporation fields or moving fronts;
- temperature, humidity, solar radiation or wind-driven evaporation;
- object/canopy atmospheric occlusion;
- deep drainage/groundwater;
- plant uptake;
- terrain erosion;
- traversal/pathfinding effects from wet soil or Water depth;
- authoritative generated world bounds;
- automatic hydraulic wake coordination for arbitrary runtime Geometry changes.

These should be added by their first real consumers rather than by expanding Terrain or creating a giant environment cell.

## Tests

Headless tests cover precipitation, finite soil retention, shared sky targeting, Water/terrain shielding, deterministic sparse surface iteration, exact source accounting, finite exposed evaporation, Water-before-soil evaporation, covered moisture protection, fixed absolute evaporation volume, periodic cadence and same-tick precipitation suppression.

See [Water](water.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).
