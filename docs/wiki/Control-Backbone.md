# Control Backbone

The Control Backbone is the single boundary through which external intent enters authoritative simulation logic.

It is intentionally small. It is not an EventBus, an internal RPC layer, a scheduler replacement, or a requirement that every world mutation must be represented as a Command.

## Core principle

A Command crosses the **external intent boundary**.

Typical producers are:

- Player input;
- AI controllers;
- scripts and scenarios;
- network adapters;
- debug/admin tools.

After an intent has been accepted, continuing internal processes and internal state producers work directly through the narrow domain APIs of authoritative systems.

```text
Player / AI / Script / Network
            |
            v
          Command
            |
            v
SynchronousCommandGateway
            |
            v
     CommandDispatcher
            |
            v
          Handler
            |
            v
       domain write API
```

World generation, erosion, an already-running Movement Action, a mining process, or another internal mechanic does not need to manufacture Commands merely to call another authoritative system.

This prevents Control from becoming a message bus where every mutation is hidden behind `ApplySomethingCommand` objects.

## Result model

All operation outcomes share a deliberately tiny neutral contract:

```java
public interface OperationResult {
    boolean accepted();
    ResultCode code();
}
```

`ResultCode` is namespaced, for example:

```text
terrain:placed
terrain:position_occupied
movement:started
movement:already_moving
movement:transition_unavailable
```

There is no project-wide enum of every possible rejection reason.

`CommandResult` extends `OperationResult`, so generic Control can record whether a command was accepted and which namespaced result code was produced without learning domain semantics.

Concrete domains remain free to expose richer typed results with additional data.

## Rejection versus exception

The boundary is fixed:

```text
conflict caused by current world state
    -> structured result

invalid programming/configuration input
    -> exception
```

Examples of normal structured rejection:

- terrain position is already occupied;
- an object has no ordinary movement capability;
- an object is not placed;
- an object already has an active Movement Action;
- the requested destination is not adjacent;
- Navigation does not expose the requested directed transition.

Examples of programming/configuration errors:

- null command or dependency;
- no handler registered for a command type;
- duplicate handler registration;
- handler returns null;
- unknown trusted runtime definition/object id supplied by calling code;
- a valid movement edge reaches broken traversal definition/configuration.

## Internal expectations

A domain result may be a normal rejection for one caller and an invariant failure for another.

A Player placement command can legitimately receive `terrain:position_occupied`. A deterministic world generator may instead require that a generated position is free.

Internal producers express that expectation generically:

```java
OperationResults.requireAccepted(
        landscape.placeTerrain(...));
```

They do not compare against concrete success constants unless the concrete distinction is actually part of their logic.

`requireAccepted` does not change the domain operation contract. It only states that this caller considers any rejection unexpected.

## Command core

The generic core lives under:

```text
simulation/control/core/
```

Current types are:

- `Command<R extends CommandResult>` — immutable intent marker;
- `CommandResult` — observable result floor;
- `CommandHandler<C,R>` — typed execution boundary;
- `CommandDispatcher` — exact-runtime-type registration and dispatch.

The dispatcher owns the small registration map directly. A separate registry is not introduced until a real requirement justifies it.

### Exact type rule

Registration is one handler for one concrete command class.

```text
PlaceTerrainCommand.class -> PlaceTerrainHandler
MoveStepCommand.class     -> MoveStepHandler
```

The dispatcher does not search superclasses or interfaces for a “closest” handler.

Missing or duplicate registration is a bootstrap/programming error, not a domain rejection.

## Synchronous delivery semantics

The current delivery implementation is:

```text
simulation/control/sync/SynchronousCommandGateway
```

`submit(command)` dispatches and executes immediately.

For an immediate operation such as accepted terrain placement, the mutation is visible before `submit` returns.

For a timed operation such as Movement, synchronous **command delivery** does not mean synchronous **domain completion**:

```text
submit(MoveStepCommand)
    -> immediately validates and starts MovementAction
    -> returns movement:started
    -> Spatial position still remains at source
    -> Scheduler completes the action later in simulation time
```

This distinction is important. Control delivery determines when intent reaches the domain; the domain determines whether accepted work is immediate or long-lived.

For deterministic callers, submitted command order is still the deterministic order of calls.

