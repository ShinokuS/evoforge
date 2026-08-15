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

## Normal feature flow

```text
update develop
    ↓
create feature/<focused-name>
    ↓
discuss semantic ownership and contract
    ↓
implementation + headless tests
    ↓
diagnostics/manual observation where relevant
    ↓
update only the normative documentation whose semantics changed
    ↓
PR -> develop
    ↓
CI + documentation build
    ↓
manual acceptance where behavior/presentation requires it
    ↓
squash merge
    ↓
delete the feature branch
```

Feature branches should answer one architectural question or deliver one independently understandable slice. If a PR becomes difficult to review because several unrelated contracts changed together, split it before adding more code.

Draft PRs are preferred while semantics, visual acceptance or performance work are still active. A draft PR is a working integration record, not a promise that the current design will ship.

## Experiments

An experiment exists to buy knowledge, not necessarily production code.

Good uses include comparing reservation models, testing a different data representation, measuring a suspected hot path or proving that an architecture is unsuitable.

When an experiment succeeds, prefer implementing the learned design cleanly on a production `feature/*` branch unless the experiment itself already meets normal production quality. When it fails, record any durable lesson in a decision or Development Journal note and delete the branch. Do not merge a dead end merely to preserve the work; Git history already preserves committed investigation while the branch exists, and important conclusions belong in documentation.

## Milestone flow

`develop` moves to `main` only when the whole milestone is accepted.

Before opening the milestone PR:

1. merge or deliberately close all slices required by the milestone;
2. run the full repository test suite;
3. build the documentation site;
4. reconcile `roadmap.md` and every normative system page touched by the milestone with current code/tests;
5. perform required desktop/manual acceptance and representative profiling;
6. make sure no known correctness issue is being hidden behind a future TODO.

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

See [Documentation Guide](documentation.md) and [Development Branching Model](../decisions/005-development-branching-model.md).

## Performance

When a regression appears, add or inspect lightweight telemetry, reproduce it and optimize the measured hot path behind existing contracts. Keep the measurement when it remains useful as a development guard. Performance work follows the same branch and acceptance rules as semantic work.
