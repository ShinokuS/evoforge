# Water Foundation — working design note

> Status: first redistribution foundation implemented. This note records the current Water milestone boundary; normative cross-system rules remain in `architecture.md` and current subsystem semantics in `systems/`.

## Goal

Introduce finite, deterministic water as an independent landscape mechanic without turning Terrain, Geometry, Navigation or World cells into a water-specific model.

The intended progression is:

```text
neutral volumetric Shape facts        done
        ↓
finite Water state                    done
        ↓
deterministic local flow              done
        ↓
rain / soil moisture / evaporation    next
        ↓
traversal integration
        ↓
visual hydrology acceptance
        ↓
Thirst + Drink
```

## Authoritative quantity

Water quantity uses deterministic integer/fixed-point volume, not `float`/`double` authoritative mass.

The neutral geometry scale is:

```text
0         = no cell volume
1_000_000 = one full discrete cell volume
```

This does not define the physical size of a world cell. If EvoForge later fixes a cell edge length in metres, litres and mass can be derived from cell volume and fluid density without changing the stored geometric fraction.

For every closed flow update:

```text
sum(after) = sum(before)
```

Only explicit future sources/sinks may change total quantity. Numerical damping changes flux, never mass.

## XYZ convention

Shared XYZ is an address, not shared storage.

```text
TerrainState   XYZ -> terrain identity | absence
Geometry       terrain anchor -> Shape | default FullShape
WaterState     XYZ -> liquid quantity | absence
```

Water is not stored "inside Terrain". A Water cell at XYZ occupies free spatial volume at that XYZ.

Current production approximation:

```text
FullShape solid volume = 1.0 cell
RampShape solid volume = 0.5 cell
```

## Geometry boundary proved by the first solver

`Shape.solidVolume()` alone was insufficient for deterministic flow because it could not answer where free space lies or where neighboring free spaces connect.

The first solver proved the smallest current neutral extension:

```text
freeVolumeBelow(localHeight)
boundaryOpeningFloor(CellFace)
```

`freeVolumeBelow` provides a monotonic cell-local free-space profile. `boundaryOpeningFloor` provides the lowest local sill through one of the six physical faces, or `CellSpace.CLOSED`.

These are Geometry facts, not Water methods. Navigation `transitionPorts()` remain traversal roles and are not fluid openings.

The current boundary representation is intentionally coarse. A future arch, hole or other Shape that cannot be represented by one lower sill must justify a richer neutral boundary profile. Water must not grow concrete `RampShape` / `FullShape` branches to compensate.

## Solver model

The baseline solver is discrete and local, not Navier–Stokes/SPH/FLIP.

It uses one hydraulic-head rule rather than separate gravity/spread procedures:

```text
absolute Z + local liquid surface height
```

The same comparison yields downward transfer between vertically adjacent open cells and lateral equalization between same-Z cells.

A transfer may use only source volume above the shared physical opening sill and only destination snapshot free capacity.

Every update is two-phase:

```text
current snapshot
    ↓
calculate desired face fluxes
    ↓
bound source/destination totals deterministically
    ↓
commit aggregate deltas
```

Newly received water cannot be forwarded again during the same update. This prevents iteration-order teleportation through multiple cells.

## Determinism and convergence

Candidate active cells are sorted. Undirected physical edges are canonicalized and evaluated once. Integer remainder allocation uses stable coordinate order.

The current relaxation policy uses half-equilibrium pair fluxes and limits combined outgoing volume from one source to half of its snapshot quantity. The purpose is convergence, not hidden loss.

Integer truncation creates a natural one-quantum deadband. When no meaningful integer transfer remains, the region sleeps instead of oscillating forever around a fractional equilibrium.

## Active frontier / dormancy

The solver does not scan the world or all wet cells.

A cell becomes active when:

- `WaterSystem.addAtMost` changes its quantity;
- `WaterSystem.removeAtMost` changes its quantity;
- previous flow changed it;
- an external Geometry coordinator calls `WaterFlowSystem.activateAt` after a local physical mutation.

An active cell examines only the six neighboring physical faces. If an update produces no transfer, no cells are reactivated and the stable region becomes dormant.

This keeps large stable lakes/puddles at zero recurring solver work until an actual local disturbance occurs.

## Geometry changes and displacement

Geometry never owns Water mass and therefore never deletes displaced quantity.

If geometry reduces capacity while water already exists, the solver can move excess through any remaining physical openings after the changed cell is activated. If a mutation closes every exit while water is still present, post-hoc flow cannot solve the impossible state; the higher-level landscape mutation must coordinate displacement before/with the Geometry write.

This preserves ownership rather than inventing silent deletion.

## Sources and sinks

A future source adds finite configured volume over time through the existing arithmetic Water API. A drain/sink removes finite configured volume over time.

Turning a source off stops future input; it does not delete water that already exists.

The flow solver must not know whether incoming water came from rain, a spring, a pipe, a script or another mechanic.

## Rain and sky exposure

Rain is an external water input to cells exposed to precipitation. Roof/occluding geometry must prevent that input.

Sky exposure should be a derived/cached fact with local invalidation when occluding world structure changes. Rainfall itself must not rescan an entire vertical world column for every wet cell on every rain step.

The exact exposure owner remains deferred until precipitation/roofs provide the concrete consumer.

## Soil moisture and evaporation

Soil moisture remains separate from free Water. Rain should first interact with soil infiltration/capacity; excess becomes free surface water.

The first evaporation model should remove exposed surface water periodically rather than deleting a percentage of total volume. Temperature/humidity/wind/solar detail remains later evidence-driven work.

## Traversal and Pathfinding

Water physics changes much more often than meaningful traversability.

A raw change such as:

```text
depth 420_000 -> 420_001
```

must not globally stale every active PathSearch.

Future integration must preserve:

```text
water-state change
        !=
traversal-semantic change
```

Movement still revalidates concrete next edges against authoritative conditions. Pathfinder routes remain disposable advice.

Mover-relative depth/capability semantics belong to traversal integration, not to the Water solver.

## Still intentionally absent

Not introduced by the baseline flow slice:

- runtime/scheduler cadence;
- rain / precipitation exposure;
- soil absorption;
- evaporation;
- springs and drains as scheduled producers/consumers;
- object displacement;
- traversal/pathfinding invalidation;
- drinking/Thirst;
- water-body identity;
- detailed pressure, inertia, viscosity, turbulence, waves or erosion.

Each becomes active only when its first real consumer requires it.
