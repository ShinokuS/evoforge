# Decision 020: Terrain palettes hide generated complexity

## Status

Accepted.

## Context

Generated Terrain now needs material composition richer than the temporary uniform-material bridge. The immediate target is a familiar natural layering:

```text
topsoil / soil / bedrock
```

plus exposed rock and sand in plausible accumulation locations.

A naive implementation can fail in two opposite ways:

1. hard-code content identities and placement rules in Java (`if height < N -> sand`);
2. move the same condition tree into authored JSON and force content authors to understand slope, deposition, erosion and layer-depth coefficients.

Both approaches couple ordinary content authoring to generator implementation details.

## Decision

Terrain material generation is split into four responsibilities:

```text
Landscape material definition
    -> what the material does at runtime

Terrain preset
    -> reusable generation-process intent

Terrain palette
    -> small authored composition of presets + material choices

TerrainMaterialGenerator
    -> derives actual spatial/depth placement from causal world facts
```

The authored palette must remain intentionally small. The first canonical palette selects `natural_ground` and `depositional_sand` presets and maps four understandable roles to existing Landscape definition keys.

Technical thresholds and derived layer depths are internal generated complexity. They are not required fields in authored palette JSON.

## Preset ownership

Each resolved preset owns an explicit generation capability. Presets with different capabilities compose; multiple presets claiming the same capability are rejected.

Preset array order has no conflict-resolution semantics. There is no silent `last wins` rule.

The first capabilities are deliberately narrow:

- `GROUND_PROFILE`;
- `SURFACE_DEPOSITION`.

This is not a generic world-generation rules framework. More capability structure is added only when a real mechanic requires it.

## Causal placement

Material placement must be derived from existing/generated world facts rather than absolute coordinates or material identities.

The first model uses:

- precise local elevation differences for slope;
- local concavity;
- drainage contributing area.

Normal ground derives variable Soil depth and bedrock exposure from slope. Depositional sand derives shallow sediment accumulation from low slope, concavity and drainage influence.

The first sand process is not named or treated as a shoreline model. Shoreline sediment remains dependent on future explicit hydrography facts.

## Stable generated identity

Generated material facts use stable semantic Landscape definition keys, not runtime `LandscapeDefinitionId` values.

Runtime ids are resolved only at materialization through explicit `TerrainMaterialBindings` supplied by content composition.

Therefore:

- generator output does not depend on registry ordering;
- Atlas/worldgen code does not register content;
- material physics remains in ordinary Landscape definitions;
- changing runtime storage/ids does not change generated material semantics.

## Authoring invariant

> Authoring surface area should be proportional to creative intent, not simulation complexity.

If an author wants a normal temperate palette with natural soil, deposited sand and granite bedrock, they should identify those materials/presets, not reproduce the geomorphology algorithm.

## Consequences

### Positive

- common terrain definitions remain short and understandable;
- generator sophistication can grow independently from authored JSON;
- ordinary material changes do not require algorithm branches;
- multiple presets can compose without hidden order dependence;
- generated material patterns remain testable independently from runtime ids;
- future biome authoring can compose higher-level presets without becoming direct Terrain authority.

### Costs

- engine preset/model changes require generation-revision discipline when they change durable generated semantics;
- the first preset catalog is engine-provided rather than a full user-extensible preset-definition language;
- causal model constants still need evidence-driven calibration.

These costs are preferred to prematurely exposing a generic rules DSL.

## Rejected alternatives

### Material-specific generator branches

Rejected because adding ordinary materials would require Java changes and violate Definition Locality.

### Absolute elevation rules

Rejected because beaches/deposits/soil are local environmental phenomena, not universal Z bands. Vertical translation must preserve the relative material pattern.

### Large `fit` / `layers` authored JSON

Rejected because it exposes generator ontology (`slope`, `deposition`, role taxonomies and depth expressions) to ordinary content authors.

### Biome directly paints materials

Rejected as primary authority. Biomes may later supply high-level composition intent, but causal terrain/geology/hydrography models decide the concrete generated Terrain.

### Generic rule-tree DSL

Deferred. It would be a declarative `if/else` framework before we have enough distinct terrain mechanics to justify one.

## Follow-ups

1. run representative material-distribution audits across existing fixed seeds;
2. calibrate internal ground/deposition response from observed worlds rather than adding authored knobs;
3. introduce generated geology/provinces when multiple bedrock families become causal facts;
4. introduce HydrographyField before claiming shoreline-specific sand placement;
5. introduce an independent solid/open field before cave generation.

See [Terrain Generation](../systems/terrain-generation.md), [World Materialization](../systems/world-materialization.md), and [Decision 016](016-atlas-terrain-materialization.md).
