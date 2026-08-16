# Decision 008 — Completion-driven autonomous wakeups

**Status:** Accepted

## Problem

Autonomous Agents keep a committed intent while MoveTo or provider-owned use is active. The earlier implementation nevertheless woke every active Agent once per simulation tick only to ask whether that work had finished.

This did not rescore Utility or change intent, but it created scheduler work proportional to the number of active agents on every tick. That polling conflicts with EvoForge's discrete event-driven simulation model and becomes unnecessary overhead at representative population sizes.

The fix must not introduce species-specific callbacks or make Movement depend on Agent.

## Decision

Committed autonomous work sleeps until its next meaningful lifecycle boundary.

```text
Agent commits MoveTo
        ↓
Agent has no periodic active wake
        ↓
MoveTo reaches terminal completion
        ↓
neutral MoveToCompletionSink
        ↓
owning Agent continuation runs at that terminal boundary
        ↓
Agent consumes the terminal outcome
```

`MoveToSystem` publishes terminal `MoveToCompletion` through a neutral sink contract. The default MoveTo composition contains a bind-once relay; `AgentSystem`, which already owns autonomous MoveTo intents, binds itself as the autonomous completion consumer. `MoveToSystem` has no dependency on `AgentSystem`.

A completion only continues an Agent when the action id matches the MoveTo currently owned by that Agent's opportunity intent or search relocation. Unrelated/manual MoveTo completions are ignored by autonomous decision state.

The matching autonomous continuation runs synchronously from the terminal completion callback. This is deliberate: `Scheduler` snapshots the current due batch before dispatch, so scheduling a new same-tick task during dispatch would defer it until the next simulation step and add an artificial orchestration tick. The completion callback is already a terminal lifecycle boundary: MoveTo has removed its active state and released its movement claim before publishing the completion. Continuing the owner there preserves existing same-tick semantics without recursive scheduler dispatch.

Synchronous terminal outcomes produced inside `MoveTo.start(...)` are still handled by the caller after `start(...)` returns. At that point the autonomous intent has not yet been published, so the completion sink correctly ignores them rather than re-entering an unfinished decision pass.

## Timed provider use

Current opportunity-use providers schedule deterministic timed actions and return an exact `expectedCompletionTick` in `OpportunityUseStartAttempt`.

Agent schedules its own continuation directly at that declared completion tick. The provider's completion process is scheduled before the Agent continuation, so deterministic Scheduler ordering makes authoritative provider completion visible when Agent resumes.

Zero-duration/synchronously terminal use does not create an active wait. It retains a bounded one-shot continuation instead of recursively executing unlimited uses in one simulation tick.

`ProcessScheduler.scheduleAt(...)` is available as an absolute-time capability on the production bound scheduler while `scheduleAfter(...)` remains the single abstract relative-time contract. This preserves `ProcessScheduler` as a functional interface for simple subsystem/test schedulers that never need absolute wake scheduling.

## Search relocation

Search relocation uses production MoveTo, so it receives the same completion-driven continuation behavior. There is no separate search polling loop.

## What remains periodic

This decision removes periodic wakeups only while committed work is already active.

Idle autonomous cognition still uses the existing bounded recheck interval. Search may also request a deliberate near-term continuation when its own state machine has immediate cognitive work but has not started locomotion.

A fully reactive idle model would require real stimulus contracts such as Need threshold crossings or perception/world-change notifications. Those are separate mechanics and are not invented by this refactor.

## Consequences

- active MoveTo no longer causes one Agent scheduler wake per tick;
- active provider use no longer causes one Agent scheduler wake per tick;
- search relocation no longer causes one Agent scheduler wake per tick;
- successful MoveTo-to-use handoff preserves its existing simulation-tick timing;
- committed-intent semantics and Utility selection remain unchanged;
- autonomous continuations are tied to owned terminal outcomes rather than elapsed polling intervals;
- MoveTo remains a generic movement mechanic and does not know about Cow, Agent, Hunger or Thirst;
- the same pattern can later support other long-running mechanics when they expose explicit terminal lifecycle contracts.

## Deliberately deferred

This decision does not define:

- reactive idle wakeups from Need threshold crossings;
- stimulus subscriptions for Vision/hearing/smell;
- deliberate preemption of a still-valid committed intent;
- a general-purpose global event bus;
- early asynchronous cancellation for provider use actions that currently have exact scheduled completion ticks.

If a future provider can terminate earlier than its declared completion tick, that real consumer should introduce the corresponding neutral completion signal rather than restore polling.
