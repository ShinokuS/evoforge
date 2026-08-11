# Glossary

This page defines project-specific terms as they are used in EvoForge documentation.

## Authoritative state

State whose value determines the simulation result and has exactly one mutable owner. Derived caches, indexes, rendered views, and diagnostics are not authoritative unless explicitly stated otherwise.

## Authoritative owner

The subsystem responsible for mutating and defining one authoritative fact. Examples: `ObjectRepository` owns object existence; `SpatialSystem` owns object XYZ; `TerrainSystem` owns terrain content.

## Derived state

State that can be reconstructed from authoritative owners. Spatial reverse indexes and future Navigation caches are examples. Derived state must never silently become a competing source of truth.

## `ObjectId`

Stable runtime identity for an individual object. The current implementation combines a slot and generation so stale ids cannot resolve to later slot reuse.

## Definition

Immutable runtime description compiled from source data. Source identity uses stable keys such as `namespace:name`; runtime typed ids are implementation references and not persistence identity.

## Aspect

A definition composition unit compiled by a mechanic-specific `DefinitionAspectCompiler`. Aspects allow content to opt into mechanics without one universal definition class containing every possible property.

## Landscape

Environmental world content stored by coordinate rather than as individual `WorldObject` identity. Terrain is the currently implemented landscape owner.

## Terrain

Base landscape content at XYZ:

```text
XYZ -> LandscapeDefinitionId | absence
```

Terrain owns material/content identity, not navigation topology.

## Geometry

A mechanic layered over present terrain that maps terrain presence plus sparse overrides to a `Shape`. Geometry does not own landscape material identity.

## Shape

A context-free local topology declaration anchored at one terrain coordinate. A Shape contributes transition ports and blocks based only on the current source position relative to its own anchor.

## Terrain anchor

The XYZ coordinate of terrain whose geometry a Shape describes.

## Standing position

A world position supported by terrain geometry where an object can normally stand. Current production Shapes use `anchor + (0,0,1)` as their single standing position.

## Relative source

The Navigation query source expressed relative to a Shape anchor:

```text
relative source = source XYZ - Shape anchor XYZ
```

## Structural edge

A directed adjacency from one XYZ position to one of its 26 immediate three-dimensional neighbors. Structural edges describe geometry only, not actor capabilities or occupancy.

## Transition direction

The `(dx,dy,dz)` delta for one structural edge. Every component is in `[-1,1]`, and `(0,0,0)` is invalid.

## Departure

A Shape contribution saying that a transition direction may leave the current source. In the current Shape model, departures originate from the Shape's own standing position.

## Arrival

A Shape contribution confirming that a transition direction ends on a position supported by that Shape. A missing destination-supporting Shape therefore removes the edge naturally.

## Block

A Shape contribution saying that solid geometry obstructs a transition direction. Blocks override matching departure/arrival permission during composition.

## Transition ports

The packed `long` containing independent departure and arrival masks for one Shape contribution.

## Transition mask

The primitive `int` bit mask representing structural directions. `TransitionMask.ALL` contains only the 26 valid neighbor directions.

## Transition composition

Generic edge resolution:

```text
resolved = departures & arrivals & ~blocks
```

Contributions are OR-accumulated before resolution.

## Navigation

The structural adjacency query layer. Navigation reads Geometry and returns immediate neighbor edges. It does not perform pathfinding or actor-specific traversal checks.

## Movement

Future subsystem that will decide whether and how a concrete actor performs a structural edge. Movement is not implemented yet.

## Occupancy

Future transient world-object constraint describing whether a structural destination is currently usable because of other objects. Occupancy is intentionally separate from terrain topology.

## Falling

Future involuntary movement mechanic. Empty space is not currently a normal Navigation edge and must not be interpreted as falling implicitly.

## Pathfinder

Future Navigation consumer that will search multiple structural edges to reach a target. Algorithm and cost representation remain deferred.

## Controller

Future external decision source such as player input, AI, script, or scenario. Controllers submit Commands rather than mutating authoritative systems directly.

## Command

Intent submitted through the future Control Backbone. A Command is not the same thing as a long-running Action/process.

## Scheduler

Generic simulation-time infrastructure that orders registered handlers. It controls timing, not domain semantics.

## FIXED

Architecture status meaning a semantic contract is stable. Internal implementation may change without changing consumers.

## WORKING

Architecture status meaning a current design direction is useful but may be revised when a real vertical slice provides better evidence.

## DEFERRED

Architecture status meaning a decision is intentionally postponed. Existing boundaries should leave room for the later decision without implementing speculative infrastructure now.
