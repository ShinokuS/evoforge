# Development Workflow

EvoForge develops in small, reviewable slices while keeping a stable recovery point at all times.

## Branch model

```text
main
  ^
  |  accepted milestone PR
  |
develop
  ^   ^
  |   |
feature/*   experiment/*
```

The long-lived branches have different jobs:

- `main` is the stable, accepted project baseline. It should always represent a state we are willing to return to, tag and reproduce.
- `develop` is the integration branch for the next milestone. Merged heads should keep CI green, but the milestone may still be incomplete.
- `feature/*` contains one coherent production slice and normally branches from the latest `develop`.
- `experiment/*` contains disposable investigation. It may be rough, may be abandoned and is never assumed to deserve a merge merely because code was written.
- `release/*` is intentionally absent for now. Introduce it only when a release must be stabilized while later development continues in parallel.
- `hotfix/*` branches from `main` only when the stable baseline itself needs an urgent correction. The accepted fix must also be brought back into `develop`.

A branch is cheap. Use one before an uncertain architectural direction becomes entangled with unrelated work.

## Architecture-quality contract

Every production slice must satisfy [ADR-023: Strict modular architecture and replaceable boundaries](../decisions/023-strict-modular-architecture.md) before it is considered complete.

Before implementation, answer these questions explicitly:

```text
Who owns the fact/behavior?
What are the exact typed inputs and outputs?
Which parts are semantic meaning, calibration/model policy, execution and composition?
Which algorithm/process can vary independently?
Where does that replaceable seam live?
Which package/file owns each responsibility?
What evidence proves an alternate implementation can be substituted cleanly?
```

The architecture rules are:

1. **One explainable responsibility per block.** Classes, processes, records and packages should not become mixed owners of unrelated semantics.
2. **Typed boundaries over concrete dependencies.** Cross-block code depends on the narrowest semantic contract it needs. Avoid ambient mutable contexts, service-locator bags and concrete implementation knowledge in generic consumers.
3. **Replace independently meaningful mechanisms.** Algorithms, calibrators, planners, selectors, runtime processes or strategies that can change independently are injected/composed behind typed seams.
4. **Orchestrators compose only.** Composition roots choose implementations and order dependencies; they do not absorb domain formulas, tuning policy or feature-specific branches.
5. **Separate semantic meaning, calibration, model policy and execution.** One layer should not silently re-derive responsibility already owned upstream.
6. **One owner for shared policy.** A model constant or rule consumed by multiple components has one explicit versioned/policy owner rather than being copied into several implementations.
7. **Package/file structure mirrors ownership.** Names and directories must make the owner discoverable. Do not use generic dumping-ground packages as an alternative to deciding responsibility.
8. **Verify replaceability.** Important seams receive composition/substitution tests when practical.
9. **Abstract boundaries, not private details.** Use a few strong abstractions around independently meaningful components; keep internal helpers concrete until multiple real consumers prove a reusable concept.
10. **Stop on architecture friction.** If the next feature requires widening a god-object, adding concrete-type branching in generic code or passing a larger universal context, repair the smallest missing boundary before continuing.

A useful target is:

```text
clear semantic owner
      ↓
narrow typed interface
      ↓
simple concrete implementation
      ↓
explicit composition root
```

The goal is not maximum interface count. The goal is that a future compatible implementation can be swapped, combined or edited without reopening unrelated parts of the project.

## Green-checkpoint engineering contract

Production development is a sequence of **small green checkpoints**, not one large implementation followed by late debugging.

For each semantic block:

```text
one contract / invariant
        ↓
smallest independently meaningful component
        ↓
focused test / diagnostic / acceptance evidence
        ↓
focused check
        ↓
understood green checkpoint
        ↓
coherent commit
        ↓
next block
```

The rules are mandatory:

1. **Define the contract first.** State what owns the behavior, what must remain unchanged and what evidence will prove the change works. For visual work, define both machine-checkable invariants and what requires manual observation.
2. **Change one ownership boundary or behavior at a time.** Do not combine a new algorithm, a new calibration model, unrelated cleanup and presentation changes merely because they belong to one feature idea.
3. **Test at the nearest boundary.** Prefer deterministic unit/component tests for the changed contract; add integration/audit coverage only where composition is the actual risk.
4. **Run focused checks immediately.** A failing focused test is cheaper and more informative than a later repository-wide red build.
5. **Do not build on unexplained red.** When a checkpoint fails, stop adding scope. Localize the failure, fix it, revert it or move the hypothesis to an experiment branch before proceeding.
6. **Commit only coherent checkpoints.** A commit should be reviewable and revertible as one idea. Diagnostic logging, temporary probes and failed approaches do not belong in the final production history.
7. **Do not weaken evidence to fit code.** Change a test because the intended contract deliberately changed, not because the implementation cannot satisfy the existing contract.
8. **Separate experiments from production.** If diagnosis requires invasive probes or competing designs, use `experiment/*` or a temporary diagnostic branch. Transfer only the confirmed minimal fix or clean implementation back to the production branch.
9. **Escalate verification with scope.** After focused tests pass, run affected-module checks; after the coherent feature is assembled, run full CI/audits/docs checks. Manual visual/performance acceptance is an additional gate when appropriate.
10. **Split before complexity compounds.** If the commit sequence no longer explains the PR or several independent hypotheses are simultaneously in flight, stop and split/rebuild the branch instead of continuing to accumulate code.

A refactor that claims to preserve behavior must preserve the existing acceptance evidence. Prefer exposing/rearranging contracts in separate commits from semantic changes.

### Debugging rule

When something fails, first identify **which stage first produces the wrong fact**. Instrument or test that boundary directly. Do not add a downstream repair pass until the upstream output is proven correct and the repair is itself a deliberate architectural responsibility.

