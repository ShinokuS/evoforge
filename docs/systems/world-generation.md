# World generation

This page is the canonical contract for generated-world ownership after the V12 terrain acceptance and the August 2026 pipeline audit.

## Core law

Generation answers **what exists at world start**. Runtime Simulation owns **what happens afterwards**.

The generated world is one continuous XYZ space. A generated field may be dense or compact internally, but consumers must not infer storage or chunk boundaries from that representation.

Definitions describe authored semantic possibilities. Generation algorithms decide coherent spatial placement. A definition must not contain hand-authored coordinate noise or require content authors to paint individual cells.

## Current authoritative preparation pipeline

```text
WorldSpec + seed + WorldGenerationIntent
                ↓
             WorldGenesis
                ↓
              WorldAtlas
  ┌─────────────┼──────────────────────────────────────┐
  ↓             ↓             ↓          ↓            ↓
Elevation     Geology       Climate    Drainage   Hydrography
  │                                         │            │
  └─────────────────────────────────────────┴────────────┤
                                                        ↓
                                               SurfaceHydrology
                ↓
        SurfaceMorphology
                ↓
          TerrainShape
                ↓
        TerrainMaterial
                ↓
    optional SoilHydraulics
                ↓
       PreparedGeneratedWorld
                ↓
       runtime materialization
```

`WorldAtlasGenerator` is the authoritative producer of atlas facts. `GeneratedWorldPreparation` is the authoritative bridge from atlas facts into prepared terrain/material/shape facts. `GeneratedWorldRuntimeBootstrap` is the handoff into runtime state.

The accepted V12 `ElevationField` is the **base surface fabric**. Its current morphology is a protected visual baseline: future mountains and hydrological carving extend it through explicit later morphology stages rather than retuning the accepted V12 landform model in place.

## What the existing fields mean today

### Elevation

Active and authoritative. V12 produces the accepted ocean-first base terrain: landmasses, coastlines, broad uplift, explicit hills/depressions, rolling relief and rugged ridges. It does not yet represent true mountain provinces or a deep crustal world.

### Drainage

Active analytical fact. The current D8-style field resolves downstream links, equal-elevation flats, contributing area and terminal sinks from precise elevation.

It is **not erosion** and it does not change elevation.

### Hydrography

Active analytical/durable channel footprint. Current V12 marks cells whose contributing area exceeds a threshold.

It is **not yet a river-network morphology model**: it has no basin filling, lake identity, outlet hierarchy, width hierarchy, valley carving or channel carving. A visible blue channel must never be mistaken for a completed river system.

### Surface hydrology

Active initial-water fact. It assigns finite initial channel water from hydrography, drainage and climate. Runtime Liquid/Water owns later redistribution.

It does not create river beds or lake bowls.

### Climate normals

Active generated environmental input. It currently supports water initialisation and other preparation/runtime calibration. It is not yet a biome generator.

### Geology

Active but **provisional**. The current implementation provides deterministic macro-province identity and coarse vertical strata so terrain materialization has non-uniform rock identity and geology has a tested typed contract.

It is not the final geology model. In particular it must not be extended by adding more independent random rock choices. The target model is coherent geological formations: provinces, belts, strata, intrusions, lenses and deposits with spatial continuity and relationships between layers.

### Terrain material profile JSON

Active and useful, but its role is narrower than a biome/material-distribution system.

For example `assets/definitions/worldgen/terrain/temperate.json` chooses reusable terrain process presets and a material set, while `material-sets/temperate-ground.json` binds semantic roles such as `surface`, `subsurface`, `sediment` and `bedrock` to landscape definitions.

This is a **palette of role identities**, not a spatial placement algorithm. It should be preserved. Future coherent soil/geology formation chooses where a role/material applies; the JSON remains authored content.

### Soil formation

Active only when `SoilSemanticProfileBindings` are supplied to generated-world preparation. The current stage develops authored soil semantics into local **hydraulic physical properties** using surface morphology and drainage.

