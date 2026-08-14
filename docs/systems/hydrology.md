# Surface Hydrology

## Purpose

Model the source side of the surface-water cycle without turning Weather, Terrain and Water into one system.

The current slice supports both explicit single-surface precipitation and an opt-in periodic runtime source:

```text
periodic external precipitation
        |
        v
cached sky-addressable XY surfaces
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
```

The periodic source is deliberately simple and uniform. It is not yet a complete Weather model.

## Ownership

The relevant authoritative states remain independent:

```text
Terrain        XYZ -> landscape definition
SoilMoisture   terrain XYZ -> retained finite moisture
Water          XYZ -> free liquid volume
```

`SoilMoistureSystem` owns retained soil water. `WaterSystem` owns free liquid water. `PrecipitationSystem` owns neither state: it routes an external input through their public mutation boundaries.

Derived surface indexes are not new authoritative world state. `TerrainSystem` maintains the top occupied terrain Z for each XY column as terrain changes. `WaterSystem` maintains the top positive-water Z for each wet XY column through the same mutation boundary used by external transfers and flow commits.

Consequently a dormant lake remains visible to precipitation targeting even though its Water flow frontier is empty.

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

Both quantities use the deterministic `0..CellVolume.FULL` fixed-point volume scale shared with surface Water.

`capacity` is the maximum retained moisture represented for one terrain cell. `infiltrationLimit` bounds one requested infiltration transfer. It intentionally remains independent from simulation time: the periodic precipitation source owns cadence and converts its configured event amount into one transfer request.

A landscape definition with no `soil` aspect is non-absorbing. There is no implicit dirt/rock/material fallback and no hardcoded landscape-definition switch.

## Finite retained moisture

`SoilMoistureSystem` exposes finite arithmetic mutation:

```java
int infiltrateAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

`infiltrateAtMost` is bounded by:

```text
requested volume
material infiltrationLimit
remaining material capacity
```

The smallest bound is the actual retained amount returned to the caller.

`removeAtMost` is the symmetric authoritative drain primitive. It does not implement evaporation or plant uptake itself; those later consumers can use it without bypassing moisture invariants.

Storage is sparse: zero moisture is absence. As with Water, this is a replaceable representation rather than a world-cell architecture.

A terrain/material change never silently deletes already retained moisture. If the new material cannot retain the existing amount, coordinated terrain/hydrology displacement remains a separate mutation concern; the authoritative quantity stays until an explicit consumer moves or removes it.

## Explicit precipitation targets

`PrecipitationSystem` has two objective target operations:

```java
PrecipitationResult applyTerrainSurface(x, y, terrainZ, amount);
PrecipitationResult applyWaterSurface(x, y, waterZ, amount);
```

A terrain target applies soil infiltration first. Remaining volume is offered to terrain-anchor free space when neutral Geometry opens from above, then to the cell directly above.

A Water target bypasses soil and adds directly to the exposed liquid cell, then the cell above if the current top cell saturates. This prevents rain over an existing lake from repeatedly infiltrating soil underneath the lake.

The terrain-anchor step remains geometry-driven. A Ramp can contain liquid in its free wedge while a full solid terrain cell cannot. The implementation asks neutral `CellSpace` / `Shape.boundaryOpeningFloor(...)` facts and never branches on `RampShape`, `FullShape` or landscape material identity.

Unknown Shapes are conservative: Shape's default physical boundary is closed, so spare scalar volume alone never implies that rain can enter internal space.

## Cached sky targeting

`TerrainSurfaceLookup` exposes one topmost terrain anchor per occupied XY column and deterministic X/Y iteration. Internally `TerrainSystem` maintains this index during accepted place/remove mutations; rain never scans vertical world space to rediscover column tops.

`WaterSurfaceLookup` exposes the top positive-water cell per wet XY column and deterministic X/Y iteration. `WaterSystem` updates it when a cell becomes wet or dry, including simultaneous Water flow commits.

`SkyPrecipitationSystem` works over the union of occupied Terrain columns and wet Water columns. A shared XY column is processed exactly once:

```text
terrain exists and highest wet Z > highest terrain Z
        -> exposed Water target
terrain exists otherwise
        -> exposed terrain target
water exists without terrain
        -> exposed Water target
```

This gives several useful emergent semantics without special object types:

- a cave roof, bridge-like terrain layer or other higher terrain anchor shields lower terrain from vertical precipitation;
- a lake whose Water rises above terrain receives rainfall at its liquid surface;
- runoff, a stream or a waterfall occupying a wet column without terrain still receives vertical precipitation;
- Water sharing the same anchor Z as a partial terrain Shape keeps terrain-first semantics because the coarse cell model cannot yet resolve subcell exposed area;
- a column containing neither Terrain nor Water is outside the current precipitation domain and creates no state merely because rain exists globally.

The indexes are caches of authoritative Terrain/Water mutations, not a separate weather grid.

## Periodic runtime source

`SimulationAssembly` may opt into uniform periodic precipitation:

```java
assembly.periodicPrecipitation(amountPerColumn, intervalTicks);
```

The first event occurs after `intervalTicks`; later events repeat at that cadence. `amountPerColumn` is a finite source quantity applied independently once to every currently sky-addressable Terrain/Water column.

`PeriodicPrecipitationSystem` owns only recurrence. It delegates surface selection and transfer accounting to `SkyPrecipitationSystem`.

Production composition also installs `WaterFlowProcess`. After each precipitation event the composition root asks that process to wake. Wakeups coalesce, and the process advances one local `WaterFlowSystem` update per simulation tick until the active frontier reaches dormancy. Soil-only precipitation therefore schedules no unnecessary hydraulic work.

No fixed global Water scan or vertical Z scan is introduced.

## Conservation

`PrecipitationResult` carries exact accounting for one target:

```text
input = infiltrated + surfaceWater + unplaced
```

`PrecipitationBatchResult` carries the same invariant using `long` totals across the complete union of exposed Terrain/Water columns.

Precipitation is an external source, so `input` may increase total world water. No part of that source is silently lost inside the transfer operation.

`unplaced` is deliberate. A blocked/saturated surface is evidence that a higher-level weather/source policy must decide what the remaining source means; the low-level mechanic does not invent runoff destinations, delete water, or teleport it elsewhere.

## Deliberately absent

The current runtime does **not** yet implement:

- weather state or weather transitions;
- spatial rainfall intensity fields, storms or moving fronts;
- object/canopy occlusion of sky;
- evaporation from surface Water or SoilMoisture;
- deep drainage/groundwater;
- plant uptake;
- terrain erosion;
- traversal/pathfinding effects from wet soil or water depth;
- automatic hydraulic wake coordination for arbitrary runtime Geometry changes.

Those systems should consume the finite primitives and derived surface indexes here rather than expanding Terrain or introducing a giant mutable environment cell.

## Tests

Headless tests cover:

- soil-first precipitation ordering;
- infiltration limit and retained-moisture capacity;
- non-absorbing definitions without a `soil` aspect;
- Ramp anchor-space reception without concrete Shape checks;
- conservative unknown-Shape top boundaries;
- direct exposed-Water precipitation;
- exposed Water columns without Terrain;
- explicit unplaced remainder on blocked surfaces;
- exact single-target and union-batch precipitation balance;
- deterministic cached terrain/water-surface iteration;
- top wet-cell tracking through external and flow mutations;
- cave-roof shielding and lake-surface targeting;
- scheduled flow dormancy;
- production periodic precipitation cadence;
- no hydrology work when no periodic source is configured.

See [Water](water.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).
