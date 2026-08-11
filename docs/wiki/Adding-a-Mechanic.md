# Adding a Mechanic

This guide describes the preferred process for adding genuinely new runtime behavior to EvoForge.

## First question: content or mechanic?

Before adding Java code, ask whether existing mechanics already express the desired behavior.

If yes, add definition data only.

If no, the feature is a new mechanic and usually needs its own semantic owner.

Examples:

```text
new terrain material with another traversal.cost
    -> content using existing traversal mechanic

new animal definition with movement.rate
    -> content using existing Movement capability

new actor-specific swimming policy
    -> new mechanic/capability interaction
```

This distinction prevents every content addition from expanding the runtime type system.

## Define the owned state

Write down the authoritative mutable property owned by the mechanic.

Examples:

```text
Health      ObjectId -> health state
Inventory   ObjectId -> inventory state
Disease     ObjectId -> disease state
Water       world position -> water state
Movement    ObjectId -> active movement + timing carry
```

If the answer overlaps an existing owner, stop and resolve ownership before coding. Two systems must not both believe they are authoritative for the same mutable fact.

## Separate immutable definition data

If the mechanic needs configuration shared by all instances of a definition, add a composition-driven definition aspect and compiler.

For example:

```text
movement aspect
    -> ObjectDefinitionId -> MovementRate

traversal aspect
    -> LandscapeDefinitionId -> SurfaceTraversalCost
```

Do not put mutable per-instance state into definitions.

Useful test:

```text
same value for every instance of the definition
    -> definition data may be appropriate

changes independently for one runtime instance/process
    -> runtime state owned by a mechanic
```

## Prefer narrow read contracts

Other systems should depend on the smallest semantic read interface possible rather than the concrete mechanic implementation.

Examples:

```text
TransformLookup
TerrainLookup
GeometryLookup
NavigationLookup
TransitionCostLookup
SimulationTime
ObjectLookup
```

A read contract should describe what consumers need to know, not expose internal collections or storage classes.

## Define writes explicitly

If mutation must cross system boundaries, define a narrow write capability or coordinated operation. Do not expose broad mutable state merely to make integration convenient.

When one logical mutation must update multiple owners, coordinate above those owners rather than introducing circular dependencies.

Examples:

```text
LandscapeMutations
    -> coordinates Terrain + Geometry lifecycle

SpatialSystem.move
    -> authoritative object position mutation + indexes
```

## Decide whether the mechanic is immediate or timed

Some domain operations complete during the initiating call. Others start a process that completes later in simulation time.

Do not hide this distinction behind Command delivery.

```text
synchronous Command delivery
    !=
necessarily synchronous domain completion
```

Current examples:

```text
PlaceTerrainCommand
    -> accepted operation mutates landscape before submit returns

MoveStepCommand
    -> accepted operation starts MovementAction
    -> Spatial changes only after scheduled completion
```

If the mechanic is timed, define its process state explicitly in the domain.

## Scheduler integration for timed mechanics

A timed mechanic should not place domain meaning inside Scheduler and should not require every process instance to be its own handler.

Current reusable pattern:

```text
DomainStartSystem
    -> creates domain-owned process/action state
    -> calls ProcessScheduler.scheduleAfter(delay, processId)

BoundProcessScheduler
    -> knows SimulationTime + Scheduler + one HandlerId

Scheduler
    -> stores when / handler / opaque processId

DomainProcessProcessor
    -> registered once for the process family
    -> reloads domain state by processId
    -> revalidates and applies domain effects
```

The law is:

```text
Scheduler knows WHEN / HANDLER / PROCESS ID.
Domain knows WHAT THE PROCESS MEANS.
```

Current Movement is the reference implementation:

```text
MovementSystem
    -> MovementActionStore/MovementStateStore state
    -> ProcessScheduler

MovementActionProcessor::complete
    -> one registered ScheduledHandler for all MovementActions
```

Do not create:

```text
one ScheduledHandler per object
global Scheduler switch over process types
universal ActionSystem only because multiple mechanics use time
raw HandlerId authority inside every domain system
```

Domain process identity and Scheduler `TaskHandle` identity remain separate.

## Use production simulation stepping

