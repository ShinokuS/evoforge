# Occupancy

## Purpose

Describe present-tense dynamic availability of discrete object space without duplicating authoritative object position and without absorbing structural Navigation rules.

Occupancy answers a different question from Spatial:

```text
Spatial    where is this object now?
Occupancy  may an exclusive object claim this cell now?
```

## Ownership

`SpatialSystem` remains the sole authoritative owner of `ObjectId → XYZ`.

`OccupancySystem` owns only **execution-time destination reservations** and the identity of those reservations. It does not store a second occupied-position grid.

`OCCUPIED` is derived from:

```text
CellObjectLookup
    + ObjectLookup
    + immutable OccupancyDefinitions
```

`RESERVED` is authoritative state owned by Occupancy.

## Definition capability

An object definition may opt into the `occupancy` aspect:

```json
{
  "occupancy": {
    "exclusive": true
  }
}
```

The compiled fact is conceptually:

```text
ObjectDefinitionId → requires exclusive cell?
```

Absence of the aspect means the object is transparent to exclusive occupancy.

This is deliberately independent from other mechanics. A future bush may be spatially present, occupancy-transparent, slow traversal and provide concealment at the same time. Those are separate mechanic contributions rather than one universal physical flag.

During `SimulationAssembly` setup, exclusive occupancy for a definition must be configured before instances of that definition are spatially placed. Object creation alone does not lock this choice; placement does, because placement already consumes the occupancy semantics.

## Read projection

`OccupancyLookup` exposes exactly three current states:

```text
FREE
OCCUPIED
RESERVED
```

Their meanings are:

```text
OCCUPIED
    an exclusive object is physically present in Spatial now

RESERVED
    no exclusive object is present, but an accepted execution action
    owns the destination claim

FREE
    neither condition applies
```

Structural impossibility is **not** an Occupancy state. Navigation owns directed structural edges, so a destination may be structurally reachable from one direction and unreachable from another.

## Candidate admission

Occupancy is asymmetric with respect to transparent objects.

A non-exclusive object does not require an exclusive claim and may share a cell with exclusive or reserved space. An exclusive object may share a cell with any number of non-exclusive objects but may not enter a cell containing another exclusive occupant or another execution reservation.

Therefore examples such as these are valid:

```text
cow + bush      allowed if bush is non-exclusive
cow + sword     allowed if sword is non-exclusive
cow + cow       rejected
```

The system never branches on concrete object classes; definition capabilities select the behavior.

## Execution reservations

A reservation protects only the **immediate destination of an already starting concrete action**. It is not a route reservation.

For timed Movement:

```text
Spatial source        = OCCUPIED
immediate destination = RESERVED
later path cells      = not claimed
```

`OccupancySystem` mints a monotonic opaque `OccupancyReservationId` when an exclusive destination claim is successfully acquired. The owner action stores that exact handle for its lifetime. Occupancy therefore owns reservation identity without depending on Movement-specific process ids.

Release requires the exact reservation identity, object id and destination. A stale action therefore cannot release another action's claim.

## Movement interaction

For an exclusive mover, start of one adjacent step is conceptually:

```text
validate Movement capability/source/adjacency/Navigation
    ↓
calculate shared TransitionCost and duration
    ↓
try to claim immediate destination
    ├─ OCCUPIED → structured movement:destination_occupied rejection
    ├─ RESERVED → structured movement:destination_reserved rejection
    └─ FREE     → Occupancy returns a reservation handle
                  create MovementAction
                  store handle with the active action
                  schedule completion
```

A rejected occupancy claim therefore never creates Movement action state, never leaves a reservation and never mutates timing carry.

If later action creation or scheduling fails exceptionally after a claim was acquired, Movement rolls the exact claim back before propagating the failure.

During the timed action Spatial remains at the source, preserving the existing Movement contract.

At completion Movement revalidates source, Navigation and exact reservation ownership. Successful completion commits `Spatial.move`, then releases the reservation and removes the action. Interrupted completion leaves Spatial unchanged, releases the reservation and removes the action.

World changes do not currently wake a sleeping movement action immediately. Existing completion-time revalidation remains the lifecycle boundary.

## Object placement

Exclusive occupancy must also apply to initial/runtime placement; otherwise setup could create a state that Movement itself would never permit.

`ObjectPlacementSystem` is the coordinated semantic mutation boundary:

```text
object placement request
    ↓
Occupancy admission
    ↓ accepted
SpatialSystem.place
```

`SpatialSystem` itself remains a low-level position owner and does not learn Occupancy semantics.

Non-exclusive objects may be placed into occupied/reserved cells. Exclusive objects receive structured `DESTINATION_OCCUPIED` / `DESTINATION_RESERVED` placement rejection.

## Determinism

Current authoritative mutation is single-threaded and command delivery is synchronous. Competing claims therefore resolve by ordinary deterministic execution order:

```text
first successful tryReserve → owns destination
later tryReserve            → RESERVED
```

There is no additional ObjectId priority, random arbitration or fairness policy in this milestone.

## Known consequence of current Movement semantics

Because an exclusive mover stays physically at its source while reserving its destination, narrow corridors have conservative throughput: a follower cannot claim the leader's source until the leader actually completes its step.

This “caterpillar” behavior is currently intentional. Early source release, coordinated following, yielding and group movement would change multi-agent execution semantics and are deferred until real agents demonstrate the need.

## Does not own

Occupancy does not own:

- structural passability or directed transitions;
- TransitionCost or terrain/object traversal modifiers;
- object XYZ;
- pathfinding or route lifecycle;
- AI decisions to wait, replan, yield, push or swap;
- concealment/visibility;
- future space-time planning reservations.

Execution reservation and future planning reservation are distinct concerns. A path remains advice; only the next edge becomes authoritative when Movement successfully starts it.

## Diagnostics and tests

The visualizer exposes an F5 Occupancy overlay and the cell inspector reports `FREE / OCCUPIED / RESERVED`.

Headless coverage includes:

- occupancy definition compilation/freeze;
- transparent objects sharing cells with exclusive objects;
- rejection of two exclusive occupants in one cell;
- Occupancy-owned reservation identity and exact-owner release;
- same-destination Movement contention;
- source remaining physically `OCCUPIED` while destination is `RESERVED`;
- rejected claims leaving Movement/timing behavior unchanged;
- distinction between `RESERVED` and later physical `OCCUPIED`;
- transparent movers sharing a cell with an exclusive object;
- release after completion-time structural interruption;
- setup ordering that prevents occupancy semantics changing after placement.

## Deferred

- path-wide / space-time reservations;
- multi-cell footprints and capacity rules;
- swap/displacement and pushing;
- yielding, priorities, fairness and deadlock resolution;
- coordinated following/group movement;
- early source release while in transit;
- crowd-aware planning costs.

These become active only when a real multi-agent consumer demonstrates which semantics are needed.
