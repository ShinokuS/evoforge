# EvoForge

EvoForge is a deterministic emergent-simulation project built with Java 21 and libGDX.

The authoritative simulation lives in the pure-Java `simulation` module. libGDX modules are presentation/launcher layers and must not become owners of simulation state.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — global cross-system architecture contract.
- [`docs/roadmap.md`](docs/roadmap.md) — milestone status and intentionally deferred work.
- [`docs/systems/`](docs/systems/) — one canonical semantic page per implemented subsystem.
- [`docs/decisions/`](docs/decisions/) — durable architectural rationale.
- [`docs/guides/`](docs/guides/) — practical development recipes.
- [`docs/notes/`](docs/notes/) — non-normative Development Journal.

The same Markdown is published through VitePress/GitHub Pages. There is no parallel translation or Wiki source tree.

## Development

EvoForge uses a lightweight integration workflow:

- `main` — stable, accepted milestone baseline;
- `develop` — integration branch for the next milestone;
- `feature/*` — focused production slices branched from `develop`;
- `experiment/*` — disposable investigations that do not have to be merged.

Feature work returns to `develop` through pull requests and CI. A completed milestone moves from `develop` to `main` only after automated checks, documentation reconciliation and required manual acceptance; accepted `main` milestones are marked by immutable version tags.

See [`docs/guides/development-workflow.md`](docs/guides/development-workflow.md) for the full branch, merge, release and recovery policy.

## Modules

- `simulation` — domain and simulation code, headless-testable and independent of libGDX.
- `core` — shared libGDX application/presentation layer.
- `lwjgl3` — desktop launcher.
- `assets` — definitions and presentation assets.

## Tests

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Run the full repository test suite when changing cross-module code:

```bash
./gradlew test --rerun-tasks --console=plain
```

Documentation:

```bash
npm ci
npm run docs:build
```

The project uses the included Gradle wrapper. Avoid routine `clean`; normal incremental test/build tasks are preferred unless a clean build is specifically required.
