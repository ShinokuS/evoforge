# Adding a Shape

This guide describes how to add a new terrain geometry implementation without coupling Navigation, Movement, or TransitionCost calculation to the new concrete type.

## Before adding a Shape

Confirm that the requirement is genuinely new geometry rather than an actor ability, occupancy rule, temporary traversal state, material property, or AI preference.

A Shape may describe two kinds of **intrinsic local geometry**:

```text
structural topology
    -> departures / arrivals / blocks

actor-independent directed traversal geometry
    -> departureTraversalFactor / arrivalTraversalFactor
```

A Shape should **not** encode:

```text
which actor is moving
whether this actor can climb/swim/fly
whether another object occupies the destination
terrain material traversal.cost
actor-specific surface affinity
AI preference
falling policy
world-neighbor queries
```

An intrinsic Shape traversal factor is allowed only when the geometry itself has a real directed cost effect that is not already represented by grid direction length. It is not a place to hide actor policy.

## Step 1: define the geometry semantically

Write the local topology in coordinates before writing Java.

For example, the current positive-Y Ramp can be described by standing positions:

```text
lower A
    ↗
ramp B
    →
upper C
```

with directed edge deltas:

```text
A -> B = (0,+1,+1)
B -> A = (0,-1,-1)
B -> C = (0,+1,0)
C -> B = (0,-1,0)
```

The movement deltas must remain within the 26-neighbor transition space.

Also identify whether the geometry needs any intrinsic traversal multiplier beyond that grid displacement. Do not add a multiplier merely because a Shape is “special”. Current Ramp uses neutral factors because its elevation/distance is already represented by the actual edge direction and `GridTransitionLength`.

## Step 2: identify ownership of each directed edge

For every directed edge, ask which Shape supports the source standing position and which Shape supports the destination standing position.

Under the current production Shape role convention:

```text
source-supporting Shape      -> departure
source-supporting Shape      -> departure traversal factor

destination-supporting Shape -> arrival
destination-supporting Shape -> arrival traversal factor

solid obstruction            -> block
```

Topology and traversal cost use the same ownership law.

Do not let one Shape provide both roles for an external edge merely to make a test pass. That creates phantom topology when the real endpoint Shape is missing and would also assign traversal price to geometry the Shape does not own.

## Step 3: implement only local declarations

Current Shape API:

```java
long transitionPorts(
        int relativeX,
        int relativeY,
        int relativeZ);

int transitionBlocks(
        int relativeX,
        int relativeY,
        int relativeZ);

int departureTraversalFactor(
        int relativeX,
        int relativeY,
        int relativeZ,
        int directionX,
        int directionY,
        int directionZ);

int arrivalTraversalFactor(
        int relativeX,
        int relativeY,
        int relativeZ,
        int directionX,
        int directionY,
        int directionZ);
```

Relative source coordinates always use the Shape terrain anchor as origin. Direction is the immediate edge from that source to its destination.

The Shape must not perform world lookup or inspect neighboring concrete Shape types.

Good:

```text
if source is at my supported position, offer these departures
if source is at this relative mouth position, confirm this arrival
if I own this departure role, contribute my local directed factor
if I own this arrival role, contribute my local directed factor
```

Bad:

```text
if neighbor is FullShape, allow edge
if world has terrain there, allow edge
if Navigation says endpoint exists, allow edge
if mover is a horse, use factor 800
if destination terrain is mud, use factor 1500
```

## Step 4: use default traversal factors unless geometry proves otherwise

The default Shape implementation derives factor ownership directly from `transitionPorts`:

```text
owned role -> ShapeTraversalFactor.NEUTRAL = 1000
not owned  -> ShapeTraversalFactor.NONE    = 0
```

Therefore most Shapes should not override traversal factors at all.

Override a factor only when intrinsic geometry has a stable actor-independent effect that is not already expressed by `GridTransitionLength`.

A correct override should preserve the role check and change only the positive factor for roles the Shape owns. Conceptually:

```text
base = Shape.super.departureTraversalFactor(...)
if base == NONE:
    return NONE
return desiredPositiveFactor
```

and analogously for arrival.

Never return a positive traversal factor for a topology role the Shape does not expose.

## Step 5: model solid volume when applicable

If the terrain body is solid, it must block ordinary entry through its occupied volume.

`FullShape` and `RampShape` share `SolidCellBlocking`. Reuse it only when the new geometry has the same solid-cell obstruction semantics. Do not force every future Shape into solid-cell blocking if its physical volume differs.

