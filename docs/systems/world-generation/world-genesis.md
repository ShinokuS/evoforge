# World Genesis

## In plain language

World Genesis is the **birth certificate** of a generated EvoForge world. It records what was requested and which deterministic generation rules were used, so the same authored world can be identified and replayed deliberately instead of depending on hidden global randomness.

It does not contain the generated mountains, Water or Terrain itself. Those facts are produced later by the owning generation stages.

A useful analogy is:

```text
recipe request + seed + algorithm version
                 ↓
            WorldGenesis
                 ↓
      generated world facts
```

## Current status

The repository supports generation revisions `V1` through `V12` and RNG revision `V1`.

There are two important meanings of “current”:

- `WorldGenesis.current(spec, seed)` still deliberately creates **GenerationRevision.V7 + RngRevision.V1** for the ordinary production convenience path. This compatibility choice remains in code until the full physical-climate/runtime-scale generation contract is promoted together.
- **V12 is the manually accepted and architecturally normalized base-terrain revision used explicitly by the current world-generation milestone, preview and audits.** It is not silently substituted into `WorldGenesis.current(...)`.

That distinction is intentional and must remain explicit in future documentation.

## What Genesis records

`WorldGenesis` contains:

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
WorldGenerationIntent
```

### `WorldSpec`

The pre-generation world specification currently carries:

```text
finite inclusive WorldBounds
ClimateSpec
optional PhysicalSpaceScale
```

`PhysicalSpaceScale` is optional because not every generation consumer requires a real-world cell size. Any algorithm that needs physical dimensions must request them explicitly rather than inventing a hidden default.

### `masterSeed`

The 64-bit master seed identifies the deterministic random family used by generation. It is provenance, not mutable runtime RNG state.

### `GenerationRevision`

This versions **world-generation semantics**. Historical revisions remain executable so a newer algorithm does not silently rewrite what an older seed used to mean.

Current known revisions are `evoforge:worldgen-v1` through `evoforge:worldgen-v12`.

### `RngRevision`

This separately versions the deterministic sample algorithm. `GenerationRandom.from(...)` currently accepts only `RngRevision.V1`; unknown revisions fail explicitly.

Separating generation semantics from RNG semantics allows either contract to evolve without pretending they are the same compatibility problem.

## Authored world intent

`WorldGenerationIntent` is a set of normalized semantic coordinates in the range `0..1` (stored as integer parts-per-million, `0..1_000_000`). They describe desired **character**, not implementation thresholds.

The current seven coordinates are:

| Coordinate | Meaning |
|---|---|
| `landCoverage` | Desired fraction of horizontal columns classified as land by ocean-first revisions. |
| `landmassScale` | How spatially broad/coherent major landmasses should be. |
| `fragmentation` | How much finer structure breaks up the coherent landmass field. |
| `relief` | Strength of large-scale vertical relief. |
| `localRelief` | Strength of ordinary rolling hills/depressions. |
| `landformScale` | Typical horizontal size/spacing of ordinary landform features. |
| `ruggedness` | Ridge prominence and tolerated readable local slope in V12. |

`WorldGenerationIntent.balanced()` currently resolves to:

```text
landCoverage   = 0.50
landmassScale  = 0.50
fragmentation  = 0.50
relief         = 0.60
localRelief    = 0.45
landformScale  = 0.50
ruggedness     = 0.35
```

Compatibility constructors keep older callers source-compatible by supplying defaults for coordinates introduced later.

These numbers are not noise frequencies, metres, erosion coefficients or slope degrees. A revision-specific calibrator translates semantic intent into exact operating values.

## Revision history that matters today

The exact historical implementation remains in code, but the important semantic milestones are:

- **V1** — original cell-quantized elevation behavior.
- **V2+** — precise sub-cell elevation facts are available.
- **V8** — full Atlas generation introduces the physical climate-water contract when physical scale/time requirements are explicitly supplied.
- **V9** — ocean-first elevation with exact rank-calibrated land coverage and global sea-level datum `z = 0`.
- **V10** — adds structured macro relief while preserving the V9 land/ocean idea.
- **V11** — smooth/domain-warped organic morphology replaces the more piecewise V10 character.
- **V12** — accepted scale-stable balanced base terrain with explicit hills/depressions, rolling relief, ridges, coast gating and bounded slope relaxation behind the Stage 0 calibration/recipe architecture.

V9–V12 ocean-first generation requires vertical `WorldBounds` to contain valid space both below and above sea level `z = 0`.

## Deterministic generation randomness

### Why a normal mutable random stream is avoided

With a mutable PRNG stream, inserting one unrelated random call can shift every later value. That is dangerous for procedural generation because an innocent refactor could rewrite the whole world.

EvoForge instead addresses every sample by meaning:

```text
masterSeed
GenerationStageId
GenerationPurposeId
scopeX, scopeY, scopeZ
ordinal
        ↓
