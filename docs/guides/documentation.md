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

Code, Javadoc and tests are the implementation reference. Do not mirror package trees, class counts or every internal helper in Markdown.

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

Edit that subsystem’s page. If the change also revises a cross-system invariant, explicitly edit `architecture.md`.

### Implementation-only refactor

No semantic docs change is required unless developer operation/diagnostics also changed.

### Important rationale

Add a decision record when future maintainers need to know why an architectural choice exists or which alternatives were deliberately rejected.

### Exploration / lessons

Add a dated Development Journal note. Notes are allowed to become historically outdated because they describe thinking at a point in time. Link to later decisions when useful rather than rewriting history.

## Adding a page

Create a Markdown file in the appropriate directory. VitePress discovers section pages from the filesystem; no sidebar list needs manual synchronization.

Use a clear `# Heading`. For journal entries, use a date-prefixed filename (`YYYY-MM-DD-topic.md`) so chronology is stable.

## Publication

Repository Markdown is canonical. VitePress/GitHub Pages is the only generated publication target. There is no translation mirror and no GitHub Wiki synchronization pipeline.
