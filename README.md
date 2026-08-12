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
