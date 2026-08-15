# Documentation Guide

The goal of EvoForge documentation is to reduce development friction, not create another system that must be synchronized with the code file-by-file.

## One thought, one canonical owner

```text
architecture.md  global rules shared across systems
roadmap.md       milestone state / intentionally deferred topics
systems/*        current semantic truth for one subsystem
decisions/*      durable rationale for important accepted choices
guides/*         practical recipes
notes/*          non-normative historical/exploratory context
```

Code, Javadoc and tests are the implementation reference. Markdown describes semantic ownership, public contracts, important lifecycle/invariants, current integration boundaries and intentionally deferred scope. Do not mirror package trees, class counts or every internal helper.

## When to edit documentation

### New subsystem

Normally:

```text
code + tests
new systems/<name>.md
roadmap status if relevant
```

Do not touch completed system pages merely because the new subsystem consumes them.

### Existing subsystem semantics changed

Edit that subsystem's page. If the change also revises a cross-system invariant, explicitly edit `architecture.md`.

### Developer operation changed

Update the relevant guide when the way contributors build, test, branch, release, diagnose or manually accept the project changes. Operational documentation is still normative even when simulation semantics are unchanged.

### Implementation-only refactor

No semantic docs change is required unless developer operation/diagnostics also changed.

### Important rationale

Add a decision record when future maintainers need to know why an architectural or project-process choice exists or which alternatives were deliberately rejected.

### Exploration / lessons

Add a dated Development Journal note. Notes are allowed to become historically outdated because they describe thinking at a point in time. Link to later decisions when useful rather than rewriting history.

## Milestone documentation audit

Documentation is part of the `develop -> main` acceptance boundary.

Before a milestone is merged to `main`:

1. compare `roadmap.md` with the actual completed/deferred state;
2. review every normative System page whose contract changed during the milestone;
3. review `architecture.md` only for genuine cross-system rules introduced by the milestone;
4. verify practical guides/controls still match the current tooling and UI;
5. confirm new durable design choices have decision records when their rationale would otherwise be lost;
6. build the VitePress site so broken internal links/configuration fail CI;
7. leave historical `notes/*` intact unless a small forward-link to the final decision materially improves navigation.

A green docs build proves syntax/link/build integrity, not semantic freshness. The semantic audit still compares prose with production code/tests.

## Branch/release relationship

Normative docs normally travel in the same `feature/* -> develop` PR as the semantic change they describe. This keeps the integration branch internally understandable rather than postponing all documentation until release day.

The milestone audit then reconciles accumulated `develop` semantics before `main` is tagged. The published GitHub Pages site deploys from `main`; `develop` documentation is built by CI but is not the canonical published release view.

## Adding a page

Create a Markdown file in the appropriate directory. VitePress discovers section pages from the filesystem; no section sidebar list needs manual synchronization.

Use a clear `# Heading`. For journal entries, use a date-prefixed filename (`YYYY-MM-DD-topic.md`) so chronology is stable.

## Publication

Repository Markdown is canonical. VitePress/GitHub Pages is the generated publication target. There is no translation mirror and no GitHub Wiki synchronization pipeline.

See [Development Workflow](development-workflow.md).
