# Control and Commands

## Purpose

Provide one external-intent boundary for player input, AI, scripts, scenarios, future network adapters and debug/admin tools without making generic Control depend on world-domain semantics.

Control is intentionally small. It is not an EventBus, Scheduler replacement, internal RPC layer or a rule that every authoritative mutation must be represented as a Command.

## Command model

`Command<R extends CommandResult>` describes immutable external intent. The command object does not execute itself or mutate the world.

```text
external producer
    ↓
Command
    ↓
CommandGateway
    ↓
CommandDispatcher
    ↓ exact runtime command type
registered CommandHandler
    ↓
narrow domain capability
```

A handler adapts external intent to the authoritative domain API. It does not become the owner of the mechanic it invokes.

## Result floor

Operation outcomes share a small neutral observation contract:

```java
boolean accepted();
ResultCode code();
```

`CommandResult` extends that operation-result floor, so generic Control can observe acceptance and a namespaced result code without importing domain semantics.

Result codes are namespaced data such as:

```text
terrain:placed
terrain:position_occupied
terrain:replaced
terrain:terrain_absent
movement:started
movement:already_moving
movement:transition_unavailable
```

There is no project-wide enum containing every possible domain outcome. Concrete domains may expose richer typed result data beyond the common floor.

## Rejection versus exception

The boundary is semantic:

```text
expected conflict caused by current valid world state
    → structured result

invalid programming/configuration/invariant state
    → exception
```

Examples of ordinary rejection include occupied terrain placement, replacing terrain where none exists, missing ordinary movement capability, an already active Movement action or an unavailable structural transition.

Examples of exceptional state include null dependencies/commands, missing or duplicate handler registration, a handler returning null, unknown trusted runtime ids or broken definition data required by an otherwise valid operation.

An internal producer can express that any rejection is unexpected using the generic operation-result helper rather than comparing to a concrete success constant. This changes the caller's expectation, not the domain operation contract.

## Exact-type dispatch

One concrete command class has one registered handler:

```text
PlaceTerrainCommand.class   → PlaceTerrainHandler
ReplaceTerrainCommand.class → ReplaceTerrainHandler
MoveStepCommand.class       → MoveStepHandler
```

The dispatcher does not search superclasses or interfaces for a “closest” handler. Missing and duplicate registrations are bootstrap/programming failures.

Exact-type dispatch keeps extension explicit: adding a command registers its adapter without adding a central `switch` over domain command types.

## Dependency law

Generic routing stays domain-neutral:

```text
simulation.control.core  -X→ world.*
simulation.control.sync  -X→ world.*
world.*                  -X→ simulation.control.*
```

Concrete use-case adapters under `simulation/control/<use-case>/` may import the narrow domain API they orchestrate.

Examples:

```text
terrain command handler   → LandscapeMutations
movement command handler  → MovementSystem
```

The reverse dependency is forbidden. `ControlDependencyContractTest` makes the generic package boundary executable policy.

## Internal work is not Command RPC

After an intent has been accepted, continuing domain work stays inside the owning mechanic.

Examples that do not need to manufacture Commands merely to continue:

- scheduled completion of an existing Movement action;
- future world generation or erosion producing terrain through narrow landscape mutation capability;
- future crafting/growth/construction process continuation.

For timed Movement:

```text
submit(MoveStepCommand)
    → synchronously validate/start MovementAction
    → return movement:started
    → Spatial remains at source

later:
Scheduler
    → MovementActionProcessor
    → completion revalidation
    → Spatial move or interruption
```

The scheduled continuation bypasses CommandDispatcher because it is no longer external intent.

## Current synchronous transport

`SynchronousCommandGateway.submit` dispatches immediately. Immediate accepted mutations are visible before `submit` returns.

Synchronous **delivery** does not imply synchronous **domain completion**: an accepted timed command may only start a long-lived action.

Submitted call order is deterministic for deterministic callers. A future queued/asynchronous gateway may reuse the same command/handler contracts only after explicitly defining queue flush order and within-tick state visibility; transport changes may not silently change simulation semantics.

## Adding a command

A new command should normally require:

1. confirm the operation crosses the external-intent boundary;
2. add the immutable command/result under the relevant use-case package;
3. implement one typed handler using narrow domain APIs;
4. register exactly one handler for the concrete command type;
5. test accepted and expected-rejection paths;
6. leave programming/configuration failures exceptional;
7. keep long-lived continuation in the owning domain rather than routing it back through Commands.

The generic dispatcher must not learn the new domain type.

See [Command Boundary decision](../decisions/003-command-boundary.md).
