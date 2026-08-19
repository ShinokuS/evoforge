# ADR-020: Terrain authoring hides generated complexity

- Status: Accepted
- Scope: Generated Terrain material authoring
- Decision: Content authors choose compact semantic generation intent and material roles; causal spatial/layer placement and technical thresholds remain inside replaceable generation/calibration algorithms.

## Context

Generated Terrain needs richer composition than one uniform material, but two easy approaches are both harmful: hard-code material names/conditions in Java, or expose the entire geomorphology rule tree/threshold vocabulary in authored JSON.

The original document called the authored composition a “Terrain palette”. The current concrete representation has evolved, but the ownership principle remains.

## Decision

Responsibilities are separated:

```text
Landscape material definition
  runtime material behavior

Terrain preset/capability
  reusable semantic generation intent

Terrain profile + material set
  compact authored composition / role bindings

TerrainMaterialGenerator
  actual causal spatial/depth synthesis
```

Preset capabilities compose explicitly; conflicting ownership of the same generation capability is rejected rather than resolved by array order. Current roles include surface, subsurface, sediment and bedrock. Generated identity uses stable semantic keys, resolved to runtime IDs only during materialization.

Causal placement uses generated facts such as morphology, drainage, geology and surface hydrology. Technical slope/deposition/depth constants are algorithm policy, not required author inputs.

## Why

Authoring surface area should be proportional to creative intent, not simulation complexity. This allows algorithm sophistication to grow without forcing content authors to reproduce implementation details.

## Consequences

- Ordinary material/content changes remain data-oriented.
- Generator internals can evolve/version independently from authored semantic roles.
- Generic code avoids `if material == ...`/absolute-Z special cases.
- Multiple preset capabilities compose without order-based hidden override semantics.
- Final Stage 5 surface synthesis can replace the provisional current material algorithm behind the same ownership direction.

## Alternatives considered

Material-specific Java branches, universal absolute-elevation bands, large authored `fit/layers` condition trees, biome-as-direct-material-painter and a generic rule DSL were rejected/deferred.

## Current implementation

The former palette concept is currently represented by `TerrainProfileDefinition` + `TerrainMaterialSetDefinition`, compiled by `TerrainProfileCompiler` into `CompiledTerrainProfile`. `TerrainPresetCatalog`/capabilities currently include ground profile and surface deposition. `TerrainMaterialGenerationStage` uses morphology/drainage/geology/surface-hydrology facts but is explicitly provisional until Stage 5; do not extend it with permanent `river -> sand` or `mountain -> granite` shortcuts.

## Related documentation

- [Terrain Generation](../systems/world-generation/terrain-generation.md)
- [World Materialization](../systems/world-generation/world-materialization.md)
- [Definitions](../systems/foundations/definitions.md)
- [World Generation](../systems/world-generation/overview.md)
