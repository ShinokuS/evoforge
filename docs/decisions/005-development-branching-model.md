# ADR-005: Lightweight development branching model

- Status: Accepted
- Scope: Repository workflow
- Decision: `main` is the accepted milestone line, `develop` is the next-milestone integration line, and focused feature/stage work returns to `develop` through pull requests.

## Context

EvoForge milestones span interacting systems, tests, documentation and manual acceptance. Using one branch for stable work and long-running experiments makes rollback, recovery-point identification and incomplete integration unnecessarily risky. Full GitFlow would add process the current project does not need.

## Decision

The intended branch roles are:

```text
main        accepted milestone baselines
  ↑
develop     integration for the next milestone
  ↑
feature/*   focused production work
experiment/* disposable investigation
```

Normal work branches from `develop`, stays scoped, and returns through a PR. Uncertain work may live on `experiment/*` and be discarded. A completed milestone is promoted from `develop` to `main` only after automated checks, documentation reconciliation and required manual/performance acceptance. Accepted stable milestones receive immutable version tags. `release/*` is introduced only when parallel stabilization is genuinely needed; urgent stable corrections may use `hotfix/*` and must be reconciled back to `develop`.

Repository-supported merge methods are operational configuration, not simulation semantics. The important invariant is a reviewable PR boundary and a readable accepted integration history; do not hard-code a merge method in documentation when repository policy differs.

## Why

The model creates cheap experimental rollback and clear stable recovery points without maintaining permanent release machinery prematurely.

## Consequences

- `main` remains a reliable accepted baseline.
- `develop` can contain coherent next-milestone integration before stable promotion.
- Feature/stage PRs preserve review/CI/acceptance evidence.
- Obsolete branches should be deleted rather than becoming parallel histories.
- Stable hotfixes must also reach `develop`.

## Alternatives considered

Direct development on `main` was rejected because incomplete architectural work would contaminate the stable recovery line. Full GitFlow was rejected as unnecessary current process overhead.

## Current implementation

Current Stage 0 work was integrated through PRs into `develop`. Repository settings currently allow squash/rebase-style PR integration while direct protected-line workflow remains intentionally explicit. Stage work continues to use focused branches/PRs; milestone promotion to `main` should follow the repository's actual protection/merge capabilities at that time rather than an obsolete hard-coded merge-method assumption.

## Related documentation

- [Development Workflow](../guides/development-workflow.md)
- [Roadmap](../roadmap.md)
- [Documentation Guide](../guides/documentation.md)
