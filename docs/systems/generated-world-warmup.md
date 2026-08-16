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

## Mandatory CI matrix

The regular headless suite runs a small deterministic matrix across seeds:

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

These exact rates and checkpoint values are **test inputs**, not user-facing world-generation presets and not a statement that 50 ticks is a universal production warmup duration.

The matrix checks current invariants such as:

- Atlas/runtime surface agreement;
- valid drainage summary;
- no spontaneous Water in the unforced case;
- Water/Soil response under generated HydroClimate forcing;
- exact deterministic replay.

## Developer audit task

A verbose representative audit is available separately:

```text
./gradlew :simulation:generatedWorldAudit
```

By default it warms the same representative seed set to tick `100` and prints checkpoints in the canonical diagnostic format:

```text
scenario=<internal-profile> event=world.generated.audit ...
```

The final tick can be changed for development experiments:

```text
./gradlew :simulation:generatedWorldAudit -Devoforge.generated.audit.ticks=500
```

This task is deliberately excluded from normal unit tests so console output and longer exploratory runs do not make standard CI noisy. It has no wall-clock pass/fail threshold.

## Logging relationship

`GeneratedWorldDiagnosticsFormat` owns the compact textual representation of one structured snapshot. Both `GeneratedWorldDiagnosticsLog` and the developer audit task use that vocabulary.

The structured `GeneratedWorldDiagnostics` record remains the correctness input. Log strings are for inspection and support, never simulation authority or a test database.

## Next step

The first larger generated-world evidence set should be used to decide which facts a `WorldViabilityEvaluator` actually needs. Thresholds and reason codes must come from concrete observed failure modes rather than being invented inside warmup.

See [Generated World Runtime](generated-world-runtime.md), [Generated World Diagnostics](generated-world-diagnostics.md), and [Decision 019](../decisions/019-generated-world-warmup-is-explicit-observation.md).
