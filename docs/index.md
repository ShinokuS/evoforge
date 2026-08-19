---
layout: home

title: EvoForge Documentation

titleTemplate: false

hero:
  name: EvoForge
  text: A deterministic world simulation, explained from first principles
  tagline: Start with the human-readable model, then drill down to exact algorithms, formulas, code ownership, tests and architectural decisions.
  actions:
    - theme: brand
      text: Project Context
      link: /project-context
    - theme: alt
      text: Systems
      link: /systems/
    - theme: alt
      text: Architecture
      link: /architecture
    - theme: alt
      text: Roadmap
      link: /roadmap

features:
  - title: Start without programming knowledge
    details: Every normative system page begins with what the mechanic means in the simulated world before introducing code-level terms.
  - title: Exact when precision matters
    details: Algorithms, equations, units, deterministic rules, invariants, current limitations and tests are documented after the plain-language explanation.
  - title: Know why a rule exists
    details: Architecture Decision Records preserve durable reasoning separately from current system behavior.
  - title: Recover the project quickly
    details: Project Context records the current baseline, protected assumptions, provisional components and the next accepted development direction.
  - title: Historical work stays historical
    details: The Development Journal preserves experiments, audits and acceptance records without being allowed to override current code or normative documentation.
  - title: Sources are explicit
    details: Published algorithms and scientific models are cited; project-specific algorithms are clearly labeled as EvoForge designs rather than given misleading external authority.
---

## How the documentation is organized

```text
project-context.md     fastest current-state/context recovery
architecture.md        global rules every subsystem must obey
roadmap.md             completed work, next milestones and deferred scope
systems/               current behavior and algorithms, grouped by domain
decisions/             durable Architecture Decision Records (ADRs)
guides/                practical development/testing/documentation recipes
journal/               historical entries, design explorations, audits, acceptance records
references.md          external algorithm/model bibliography and source policy
```

## Which page should I read?

If you are new to EvoForge, read [Project Context](project-context.md) first. It explains the project without assuming familiarity with the codebase.

If you want to understand **what a mechanic currently does**, read [Systems](systems/). If you want to know **why an architectural rule was chosen**, read [Decisions](decisions/). If you need to perform a development task, use [Guides](guides/documentation.md). If you need historical reasoning, an old experiment or a manual visual-acceptance record, use the [Development Journal](journal/).

## What is authoritative?

Documentation is intentionally layered:

```text
production code + executable tests
        ↓
current Architecture / Systems / Guides / Roadmap
        ↓
accepted ADRs
        ↓
Development Journal
        ↓
chat history / discarded prototypes
```

The code and tests are the exact implementation reference. Normative Markdown explains the model, semantics, equations, ownership and intent in a form that can be understood without reading Java first.

A green documentation build proves links and syntax are valid. It does not by itself prove that prose matches the simulation; semantic reconciliation against production code/tests remains part of milestone acceptance.
