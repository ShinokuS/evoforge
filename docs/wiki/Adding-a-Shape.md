# Adding a Shape

This guide describes how to add a new terrain geometry implementation without coupling Navigation to the new type.

## Before adding a Shape

Confirm that the requirement is genuinely new geometry rather than a movement ability or temporary traversal rule.

A Shape should describe structural terrain topology. It should not encode:

```text
which actor is moving
whether the actor can climb
whether another object occupies a destination
path cost
AI preference
falling
terrain material identity
world-neighbor queries
```

Those concerns belong to other mechanics or future traversal layers.

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

## Step 2: identify ownership of each directed edge

For every directed edge, ask which Shape supports the source standing position and which Shape supports the destination standing position.

Under the current production Shape role convention:

```text
source-supporting Shape      -> departure
destination-supporting Shape -> arrival
solid obstruction            -> block
```

Do not let one Shape provide both roles for an external edge merely to make a test pass. That creates phantom topology when the real endpoint Shape is missing.

## Step 3: implement only local declarations

A Shape receives only relative source coordinates:

```java
long transitionPorts(int relativeX, int relativeY, int relativeZ)
int transitionBlocks(int relativeX, int relativeY, int relativeZ)
```

It must not perform world lookup or inspect neighboring Shape types.

Good:

```text
if source is at my standing position, offer these departures
if source is at this relative mouth position, accept this arrival
```

Bad:

```text
if neighbor is FullShape, allow edge
if world has terrain there, allow edge
if Navigation says the endpoint exists, allow edge
```

## Step 4: model solid volume when applicable

If the terrain body is solid, it must block ordinary entry through its occupied volume.

`FullShape` and `RampShape` share `SolidCellBlocking`. Reuse it only when the new geometry has the same solid-cell obstruction semantics. Do not force every future Shape into solid-cell blocking if its physical volume differs.

## Step 5: unit-test local topology

Test the Shape directly before constructing world integration.

Useful assertions include:

```text
exact departure mask from standing position
exact arrival mask from each supported mouth/source position
no unexpected side ports
orientation symmetry
expected block mask around occupied volume
no ports outside the intended local domain
```

If the Shape has rotated instances, prove that every orientation is the same topology under rotation/sign change.

## Step 6: test endpoint absence

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

## Step 7: test chains

If the geometry can connect to another instance of itself, add a direct chain test. Ramp chaining caught role mistakes that simple Full/Ramp/Full scenarios did not expose.

The chain should use the canonical world placement rather than artificial intermediate cells.

## Step 8: run generic contract tests

Current production Shapes should obey the single-standing-position role law documented in [Shape Contract](Shape-Contract.md).

A new Shape should also participate in generic tests for:

```text
terrain body non-navigation when solid
role-law consistency
reference Navigation composition
no center-bit leakage
no unexpected endpoint edges
```

If the new Shape cannot obey the current role law for a legitimate semantic reason, do not add a local exception. Stop and reconsider the Shape contract and resolver read window together.

## Step 9: Navigation should remain unchanged by type

Adding a Shape must never produce code like:

```java
if (shape instanceof NewShape) {
    ...
}
```

inside `NavigationSystem`.

A generic resolver implementation may change if the existing Shape contract is proven insufficient, but that change must be justified by the contract itself rather than the concrete new class.

## Step 10: update documentation

When the Shape becomes production behavior, update:

```text
docs/TECHNICAL_REFERENCE.md
relevant Wiki pages
ARCHITECTURE.md only if a stable semantic contract changes
```

Do not duplicate every implementation detail in `ARCHITECTURE.md`; keep that file normative and concise.

## Completion checklist

A production Shape is ready when its intended topology, reverse topology, missing endpoints, solid volume, orientations, chains, and generic role invariants are all tested and the full simulation suite is green.
