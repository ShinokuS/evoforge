# Control and Commands

## Purpose

Provide one external-intent boundary for player input, AI adapters, scripts, scenarios, future network adapters and debug/admin tools without making generic Control depend on world-domain semantics.

Control is intentionally small. It is not an EventBus, Scheduler replacement, internal RPC layer or a rule that every authoritative mutation must be represented as a Command.

## Command model

`Command<R extends CommandResult>` describes immutable external intent. The command does not execute itself.

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

A handler adapts external intent to the authoritative domain API; it does not become the owner of that mechanic.

## Result floor

Operation outcomes share the neutral observation contract:

```java
boolean accepted();
ResultCode code();
```

`CommandResult` extends that floor. Result codes are open namespaced data such as:

```text
terrain:placed
terrain:position_occupied
movement:started
movement:transition_unavailable
movement:move_to_started
movement:move_to_cancel_requested
```

There is no project-wide enum of every domain outcome. A use-case handler forwards `accepted + ResultCode` rather than recreating a closed mirror of domain reasons.

Command acceptance and eventual completion are separate facts. An accepted timed `MoveStep` means its edge started. An accepted `MoveTo` means route-level intent was accepted; later `MoveToCompletion` says whether the goal was actually reached. An accepted `CancelMoveTo` means cancellation was requested; an already scheduled atomic edge may still complete before the route-level action terminates.

## Rejection versus exception

```text
expected conflict caused by valid current world state
    -> structured result

invalid programming/configuration/invariant state
    -> exception
```

Normal rejection includes unavailable movement capability, locomotion already owned, unavailable transitions and occupied/reserved destinations. Null dependencies, duplicate handler registration or broken trusted-runtime invariants remain exceptional.

## Exact-type dispatch

One concrete command class has exactly one registered handler. Current production registration includes:

```text
PlaceTerrainCommand.class   -> PlaceTerrainHandler
ReplaceTerrainCommand.class -> ReplaceTerrainHandler
MoveStepCommand.class       -> MoveStepHandler
MoveToCommand.class         -> MoveToHandler
CancelMoveToCommand.class   -> CancelMoveToHandler
```

The dispatcher does not search superclass/interface hierarchies for a "closest" handler. Missing and duplicate registration are bootstrap/programming failures.

Adding another command registers one adapter; the generic dispatcher does not gain another domain `switch` branch.

## Dependency law

Generic routing stays domain-neutral:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                  -X-> simulation.control.*
```

Concrete use-case packages under `simulation/control/<use-case>/` may import the narrow domain API they adapt, for example:

```text
terrain handlers          -> LandscapeMutations
MoveStepHandler           -> MovementSystem
MoveToHandler             -> MoveToSystem
CancelMoveToHandler       -> MoveToSystem
```

The reverse dependency remains forbidden.

## Internal continuation is not command RPC

After external intent has been accepted, continuing work stays inside the owning mechanic.

Examples:

- scheduled completion of an existing Movement action;
- MoveTo starting its next child edge after a Movement completion;
- provider-owned timed opportunity use;
- Need/Growth/Water periodic process continuation.

For route-level movement:

```text
submit(MoveToCommand)
    -> synchronously accept route intent
    -> Movement/MoveTo own continuing work

later Scheduler
    -> MovementActionProcessor
    -> completion cleanup/revalidation
    -> MoveTo continues or terminates
```

A later external cancellation is a new external intent and therefore correctly crosses the command boundary once through `CancelMoveToCommand`; the cancellation's continuing completion still stays inside Movement.

## Current synchronous transport

`SynchronousCommandGateway.submit` dispatches immediately. Immediate accepted mutation/ownership is visible before `submit` returns.

Synchronous delivery does not imply synchronous domain completion. Timed commands may only start continuing work; a MoveTo may also reach an immediate terminal observation such as source-equals-goal or `NO_PATH` during submission.

A future queued/asynchronous gateway may reuse the same command/handler contracts only after defining queue order and within-tick visibility explicitly.

## Adding a command

A new command should normally require:

1. prove that the operation crosses the external-intent boundary;
2. add the immutable command/result under the relevant use-case package;
3. implement one typed handler using narrow domain APIs;
4. register exactly one concrete command type;
5. test accepted and expected-rejection paths;
6. keep programming/configuration failures exceptional;
7. forward open domain results without rebuilding an exhaustive result catalog;
8. keep long-lived continuation in the owning domain rather than routing it back through Commands.

See [Command Boundary decision](../decisions/003-command-boundary.md) and [Movement](movement.md).
