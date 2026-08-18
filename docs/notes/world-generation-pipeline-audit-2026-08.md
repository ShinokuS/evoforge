# World-generation pipeline audit — 2026-08

This audit was performed immediately after acceptance/merge of the V12 balanced terrain slice. Its purpose is to distinguish active authoritative generation from provisional/future scaffolding before mountains, carved hydrography, geology and ecology continue.

## Audit rubric

Each component is classified as:

- **KEEP** — active and correctly owned;
- **KEEP / NARROW** — active but its present responsibility must not be mistaken for a richer future system;
- **REPLACE LATER** — useful typed seam / temporary implementation whose algorithm conflicts with the target model;
- **CONNECT LATER** — useful generated fact that currently has no morphology/materialization effect by design;
- **REMOVE** — unreachable duplicate/obsolete code with no compatibility or test value.

No accepted V12 elevation morphology was changed during this audit.

## Findings

| Area | Current producer | Current consumer/effect | Verdict | Audit finding |
|---|---|---|---|---|
| Genesis/provenance | `WorldGenesis`, `GenerationRandom` | all generated stages | KEEP | Correct deterministic root; human intent remains separate from algorithm internals. |
| Base elevation | `ElevationGenerationStage` / V12 landform generator | Atlas, drainage, morphology, shapes, material preparation, preview | KEEP / PROTECT | Accepted base terrain. Future mountains/carving should be explicit later stages, not silent retuning. |
| Drainage | `DrainageGenerationStage` | hydrography, surface hydrology, terrain/soil preparation | KEEP | Real analytical topology: downstream, flats, contributing area, sinks. Does not alter terrain. |
| Hydrography | `HydrographyGenerationStage` | surface-water initialisation | KEEP / NARROW | Current boolean threshold channel footprint is useful but is not a complete river network and has no lakes/carving. |
| Surface hydrology | `SurfaceHydrologyGenerationStage` | generated runtime water bootstrap | KEEP / NARROW | Correct generation→runtime ownership handoff for finite water. It must consume future carved hydrography rather than become the carver itself. |
| Climate normals | `ClimateNormalsGenerationStage` | surface-water initialisation/calibration | KEEP | Active environmental input; not a biome owner. |
| Surface morphology | `SurfaceMorphologyGenerationStage` | terrain material/soil preparation | KEEP | Useful derived slope/concavity facts. Recompute after future mountain/carving stages. |
| Terrain shapes | `TerrainShapeGenerationStage` | prepared runtime geometry + preview | KEEP | Geometry is correctly separate from material identity. |
| Geology field | `GeologyGenerationStage` | terrain material fallback/bedrock identity | REPLACE LATER | Typed field/profile/JSON seam is useful; current 16-cell Voronoi-like provinces + independent 6-Z random strata are explicitly provisional and not adequate final geology. |
| Terrain profile JSON | `TerrainProfileLoader` family | `TerrainMaterialGenerationStage` | KEEP / NARROW | Existing presets/material sets are active role palettes, not a spatial biome/soil distribution language. |
| Landscape definitions | landscape JSON (`topsoil`, `soil`, `sand`, `granite`, `limestone`, `basalt`, `shale`, …) | material bindings/materialization | KEEP | Correct data-driven identities. Availability does not mean every material should be mixed per cell. |
| Terrain material layering | `TerrainMaterialGenerationStage` | prepared terrain/materialization | KEEP / NARROW | Useful surface/subsurface/sediment/bedrock stratification. Current role-based depth rules are not the future coherent soil/geology placement system. |
| Soil semantic compilation | `SoilDefinitionCompiler`, composition/hydraulic calibrators | optional generated soil physical properties | KEEP | Reusable authored semantics→physics compiler. |
| Spatial soil formation | `SoilFormationGenerationStage` | optional `SoilHydraulicProfileField` | KEEP / NARROW | It develops hydraulic properties from morphology/drainage for an already selected material; it does **not** distribute multiple soil identities. |
| Prepared-world boundary | `GeneratedWorldPreparation` | runtime bootstrap | KEEP | Correct explicit boundary: atlas → morphology/shapes/materials/optional soil properties → runtime. |
| Preview | world-generation 2D/3D tools | developer observation only | KEEP | Strong acceptance/profiling instrument; it must gain layers when new facts need visual acceptance. |

