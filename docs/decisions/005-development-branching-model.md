# Development Branching Model

## Context

EvoForge now contains several interacting simulation subsystems and development milestones can span many commits, tests and manual acceptance passes.

Using only `main` for both stable work and long-running experiments makes three things unnecessarily expensive:

- abandoning an architectural direction that turns out to be wrong;
- identifying the last fully accepted recovery point;
- separating incomplete integration work from a milestone that is safe to reproduce.

A full GitFlow model with permanent release branches would add process that the current single-maintainer project does not yet need.

## Decision

EvoForge uses a lightweight integration model:

```text
main       stable accepted milestones
  ↑
develop    integration for the next milestone
  ↑
feature/*  focused production slices
experiment/* disposable investigations
```

The rules are:

1. `main` contains accepted milestone states only.
2. `develop` is the long-lived integration branch for the next milestone.
3. Normal production work branches from `develop` as a small `feature/*` branch and returns through a PR.
4. Feature PRs are squash-merged into `develop` so an integrated slice has one coherent commit even when its working history was noisy.
5. Uncertain architecture may use `experiment/*`; experiments are allowed to be discarded without merge.
6. A completed milestone moves from `develop` to `main` only after automated checks, documentation reconciliation and required manual/performance acceptance.
7. The preferred `develop -> main` merge is an explicit merge commit. This preserves branch ancestry while giving `main` a clear first-parent milestone boundary.
8. The accepted `main` commit receives an immutable semantic-version tag.
9. `release/*` is introduced only when release stabilization must happen in parallel with later development.
10. An urgent correction to the stable baseline may use `hotfix/*` from `main`; the accepted correction must also reach `develop`.

## Consequences

Benefits:

- an unsuccessful feature or experiment can be abandoned without contaminating the stable baseline;
- `main` remains a reliable recovery and reproduction point;
- `develop` can integrate several coherent slices before they are collectively declared a milestone;
- pull requests preserve implementation discussion while stable branch history stays readable;
- version tags identify immutable historical baselines.

Costs:

- branches must be kept intentionally scoped and deleted when obsolete;
- a hotfix to `main` must be reconciled back into `develop`;
- repository protection settings must permit milestone merge commits while still preventing routine direct pushes;
- milestone acceptance now explicitly includes documentation and manual validation instead of treating a green unit-test run as sufficient by itself.

This is a development-process decision. It does not change simulation authority, determinism or runtime semantics.

See [Development Workflow](../guides/development-workflow.md).
