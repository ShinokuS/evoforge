---
layout: home

title: EvoForge Documentation

titleTemplate: false

hero:
  name: EvoForge
  text: Simulation architecture and development journal
  tagline: Canonical contracts, current system semantics, decisions, workflow, and the history behind them.
  actions:
    - theme: brand
      text: Architecture
      link: /architecture
    - theme: alt
      text: Systems
      link: /systems/runtime
    - theme: alt
      text: Workflow
      link: /guides/development-workflow
    - theme: alt
      text: Development Journal
      link: /notes/

features:
  - title: Architecture
    details: Small global contract containing only rules that span systems or constrain future extension.
  - title: Systems
    details: Canonical current semantics for implemented subsystems, kept separate from historical design notes.
  - title: Development Workflow
    details: Stable main, integration develop, focused feature branches, disposable experiments and tagged milestone releases.
  - title: Decisions
    details: Durable explanations of important architectural and project-process choices.
  - title: Development Journal
    details: Non-normative notes, experiments, lessons and open thoughts preserved as project history.
---

## Documentation model

EvoForge documentation is intentionally low-friction:

```text
architecture.md     global cross-system rules
roadmap.md          milestone status and intentionally deferred work
systems/            current semantic truth, one page per subsystem
decisions/          why durable architectural/process choices were made
guides/             practical development recipes and workflow
notes/              non-normative Development Journal
```

Implementation details that are obvious from source code, package listings or exact class counts are not duplicated here. Code, Javadoc and tests are the implementation reference.

Normative documentation is reconciled with production code/tests before `develop` is promoted to a tagged `main` milestone.
