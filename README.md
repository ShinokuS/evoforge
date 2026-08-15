# EvoForge

EvoForge is a deterministic headless simulation engine with a thin libGDX desktop visualizer for development and acceptance work.

## Current baseline

The accepted baseline currently includes:

- sparse multi-Z terrain with Shape-aware Geometry;
- exact pathfinding and MoveTo over the authoritative navigation/traversal model;
- deterministic person needs, knowledge, search, use-actions and scheduled world processes;
- finite Surface Water with Geometry-aware storage, Z flow, Soil infiltration, precipitation and evaporation;
- a scenario-driven Surface/Interior/Debug-Slice visualizer with contextual interaction and diagnostics.

The current development sequence is tracked in [Roadmap](docs/roadmap.md).

## Repository layout

```text
simulation/   deterministic simulation model and tests
core/         libGDX visualizer
lwjgl3/       desktop launcher
assets/       definition and presentation assets
docs/         normative architecture/system docs and development journal
```

## Build and test

Java 21 is required.

```bash
./gradlew test
```

Run the desktop visualizer:

```bash
./gradlew lwjgl3:run
```

Build the documentation site:

```bash
npm ci
npm run docs:build
```

## Development model

Routine work does not go directly to `main`.

```text
main
  accepted, green milestones only

develop
  integration branch for the next milestone

feature/*
  focused production slices -> PR -> develop

experiment/*
  disposable spikes; no promise of integration

hotfix/*
  urgent recovery from main
```

Start feature work from `develop`, merge it through a pull request, prefer squash merges into `develop`, and delete the topic branch afterwards. A milestone reaches `main` only after repository tests, documentation build, normative documentation reconciliation and relevant manual/performance acceptance are complete. The preferred milestone merge is an explicit merge commit so `main` remains a readable sequence of accepted baselines.

See [Development Workflow](docs/guides/development-workflow.md) and [Decision 005](docs/decisions/005-development-branching-model.md).

## Documentation

- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Documentation guide](docs/guides/documentation.md)
- [Development workflow](docs/guides/development-workflow.md)
- [Visualizer controls](docs/guides/visualizer.md)
- [System documentation](docs/systems)
- [Architecture decisions](docs/decisions)
- [Development journal](docs/notes)
