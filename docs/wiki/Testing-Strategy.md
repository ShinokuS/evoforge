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
deterministic time/process semantics
```

No single test style replaces the others.

## Unit tests

Unit tests validate one abstraction in isolation.

Examples include:

```text
ObjectId slot/generation representation
DefinitionId validation
MovementDefinitionCompiler
LandscapeTraversalDefinitionCompiler
TransitionMask bit mapping
GridTransitionLength values
TransitionPorts packing
TransitionComposition algebra
FullShape local topology
RampShape orientation symmetry
TerrainStorage behavior
BoundProcessScheduler relative scheduling
SimulationStepper phase order
```

These tests should be precise enough to make a broken local contract obvious without constructing an entire World.

## Integration tests

Integration tests validate ownership boundaries and composition between systems.

Examples:

```text
Definition source -> compiled catalog -> ObjectFactory
Terrain -> Geometry
Geometry -> Navigation
Navigation-valid edge -> TransitionCost
TransitionCost + MovementRate -> scheduled duration
Movement completion -> SpatialSystem.move
terrain mutation during Movement -> completion revalidation
SpatialSystem -> reverse object spatial indexes
```

An integration test is especially valuable when two systems are intended to remain ignorant of each other's concrete implementation.

## Scenario vertical-slice tests

The test-only Scenario fixture validates complete production paths without becoming a second simulation runtime.

Arrange phase uses controlled setup operations. After `start()`, tests submit production Commands, advance the production `SimulationStepper`, and observe read-only state.

Current Movement scenarios prove:

```text
MoveStepCommand accepted -> action starts but position does not change immediately
position changes exactly on scheduled completion tick
different MovementRate values produce different completion times
diagonal direction length changes duration
fractional carry persists across repeated steps
second movement while active is rejected
missing movement capability is rejected
invalid structural edge is rejected
surface traversal cost changes actual authoritative duration
Shape traversal factor changes actual authoritative duration
advanceTicks(N) == N calls to advance()
```

Scenario tests should not expose raw runtime write systems merely to make one assertion easy. If a test needs to mutate authoritative state after `start()` to exercise a system boundary, a focused integration fixture using the real domain write capability is preferable to weakening Scenario Harness encapsulation.

## Architectural invariant tests

Some behaviors are more important than any one example. These should be expressed as reusable invariants.

Current examples include:

```text
stale ObjectId never resolves to a reused object
terrain absence never exposes default Full geometry
solid terrain cells are not normal navigation positions
Shape topology roles obey the current standing-position contract
Shape traversal-factor ownership matches departure/arrival topology roles
Navigation never emits the center direction
Navigation never emits directions outside the 26-neighbor mask
composition is independent of Shape processing order
missing endpoint support removes the corresponding structural edge
at most one ordinary MovementAction is active per object
Movement does not commit Spatial before scheduled completion
```

The point is to catch a whole class of mistakes, not only reproduce one previously observed bug.

## TransitionCost tests

TransitionCost is a compositional formula and therefore needs tests for each independent contribution plus the combined result.

Current coverage includes:

```text
surface cost A=1000, B=1600 -> cardinal average 1300
neutral double diagonal -> grid factor 1414
source departure factor and destination arrival factor are applied independently
reverse directed transition may have a different cost
missing traversal definition fails loudly as configuration error
non-adjacent input is rejected
```

A custom test Shape is used to prove factor extensibility without teaching production code about a new concrete Shape type.

The production Shape role-contract sweep additionally verifies that current `FullShape` and all four `RampShape` orientations return neutral traversal factor exactly for roles their transition ports own and NONE elsewhere.

This matters because topology and cost must not drift into two incompatible coordinate/role laws.

## Movement timing tests

Movement timing deliberately avoids per-step ceiling. Tests therefore verify long-run remainder behavior, not only one convenient rate that divides the neutral cost exactly.

For repeated transitions, expected timing is based on persistent per-object carry:

```text
cost = 1000
rate = 300

