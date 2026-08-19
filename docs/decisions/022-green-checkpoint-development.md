# ADR-022: Green checkpoint development

- Status: Accepted
- Scope: Repository engineering workflow
- Decision: Production development advances through small independently understandable green checkpoints; an unexplained failing checkpoint blocks the next semantic change.

## Context

Large batches of interacting changes are cheap to write but expensive to understand. When algorithm changes, calibration changes, repairs, presentation behavior and diagnostics accumulate before any intermediate state is verified, a later failure no longer identifies which ownership boundary first became wrong. Developers are then tempted to add downstream repair passes, weaken tests or continue experimenting inside the production history.

EvoForge already prefers focused feature branches and headless deterministic tests. That is not sufficient by itself: a feature branch can still contain a long sequence of unresolved hypotheses before CI is consulted. The project needs an explicit rule for the size and verification state of each development step.

## Decision

Production work is a sequence of green checkpoints:

```text
one stated contract
      ↓
one smallest meaningful implementation step
      ↓
focused acceptance evidence
      ↓
focused check
      ↓
understood green state
      ↓
coherent commit
      ↓
next contract/block
```

The following rules apply:

1. State semantic ownership, preserved behavior and acceptance evidence before changing a non-trivial subsystem.
2. Change one independently reviewable concern at a time. Refactoring, semantic changes, diagnostics and presentation changes should not be bundled merely because they contribute to the same feature.
3. Test at the nearest meaningful boundary and run that focused check immediately.
4. Do not add a new hypothesis or unrelated change on top of an unexplained failing checkpoint. Fix, revert or isolate the failing step first.
5. A production commit must be a coherent, understood checkpoint. Temporary diagnostics and failed approaches belong on disposable diagnostic/experiment branches when they would obscure production history.
6. Tests change only when the intended contract deliberately changes; failing evidence is not weakened to accommodate an implementation.
7. Repository-wide CI, audits, documentation builds and manual visual/performance acceptance are escalation/final gates. They supplement rather than replace focused component checks.
8. When a visual symptom appears, diagnose the earliest stage that emits the wrong fact before adding a downstream repair. Generated pipelines are inspected in causal order.
9. If a PR becomes hard to explain from its checkpoint sequence, stop adding scope and split or rebuild it from the last accepted state.

## Why

Small green checkpoints reduce the search space of every regression. They make each commit independently reviewable/revertible, preserve architectural ownership and keep experimental diagnosis from becoming accidental production design. The workflow also distinguishes two kinds of evidence: precise automated invariants and manual observation for qualities that are inherently visual or performance-sensitive.

## Consequences

- Some changes will use more commits and more frequent focused test runs while being developed.
- A red head intentionally slows feature velocity until its cause is understood; this is considered cheaper than compounding uncertainty.
- Diagnostic branches may be short-lived and never merged.
- Refactors that claim to preserve behavior should be separated from semantic changes and reuse existing acceptance evidence.
- Large features are assembled from independently valid components instead of landing as one indivisible implementation.
- Final PR history should explain the design rather than reproduce every failed investigation.

## Alternatives considered

Writing the complete feature first and relying on full CI at the end was rejected because failures then span too many possible causes. Keeping every diagnostic/failure in the production PR was rejected because historical completeness is less valuable than a reviewable production design; durable lessons belong in ADRs/notes. Requiring every tiny line edit to be a separate commit was also rejected: the checkpoint unit is one coherent independently meaningful concern, not an arbitrary diff size.

## Current implementation

`CONTRIBUTING.md` contains the mandatory short loop and `docs/guides/development-workflow.md` defines the detailed process, debugging rule, experiment handling and PR shape. Feature PRs remain governed by ADR-005 for branch ownership; this ADR governs how work advances inside those branches.

## Related documentation

- [Development Workflow](../guides/development-workflow.md)
- [Development Branching Model](005-development-branching-model.md)
- [Architecture](../architecture.md)
- [Generated-world diagnostic audits](017-generated-world-diagnostic-audits.md)
