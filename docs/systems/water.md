# Water

## Purpose

Own finite authoritative liquid-water quantity in the shared discrete XYZ world and redistribute that quantity locally without embedding water into Terrain, Geometry or a giant world-cell object.

The current slice includes finite state, the deterministic local flow solver, cached wet-column surfaces, an opt-in runtime process that advances active flow one local step per simulation tick until dormancy, precipitation-driven replenishment and a simple exposed-surface evaporation sink. Traversal effects and drinking remain later slices.

## Ownership

Water is an independent landscape mechanic:

```text
TerrainState   XYZ -> terrain identity | absence
Geometry       XYZ terrain anchor -> Shape | default FullShape
WaterState     XYZ -> liquid volume | dry
```

Shared coordinates are interaction addresses; Water does not become a Terrain field.

`WaterSystem` is the authoritative quantity mutation owner. `WaterLookup` exposes current quantity:

```java
int amount(int x, int y, int z);
```

Dry/absent water is represented by `CellVolume.EMPTY` (`0`), not by a special water object.

`WaterFlowSystem` owns redistribution policy and a sparse activity frontier, but no separate liquid quantity. `WaterFlowProcess` is only a scheduler adapter around that solver.

## Quantity representation

Water quantity uses the deterministic cell-volume scale introduced by Geometry:

```text
CellVolume.EMPTY = 0
CellVolume.FULL  = 1_000_000
```

A quantity is a fraction of one discrete cell volume. It does not yet define litres, density or world-cell dimensions.

Current storage is sparse: only positive quantities require entries. This is a replaceable representation, not a public promise about future chunking or packing.

## Derived wet-column surface

`WaterSystem` also maintains `WaterSurfaceLookup`, a derived cache of the top positive-water Z in each wet XY column.

The cache is updated through the same authoritative mutation boundary as Water quantity:

- external `addAtMost(...)` / `removeAtMost(...)` calls;
- simultaneous flow commits through `replaceFromFlow(...)`.

It therefore remains correct when a stable lake is hydraulically dormant. Atmosphere consumers can find an exposed lake surface without iterating the Water flow frontier or scanning vertical world space.

The cache owns no independent liquid quantity and cannot mutate Water.

## Geometry-derived free space

Water reads only neutral Geometry facts. It does not branch on `FullShape`, `RampShape` or future concrete Shape classes.

The basic capacity remains:

```text
open cell             capacity = 1.0
FullShape anchor cell capacity = 0.0
RampShape anchor cell capacity = 0.5
```

Flow additionally needs more than one scalar volume. Geometry exposes a coarse cell-local free-space profile:

```text
freeVolumeBelow(localHeight)
boundaryOpeningFloor(CellFace)
```

`freeVolumeBelow` answers how much geometric free volume exists below a normalized height. `boundaryOpeningFloor` answers the lowest local elevation where that free space connects through one physical cell face, or `CellSpace.CLOSED`.

These are objective physical facts. They are **not** water-specific behavior and are deliberately independent from navigation `transitionPorts()`.

For a coordinate without a Shape, free volume is linear with height and all physical faces are open. `FullShape` exposes no free volume/opening. `RampShape` exposes a height-dependent wedge profile, an open low side, a closed high side, an open top and a blocked bottom.

The current boundary contract is intentionally first-order. It is sufficient for the finite flow solver without inventing a mesh/subcell/hydraulic hierarchy. A future Shape whose physically important opening cannot be represented by one lower sill must drive a richer neutral boundary profile rather than a concrete-type branch in Water.

## Mutation semantics

The external primitive operations remain arithmetic:

```java
int addAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

They return the volume that actually entered or left Water state.

Successful external mutation also wakes the changed cell in the local flow frontier. Zero/no-op mutations do not create work.

Negative requests are programming errors. Zero is a valid no-op.

This API is the shared boundary for finite sources and sinks. Precipitation uses `addAtMost(...)`; evaporation uses `removeAtMost(...)`. Neither needs to import `WaterFlowSystem`, and conservation accounting uses the returned actual quantity rather than bypassing Water ownership.

## Hydraulic-head model

The solver deliberately does not have separate `gravity()` and `spreadHorizontally()` procedures.

For every active local cell pair it derives one hydraulic head from:

```text
absolute cell Z
+
local liquid surface height
```

The local surface height is the inverse of Geometry's `freeVolumeBelow(height)` profile.

Therefore the same comparison explains both cases:

```text
same Z, unequal surfaces -> lateral equalization
higher cell over lower   -> downward flow through an open face
```

A face opening contributes a lower sill. Water below that sill is retained in the source; Water above it may participate in the pair flux. A closed face contributes no connection.

If Geometry has changed so existing authoritative Water exceeds the new free-space capacity, the excess contributes additional hydraulic head rather than being deleted. Flow can displace it through available openings. A completely closed replacement Shape can still require a higher-level coordinated Geometry/Water mutation because there is no post-hoc physical exit to use.

## Deterministic two-phase redistribution

One flow update is conceptually:

```text
active frontier
      |
      v
read one authoritative Water + Geometry snapshot
      |
      v
calculate desired neighbor transfers
      |
      v
bound by source quantity + opening sill + destination capacity
      |
      v
deterministic proportional reduction
      |
      v