For deterministic generators this normally means inspecting the chain in order: semantic intent → calibration → spatial algorithm → immutable generated fact → materialization/presentation. A late visual symptom is not evidence that the presentation layer owns the defect.

## Normal feature flow

```text
update develop
    ↓
create feature/<focused-name>
    ↓
define owner + typed boundaries + replaceable seams + acceptance evidence
    ↓
place the component in the package that owns that responsibility
    ↓
implement one small component
    ↓
focused test/check → green commit
    ↓
repeat small green checkpoints
    ↓
feature-level diagnostics/manual observation where relevant
    ↓
architecture review: ownership, dependencies, replaceability, package/file clarity
    ↓
update only normative documentation whose semantics changed
    ↓
PR -> develop
    ↓
full CI + documentation build/audits as applicable
    ↓
manual acceptance where behavior/presentation requires it
    ↓
squash merge
    ↓
delete the feature branch
```

Feature branches should answer one architectural question or deliver one independently understandable slice. If a PR becomes difficult to review because several unrelated contracts changed together, split it before adding more code.

Draft PRs are preferred while semantics, visual acceptance or performance work are still active. A draft PR is a working integration record, not a promise that the current design will ship.

## Experiments and diagnostics

An experiment exists to buy knowledge, not necessarily production code.

Good uses include comparing reservation models, testing a different data representation, measuring a suspected hot path, isolating a deterministic generator defect or proving that an architecture is unsuitable.

When an experiment succeeds, prefer implementing the learned design cleanly on a production `feature/*` branch unless the experiment itself already meets normal production quality. When it fails, record any durable lesson in a decision or Development Journal note and delete the branch. Do not merge a dead end merely to preserve the work.

A temporary diagnostic branch may intentionally contain probes that production code should not. Once the cause is known, restore the production branch to its last understood checkpoint and transfer only the minimal verified correction. Do not drag diagnostic history into the final PR merely because it was useful during investigation.

## Pull request shape

A healthy feature PR should be understandable from its commit sequence and package structure. A typical non-trivial PR may look like:

1. semantic contract/model;
2. one replaceable implementation component;
3. composition/integration wiring;
4. focused tests or acceptance guards that could not live with an earlier component;
5. documentation/process reconciliation.

This is not a required file-count template. The invariant is that every commit is a coherent, green checkpoint and later commits do not hide unresolved defects from earlier ones.

Before final acceptance, remove temporary diagnostics and dead/superseded code, review the whole diff for ownership leaks, verify that replaceable boundaries are actually consumed through interfaces/contracts, check that package/file names still communicate responsibility, run the full required checks and reconcile documentation with the actual final behavior.

## Milestone flow

`develop` moves to `main` only when the whole milestone is accepted.

Before opening the milestone PR:

1. merge or deliberately close all slices required by the milestone;
2. run the full repository test suite;
3. build the documentation site;
4. reconcile `roadmap.md` and every normative system page touched by the milestone with current code/tests;
5. perform required desktop/manual acceptance and representative profiling;
6. make sure no known correctness issue is being hidden behind a future TODO;
7. perform a final architecture review for ownership, dependency direction, replaceability and package/file clarity.

Then open one `develop -> main` PR. The preferred merge is a **merge commit** for this boundary: feature PRs were already squashed into coherent commits on `develop`, while the merge commit preserves ancestry and gives `main` an explicit milestone boundary without making `develop` diverge from it.

After merge, tag the resulting `main` commit with the accepted release/milestone version. During pre-1.0 development use semantic pre-release versions such as `v0.1.0-alpha.1`; advance the version deliberately rather than deriving it from commit count.

`git log --first-parent main` should therefore read as the sequence of accepted project milestones, while `develop` retains the coherent feature commits that formed each milestone.

## Protection and merge policy

`main` and `develop` are protected integration branches:

- no routine direct pushes;
- changes enter through pull requests;
- required CI must pass;
- stale review/acceptance is repeated when the branch meaningfully changes;
- force pushes and branch deletion are disabled;
- completed `feature/*` and `experiment/*` branches are deleted when no longer useful.

Repository settings should allow squash merging for ordinary feature PRs and merge commits for `develop -> main`. Do not enable a linear-history rule on `main` if it would forbid the explicit milestone merge commit.

For this single-maintainer project, branch protection is still valuable: it protects the repository from accidental local state and makes every integration point reproducible.

## Recovery rules

A failed feature should usually be abandoned before it reaches `develop`. If a bad slice has already been squash-merged to `develop`, revert that coherent squash commit or replace it with a correcting PR; do not rewrite shared integration history.

`main` is the recovery anchor. Do not "temporarily" merge unfinished work there because it seems close to done. A long experiment can fail completely without making the last accepted milestone harder to recover.

Release tags are immutable historical anchors. Never move an existing version tag to a different commit.

## Documentation rule

An implementation-only refactor does not require editing system documentation when semantics are unchanged. A new subsystem normally adds one system page. `architecture.md` changes only for deliberate cross-system rules. Durable rationale belongs in `decisions/`; exploratory history belongs in `notes/`.

Normative documentation is part of milestone acceptance. Historical Development Journal notes are not rewritten to pretend earlier uncertainty never existed.

See [Documentation Guide](documentation.md), [Development Branching Model](../decisions/005-development-branching-model.md), [Green Checkpoint Development](../decisions/022-green-checkpoint-development.md) and [Strict Modular Architecture](../decisions/023-strict-modular-architecture.md).

## Performance

When a regression appears, add or inspect lightweight telemetry, reproduce it and optimize the measured hot path behind existing contracts. Keep the measurement when it remains useful as a development guard. Performance work follows the same checkpoint and acceptance rules as semantic work.
