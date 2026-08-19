# Occupancy

## In plain language

Occupancy answers: **can an exclusive object use this cell right now?**

That is different from asking where an object is. Spatial owns position. Occupancy observes current positions and additionally owns temporary destination reservations for actions that have already started.

A useful example:

```text
Cow A is physically in cell 1
Cow A starts moving to cell 2

cell 1 -> OCCUPIED  (derived from Spatial)
cell 2 -> RESERVED  (owned by Occupancy for the accepted move)
```

Cow B cannot claim cell 2, but Occupancy has not pretended Cow A is already physically there.

## Current status

The read projection exposes three states:

```text
FREE
OCCUPIED
RESERVED
```

`OCCUPIED` is derived from current Spatial objects + immutable definition capability. `RESERVED` is authoritative temporary state owned by `OccupancySystem`.

Occupancy does not store a second object-position grid.

## Definition capability

An object definition may opt into exclusive-cell occupancy:

```json
{
  "occupancy": {
    "exclusive": true
  }
}
```

Conceptually:

```text
ObjectDefinitionId -> requires exclusive cell?
```

Absence means the object is transparent to exclusive occupancy under the current model.

This is deliberately independent from movement, visibility, traversal cost or other mechanics.

A bush can therefore be Spatially present but non-exclusive while a Cow is exclusive. The system does not branch on “Cow” or “Bush”; it reads definition capability.

## Meaning of the three states

### `OCCUPIED`

An exclusive object is physically present according to authoritative Spatial state.

### `RESERVED`

No exclusive object is physically present, but an accepted execution action owns an exclusive destination claim.

### `FREE`

Neither physical exclusive occupancy nor reservation exists.

Structural impossibility is **not** another Occupancy state. Navigation separately determines whether an edge exists.

## Asymmetric admission

Non-exclusive objects do not require exclusive claims and may share cells with exclusive/reserved space.

An exclusive object may share a cell with any number of non-exclusive objects, but not with another exclusive occupant/reservation.

Examples:

```text
exclusive Cow + transparent Bush -> allowed
exclusive Cow + transparent item -> allowed
exclusive Cow + exclusive Cow    -> rejected
```

This rule applies during ordinary placement as well as Movement admission.

## Reservation identity

A successful exclusive claim mints an opaque monotonic `OccupancyReservationId`.

The owning action stores that exact handle. Release requires matching:

```text
reservationId
objectId
destination coordinate
```

A stale action therefore cannot accidentally release a newer action's reservation for the same object/cell.

Occupancy owns this reservation identity; it does not use Movement process IDs as its own identity model.

## Immediate destination only

Occupancy reservations are **execution reservations**, not path reservations.

For a MoveTo route:

```text
current Spatial cell       OCCUPIED
currently accepted next cell RESERVED
all later path cells       unclaimed advice
```

A pathfinder route never locks an entire future corridor.

Future space-time/path-wide reservation is a separate multi-agent planning problem.

## Movement start interaction

For an exclusive mover, after structural/cost/duration validation:

```text
try reserve immediate destination
    ├─ physically exclusive occupant -> destination_occupied
    ├─ another reservation           -> destination_reserved
    └─ free                          -> return reservation handle
```

Only after successful admission does Movement create/schedule its action.

Rejected claims do not:

- create Movement action state;
- mutate Spatial;
- consume movement timing carry;
- leave a reservation behind.

If an exceptional later setup step fails, Movement releases the exact acquired reservation before propagating failure.

## Completion interaction

While the action sleeps:

```text
source        = physically OCCUPIED
reserved dest = RESERVED
Spatial       = source
```

At completion Movement revalidates source/topology/mover constraints and exact reservation ownership.

Successful commit:

```text
Spatial.move(destination)
release reservation
remove MovementAction
```

Interrupted/invalid completion:

```text
Spatial unchanged
release reservation
remove MovementAction
```

This lifecycle is why `RESERVED` and later `OCCUPIED` are different observable states.

## Placement interaction

Initial/runtime object placement must obey the same exclusive rule; otherwise setup could create a state Movement itself would never allow.

`ObjectPlacementSystem` is the coordinated semantic mutation:

```text
placement request
    ↓
Occupancy admission
    ↓
SpatialSystem.place
```

Spatial remains the low-level position owner and does not learn Occupancy policy.

Exclusive occupancy configuration for a definition must be settled before instances of that definition are Spatially placed during assembly.

## Deterministic contention

Current authoritative mutation is single-threaded and synchronous.

For two competing reservation attempts:

```text
first successfully executed tryReserve -> owns claim
later attempt                          -> sees RESERVED
```

There is currently no random arbitration, ObjectId priority, fairness queue or yielding policy.

## Conservative corridor consequence

Because a moving exclusive actor remains physically at its source until completion while reserving the destination, a follower in a narrow corridor cannot claim the leader's source early.

This produces conservative “caterpillar” throughput.

That behavior is intentional for the current atomic-edge model. Early source release, coordinated following, swaps and yielding would change multi-agent execution semantics and are deferred until real agents need them.

## Invariants

- Spatial is the only owner of actual object position.
- `OCCUPIED` is derived; `RESERVED` is Occupancy-owned state.
- Transparent objects do not block exclusive occupancy.
- Exclusive objects never share with another exclusive occupant/reservation.
- Reservation release requires exact owner identity.
- Only the immediately executing destination is reserved.
- Normal start/completion/cancellation paths do not orphan reservations.
- Occupancy does not decide structural topology or pathfinding.

## Current limitations

Deferred:

- multi-cell footprints/capacity;
- path-wide or space-time reservations;
- swaps/pushing/displacement;
- priority/fairness/yield/deadlock policy;
- group movement/formation reservation;
- early source release while in transit;
- crowd-aware planning cost.

## Code and tests

Primary code:

```text
simulation/.../world/mechanics/occupancy/
simulation/.../world/object/placement/
```

Coverage includes definition freeze, transparent sharing, exclusive conflicts, reservation identity/release, Movement contention, source/destination state during timed movement, interrupted completion cleanup and setup-order constraints.

## Sources

**Internal EvoForge design.** The derived-occupied + explicit-reservation model is project-specific.

See [Spatial](../foundations/spatial.md), [Movement](movement.md), [Navigation](navigation.md), and [Architecture](../../architecture.md).
