# Testing Strategy

EvoForge treats tests as executable architecture. A subsystem is not complete merely because example scenarios work; tests should also encode the laws that prevent future mechanics from silently violating established boundaries.

## Testing goals

The test suite serves several distinct purposes:

```text
local correctness
architectural invariants
cross-system integration
regression protection
reference/property validation
boundary safety
```

No single test style replaces the others.

## Unit tests

Unit tests validate one abstraction in isolation.

Examples include:

```text
ObjectId slot/generation representation
DefinitionId validation
TransitionMask bit mapping
TransitionPorts packing
TransitionComposition algebra
FullShape local topology
RampShape orientation symmetry
TerrainStorage behavior
```

These tests should be precise enough to make a broken local contract obvious without constructing an entire World.

## Integration tests

Integration tests validate ownership boundaries and composition between systems.

Examples:

```text
Definition source -> compiled catalog -> ObjectFactory
Terrain -> Geometry
Geometry -> Navigation
terrain mutation -> next Navigation query
Shape pairs -> resolved edges
SpatialSystem -> reverse CellSpatialIndex
```

An integration test is especially valuable when two systems are intended to remain ignorant of each other's concrete implementation.

## Architectural invariant tests

Some behaviors are more important than any one example. These should be expressed as reusable invariants.

Current and planned examples include:

```text
stale ObjectId never resolves to a reused object
terrain absence never exposes default Full geometry
solid terrain cells are not normal navigation positions
Shape role contributions obey the current standing-position contract
Navigation never emits the center direction
Navigation never emits directions outside the 26-neighbor mask
composition is independent of Shape processing order
missing endpoint support removes the corresponding structural edge
```

The point is to catch a whole class of mistakes, not only reproduce one previously observed bug.

## Ramp hardening scenarios

Ramp behavior is intentionally tested through complete topology scenarios because role bugs are easy to miss in local Shape tests.

Important cases include:

```text
lower Full <-> Ramp <-> upper Full
missing upper Full -> no upper edge
missing lower Full -> no lower ascent or descent edge
consecutive Ramp chain
side entry blocked
Ramp terrain body non-navigable
Full occupying a transition destination blocks ascent
```

The tests also verify reverse traversal independently rather than assuming bidirectionality from a forward edge.

## Reference resolver tests

`NavigationReferencePropertyTest` compares the production resolver with a deliberately simpler implementation over deterministic randomized geometry mutations.

The reference implementation should not copy the optimized loop structure line-for-line. Its value comes from independence: if production and reference implementations make the same structural mistake for the same reason, the comparison gives false confidence.

The reference test samples:

```text
FullShape
all four RampShape orientations
synthetic table-driven Shapes
random departure masks
random arrival masks
random block masks
near and distant mutations
```

A fixed seed makes every failure reproducible. Failure messages include the seed, mutation step, and source XYZ.

## Deterministic tests

Randomized tests must still be deterministic. Use explicit seeds and stable iteration/tie-breaking. A test that “usually passes” is not acceptable evidence for deterministic simulation behavior.

The same principle will later apply to authoritative simulation randomness.

## Negative-space tests

It is not enough to verify what exists. Structural systems need tests for what must *not* exist.

Examples:

```text
no free Full-to-Full vertical step
no edge into an unsupported void
no Ramp side entry
no walk through solid terrain
no false edge after endpoint removal
no coordinate wrap neighbor
```

These tests are especially important in compositional systems because OR-accumulation can accidentally create a valid-looking pair of bits from unrelated contributors.

## Boundary tests

Public coordinates are `int`. Local arithmetic near integer extremes is tested to ensure no wrap produces false neighbors. These are implementation-safety tests, not world-size requirements.

Future chunk/region boundaries will need equivalent edge tests once their semantics are fixed.

## Test-first hardening workflow

When an architectural defect is suspected:

```text
1. express the expected semantic behavior as the smallest failing test;
2. run it on current production code;
3. confirm the actual failure mechanism;
4. apply the smallest production change that satisfies the contract;
5. run nearby regression tests after every role/topology change;
6. run the complete simulation suite before merge.
```

This avoids “fixing” architecture from intuition alone.

## What should not be tested as a permanent invariant too early

A useful current convention is not automatically a forever-contract.

For example, current production Shapes have one standing position at `anchor + (0,0,1)`. That is strong enough to test for current Shapes and to derive the current Navigation read window. It should not be generalized to every imaginable future Shape without a consumer proving that restriction desirable.

Tests should distinguish:

```text
FIXED semantic invariant
CURRENT production-shape contract
implementation detail
```

## Running tests

Full simulation suite:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

For focused development, use Gradle `--tests` filters, then return to the complete module suite before final review.
