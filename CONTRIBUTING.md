# Contributing to EvoForge

EvoForge keeps `main` as an accepted milestone baseline and integrates the next milestone on `develop`.

Before changing simulation semantics, read [`docs/architecture.md`](docs/architecture.md) and the relevant page under [`docs/systems/`](docs/systems/). Durable architecture/process choices belong in [`docs/decisions/`](docs/decisions/); exploratory history belongs in [`docs/notes/`](docs/notes/).

## Branches

- normal production work: branch `feature/<focused-name>` from the latest `develop` and open a PR back to `develop`;
- uncertain/disposable research: use `experiment/<focused-name>` from `develop`;
- urgent stable-baseline repair: use `hotfix/<focused-name>` from `main`, then bring the accepted fix back into `develop`;
- `develop -> main` is reserved for an accepted milestone.

Do not routinely push directly to `main` or `develop`.

## Mandatory small-step development loop

Every production change proceeds through green, reviewable checkpoints:

1. state one semantic/architectural contract and how it will be verified;
2. change the smallest independently meaningful component that advances that contract;
3. add or update the focused test, diagnostic or other acceptance evidence for that component;
4. run the narrowest relevant checks immediately;
5. commit only when that checkpoint is understood and green;
6. only then begin the next semantic block.

Do **not** stack a new hypothesis on an unexplained red head. If a checkpoint fails, stop adding scope, localize the failure and fix or revert that checkpoint first. Diagnostic instrumentation or risky experiments belong on a disposable branch when they would pollute the production history; only the confirmed production fix is transferred back.

A test is changed because the intended contract changed, never merely because the implementation fails it. Visual/performance acceptance complements automated invariants; it does not replace them.

See [ADR-022](docs/decisions/022-green-checkpoint-development.md) and the detailed [Development Workflow](docs/guides/development-workflow.md).

## Mandatory architecture discipline

Every non-trivial production change must also preserve EvoForge's strict modular architecture:

1. identify the authoritative owner of the new fact/behavior before coding;
2. keep each class/process/package focused on one explainable responsibility;
3. depend across blocks on narrow typed contracts rather than concrete implementation classes or generic mutable contexts;
4. expose an explicit replaceable seam when an algorithm, calibrator, process or strategy can vary independently;
5. keep composition/orchestration separate from domain policy and algorithm mathematics;
6. keep semantic meaning, calibration, versioned model policy and execution separate when they are independently meaningful concerns;
7. make package and file structure communicate domain ownership clearly instead of accumulating unrelated code in generic `util`, `manager`, `service`, `common` or giant stage classes;
8. verify important replaceability boundaries with substitution/composition tests;
9. abstract real semantic boundaries strongly, but keep private implementation detail simple and concrete until another real consumer proves a common abstraction;
10. if adding a feature requires widening a confused owner or branching generic code on a replaceable concrete type, stop and repair the boundary before adding more behavior.

A compatible implementation should be replaceable without editing unrelated downstream consumers. Clear code organization is part of correctness because future development must be able to locate, reason about, replace and test one block without reconstructing historical context.

See [ADR-023](docs/decisions/023-strict-modular-architecture.md), [Architecture](docs/architecture.md) and the detailed [Development Workflow](docs/guides/development-workflow.md).

## Before a feature PR is merged

- keep the change focused on one coherent slice and the commit sequence readable as independent checkpoints;
- review package/file placement and verify each changed block has one clear owner/responsibility;
- verify replaceable algorithms/processes are consumed through contracts rather than concrete-class knowledge;
- add/update headless tests for semantic changes and architecture/composition tests where replaceability is part of the contract;
- run `./gradlew test --rerun-tasks --console=plain` for cross-module work;
- update normative documentation when semantics or developer operation changed;
- run `npm run docs:build` when documentation/site configuration changed;
- perform manual visual/performance acceptance where automated tests cannot establish the result;
- remove temporary diagnostics, dead code and superseded experimental paths;
- do not merge with unexplained failing checks or TODOs hiding known correctness defects.

Feature PRs are normally squash-merged to `develop`. Experiments do not have to be merged.

## Milestones

A milestone PR from `develop` to `main` requires green CI, reconciled normative documentation and the required manual/performance acceptance. The preferred merge is an explicit merge commit so `develop` remains an ancestor of the accepted `main` milestone. Tag the resulting `main` commit with an immutable semantic pre-release/release version.

The detailed policy is canonical in [`docs/guides/development-workflow.md`](docs/guides/development-workflow.md).
