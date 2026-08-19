# Documentation should not tax every feature

- Type: Entry
- Status: Historical record
- Date: 2026-08-12
- Normative: No

## Context

The first EvoForge documentation setup deliberately used several synchronized surfaces: normative architecture, a technical reference, many explanatory pages, a complete Russian mirror with freshness tracking, VitePress and generated GitHub Wiki content.

That strictness caught some drift, but eventually documentation maintenance itself became a source of mistakes. CI could prove a translation matched an English file while another authoritative-looking page still described an already completed milestone as active.

## What was observed

The problem was not lack of documentation; it was **duplicate ownership of meaning**.

Keeping the same semantic truth in several trees meant every feature required unrelated synchronization work. The larger the project became, the easier it was for one copy to be technically “fresh” but semantically wrong.

## Outcome

The project moved toward one canonical repository documentation tree and one generated reading surface (VitePress). The old standalone technical-reference/Wiki/translation-mirror model was removed.

The desired editing cost became:

```text
implement + test
update the owning System page
update Roadmap only if milestone state changed
record durable rationale only when an ADR is justified
```

rather than editing many parallel copies.

## What became canonical

Stage 0 later made this principle more explicit:

```text
Project Context     current-state recovery
Architecture        only global cross-system laws
Systems             current subsystem semantics/algorithms
Decisions           durable rationale
Guides              contributor procedures
Development Journal historical/exploratory/acceptance/audit context
References          reusable external sources
```

The Journal remains public because old reasoning is useful, but it is explicitly non-normative.

## Links forward

- [Documentation Guide](../../guides/documentation.md)
- [Project Context](../../project-context.md)
- [Architecture](../../architecture.md)
- [Systems](../../systems/)
