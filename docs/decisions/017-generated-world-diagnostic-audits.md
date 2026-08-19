# ADR-017: Generated-world diagnostics are deterministic audit snapshots

- Status: Accepted
- Scope: Generated-world observability
- Decision: Generated-world verification uses immutable structured snapshots captured on demand from authoritative generated/runtime read capabilities; logging is only a presentation of those facts.

## Context

Generation, Terrain materialization and runtime hydrology must be exercised together. Manual visual inspection alone cannot prove deterministic invariants, while test-only metrics would give desktop failures a different diagnostic vocabulary. Logging cannot become authoritative state or a correctness database.

## Decision

`GeneratedWorldDiagnosticsProbe` captures an immutable checkpoint snapshot from `WorldAtlas` plus runtime read capabilities. `GeneratedTerrainMaterialDiagnosticsProbe` similarly observes generated/prepared material composition.

The same typed records can be asserted in CI or formatted/logged for human inspection. Capture is explicit and may scan the finite world; it is never a per-tick scheduled simulation process and cannot mutate state.

Wall-clock/rendering facts are excluded from deterministic snapshots. Viability/balance verdicts are separate future evaluator policy, not diagnostic facts.

## Why

One diagnostic vocabulary improves replay/debugging without turning logs or tools into another world model.

## Consequences

- CI can compare exact replay snapshots.
- Developers can inspect the same facts in audit logs.
- Full scans happen only at deliberate checkpoints.
- Adding/removing logging cannot affect simulation outcomes.
- Evaluators may later consume snapshots without being embedded in the probes.

## Alternatives considered

Per-tick INFO world dumps, parsing log text in tests, a test-only diagnostic model and built-in “healthy world” verdicts were rejected.

## Current implementation

Runtime diagnostics now cover provenance, Terrain/surface agreement, drainage/geology summaries and free/retained Water facts. Terrain-material diagnostics separately report generated material composition with stable semantic keys. The Generated World Audit workflow runs the same structured probes through ordinary generated-world runtime paths.

## Related documentation

- [Generated World Diagnostics](../systems/tooling/generated-world-diagnostics.md)
- [Generated World Warm-up](../systems/tooling/generated-world-warmup.md)
- [World Atlas](../systems/world-generation/world-atlas.md)
