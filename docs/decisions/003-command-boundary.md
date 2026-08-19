# ADR-003: Commands are external intent

- Status: Accepted
- Scope: Control boundary
- Decision: Commands enter the simulation from external callers; continuing internal mechanics call narrow domain capabilities directly instead of routing through Control as internal RPC.

## Context

Player input, autonomous controllers, scripts and scenarios need one controllable entry point. Forcing every internal state transition through Commands would turn Control into a universal event/RPC framework and reverse dependencies from world mechanics back toward application transport.

## Decision

Generic Control routes an immutable concrete command type to exactly one registered handler and returns a structured domain result. The handler adapts external intent to the narrow owning domain capability.

Once a continuing action is accepted, its lifecycle remains inside the owning mechanic and Scheduler/process infrastructure. A later external cancellation is a new command because it is new external intent; internal completion is not.

## Why

This keeps external transport replaceable while preserving domain ownership and makes it clear which actions are requests versus internal causal continuation.

## Consequences

- Player, AI adapters, scripts and debug tools can share external command semantics.
- World mechanics do not depend on Control packages.
- Scheduled Movement/provider completions stay domain logic.
- Expected world-state rejection remains structured data rather than an exception.
- A future queued/asynchronous command transport must define ordering and within-tick visibility explicitly.

## Alternatives considered

A universal internal Command/Event bus was rejected because it would hide ownership and create unnecessary indirection between domains that already have typed capabilities.

## Current implementation

`CommandGateway`/`CommandDispatcher` perform synchronous exact-type dispatch. Production handlers adapt Terrain placement/replacement and Movement/MoveTo/cancellation requests to owning systems. Continuing Movement, MoveTo, Growth, Need, liquid and provider-use work is not re-issued as Commands.

## Related documentation

- [Control and Commands](../systems/foundations/control.md)
- [Runtime Composition](../systems/foundations/runtime.md)
- [Time and Scheduling](../systems/foundations/time.md)
- [Movement](../systems/traversal/movement.md)