## Important negative finding: the hydro code is not dead

The repository already contains substantial drainage/hydrography/surface-water code, and the standard `WorldAtlasGenerator` invokes it on every generated world:

```text
Elevation
  ↓
Drainage
  ↓
Hydrography
  ↓
SurfaceHydrology (+ Climate)
```

The reason the accepted V12 preview has no river beds or lake bowls is not that this code is unreachable. The current contracts intentionally produce **analytical channel membership and initial water**, not a terrain mutation stage.

Therefore the next water work must reuse `DrainageField` where correct, replace/extend the simplistic channel-footprint algorithm where necessary, add basin/lake/outlet hierarchy, and introduce a separate morphology/carving stage. Duplicating a second unrelated river implementation would be an architectural regression.

## Important negative finding: the soil code is not dead either

There are two different concepts that had become easy to conflate:

1. **terrain material role palettes** — e.g. `temperate-ground.json` maps `surface → topsoil`, `subsurface → soil`, `sediment → sand`, `bedrock → granite`;
2. **soil semantic/physical calibration** — develops an authored soil semantic profile into composition and hydraulic properties, optionally varying those properties by morphology/drainage.

Both are active/reusable. Neither solves the future problem “given several valid soil types, create coherent regional patches and transitions”. That missing responsibility belongs to the planned Spatial Formation + Soil placement stage.

The audit therefore preserves the JSON/profile/compiler code and narrows its documented meaning instead of deleting it or wiring it into V12 elevation.

## Geology conflict discovered

The current geology implementation was a useful early vertical slice, but it must not become the basis of final material distribution by incremental tuning.

Today it:

- chooses coherent-ish horizontal macro provinces from jittered lattice sites;
- divides Z into fixed six-cell strata;
- independently samples a geology-unit ordinal for each `(province, stratum)`.

This is deterministic and non-cell-checkerboard, but it does not express formation continuity, stratigraphic relationships, dipping/folding layers, intrusions, lenses, seams or geological causes. Adding more rock definitions to this algorithm would produce more arbitrary mixing, not better geology.

Verdict: keep `GeologyField`, material keys, profile loading/compilation and consumers; later replace the generation algorithm behind that seam with the Spatial Formation–driven geology stage.

## Legacy/compatibility seams

`WorldAtlasGenerator`, `SurfaceHydrologyGenerator`, `GeneratedWorldPreparation` and bootstrap classes contain historical constructor/default-method seams. They are not currently removed because they preserve custom-generator/test compatibility and the canonical production path already uses the explicit typed dependency bundle.

Rule going forward: no new compatibility overloads. New production dependencies belong in the canonical typed composition path. A compatibility seam can be deleted only with repository-wide usage evidence and migration in the same change.

## What was intentionally not connected during this audit

- geology was not made visible in the accepted surface preview;
- soil palettes were not applied to change V12 surface colors/material appearance;
- hydrography was not drawn as fake rivers;
- channels/lakes were not carved;
- mountains were not added;
- a speculative generic formation framework was not implemented yet.

Those would change behavior/visual acceptance and belong to their own vertical slices.

## Dead-code result

No major world-generation subsystem inspected in the authoritative preparation path qualified for safe wholesale deletion. Several pieces looked “unused” only because the current preview visualizes elevation/shapes rather than prepared material/soil/hydro facts. Deleting them would discard active runtime preparation contracts.

The cleanup result is therefore primarily **semantic de-confliction** rather than deletion: one authoritative pipeline is documented, existing stages have narrower explicit meanings, and provisional algorithms are marked as such so future work replaces rather than duplicates them.

## Locked continuation

See [`../systems/world-generation.md`](../systems/world-generation.md). In short:

1. deep vertical world;
2. mountain provinces/ranges over protected V12 base terrain;
3. hydrographic basins/outlets/river hierarchy;
4. terrain carving;
5. generated-water handoff;
6. generic Spatial Formation framework;
7. coherent geology;
8. caves;
9. coherent soil identity/formation;
10. ecological potential;
11. vegetation communities;
12. derived biomes;
13. resources/natural sites.