Timed mechanic tests and presentation should advance time through `SimulationStepper`, not manually increment `SimulationClock` and invoke handlers in arbitrary order.

Current production step:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

This keeps tick ordering one production contract shared by scenarios and future GUI.

If a new mechanic genuinely requires another simulation phase, that is evidence to revise `SimulationStepper` explicitly rather than create private per-system time semantics.

## Define structured results

If an operation can fail because of ordinary world state, return a structured result implementing the neutral `OperationResult` floor.

```text
accepted
ResultCode
optional typed domain data
```

Examples:

```text
terrain:position_occupied
movement:already_moving
movement:transition_unavailable
```

Reserve exceptions for invalid programming/bootstrap/configuration state and violated internal invariants.

## Decide how external intent reaches the mechanic

If Player, AI, scripts, network adapters, scenarios or debug tools need to request the operation, add a Control use-case:

```text
immutable Command
CommandResult
one typed handler
```

The handler should remain a thin adapter to the domain API.

A timed process does **not** continue by submitting internal Commands for each scheduled phase. After acceptance, its domain processor continues through domain APIs directly.

## Keep diagnostics separate from hot reads

A hot read contract should stay primitive and cheap. Diagnostic explanations can use a separate cold path when required by debugging or visualization.

Do not make every high-frequency query allocate strings/collections simply because tooling might someday need a detailed explanation.

## Think about invalidation and revalidation

Timed or cached mechanics need a clear answer for world changes that occur after work starts.

Current Movement deliberately uses:

```text
start-time validation
    -> sleep until completion
    -> completion-time revalidation
```

It does not yet wake immediately on terrain mutation.

A new mechanic should document whether it:

```text
revalidates only when scheduled
subscribes/reacts to relevant mutation events
reserves state to prevent conflicts
or uses another explicit policy
```

Do not leave the timing of invalidation as accidental behavior.

## Add focused tests first

Test local laws before building a huge scenario.

Depending on the mechanic, useful layers include:

```text
definition compiler tests
state-owner unit tests
result/rejection tests
scheduler binding tests
integration with authoritative owner
scenario vertical slice
negative-space tests
boundary arithmetic tests
reference/property tests
```

Timed mechanics should also prove:

```text
no final mutation before due tick
correct completion tick
caller tick batching does not alter result
intermediate world mutation is handled according to documented policy
```

## Consider performance only after semantics

Write down the likely workload, but do not immediately replace a clear implementation with packed arrays, off-heap buffers, custom heaps, or multi-threaded infrastructure.

Ask in order:

```text
Can unnecessary work be removed?
Can the query be localized/indexed?
Can derived work be reused?
Are allocations visible in a representative profile?
Does data representation need specialization?
Does parallelism still matter after that?
```

Current Movement follows this rule by scheduling one completion per active adjacent step rather than per-tick polling every mover, while leaving state-storage specialization to future representative agent workloads.

## Update documentation by layer

A real mechanic usually changes several documentation layers:

```text
ARCHITECTURE.md
    -> only stable semantic contracts/invariants

TECHNICAL_REFERENCE.md
    -> concrete classes, current algorithms, tests, known gaps

Wiki subsystem page
    -> detailed reasoning, examples, formulas, extension rules

Project Structure / Overview / Roadmap
    -> when package/phase/current capabilities changed

EN/RU counterparts + i18n source hashes
```

Current Movement demonstrates the desired depth: its detailed Wiki page documents Command start semantics, Action state, timing carry, Scheduler binding, revalidation, TransitionCost, Shape roles and known deferred responsibilities.

## Completion checklist

A new mechanic is ready when:

```text
single authoritative owner identified
immutable definition data separated from mutable runtime state
narrow read/write contracts defined
immediate vs timed semantics explicit
timed process state owned by domain when applicable
Scheduler binding uses narrow process scheduling when applicable
normal rejection vs exception boundary explicit
external Command added only when external intent needs it
revalidation/invalidation policy explicit
headless unit/integration/scenario tests added
negative cases covered
performance risks noted without speculative infrastructure
architecture/reference/Wiki docs synchronized EN/RU
full simulation suite and docs checks green
```