commit aggregate cell deltas simultaneously
```

No transfer can consume Water that arrived earlier in the same update. This prevents traversal-order cascades such as `A -> B -> C` in one solver step.

Every undirected face pair is considered at most once per update. Candidate cells/edges and integer remainder allocation use stable coordinate ordering, so `HashMap`/`HashSet` iteration order cannot change authoritative results.

The commit applies net deltas after the whole plan is resolved. The exact redistribution invariant is:

```text
sum(delta over every changed cell) = 0
```

Only explicit external source/sink operations may change total Water quantity.

## Relaxation and integer deadband

Direct pair equalization can overshoot when one source has several lower neighbors. The current solver therefore uses deterministic fixed-point relaxation:

```text
pair transfer -> half of the pair equilibrium transfer
combined outgoing source budget -> at most half of snapshot source quantity
```

Integer division supplies a natural one-quantum deadband. Once no meaningful integer flux remains, the region is considered locally stable rather than oscillating forever around an unrepresentable fractional equilibrium.

These constants are solver policy, not Shape data or fluid identity. They can be refined later from representative hydrology scenarios without changing Water ownership.

## Active frontier, runtime cadence and dormancy

The solver never scans the whole world and does not iterate every wet cell forever.

A cell is active when:

- external Water quantity at that cell changes;
- a previous flow step changed that cell;
- a higher-level Geometry coordinator explicitly activates the cell after physical geometry changes.

`WaterFlowSystem.update()` drains the current active set in deterministic order. If no transfer occurs, nothing is reactivated and the region becomes dormant:

```text
stable lake / isolated puddle
        |
        v
active set becomes empty
        |
        v
future flow work = 0
```

`WaterFlowProcess` connects this model to the simulation scheduler. `activate()` schedules at most one continuation. Each continuation performs one local solver update one tick later and reschedules itself only while the active frontier still contains work.

Production precipitation and evaporation mutate Water only through `WaterSystem`; composition ensures pending hydraulic work is scheduled when free Water was actually changed. If a weather event only changes SoilMoisture, no hydraulic work is needed.

The frontier stores changed cells, not every cell containing Water. Each active cell examines only its six physical face neighbors.

## Atmosphere interaction

Vertical atmospheric exposure is now shared through `SkySurfaceLookup` rather than being implemented separately by every environment effect.

Precipitation can add Water to an exposed liquid surface or route rainfall through exposed terrain and SoilMoisture first.

The current evaporation placeholder requests a fixed absolute volume per exposed wet XY column. It removes exposed free Water before exposed retained SoilMoisture. This intentionally approximates surface-area-driven loss rather than percentage decay of stored volume.

Covered Water is not evaporated merely because it is present in `WaterStorage`: sparse wet candidates are revalidated against the shared sky surface before the sink is applied.

## Geometry changes and displacement

Geometry owns Shape state; Water owns quantity. Geometry therefore never silently deletes displaced Water.

A higher-level landscape mutation coordinator should perform the Geometry change and call:

```java
waterFlow.activateAt(x, y, z);
```

and ensure the runtime flow process is awakened when local hydraulic reevaluation is required.

Arbitrary runtime Geometry-to-Water wake coordination is still separate work.

The solver can redistribute over-capacity Water when changed geometry still exposes a physical route. More complex object/terrain displacement remains a coordinated future mechanic rather than an implicit side effect of Geometry writes.

## Current world-boundary limitation

The current sandbox has no authoritative finite generated-world boundary. In a deliberately open test setup, Water can therefore continue flowing into coordinates with no supporting generated landscape.

Water does not compensate for this with invisible walls, deletion at an edge, or an arbitrary coordinate clamp. Physical containment belongs to the future world-generation/world-bounds architecture.

Until then, hydrology scenarios that require retained Water should construct enclosing terrain and avoid layouts that permit indefinite runoff into ungenerated empty space. This does not weaken Water conservation: the solver still conserves finite quantity while redistributing it among represented coordinates.

## Deliberately absent

The current Water/Surface-Hydrology slices do **not** yet implement:

- full Weather state, storm fields or moving atmospheric fronts;
- temperature, humidity, wind or solar-driven evaporation rates;
- authoritative generated world bounds;
- springs or other independent scheduled sources/drains;
- deep drainage/groundwater or plant soil-water uptake;
- automatic Water wake coordination for arbitrary runtime Geometry changes;
- object displacement volume;
- traversal/pathfinding effects;
- semantic Water-depth bands for movers;
- Water-body identity;
- Thirst/Drink interactions;
- detailed pressure, inertia, viscosity, turbulence or erosion.

Those behaviors must be introduced by their first real consumer. In particular, raw Water amount changes do not increment Navigation/Pathfinder revisions.

## Tests

Headless tests cover finite add/remove arithmetic, generic Shape capacity, deterministic hydraulic redistribution and conservation, ramp opening behavior, convergence/dormancy, cached wet surfaces, precipitation source accounting, shared sky targeting, finite exposed evaporation, Water-before-soil evaporation, covered-water protection, periodic evaporation cadence and rain suppression.

See [Geometry and Shape](geometry.md), [Surface Hydrology](hydrology.md), and the [Water Foundation design note](../notes/water-foundation.md).
