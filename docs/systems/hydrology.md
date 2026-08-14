# Surface Hydrology

## Purpose

Introduce the first source side of the surface-water cycle without turning Weather, Terrain and Water into one system.

The current slice models one explicit precipitation input as:

```text
external precipitation
        |
        v
terrain material
        |
        +--> retained SoilMoisture
        |
        v
remaining volume
        |
        v
physical free space
        |
        v
authoritative WaterSystem
```

It does not yet choose when or where rain occurs. Sky exposure, weather cadence and storm generation remain separate future consumers of this transfer primitive.

## Ownership

The three relevant states remain independent:

```text
Terrain        XYZ -> landscape definition
SoilMoisture   terrain XYZ -> retained finite moisture
Water          XYZ -> free liquid volume
```

`SoilMoistureSystem` owns retained soil water. `WaterSystem` continues to own free liquid water. `PrecipitationSystem` owns neither state: it only routes an external input through their public mutation boundaries.

Consequently precipitation does not import or call `WaterFlowSystem`. A successful `WaterSystem.addAtMost(...)` already activates the local flow frontier through Water's own mutation semantics.

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

Both quantities use the same deterministic `0..CellVolume.FULL` fixed-point volume scale as surface water.

`capacity` is the maximum retained moisture represented for one terrain cell. `infiltrationLimit` bounds one requested infiltration transfer. It is intentionally **not** named `perTick`: the moisture owner has no simulation clock, so a time-based rate would be a false invariant at this layer. A future weather process may convert rainfall intensity and elapsed simulation time into transfer quantities before calling precipitation.

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

Storage is currently sparse: zero moisture is absence. As with Water, this is a replaceable representation rather than a world-cell architecture.

A terrain/material change never silently deletes already retained moisture. If the new material cannot retain the existing amount, coordinated terrain/hydrology displacement is a later mutation concern; the authoritative quantity remains until an explicit consumer moves or removes it.

## Precipitation transfer

The current operation is:

```java
PrecipitationResult applyTick(x, y, terrainZ, amount);
```

The caller explicitly supplies a terrain surface that precipitation reached. `PrecipitationSystem` does not scan the world and does not infer sky exposure.

The transfer order is deterministic:

```text
input
  |
  +-- soil infiltration first
  |
  +-- remaining water into terrain-anchor free space when Geometry opens from above
  |
  +-- remaining water into the cell directly above
  |
  +-- unplaced remainder returned to the caller
```

The anchor step is geometry-driven. A ramp can contain liquid in its free wedge, while a full solid terrain cell cannot. The implementation asks neutral `CellSpace`/`Shape.boundaryOpeningFloor(...)` facts and never branches on `RampShape`, `FullShape` or landscape material identity.

Unknown Shapes are conservative: Shape's default physical boundary is closed, so spare scalar volume alone never implies that rain can enter internal space.

## Conservation

`PrecipitationResult` carries exact accounting:

```text
input = infiltrated + surfaceWater + unplaced
```

The constructor enforces this invariant. Precipitation is an external source, so `input` may increase total world water, but no part of that source is silently lost inside the transfer operation.

`unplaced` is deliberate. A blocked/saturated surface is evidence that a higher-level precipitation/weather process must decide what the remaining source means; this low-level mechanic does not invent runoff destinations, delete water, or teleport it elsewhere.

## Deliberately absent

This foundation does not yet implement:

- weather state or weather transitions;
- rainfall scheduling, intensity fields or storm regions;
- derived/cached sky exposure;
- evaporation from surface Water or SoilMoisture;
- deep drainage/groundwater;
- plant uptake;
- terrain erosion;
- traversal/pathfinding effects from wet soil or water depth;
- runtime composition of Weather/Water in `SimulationAssembly`.

Those systems should consume the finite primitives here rather than expanding Terrain or introducing a giant mutable environment cell.

## Tests

Headless tests cover:

- soil-first precipitation ordering;
- infiltration limit and retained-moisture capacity;
- non-absorbing definitions without a `soil` aspect;
- ramp anchor-space reception without concrete Shape checks;
- conservative unknown-Shape top boundaries;
- explicit unplaced remainder on blocked surfaces;
- exact precipitation balance;
- no silent retained-moisture deletion after terrain change;
- definition compiler validation and freezing.

See [Water](water.md), [Geometry and Shape](geometry.md), and [Definitions](definitions.md).