It does not choose among several soil identities and it must not be used as a per-cell random soil selector. Future soil identity/distribution belongs to the spatial-formation + soil domain. The existing semantic composition/hydraulic calibration remains reusable after that placement decision.

### Terrain shapes

Active and authoritative presentation/geometry preparation derived from accepted elevation. Shape generation remains separate from material identity.

## Spatial coherence law

Future natural content must not be distributed by an independent hash/roll per cell.

A generic **Spatial Formation** layer is introduced before final geology/soil/ecology placement. It owns spatial forms, not domain semantics:

- `Province` — very large coherent area;
- `Belt` — elongated regional structure;
- `Patch` / `Cluster` — connected local area;
- `Layer` / `Stratum` — coherent depth-oriented sheet;
- `Lens` / `Vein` — bounded elongated body;
- controlled internal mixture;
- soft/hard transition zones.

Geology, soil, vegetation and resources consume these forms with their own domain constraints. The generic framework never branches on `Granite`, `Birch`, `IronOre`, etc.

A content author supplies available definitions and semantic characteristics. The generator chooses coherent locations, sizes, adjacency, mixing and transitions from those characteristics and world causes.

## Locked next sequence

### 1. Deep vertical world

Separate meaningful world depth from current surface relief range. Surface elevation may reach tens or hundreds of cells while underground extent remains independently defined/materialized.

Acceptance: a +100 mountain does not require globally scaling ordinary V12 hills, and underground coordinates remain valid below the local surface.

### 2. Mountain provinces and ranges

Add sparse large-scale uplift/range structures over the accepted V12 base fabric. Controls remain human semantic: abundance, scale, height and ruggedness.

Acceptance: ordinary terrain still resembles accepted V12 when mountain abundance is zero/low; selected regions produce coherent foothills, ranges and peaks around +40/+50/+100 or more.

### 3. Hydrographic structure

Build watersheds, flow accumulation, depression/basin analysis, lake fill levels, outlets and river hierarchy on the final mountain-bearing surface.

### 4. Terrain carving

Turn hydrographic facts into morphology: river valleys, channel beds, lake bowls and mountain valleys. Water visibility is not the acceptance criterion; with water hidden, the channel/basin geometry must still exist.

### 5. Generated-water handoff

Materialize finite initial water into the carved surface and hand ownership to runtime hydrology/liquids.

### 6. Spatial Formation framework

Implement the generic coherent spatial forms listed above, only to the extent required by the first real geology consumer.

### 7. Geology

Replace the provisional random-stratum geology with causally coherent provinces/formations/strata/intrusions and arbitrary authored rock palettes.

### 8. Caves

Generate real underground volumes from geology and water/formation causes rather than independent cave noise.

### 9. Soil identity and formation

Choose coherent soil regions/transitions from parent geology, morphology, drainage, water and climate; then reuse the existing semantic soil composition/hydraulic calibration for local physical properties.

### 10. Ecological potential

Derive suitability/productivity conditions from climate, water, soil, terrain and other environmental facts.

### 11. Vegetation communities

Generate coherent stands/patches with dominant/secondary species, density, mixing and edge transitions. Individual plants/trees become normal runtime objects after materialization.

### 12. Derived biomes

Biome labels classify the resulting environment/ecological community; they do not dictate climate, soil or physics backwards.

### 13. Resources and natural sites

Use coherent formations for ore bodies, seams, clay basins and other resources/sites, then materialize runtime-relevant objects/state.

## Change-control rules

- Do not change accepted V12 morphology incidentally while implementing a later stage.
- A generated fact needs an owner, consumer and observable acceptance test.
- If a field is only future-facing, document it explicitly rather than pretending it changes the visible world.
- Do not duplicate spatial-distribution frameworks per domain.
- New content using known mechanics is primarily definitions/data; genuinely new mechanics get a narrow domain contract and tests.
- Preview is observer/tooling only. It never becomes an alternate simulation truth.
