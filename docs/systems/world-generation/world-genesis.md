# World Genesis

## In plain language

`WorldGenesis` is the immutable **birth certificate** of a generated EvoForge world. It records what was requested and which deterministic generation rules were used so the same authored world can be replayed deliberately instead of depending on hidden global randomness.

It does not contain generated Terrain, mountains or Water. Owning generation stages produce those facts later.

```text
semantic request + seed + algorithm revisions
                    ↓
               WorldGenesis
                    ↓
          generated world facts
```

## Current status

The repository supports generation revisions `V1` through `V13` and RNG revision `V1`.

There are two deliberately different meanings of “current”:

- `WorldGenesis.current(spec, seed)` still creates **GenerationRevision.V7 + RngRevision.V1** for the ordinary compatibility convenience path until the broader production generation contract is promoted together;
- **V12 is the accepted ordinary base-terrain revision and V13 is the accepted dedicated-mountain revision used explicitly by the current world-generation milestone, preview and tests.**

Neither is silently substituted into `WorldGenesis.current(...)`.

## What Genesis records

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
WorldGenerationIntent
```

### `WorldSpec`

The pre-generation specification currently carries:

```text
finite inclusive WorldBounds
ClimateSpec
optional PhysicalSpaceScale
```

Algorithms that need physical dimensions request them explicitly; they do not invent a hidden default.

### `masterSeed`

The 64-bit master seed identifies the deterministic random family used by generation. It is provenance, not mutable runtime RNG state.

### `GenerationRevision`

This versions **world-generation semantics**. Historical revisions remain executable so a newer model does not silently rewrite what an older seed meant.

Current known revisions are `evoforge:worldgen-v1` through `evoforge:worldgen-v13`.

### `RngRevision`

This separately versions the deterministic addressable sample algorithm. `GenerationRandom.from(...)` currently accepts only `RngRevision.V1`.

## Authored world intent

`WorldGenerationIntent` stores normalized semantic coordinates in integer parts-per-million (`0..1_000_000`). These values describe desired **character**, not implementation thresholds.

### Ordinary V12 landscape coordinates

| Coordinate | Meaning |
|---|---|
| `landCoverage` | Desired fraction of horizontal columns classified as land by ocean-first revisions. |
| `landmassScale` | Spatial breadth/coherence of major landmasses. |
| `fragmentation` | Amount of finer structure breaking the coherent landmass field. |
| `relief` | Strength of large-scale ordinary vertical relief. |
| `localRelief` | Strength of rolling hills/depressions. |
| `landformScale` | Typical horizontal size/spacing of ordinary landforms. |
| `ruggedness` | V12 ridge prominence and ordinary readable local slope character. |

Balanced ordinary defaults are:

```text
landCoverage   0.50
landmassScale  0.50
fragmentation  0.50
relief         0.60
localRelief    0.45
landformScale  0.50
ruggedness     0.35
```

### Dedicated V13 mountain intent

`WorldGenerationIntent` also owns a typed `MountainIntent`:

| Coordinate | Meaning |
|---|---|
| `abundance` | Desired amount of V12 land occupied by dedicated mountain structures. |
| `height` | Desired mountain prominence, later bounded by world/Scale/slope capacity. |
| `scale` | Typical transverse size of individual mountain structures. |
| `chaininess` | Long-axis elongation of a structure. |
| `peakSharpness` | Geometric slope/profile character, not a concrete Shape choice. |
| `plateausEnabled` | Whether plateau-profile mountains may occur. |
| `plateauProbability` | Per-source plateau probability when enabled. |

Balanced V13 tooling defaults are:

```text
abundance          0.35
height             0.52
scale              0.50
chaininess         0.55
peakSharpness      0.60
plateausEnabled    true
plateauProbability 0.18
```

`MountainIntent.none()` explicitly disables dedicated mountain structures while preserving the V12 base path.

These semantic coordinates are not widths, metres, exact slopes, noise frequencies or source counts. Revision/domain calibrators compile them into exact operating values.

## Revision history that matters today

- **V1** — original cell-quantized elevation behavior.
- **V2+** — precise sub-cell elevation facts become available.
- **V8** — full Atlas generation introduces the physical climate-water contract when required physical inputs are supplied.
- **V9** — ocean-first elevation with exact rank-calibrated land coverage and sea-level datum `z = 0`.
- **V10** — structured macro relief over the V9 land/ocean idea.
- **V11** — smooth/domain-warped organic morphology.
- **V12** — accepted scale-stable ordinary base terrain behind semantic calibration + recipe + replaceable spatial algorithm.
- **V13** — accepted dedicated structural mountains composed over a capped V12 base, with typed `MountainIntent`, replaceable calibration/spatial seams and generic sparse shape fitting downstream.

V9–V13 ocean-first generation requires vertical `WorldBounds` with valid space below and above sea level. V13 additionally requires positive headroom above the V12 base-terrain ceiling used by its recipe.

## Deterministic generation randomness

EvoForge avoids a mutable global PRNG stream because inserting one unrelated random call would shift later results.

Samples are addressed by meaning:

```text
masterSeed
GenerationStageId
GenerationPurposeId
scopeX, scopeY, scopeZ
ordinal
        ↓
GenerationRandom.sampleLong(...)
```

The current V1 sampler combines the master seed with stable semantic keys/coordinates/ordinal through fixed deterministic hashing/mixing. Stage/purpose names therefore isolate unrelated random domains and sample values do not depend on call order.

## Invariants

- Identical Genesis + compatible revision code reproduces identical generated facts.
- Random samples do not depend on call order.
- Stage/purpose identifiers are stable semantic keys, not Java class names.
- Historical revisions do not silently acquire newer elevation semantics.
- Authored intent remains semantic; exact model values belong to calibrators/recipes.
- Genesis is immutable provenance and never owns live Terrain, Water, agents or weather.
- Adding a replaceable implementation behind the same compatible contract does not require downstream fact consumers to inspect its class.

## Ownership and lifecycle

```text
WorldGenesis
    ↓ immutable request/provenance
world-generation algorithms
    ↓ immutable generated facts
preparation/materialization
    ↓
SimulationRuntime
```

After runtime starts, Genesis remains provenance only. It does not keep steering the world.

## Persistence rule

A future save format cannot treat `seed + newest generator` as canonical lived state. Runtime may have changed authoritative Terrain/Water/etc., and historical revisions may no longer be newest. Persistence must preserve authoritative current state plus provenance needed to understand its origin.

## Current limitations

Genesis does not yet define:

- chunk/region streaming state;
- packed coordinate representation;
- tectonic/depositional history;
- biome/ecology/population generation;
- save-file schema.

Those contracts are introduced only when a real consumer needs them.

## Code and tests

Primary implementation:

```text
simulation/.../world/genesis/WorldGenesis.java
simulation/.../world/genesis/WorldGenerationIntent.java
simulation/.../world/genesis/MountainIntent.java
simulation/.../world/genesis/GenerationRevision.java
simulation/.../world/genesis/RngRevision.java
simulation/.../world/genesis/GenerationRandom.java
```

See [V13 Mountain Generation](mountain-generation.md), [World Atlas](world-atlas.md), [World Generation](overview.md), [ADR-009](../../decisions/009-world-genesis-provenance-and-randomness.md), and [Project Context](../../project-context.md).
