# 2026-08-12 — Documentation should not tax every feature

**Status:** Historical development note

The first documentation system was intentionally strict: a normative architecture file, a current technical reference, many explanatory Wiki pages, a full Russian mirror with blob-SHA freshness tracking, VitePress publication and a generated GitHub Wiki.

It successfully prevented some forms of silent translation drift, but the project reached a point where updating documentation required enough synchronized edits that the process itself became a source of mistakes.

The clearest signal was semantic duplication: CI could prove that an English page and its Russian translation were synchronized while another authoritative-looking page could still describe an already completed visualizer milestone as active.

## What changed

We decided that correctness comes primarily from **single ownership of meaning**, not from more synchronization machinery.

The new model uses one English canonical tree and one publication target (VitePress). Each subsystem owns one page. Global Architecture only owns global rules. Decisions preserve rationale. The Development Journal preserves thoughts that matter but are not contracts.

`TECHNICAL_REFERENCE.md` was removed because exact implementation structure is better represented by source, Javadoc and tests. GitHub Wiki and the Russian translation mirror were removed because VitePress already served the reading/navigation need with much lower maintenance cost.

## Desired editing experience

Adding a future subsystem should usually mean:

```text
implement + test
create systems/new-system.md
update roadmap status if useful
```

It should **not** require touching Movement, Navigation or other completed pages unless their own semantics actually changed.

The journal is intentionally part of the public VitePress site. It gives future work — human or AI-assisted — access to the evolution of project thinking without allowing old thoughts to masquerade as current architecture.