## Step 6: unit-test local topology

Test the Shape directly before constructing world integration.

Useful assertions include:

```text
exact departure mask from supported position
exact arrival mask from each supported mouth/source position
no unexpected side ports
orientation symmetry
expected block mask around occupied volume
no ports outside intended local domain
```

If the Shape has rotated instances, prove that every orientation is the same topology under rotation/sign change.

## Step 7: test traversal-factor ownership

Every production Shape must keep cost-role ownership aligned with topology-role ownership.

At minimum verify:

```text
no departure port -> departure factor NONE
departure port + neutral geometry -> departure factor NEUTRAL
no arrival port -> arrival factor NONE
arrival port + neutral geometry -> arrival factor NEUTRAL
```

If the Shape uses a non-neutral factor, test the exact directed roles and reverse direction independently.

Also add a TransitionCost-level test proving the factor changes cost without adding any concrete-type branch to `TransitionCostCalculator`.

## Step 8: test endpoint absence

Integration tests must remove each endpoint independently.

For a connector:

```text
A <-> NewShape <-> C
```

verify:

```text
missing A -> neither direction of the A connector survives
missing C -> neither direction of the C connector survives
```

This catches the most dangerous composition bug: a Shape voting both departure and arrival for an edge that should require another Shape.

Because TransitionCost is calculated only for Navigation-valid edges, missing endpoint topology should remove the edge before cost calculation is attempted.

## Step 9: test chains

If the geometry can connect to another instance of itself, add a direct chain test. Ramp chaining caught role mistakes that simple Full/Ramp/Full scenarios did not expose.

The chain should use canonical world placement rather than artificial intermediate cells.

If the Shape has a non-neutral factor, verify repeated-chain cost/timing behavior where relevant instead of testing only an isolated edge.

## Step 10: run generic contract tests

Current production Shapes should obey the single-standing-position role law documented in [Shape Contract](Shape-Contract.md).

A new production Shape should participate in generic tests for:

```text
terrain body non-navigation when solid
role-law consistency
traversal-factor role consistency
reference Navigation composition
no center-bit leakage
no unexpected endpoint edges
```

If the new Shape cannot obey the current role law for a legitimate semantic reason, do not add a local exception. Stop and reconsider together:

```text
Shape contract
Navigation resolver read window
TransitionCost support-owner lookup
```

The three are derived from the same supported-position model.

## Step 11: central systems should remain unchanged by type

Adding a Shape must never produce code like:

```java
if (shape instanceof NewShape) {
    ...
}
```

inside:

```text
NavigationSystem
MovementSystem
TransitionCostCalculator
```

Nor should there be a central registry mapping every concrete Shape class to cost logic.

A generic resolver/calculator may change if the existing Shape contract is proven insufficient, but that change must be justified by the semantic contract itself rather than the concrete class name.

## Step 12: understand what does not belong in Shape cost

Current `TransitionCost` has separate owners:

```text
LandscapeDefinitionId traversal.cost
    -> material/surface contribution

Shape traversal factor
    -> intrinsic local geometry contribution

GridTransitionLength
    -> discrete direction length

MovementRate
    -> mover speed / cost-to-time conversion
```

Do not duplicate one owner's meaning in another.

Examples:

```text
mud is slow material
    -> landscape traversal.cost

edge changes two grid axes
    -> GridTransitionLength

stairs intrinsically require more geometric effort
    -> possibly Shape factor, if actor-independent and proven

horse is faster than human
    -> MovementRate

wheels dislike stairs but legs do not
    -> future actor/geometry interaction, NOT universal Shape factor
```

## Step 13: update documentation

When the Shape becomes production behavior, update:

```text
docs/TECHNICAL_REFERENCE.md
Shape Contract / geometry pages
Movement System when traversal semantics change
ARCHITECTURE.md only if stable semantic contract changes
EN/RU counterparts and i18n freshness hashes
```

Do not duplicate every implementation detail in `ARCHITECTURE.md`; keep that file normative and concise.

## Completion checklist

A production Shape is ready when:

```text
intended topology tested
reverse topology tested
missing endpoints tested
solid volume tested when applicable
orientations/chains tested
role invariants tested
traversal factors aligned with topology roles
non-neutral factor tested through TransitionCost when applicable
no central concrete-type branch added
full simulation suite green
EN/RU documentation synchronized
```
