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

World generation, erosion, an already-running mining process, or another internal mechanic does not need to manufacture Commands merely to call another authoritative system.

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
movement:blocked
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
- required terrain is absent;
- a future movement transition is blocked;
- a future construction attempt lacks support.

Examples of programming/configuration errors:

- null command or dependency;
- no handler registered for a command type;
- duplicate handler registration;
- handler returns null;
- unknown runtime `LandscapeDefinitionId` supplied by calling code.

## Internal expectations

A domain result may be a normal rejection for one caller and an invariant failure for another.

A Player placement command can legitimately receive `terrain:position_occupied`. A deterministic world generator may instead require that a generated position is free.

Internal producers express that expectation generically:

```java
OperationResults.requireAccepted(
        landscape.placeTerrain(...));
```

They do not compare against concrete success constants such as `result == PLACED` unless the concrete distinction is actually part of their logic.

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
```

The dispatcher does not search superclasses or interfaces for a "closest" handler.

Missing or duplicate registration is a bootstrap/programming error, not a domain rejection.

## Synchronous delivery semantics

The first delivery implementation is:

```text
simulation/control/sync/SynchronousCommandGateway
```

`submit(command)` dispatches and executes immediately. Authoritative mutations performed by the handler are visible before `submit` returns.

Therefore, for deterministic callers:

```text
command A
    -> world mutation A becomes visible
command B
    -> observes the state after A
```

The current deterministic order is the deterministic order of calls.

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

For example:

```text
control/terrain/PlaceTerrainHandler
        -> LandscapeMutations
```

The reverse dependency is forbidden.

`ControlDependencyContractTest` enforces these package rules.

## Command organization

The command surface is kept discoverable under one architectural root:

```text
simulation/control/
├── core/
├── sync/
├── terrain/
├── movement/       # future
├── construction/   # future
└── ...
```

Concrete commands are grouped by **intent/use-case**, not necessarily by the authoritative system they mutate.

A future `BuildStructureCommand` belongs to construction even if its handler coordinates Inventory, Objects, Spatial and Landscape.

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

## First vertical slice

The first concrete command is `PlaceTerrainCommand`.

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

This slice validates command routing, structured rejection, immediate synchronous visibility and authoritative domain ownership without introducing Movement, Scheduler coupling, EventBus, or long-running Actions.

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
9. update architecture/reference documentation when a stable contract changes.
