# Generated World Warmup

## Purpose

Advance a generated production runtime through explicit deterministic checkpoints and capture comparable diagnostics without introducing alternate world laws or hidden balance policy.

## Contract

`GeneratedWorldWarmup` operates on `GeneratedWorldRuntime` after the normal bootstrap has completed.

```text
GeneratedWorldRuntime
      ↓
checkpoint ticks: [t0, t1, ...]
      ↓
SimulationRuntime.stepper().advance()
      ↓
GeneratedWorldDiagnosticsProbe
      ↓
List<GeneratedWorldDiagnostics>
```

Checkpoint ticks are absolute simulation ticks, must be non-negative and strictly increasing, and may begin at the runtime's current tick. A checkpoint before current time is invalid.

Warmup never invokes precipitation, evaporation, Water flow, Soil infiltration or any other mechanic directly. Every transition between checkpoints occurs through the ordinary production `SimulationStepper` and Scheduler.

## No implicit equilibrium

Warmup currently has no condition such as:

```text
water delta < threshold
flooded columns < percentage
soil moisture near target
```

Those would be evaluation/calibration policy and are not yet justified by generated-world evidence. Warmup therefore cannot declare a world viable or settled; it only returns facts at requested times.

A later evaluator may consume the checkpoint trace through its own typed contract.

## Canonical generated Terrain

The generated-world smoke matrix and representative Water audit now bootstrap the canonical `core:temperate_terrain` palette rather than a synthetic uniform porous ground.

The content setup binds the palette's existing Landscape materials with their canonical current properties:

```text
core:topsoil  -> Soil 550000 / permeability 100000
core:soil     -> Soil 450000 / permeability 60000
core:sand     -> Soil 350000 / permeability 250000
core:granite  -> no Soil retention aspect
```

The material layout itself is generated from elevation/drainage through the production terrain-palette path. Warmup therefore observes the same causal chain intended for generated worlds:

```text
Elevation / Drainage
        ↓
Terrain material profile
        ↓
Landscape material properties
        ↓
Infiltration / retained Water / free Water
```

The test fixture does not override generated placement in order to make hydrology assertions pass.

## Mandatory CI matrix

The regular headless suite runs a deliberately small deterministic smoke matrix across seeds:

```text
0
1
42
991
123456789
```

and internal verification climate inputs including:

- unforced hydrology;
- a net-positive fractional precipitation/evaporation rate pair that exercises exact `CellVolumeRate` realization.

For each case the suite captures checkpoints at ticks `0`, `10`, `25`, and `50`, then independently replays the complete scenario and compares the entire diagnostic trace.

The smoke world remains small so these correctness checks stay cheap. Its purpose is scheduler/mass/determinism coverage, not representative terrain morphology.

These exact rates and checkpoint values are **test inputs**, not user-facing world-generation presets and not a statement that 50 ticks is a universal production warmup duration.

The matrix checks current invariants such as:

- Atlas/runtime surface agreement;
- valid drainage summary;
- no spontaneous Water in the unforced case;
- Water/Soil response through the generated terrain palette under generated HydroClimate forcing;
- exact deterministic replay.

## Representative developer audit

A verbose audit uses a larger world specifically so the current elevation scales (detail/medium/coarse) can produce meaningful topographic, material and drainage variation:

```text
./gradlew :simulation:generatedWorldAudit
```

Defaults:

```text
side = 32 cells
ticks = 100
vertical bounds = -32..32
seeds = 0, 1, 42, 991, 123456789
```

The workload emits two complementary observations:

```text
event=world.generated.terrain-materials ...
scenario=<internal-profile> side=<N> event=world.generated.audit ...
```

The first reports the initial semantic Terrain material composition. The second reports runtime Water/Soil state at requested checkpoints. Keeping both makes it possible to relate Water behavior to the generated material profile without making diagnostics part of simulation authority.

The workload can be changed for development experiments:

```text
./gradlew :simulation:generatedWorldAudit \
  -Devoforge.generated.audit.ticks=500 \
  -Devoforge.generated.audit.side=64
```

The audit side is currently constrained to `8..128` as a developer-tool resource guard, not as a simulation world-size contract.

This task is deliberately excluded from normal unit tests so console output and larger exploratory runs do not make standard CI noisy. It has no wall-clock pass/fail threshold.

## Current evidence

On the first `32×32` canonical-palette run, equal climate forcing produced exactly equal total Water mass at tick `100`, while retained Water differed substantially by generated material composition.

The most rock-exposed fixed seed retained much less Water than the soil-dominated seeds because granite has no Soil-retention aspect. This is evidence that generated Terrain composition is causally participating in existing hydrology; it is not yet a balance verdict or target distribution.

## GitHub Actions audit

`.github/workflows/generated-world-audit.yml` exposes the same representative audit in GitHub Actions.

- pull requests that touch generated-world/runtime code run a `32×32` audit to tick `100` and leave the canonical trace in the job log;
- manual workflow dispatch accepts final `ticks` (default `500`) and square `side` (default `32`) inputs;
- the workflow validates only the developer workload envelope and invokes `:simulation:generatedWorldAudit`; it has no extra simulation implementation or balance rules.

This gives CI and local development the same generated-world evidence format. The regular `CI` workflow remains the correctness gate; the audit workflow exists to make checkpoint values visible and comparable while relevant world code changes.

## Logging relationship

`GeneratedWorldDiagnosticsFormat` owns the compact textual representation of one runtime snapshot. `GeneratedTerrainMaterialDiagnosticsFormat` owns the initial generated-material representation. The local developer audit and GitHub Actions use both vocabularies.

Structured diagnostic records remain the correctness inputs. Log strings are for inspection and support, never simulation authority or a test database.

## Next step

Representative audit results should continue to determine which generated facts/mechanics need extension. Thresholds and reason codes must come from concrete observed failure modes rather than being invented inside warmup.

See [Terrain Generation](terrain-generation.md), [Generated World Runtime](generated-world-runtime.md), [Generated World Diagnostics](generated-world-diagnostics.md), and [Decision 019](../decisions/019-generated-world-warmup-is-explicit-observation.md).
