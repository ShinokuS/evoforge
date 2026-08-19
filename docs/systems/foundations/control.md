# Control and Commands

## In plain language

Control is the **front desk** for external requests to the simulation.

A player, AI adapter, scenario, script or future network client can say “start this move” or “place this terrain”. Control routes that request to the system that actually owns the rule. It does not itself decide movement physics, terrain legality or resource behavior.

Once a long-running action has started, its mechanic continues the work internally. The front desk is not called again every tick.

## Current status

The production Control layer provides synchronous external command submission with exact concrete-command-type dispatch and open structured result codes.

It is intentionally not:

- an EventBus;
- a replacement for Scheduler;
- an internal RPC layer between world systems;
- a rule that every internal mutation must be a Command.

## Command lifecycle

```text
external producer
      ↓
immutable Command<R>
      ↓
CommandGateway
      ↓
CommandDispatcher
      ↓ exact concrete command class
registered CommandHandler
      ↓
narrow domain-owned capability
      ↓
structured result
```

A handler adapts external intent to a domain API. It never becomes the owner of that domain.

## Result model

Operation results share a neutral floor:

```java
boolean accepted();
ResultCode code();
```

`CommandResult` extends this model. `ResultCode` is open namespaced data instead of a giant global enum:

```text
terrain:placed
terrain:position_occupied
movement:started
movement:transition_unavailable
movement:move_to_started
movement:move_to_cancel_requested
```

This matters because a generic caller can observe success/rejection without forcing every domain to edit one central result catalog.

## Acceptance is not completion

For an immediate operation, acceptance may effectively be completion. For a timed/continuing operation they are separate facts.

Example:

```text
submit MoveStep
    ↓ accepted
Movement edge is now owned/in progress
    ↓ later scheduled completion
MovementCompletion reports final outcome
```

For `MoveTo`, command acceptance means route-level intent was accepted. A later completion may still report success/failure after multiple atomic edges.

For cancellation, accepted `CancelMoveTo` means cancellation was requested. The currently scheduled atomic edge may finish; the route starts no later edge and then releases route ownership.

## Expected rejection versus exception

EvoForge distinguishes a valid request that the current world cannot satisfy from broken program/configuration state.

```text
valid current-world conflict
        ↓
structured rejection result

programming/configuration/invariant failure
        ↓
exception
```

Typical structured rejections include:

- movement capability unavailable;
- actor already owns locomotion work;
- structural transition unavailable;
- destination occupied/reserved.

Examples of exceptional conditions include null core dependencies, duplicate handler registration or violated trusted-runtime invariants.

## Exact-type dispatch

One concrete command class has one registered handler. Production registration includes commands such as:

```text
PlaceTerrainCommand   -> PlaceTerrainHandler
ReplaceTerrainCommand -> ReplaceTerrainHandler
MoveStepCommand       -> MoveStepHandler
MoveToCommand         -> MoveToHandler
CancelMoveToCommand   -> CancelMoveToHandler
```

The dispatcher does not search interfaces/superclasses for the “closest” handler. Missing/duplicate registration is a bootstrap/programming failure.

Therefore adding another command normally means adding/registering one adapter, not editing a central domain switch.

## Dependency boundary

Generic routing packages remain world-domain neutral:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                  -X-> simulation.control.*
```

Concrete use-case handlers may depend on the narrow domain API they adapt:

```text
terrain handler -> LandscapeMutations
move handler    -> MovementSystem
MoveTo handler  -> MoveToSystem
```

World domains never depend backwards on Control merely so they can continue their own work.

## Internal continuation is not command RPC

After acceptance, continuing work belongs to its mechanic:

- Movement completion remains Movement work;
- `MoveTo` starting the next child edge remains route/Movement work;
- timed opportunity use remains provider-owned work;
- Need/Growth/liquid/environment processes remain their own scheduled work.

A later external cancellation is correctly a new Command because it is a new external intent. Its internal completion still stays inside Movement/MoveTo.

## Synchronous transport semantics

`SynchronousCommandGateway.submit(...)` dispatches immediately. Any immediate ownership/state mutation caused by accepting the request is visible before the call returns.

This does **not** mean a timed action completes synchronously.

A future queued/asynchronous gateway could reuse the command/handler model only after defining queue order and within-tick visibility explicitly.

## Invariants

- Control routes external intent; it does not own domain meaning.
- Every concrete command type has at most one handler.
- Domain outcomes remain open namespaced result data.
- Expected world-state conflicts are results, not exceptions.
- Continuing internal mechanics do not route through Control repeatedly.
- World domains do not depend on generic Control packages.

## Current limitations

There is no queued/network command transport, permission/auth model, command persistence or distributed command ordering yet.

Those future transports must preserve explicit deterministic ordering/visibility semantics rather than assuming synchronous behavior accidentally.

## Code and tests

Primary code:

```text
simulation/.../control/core/
simulation/.../control/sync/
simulation/.../control/<use-case>/
```

Tests cover registration/dispatch, accepted/rejected domain adaptation and Movement/MoveTo command lifecycle behavior.

## Sources

**Internal EvoForge design.** The external-intent boundary and open-result convention are project architecture, not an implementation of an external command-bus framework.

See [ADR-003: Command boundary](../../decisions/003-command-boundary.md), [Runtime Composition](runtime.md), [Movement](../traversal/movement.md), and [Time and Scheduling](time.md).
