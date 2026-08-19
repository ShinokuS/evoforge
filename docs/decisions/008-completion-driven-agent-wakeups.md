# ADR-008: Completion-driven autonomous wakeups

- Status: Accepted
- Scope: Agent scheduling
- Decision: While an Agent owns committed long-running work, it sleeps until the next meaningful lifecycle boundary instead of polling completion every simulation tick.

## Context

Committed MoveTo/provider-use/search work previously caused active Agents to wake every tick only to ask whether the already-owned work had finished. This did not improve decisions but created scheduler work proportional to active population and conflicted with EvoForge's event-driven process model.

## Decision

Committed work resumes cognition at explicit terminal or expected-completion boundaries.

For MoveTo:

```text
Agent commits MoveTo
   ↓ sleeps
MoveTo reaches terminal state
   ↓ neutral MoveToCompletionSink
Agent continuation consumes owned matching action result
```

`MoveToSystem` publishes through a neutral completion sink and has no Agent dependency. The autonomous continuation runs synchronously only after MoveTo has removed terminal state/released its movement claim, preserving same-tick handoff without recursively draining Scheduler work.

Timed opportunity providers return an exact expected completion tick. Agent schedules its continuation there; provider completion is ordered first so authoritative use result is visible when Agent resumes. Zero-duration terminal use uses a bounded one-shot continuation rather than unbounded same-tick recursion.

Search relocation uses ordinary MoveTo and the same completion-driven behavior. Idle cognition still uses a bounded recheck interval because no general stimulus subscription model has been justified yet.

## Why

The model removes meaningless active polling without creating species callbacks, a global event bus or Movement→Agent dependency.

## Consequences

- Active locomotion/provider use/search relocation no longer creates one Agent wake per tick.
- Existing same-tick MoveTo→use handoff semantics are preserved.
- Completion signals are tied to exact owned action identities; unrelated/manual completions are ignored by autonomous state.
- Long-running future mechanics can expose explicit lifecycle boundaries if real consumers need them.

## Alternatives considered

Per-tick polling was rejected as unnecessary scheduled work. A general global event bus and speculative reactive Need/perception subscriptions were rejected because current consumers do not require them.

## Current implementation

`MoveToCompletionSink`/relay provides neutral route completion. Agent matches the exact MoveTo it owns before continuation. Timed opportunity-use start results expose expected completion tick. Idle/search immediate cognition still uses bounded scheduling where no long-running owner was started.

## Related documentation

- [Autonomous Agents](../systems/agents/agents.md)
- [Movement](../systems/traversal/movement.md)
- [Time and Scheduling](../systems/foundations/time.md)
