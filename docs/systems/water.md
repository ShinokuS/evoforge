# Water

## Purpose

Own finite authoritative liquid-Water quantity in the shared discrete XYZ world and redistribute it locally without embedding Water into Terrain, Geometry or a universal world-cell object.

The current foundation includes finite sparse quantity, Shape-derived free capacity, deterministic local redistribution, SurfaceWaterStorage, run-on Soil infiltration, shared sky-surface precipitation/evaporation, sparse latest-step flow diagnostics, optional finite runtime bounds and mover-specific terrestrial wading integrated into both MoveTo planning and authoritative Movement revalidation.

Drinking, swimming and richer fluid mechanics remain later consumers.

## Ownership

Water is independent landscape state:

```text
TerrainState   XYZ -> terrain identity | absence
Geometry       XYZ terrain anchor -> Shape | default FullShape
SoilMoisture   terrain XYZ -> retained finite moisture
WaterState     XYZ -> free liquid volume | dry
```

Shared coordinates are interaction addresses; Water does not become a Terrain field.

`WaterSystem` is the authoritative mutation owner. `WaterLookup` exposes current quantity:

```java
int amount(int x, int y, int z);
```

Dry Water is `CellVolume.EMPTY` (`0`). Current storage is sparse: only positive quantities require entries. Storage representation is replaceable.

## Quantity and geometry

Water uses the deterministic normalized cell-volume scale:

```text
CellVolume.EMPTY = 0
CellVolume.FULL  = 1_000_000
```

This is a fraction of one discrete cell volume, not litres or a declaration that a world cell is one cubic metre.

Water reads only neutral Geometry facts. It does not branch on `FullShape`, `RampShape` or future concrete Shape classes.

Important Geometry inputs are:

```text
CellSpace.capacity(shape)
CellSpace.surfaceHeight(shape, volume)
Shape.freeVolumeBelow(localHeight)
Shape.boundaryOpeningFloor(CellFace)
```

A coordinate with no Shape is open space. `FullShape` has zero Water capacity. A current cardinal `RampShape` leaves a half-cell free wedge with its own height/opening profile.

Navigation transition ports are not hydraulic openings.

## Mutation boundary

External quantity operations are bounded arithmetic:

```java
int addAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

They return the volume that actually entered or left authoritative Water. Negative requests are programming errors; zero is a valid no-op.

Successful mutation updates the derived surface cache and wakes the local flow frontier. Sources/sinks such as precipitation and evaporation therefore never bypass Water ownership.

## Derived wet-column surface

`WaterSystem` maintains `WaterSurfaceLookup`, the top positive-Water Z for each wet XY column.

The cache changes only through authoritative Water mutation/flow commit. A hydraulically dormant lake therefore remains discoverable by atmosphere/presentation without scanning vertical world space or iterating the flow frontier.

The cache owns no independent liquid quantity.

## Hydraulic flow model

`WaterFlowSystem` owns redistribution policy but no duplicate Water amount.

For a connected pair it compares hydraulic head derived from:

```text
absolute cell Z
+
local liquid surface height from Geometry
```

The same comparison explains lateral equalization and downward movement. A physical face's opening floor retains Water below its sill; a closed face creates no transfer.

Each update is two-phase:

```text
active frontier
      ↓
read authoritative Water + Geometry snapshot
      ↓
plan local face transfers
      ↓
apply source/opening/storage/destination bounds
      ↓
deterministic proportional limiting
      ↓
commit aggregate deltas simultaneously
```

Water that arrives during a step cannot be forwarded again during that same step. Candidate ordering and integer remainder allocation are deterministic.

The conservation invariant is exact:

```text
sum(delta over every changed cell) = 0
```

Only external sources/sinks may change total Water quantity.

## SurfaceWaterStorage

A supporting terrain definition may declare a finite `SurfaceWaterStorage` capacity.

This reserve is **free Water**, not SoilMoisture. It stays in `WaterState`, contributes to rendering/depth, and is conserved.

For same-Z horizontal flow, the source keeps the declared reserve before Water becomes mobile. The aggregate source limiter shares one reserve across simultaneous exits, preventing several neighbors from each draining the same shallow storage independently.

For vertical flow (`dz != 0`) the reserve is not subtracted. Water that can physically fall through an open lower boundary is not artificially held up by a horizontal runoff threshold.

## Relaxation and dormancy

The solver uses deterministic fixed-point relaxation instead of directly forcing pair equality. Pair transfer and aggregate source budgets are deliberately damped, and integer arithmetic supplies a one-quantum deadband.

Once no meaningful integer transfer remains, the active region sleeps:

```text
stable lake / retained puddle
        ↓
