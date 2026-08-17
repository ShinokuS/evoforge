# 020 — World preparation and modular calibration boundary

## Status

Accepted.

## Context

World generation, calibration and simulation solve different problems and must not become one long-lived subsystem.

Generation creates durable facts about the world. Calibration translates higher-level world intent and generated context into physically meaningful parameters needed by later systems. Simulation owns current mutable state and executes processes through time.

Mixing these responsibilities creates several failure modes:

- generation or calibration objects remain alive and influence a running world;
- runtime consumers need to know which generator/calibrator produced a value;
- adding one domain creates a central `switch`, generic service registry or monolithic Balancer;
- algorithm-specific knobs leak into authored world intent;
- mutable runtime state becomes reachable through preparation APIs.

## Decision

Use a strict one-way preparation boundary:

```text
semantic WorldSpec + provenance
          ↓
     world generation
          ↓
       WorldAtlas
   durable generated facts
          ↓
  domain calibration stages
          ↓
 PreparedGeneratedWorld
 immutable prepared inputs
          ↓
---------------- START BOUNDARY ----------------
          ↓
 GeneratedWorldRuntimeBootstrap
          ↓
 SimulationRuntime
 mutable state + processes
```

Generation and calibration are preparation-time only. They may read immutable authored intent and previously produced immutable facts. They may not schedule runtime processes, mutate runtime Water/Soil/objects, or remain dependencies of a started simulation.

Runtime bootstrap may consume prepared data once to initialize authoritative simulation state and runtime models. After start, only simulation-owned state/processes advance the world.

## Domain calibrators

Calibration is **not** one global Balancer.

Each domain that needs calibration owns a narrow typed seam, for example:

```text
semantic climate projection
        ↓
RainfallRegimeCalibrator
        ↓
RainfallRegimeField
        ↓
driver-specific compiler
        ↓
WeatherDriver
```

A future soil domain may independently add:

```text
soil/geology context
        ↓
SoilHydraulicCalibrator
        ↓
SoilHydraulicField
        ↓
runtime Soil physics
```

The same pattern may later be used for hydrology, ecology, metabolism, erosion, or other domains.

Rules for every calibrator:

1. Input and output are explicit typed domain data, never `Map<String,Object>`, `Map<Class<?>,Object>` or a generic service locator.
2. The calibrator interface describes the domain transformation; concrete algorithms are constructor-injected/replaceable behind that interface.
3. Calibrated output is immutable preparation data with physical or otherwise well-defined semantics.
4. Algorithm-specific runtime parameters are produced by a separate compiler when they are not true domain facts.
5. A calibrator does not mutate another domain to force a target balance. Cross-domain dependencies are explicit inputs in a preparation DAG.
6. Adding a new domain calibrator does not require modifying unrelated runtime systems.
7. Runtime code does not call calibrators or branch on which calibrator implementation was used.

## Algorithm-specific parameters

Do not mistake the parameters of one numerical model for universal world facts.

For example, `RainfallPulseParameters` belongs to a rectangular-pulse weather algorithm. It should eventually be compiled from an algorithm-independent calibrated rainfall regime rather than becoming authored climate intent or a universal climate output.

This allows:

```text
RainfallRegimeField
      ├─→ AlternatingPulseCompiler → AlternatingRainfallPulseDriver
      └─→ FutureStormModelCompiler → FutureWeatherDriver
```

without changing authored world meaning.

## Composition

Preparation composition remains explicit and typed. A central orchestrator may define execution order from real data dependencies, but it must not invent fake dependencies merely to linearize the pipeline.

Algorithm replacement uses typed bundles/constructors such as `WorldGenerationAlgorithms`; generic registries are rejected.

When a new calibration domain is introduced, its dependency edges and immutable output are added deliberately to preparation composition. This is preferable to hiding arbitrary extensions in a dynamic bag because ownership and causal order stay visible in code review and diagnostics.

## Runtime atmosphere example

Runtime atmosphere follows the same rule:

- `ClimateNormalsField` is durable prepared climate data;
- `WeatherState` is current mutable simulation state;
- `AtmosphericRuntimePlan` is a one-shot runtime composition strategy;
- `AtmosphericWaterForcing` is the runtime process-facing contract;
- Water/Soil consumers do not know whether forcing came from compatibility climate projection, eventful weather, or another model.

No runtime atmospheric contract belongs in `world.atlas`.

## Consequences

Positive:

- generation/calibration can be tested without starting a simulation;
- runtime replay/debugging has fewer hidden writers;
- algorithms can be replaced without changing world semantics;
- new calibrated domains can be added independently;
- causal dependencies remain reviewable and observable.

Costs:

- preparation outputs and dependencies must be modeled explicitly;
- adding a genuinely new domain may require extending a typed preparation result/composition root;
- compatibility facades may temporarily remain while callers migrate to the explicit two-phase API.

These costs are intentional: explicit structural change is safer than invisible generic extensibility in an authoritative simulation.
