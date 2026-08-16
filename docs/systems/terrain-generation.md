# Terrain Generation

## Purpose

Generate understandable natural Terrain composition without making content authors reproduce the generator's internal geomorphology model.

The first material slice produces four already-defined Landscape materials:

- `core:topsoil`;
- `core:soil`;
- `core:sand`;
- `core:granite`.

Terrain generation chooses where those materials occur. Their traversal, Soil and other runtime properties remain owned by their normal Landscape definitions.

## Authoring boundary

The canonical first palette is intentionally small:

```json
{
  "key": "core:temperate_terrain",
  "presets": [
    "core:natural_ground",
    "core:depositional_sand"
  ],
  "materials": {
    "topsoil": "core:topsoil",
    "soil": "core:soil",
    "sand": "core:sand",
    "rock": "core:granite"
  }
}
```

Authors do **not** provide:

- absolute elevation thresholds;
- slope cutoffs;
- deposition scores;
- Soil layer depths;
- random placement chances;
- world coordinates.

Those are generated complexity. `TerrainPaletteLoader` is strict: unknown fields, unknown presets, duplicate presets and conflicting preset capabilities fail explicitly rather than being silently ignored.

## Preset composition

A preset is reusable generation intent. It owns one explicit generation capability in the current narrow model.

The first presets are:

```text
core:natural_ground
    -> GROUND_PROFILE

core:depositional_sand
    -> SURFACE_DEPOSITION
```

Two presets that own different capabilities compose. Two presets that claim the same capability are a compile/load conflict; array order never decides authority and there is no `last preset wins` behavior.

This is deliberately smaller than a generic rule engine. More preset structure should be introduced only when a real second implementation requires it.

## Causal material model

`TerrainMaterialGenerationStage` consumes existing generated facts:

```text
ElevationField
DrainageField
TerrainPalette
      ↓
local slope
local concavity
contributing area
      ↓
column ground/deposition profile
      ↓
TerrainMaterialField
```

For normal ground, local slope controls how much loose Soil can remain above bedrock:

```text
stable / low slope
    topsoil
    soil
    soil
    rock

steeper surface
    topsoil or thin soil
    rock

sufficiently steep surface
    exposed rock
```

The current model has a maximum generated ground profile depth of four cells. Its exact slope-response constants are internal model-v1 policy and calibration candidates, not content authoring values.

## Depositional sand

`core:depositional_sand` uses a deterministic combination of:

- low local slope;
- local concavity (surface lower than its surroundings);
- drainage contributing area.

That produces sand in plausible accumulation locations without any rule such as:

```text
height < N -> sand
```

This first sand process is explicitly **depositional terrain**, not yet a universal shoreline rule. True lake/river shore placement should later consume a generated `HydrographyField`; Water proximity must not be guessed from absolute elevation.

## Vertical invariance

Material composition is derived from local differences and drainage, not absolute Z.

If an otherwise identical world shape is translated vertically, its relative material layering must remain identical. Tests lock this invariant so absolute-height hardcoding cannot enter unnoticed.

## Generated identity vs runtime identity

`TerrainMaterialField` returns `TerrainMaterialKey`, a validated stable semantic Landscape key such as `core:soil`.

It never stores `LandscapeDefinitionId` because runtime integer ids are composition-local.

At materialization:

```text
TerrainMaterialField
      ↓ semantic keys
TerrainMaterialBindings
      ↓ runtime ids
TerrainMaterialResolver
      ↓
WorldTerrainMaterializer
```

Content composition owns the bindings. The generator therefore does not register content, assign material physics or depend on registry order.

## Memory boundary

The first implementation stores only a compact generated profile per XY column:

- discrete generated surface;
- ground-profile depth;
- depositional-layer depth.

It does not allocate a material entry for every solid 3D cell. `materialAt(x,y,z)` derives the material from that column profile on demand.

## Diagnostics

`GeneratedTerrainMaterialDiagnostics` reports exact deterministic material counts for:

- generated surface cells;
- complete generated solid Terrain volume.

The representative generated-world audit prints:

```text
event=world.generated.terrain-materials ...
```

using stable material keys. These counts are evidence for tuning and regression analysis; no current percentage is classified as healthy, rocky, sandy or invalid.

## Deferred

This slice intentionally does not yet add:

- a geology/province generator with multiple rock types;
- Hydrography-backed beaches, riverbanks or lakebeds;
- caves / open underground volume;
- erosion as runtime Terrain mutation;
- biome authority over Terrain;
- user-facing technical terrain parameters.

Future geology can replace the single palette `rock` role with generated strata. Future caves should introduce an independent solid/open shape fact rather than abusing material identity.

See [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Generated World Diagnostics](generated-world-diagnostics.md), and [Decision 020](../decisions/020-terrain-palettes-hide-generated-complexity.md).
