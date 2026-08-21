# ADR-022: Green checkpoint development

- Status: Accepted
- Scope: Repository engineering workflow
- Decision: Production development advances through small independently understandable green checkpoints; an unexplained failing checkpoint blocks the next semantic change.

## Context

Large batches of interacting changes are cheap to write but expensive to understand. When model changes, repairs, presentation behavior and diagnostics accumulate before any intermediate state is verified, a later failure no longer identifies which boundary first became wrong.

## Decision

Production work is a sequence of green checkpoints:

```text
one stated contract
      ↓
one smallest meaningful implementation step
      ↓
focused evidence
      ↓
focused check
      ↓
understood green state
      ↓
coherent commit
      ↓
next contract/block
```

Rules:

1. State ownership, preserved behavior and acceptance evidence before changing a non-trivial subsystem.
2. Change one independently reviewable concern at a time.
3. Test at the nearest meaningful boundary immediately.
4. Do not stack a new hypothesis on an unexplained failing checkpoint; fix, revert or isolate it first.
5. Production commits are coherent understood checkpoints; disposable experiments do not become accidental architecture.
6. Tests change only when the intended contract deliberately changes.
7. Repository CI, documentation build, performance checks and manual visual acceptance are final/escalation gates, not substitutes for focused checks.
8. Diagnose the earliest layer that emits a wrong fact before adding a downstream repair.
9. If a PR becomes hard to explain from its checkpoint sequence, stop adding scope and split or rebuild from the last accepted state.

## Why

Small green checkpoints reduce regression search space, keep each change reviewable/revertible and preserve causal understanding. Automated invariants and manual visual/performance evidence complement each other.

## Consequences

- Development may use more small commits and frequent focused checks.
- A red head intentionally blocks unrelated semantic progress until understood.
- Refactors that claim to preserve behavior remain separate from semantic changes.
- Large features are assembled from independently valid blocks.

## Current implementation

`CONTRIBUTING.md` and the Development Workflow define the short loop. The Continuum development plan applies the same gate to every world-generation stage: correctness, repeatability, seam/boundary behavior, performance, visualization where meaningful and replaceability.

## Related documentation

- [Development Workflow](../guides/development-workflow.md)
- [Development Branching Model](005-development-branching-model.md)
- [Architecture](../architecture.md)
- [Continuum Development Plan](../systems/world-generation/continuum-development-plan.md)
- [ADR-024](024-continuum-large-world-architecture.md)