active frontier becomes empty
        ↓
flow work = 0 until another mutation wakes it
```

`WaterFlowProcess` advances one local hydraulic step per scheduled simulation tick while work exists.

## Water -> Soil exchange

Free Water arriving over absorbent terrain may infiltrate even when it did not originate as rain.

Before each scheduled flow solve, `WaterSoilExchangeSystem` inspects the same sparse active-Water frontier, resolves supporting terrain and transfers at most the Soil's current infiltration limit/remaining capacity from Water into `SoilMoistureSystem`.

The exact transferred amount is removed from Water. Then ordinary Water flow runs on what remains.

This gives one consistent physical chain:

```text
rain/run-on source
    ↓
SoilMoisture if capacity remains
    ↓
excess free Water
    ↓
hydraulic redistribution
```

See [Surface Hydrology](hydrology.md) for precipitation/evaporation composition.

## Latest actual-flow diagnostics

`WaterFlowSystem` exposes sparse `WaterFlowLookup` samples from the latest evaluated transfer step.

A sample exists only when real Water crossed a cell boundary. It records objective transfer direction/volume for diagnostics; it is not a persistent velocity field.

The map is cleared before each solve. A no-transfer fixed point therefore exposes no stale motion sample. Presentation maps those facts to directional/falling animation and treats no sample as calm.

## Atmosphere interaction

`VerticalSkySurfaceSystem` supplies one shared topmost exposed Terrain-or-Water surface per XY column.

- rainfall onto Terrain goes through SoilMoisture first and places only excess as free Water;
- rainfall onto exposed Water adds directly to the liquid surface;
- evaporation removes exposed free Water first and then exposed SoilMoisture;
- covered Water is not evaporated merely because it exists in sparse storage.

Periodic/cyclic scheduling belongs to the environment layer, not `WaterSystem` itself.

## Optional finite world bounds

When `SimulationAssembly.worldBounds(...)` is configured, the shared `WorldGeometryLookup` resolves coordinates outside the inclusive box as `FullShape`. Water therefore observes one physically closed world boundary through ordinary Geometry.

There is no Water-specific coordinate clamp, invisible deletion or special edge rule. Without configured bounds, the earlier unbounded semantics remain intentionally available.

Generated/unloaded/streamed world state is still separate future architecture.

## Water-aware terrestrial traversal

Finite Water influences ordinary terrestrial movement through a separate mover-specific constraint, not by changing Navigation topology.

A `WaterWadingProfile(maxDepth)` may be attached to an object definition. The same `WaterWadingConstraint` is composed into:

```text
MoveTo advisory PathQuery
MovementSystem start validation
MovementActionProcessor commit revalidation
```

Raw Water changes do not increment the landscape traversal revision. Current Water is read when planning/executing, and authoritative Movement remains the final gate.

See [Water Traversal](water-traversal.md).

## Geometry changes

Geometry owns Shape state; Water owns quantity. Geometry therefore never silently deletes displaced Water.

`WaterFlowSystem.activateAt(x,y,z)` exists as the narrow hydraulic wake point for a higher-level coordinator after relevant Geometry changes. General runtime Geometry/Water displacement coordination remains future work.

## Deliberately absent

Current Water does not implement:

- drinking/Thirst interactions;
- shallow-Water speed/cost penalties;
- swimming or waterborne locomotion;
- current forces, knockback or drowning;
- Water-body identity;
- pressure/inertia/viscosity/turbulence/erosion;
- object displacement volume;
- deep groundwater/drainage;
- automatic wake/displacement coordination for arbitrary runtime Geometry mutation;
- generated/streamed world-bound semantics beyond optional explicit runtime bounds.

## Tests

Headless coverage includes finite add/remove arithmetic, Shape capacity/opening behavior, exact conservation, simultaneous-exit limiting, SurfaceWaterStorage retention, vertical falling, run-on Soil infiltration, saturated-Soil behavior, deterministic convergence/dormancy, actual-flow sample clearing, cached Water surfaces, precipitation/evaporation accounting, Water-aware planning/execution and explicit finite-world containment.

Visual acceptance additionally covers Rain Cycle, stacked Z flow, Geometry/Ramp stress, Surface optical depth and calm/active Water presentation.

See [Geometry and Shape](geometry.md), [Surface Hydrology](hydrology.md), [Water Traversal](water-traversal.md), and the historical [Water Foundation note](../notes/water-foundation.md).