Future queued/asynchronous gateways may reuse the same Command, Handler and Dispatcher contracts. They must explicitly define queue flush order and state-visibility semantics; switching delivery policy is not assumed to preserve within-tick visibility automatically.

## Dependency law

The generic Control layer routes commands but does not know world domains.

The dependency direction is executable policy:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Concrete use-case adapters under `simulation/control/<use-case>/` may import the narrow domain APIs they orchestrate.

Current examples:

```text
control/terrain/PlaceTerrainHandler
        -> LandscapeMutations

control/movement/MoveStepHandler
        -> MovementSystem
```

The reverse dependency is forbidden.

`ControlDependencyContractTest` enforces the generic package rules.

## Command organization

The current command surface is:

```text
simulation/control/
├── core/
├── sync/
├── terrain/
│   ├── PlaceTerrainCommand
│   ├── PlaceTerrainResult
│   └── PlaceTerrainHandler
└── movement/
    ├── MoveStepCommand
    ├── MoveStepResult
    └── MoveStepHandler
```

Concrete commands are grouped by **intent/use-case**, not necessarily by whichever single authoritative system eventually mutates state.

A future `BuildStructureCommand` belongs to construction even if its handler coordinates Inventory, Objects, Spatial and Landscape.

## Terrain placement vertical slice

`PlaceTerrainCommand` validates the immediate synchronous mutation path:

```text
PlaceTerrainCommand
        |
        v
PlaceTerrainHandler
        |
        v
LandscapeMutations.placeTerrain
        |
        v
TerrainSystem + Geometry lifecycle
        |
        v
PlaceTerrainResult
```

Expected behavior:

```text
first placement into an empty position
    -> ACCEPTED / terrain:placed

second placement into the same position
    -> REJECTED / terrain:position_occupied
    -> original terrain remains unchanged
```

## Timed Movement vertical slice

`MoveStepCommand` validates that a Command may start a long-lived domain process without turning each internal phase into another Command.

```text
MoveStepCommand
        |
        v
MoveStepHandler
        |
        v
MovementSystem.startStep
        |
        +--> validate capability / adjacency / Navigation
        +--> TransitionCost -> MovementRate -> duration
        +--> create MovementAction
        +--> ProcessScheduler.scheduleAfter(...)
        |
        v
MoveStepResult = movement:started

later, through Scheduler rather than Control:

MovementActionProcessor.complete(processId)
        |
        +--> revalidate object/source/Navigation
        |
        v
SpatialSystem.move(...) or interrupt
```

This slice proves several Control boundaries:

```text
Command carries external start intent only
accepted does not imply immediate final mutation
continuing Action is domain state, not a stream of internal Commands
Scheduler continuation bypasses CommandDispatcher
Movement completion mutates authoritative systems through domain APIs
```

See [Movement System](Movement-System.md) for the complete timing and cost semantics.

## Landscape mutation boundary

Terrain state and Geometry state are separate authoritative concerns, but some landscape lifecycle operations must keep them coherent.

The public coordinated write capability is:

```text
LandscapeMutations
```

Current terrain lifecycle policy is:

```text
placeTerrain
    new terrain cell
    -> stale geometry override is cleared
    -> default geometry is FullShape

replaceTerrain
    existing terrain definition changes
    -> geometry override is preserved

removeTerrain
    terrain cell disappears
    -> geometry override is cleared
```

`TerrainSystem` remains the owner of terrain storage and terrain-specific invariants. It does not depend on Geometry.

`LandscapeSystem` coordinates `TerrainSystem` and `GeometrySystem`, so every client of `LandscapeMutations` receives the same lifecycle semantics whether the caller is a Command handler, generator, erosion mechanic, or another internal producer.

## Extension checklist

When adding a new command:

1. confirm that it really crosses an external intent boundary;
2. create the immutable command under the appropriate `control/<use-case>/` package;
3. define a typed `CommandResult` with observable `accepted` and namespaced `code`;
4. implement one typed handler using narrow domain APIs;
5. register exactly one handler for the concrete command class;
6. test accepted and rejected world-state paths;
7. keep invalid programming/configuration inputs as exceptions;
8. do not teach `CommandDispatcher` the new domain type;
9. if acceptance starts a long-lived process, keep that process in its domain rather than routing its continuation back through Commands;
10. update architecture/reference documentation when a stable contract changes.