GenerationRandom.sampleLong(...)
```

For example, a landmass sample and a ridge sample at the same coordinate use different semantic purpose keys, so adding a new unrelated purpose does not shift either result.

### Exact V1 sample construction

The current sampler:

1. mixes the master seed with a fixed seed salt;
2. hashes the UTF-8 stage key with 64-bit FNV-1a and mixes it with a stage salt;
3. hashes and mixes the purpose key similarly;
4. mixes `x`, then `y`, then `z` with independent salts;
5. mixes the non-negative ordinal;
6. returns the resulting 64-bit value.

The avalanche function is:

```text
x = (x xor (x >>> 30)) * 0xbf58476d1ce4e5b9
x = (x xor (x >>> 27)) * 0x94d049bb133111eb
x =  x xor (x >>> 31)
```

Those constants follow the SplitMix-style `mix64` lineage, but EvoForge is **not** operating a mutable SplitMix stream. It uses the mixer as part of an addressable deterministic function.

The stable string hash uses FNV-1a:

```text
hash = offset_basis
for each UTF-8 byte b:
    hash = hash xor b
    hash = hash * FNV_prime
```

This is non-cryptographic and is used only for deterministic semantic-key hashing.

## Invariants

- Identical Genesis + compatible algorithm code must reproduce identical generated facts.
- Sample values do not depend on call order.
- Stage/purpose identifiers are stable namespaced semantic keys, not Java class names.
- Historical generation revisions must not silently acquire newer elevation semantics.
- Genesis is immutable provenance; it is never a runtime owner of Terrain, Water, agents or weather.
- A generator may use stable lattice coordinates as random scopes without making those lattices authoritative world positions.

## Ownership and lifecycle

```text
WorldGenesis
    ↓ immutable request/provenance
WorldAtlasGenerator / other preparation stages
    ↓ immutable generated facts
materialization + bootstrap
    ↓
SimulationRuntime
```

After runtime starts, Genesis remains provenance. It does not keep steering the world.

Generated outputs belong to their domain owners: elevation/geology/climate/drainage/hydrography in the Atlas, materialized Terrain in Landscape, finite runtime Water in the liquid system, and so on.

## Persistence rule

A future save system cannot treat `seed + newest generator` as canonical world state. A lived world may have changed after generation, and historical revisions may no longer be the newest algorithm.

Persistence must eventually store the authoritative facts/state needed to continue that existing world and preserve provenance separately.

## Current limitations

Genesis does not yet define:

- chunk/region dimensions or loaded/unloaded state;
- streaming policy;
- packed coordinates;
- mountain-range semantic controls beyond the currently implemented V12 base-terrain intent;
- tectonic/depositional history;
- biome/ecology/population generation;
- save-file schema.

Those contracts are introduced only when a real stage needs them.

## Code and tests

Primary implementation:

```text
simulation/.../world/genesis/WorldGenesis.java
simulation/.../world/genesis/WorldGenerationIntent.java
simulation/.../world/genesis/GenerationRevision.java
simulation/.../world/genesis/RngRevision.java
simulation/.../world/genesis/GenerationRandom.java
```

Generation/Atlas and V12 tests provide replay, revision and deterministic-output coverage; generated-world audits exercise explicit V12 Genesis construction rather than changing `WorldGenesis.current(...)`.

## Sources

**Algorithm lineage:** the `mix64` avalanche constants follow the splittable/SplitMix PRNG lineage described by Steele, Lea & Flood (2014). EvoForge's addressable sample construction is project-specific.

**Algorithm lineage:** stable stage/purpose strings use 64-bit FNV-1a as a non-cryptographic hash.

See [References](../../references.md), [World Atlas](world-atlas.md), [World Generation](overview.md), [ADR-009](../../decisions/009-world-genesis-provenance-and-randomness.md), and [Project Context](../../project-context.md).
