# Water Foundation — working design note

> Status: working implementation plan. This note records the current Water milestone boundary; normative cross-system rules remain in `architecture.md` and current subsystem semantics in `systems/`.

## Goal

Introduce finite, deterministic water as an independent landscape mechanic without turning Terrain, Geometry, Navigation or World cells into a water-specific model.

The intended progression is:

```text
neutral volumetric Shape facts
        ↓
finite Water state
        ↓
deterministic local flow
        ↓
rain / soil moisture / simple evaporation
        ↓
traversal integration
        ↓
visual hydrology acceptance
        ↓
Thirst + Drink
```

The first implementation step is deliberately smaller than a fluid solver: Geometry must be able to state how much of a cell a Shape physically occupies.

## Authoritative quantity

Water quantity will use deterministic integer/fixed-point volume, not `float`/`double` authoritative mass.

The neutral geometry scale is:

```text
0         = no cell volume
1_000_000 = one full discrete cell volume
```

This does not define the physical size of a world cell. If EvoForge later fixes a cell edge length in metres, litres and mass can be derived from cell volume and fluid density without changing the stored geometric fraction.

Total conservation for a closed solver step must eventually satisfy exactly:

```text
sum(after)
=
sum(before)
+ explicit inputs
- explicit sinks
```

No hidden loss may be used as numerical damping.

## XYZ convention

Shared XYZ is an address, not shared storage.

```text
TerrainState   XYZ -> terrain identity | absence
Geometry       terrain anchor -> Shape | default FullShape
WaterState     XYZ -> liquid quantity | absence
```

A full terrain Shape in `(x,y,z)` occupies that spatial cell. The ordinary supported standing position is currently `(x,y,z+1)`.

Water is not stored "inside Terrain". A future Water cell at XYZ occupies free spatial volume at that XYZ. If there is no terrain Shape at the same XYZ, the geometric cell is fully open. If a partial Shape is present, only the remaining volume may be available to fluid after other displacement consumers are introduced.

Current production approximation:

```text
FullShape solid volume = 1.0 cell
RampShape solid volume = 0.5 cell
```

The ramp value is intentionally approximate.

## Geometry boundary

`Shape.solidVolume()` is neutral physical geometry. It may be useful to water, gas, snow, granular material or future displacement mechanics.

It is intentionally **not** enough to describe flow topology.

A scalar occupied volume cannot answer:

- which neighboring free spaces connect;
- at what height an opening begins;
- whether two equal free volumes have compatible boundaries.

Therefore Water must not reuse navigation `transitionPorts()` as fluid openings and must not branch on `RampShape` / `FullShape` concrete types.

The first actual deterministic flow consumer will decide the smallest neutral free-space/boundary contract required. If that consumer proves a scalar volume insufficient, Geometry will grow by a physical-space capability rather than by water-specific methods.

## Solver rules to preserve

The first solver should be discrete and local, not Navier–Stokes/SPH/FLIP.

Required properties:

- deterministic integer transfer;
- exact conservation apart from explicit sources/sinks;
- gravity-driven downward preference;
- bounded local work;
- no mandatory full-map scan;
- active/frontier cells only;
- stable water becomes dormant;
- local mutations wake only the affected local neighborhood;
- no allocation-heavy per-cell temporary object graph in the hot loop.

Damping/deadband may control oscillation, but they may only change *when/what transfers*, never destroy quantity.

## Sources and sinks

A future source adds finite configured volume over time. A drain/sink removes finite configured volume over time.

Turning a source off stops future input; it does not delete water that already exists.

This makes reservoirs, flooding and draining consequences of conserved state rather than special objects.

## Rain and sky exposure

Rain is an external water input to cells exposed to precipitation. Roof/occluding geometry must prevent that input.

Sky exposure should be a derived/cached fact with local invalidation when occluding world structure changes. Rainfall itself must not rescan an entire vertical world column for every wet cell on every rain step.

The exact exposure owner is deferred until roofs/precipitation provide the concrete consumer.

## Traversal and Pathfinding

Water physics changes much more often than meaningful traversability.

A raw change such as:

```text
depth 420_000 -> 420_001
```

must not globally stale every active PathSearch.

Future integration should separate at least:

```text
water-state change
        !=
traversal-semantic change
```

Movement must still revalidate the concrete next edge against authoritative current conditions. Pathfinder routes remain disposable advice.

If water affects passability/cost, revisions must be localized or quantized to semantic threshold changes so work scales with affected traversal facts, not with every microscopic fluid transfer.

## What is intentionally not in the first step

Not introduced yet:

- `WaterSystem`;
- hydraulic/fluid ports;
- flow solver;
- lakes/rivers as authoritative objects;
- pressure waves, turbulence, foam or rendering waves;
- pathfinding invalidation;
- drinking/Thirst;
- soil absorption;
- object displacement.

The first step proves only the neutral geometric fact required by all of those: a Shape can expose deterministic cell-local solid volume without knowing any consumer.
