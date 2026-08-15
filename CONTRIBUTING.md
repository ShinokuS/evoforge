# Contributing to EvoForge

EvoForge keeps `main` as an accepted milestone baseline and integrates the next milestone on `develop`.

Before changing simulation semantics, read [`docs/architecture.md`](docs/architecture.md) and the relevant page under [`docs/systems/`](docs/systems/). Durable architecture/process choices belong in [`docs/decisions/`](docs/decisions/); exploratory history belongs in [`docs/notes/`](docs/notes/).

## Branches

- normal production work: branch `feature/<focused-name>` from the latest `develop` and open a PR back to `develop`;
- uncertain/disposable research: use `experiment/<focused-name>` from `develop`;
- urgent stable-baseline repair: use `hotfix/<focused-name>` from `main`, then bring the accepted fix back into `develop`;
- `develop -> main` is reserved for an accepted milestone.

Do not routinely push directly to `main` or `develop`.

## Before a feature PR is merged

- keep the change focused on one coherent slice;
- add/update headless tests for semantic changes;
- run `./gradlew test --rerun-tasks --console=plain` for cross-module work;
- update normative documentation when semantics or developer operation changed;
- run `npm run docs:build` when documentation/site configuration changed;
- perform manual visual/performance acceptance where automated tests cannot establish the result.

Feature PRs are normally squash-merged to `develop`. Experiments do not have to be merged.

## Milestones

A milestone PR from `develop` to `main` requires green CI, reconciled normative documentation and the required manual/performance acceptance. The preferred merge is an explicit merge commit so `develop` remains an ancestor of the accepted `main` milestone. Tag the resulting `main` commit with an immutable semantic pre-release/release version.

The detailed policy is canonical in [`docs/guides/development-workflow.md`](docs/guides/development-workflow.md).
