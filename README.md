# EvoForge

EvoForge is a deterministic emergent-simulation project built with Java 21 and libGDX.

The simulation architecture lives in the pure-Java `simulation` module. libGDX modules are presentation/launcher layers and must not become owners of authoritative simulation state.

## Architecture

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — stable semantic contracts, invariants and deferred architectural decisions.
- [`docs/TECHNICAL_REFERENCE.md`](docs/TECHNICAL_REFERENCE.md) — current implementation, packages, algorithms, tests and known technical gaps.

## Modules

- `simulation` — domain and simulation code, headless-testable and independent of libGDX.
- `core` — shared libGDX application/presentation layer.
- `lwjgl3` — desktop launcher.
- `assets` — definitions and presentation assets.

## Tests

Run the simulation suite:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

## Gradle

The project uses the included Gradle wrapper. Avoid routine `clean`; normal incremental test/build tasks are preferred unless a clean build is specifically required.