steps -> 3, 3, 4 ticks over three cells
```

The test contract is not that every individual step has an ideal fractional duration; simulation time is discrete. The contract is that deterministic carry preserves long-run timing without speed-dependent rounding bias, while every step still takes at least one tick.

## Scheduled completion and revalidation tests

Movement is dormant between start and completion. A dedicated integration test therefore mutates Landscape after Movement starts but before the scheduled completion.

Expected result:

```text
start A -> B
remove terrain/support that made A -> B valid
advance until completion
MovementActionProcessor revalidates
Navigation no longer exposes edge
Spatial remains at A
action is removed
```

This test proves the intended current tradeoff: no immediate wake-up on terrain mutation, but no stale blind commit either.

## Ramp hardening scenarios

Ramp behavior remains tested through complete topology scenarios because role bugs are easy to miss in local Shape tests.

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

Reverse traversal is verified independently rather than assumed from a forward edge.

Current Ramp traversal factor itself is neutral; elevation/direction cost comes from `GridTransitionLength`. If Ramp later receives a real intrinsic geometry factor, that must get its own directed factor tests rather than silently changing topology tests.

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

A fixed seed makes every failure reproducible. Failure messages include seed, mutation step and source XYZ.

## Deterministic tests

Randomized tests must still be deterministic. Use explicit seeds and stable iteration/tie-breaking. A test that “usually passes” is not acceptable evidence for deterministic simulation behavior.

Timed mechanics add another determinism dimension:

```text
same initial state
+ same commands
+ same number/order of simulation ticks
= same authoritative result
```

The caller may advance ticks one at a time or through `advanceTicks(n)`; batching itself must not create different simulation semantics.

Presentation FPS is intentionally absent from these tests because it is not authoritative time.

## Negative-space tests

It is not enough to verify what exists. Structural/timed systems need tests for what must *not* happen.

Examples:

```text
no free Full-to-Full vertical step
no edge into unsupported void
no Ramp side entry
no walk through solid terrain
no false edge after endpoint removal
no coordinate-wrap neighbor
no immediate Spatial move merely because MoveStepCommand was accepted
no second ordinary movement while one is active
no silent fallback cost for broken traversal definition
no central concrete-Shape branch required by a custom traversal factor
```

These tests are especially important in compositional systems because independent contributions can accidentally combine into plausible but invalid behavior.

## Boundary tests

Public coordinates are `int`. Local arithmetic near integer extremes is tested to ensure no wrap produces false neighbors. These are implementation-safety tests, not world-size requirements.

TransitionCost also validates adjacency before doing support-coordinate work, and fixed-point multiplication uses checked arithmetic so unsupported extreme configuration fails rather than silently overflowing authoritative cost.

Future chunk/region boundaries will need equivalent edge tests once their semantics are fixed.

## Test-first hardening workflow

When an architectural defect is suspected:

```text
1. express the expected semantic behavior as the smallest failing test;
2. run it on current production code;
3. confirm the actual failure mechanism;
4. apply the smallest production change that satisfies the contract;
5. run nearby regression tests after every role/topology/timing change;
6. run the complete simulation suite before merge.
```

This avoids “fixing” architecture from intuition alone.

## What should not be tested as a permanent invariant too early

A useful current convention is not automatically a forever-contract.

For example, current production Shapes have one standing position at `anchor + (0,0,1)`. That is strong enough to test for current Shapes, derive the current Navigation read window and locate current TransitionCost support owners. It should not be generalized to every imaginable future Shape without a consumer proving that restriction desirable.

Likewise, current TransitionCost is actor-independent. Tests should not encode “all future actors always rank every surface identically” as a permanent law; that is a current model deliberately awaiting a real actor/surface-affinity consumer.

Tests should distinguish:

```text
FIXED semantic invariant
CURRENT production-model contract
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
