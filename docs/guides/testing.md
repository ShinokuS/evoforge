# Testing Strategy

EvoForge uses three complementary verification layers. No single layer is sufficient for a causal simulation.

## 1. Headless semantic tests

Fast headless tests prove exact contracts:

- owner lifecycle and invariants;
- structured rejection for expected impossibility;
- deterministic timing/order;
- conservation and exact physical conversions;
- calibration/compiler identities;
- cross-system contract boundaries;
- geometry/navigation/cost results;
- cache correctness where caches derive authoritative reads.

These tests answer: **is the implementation mathematically and architecturally correct?**

## 2. Focused acceptance scenarios

A visualizer scenario should exist when a mechanic or causal chain is important to understand as a human.

A focused scenario uses real production systems but may deliberately isolate an initial condition so the observed cause is unambiguous. For example, a rainfall acceptance scene may suppress generated initial surface water while still using generated ClimateNormals, preparation-time rainfall calibration, runtime Weather, the production scheduler, Soil and Water.

A scenario must never replace the mechanic under test with a fake implementation merely to produce a convenient picture.

These scenarios answer: **does the behavior look and evolve like the system we intended to build?**

## 3. Generated-world integration and audits

Representative generated worlds combine many accepted subsystems over multiple seeds and longer time horizons. They verify:

- deterministic replay;
- conservation and boundedness across subsystem interaction;
- generated-fact compatibility;
- long-running causal interaction;
- unexpected coupling that focused tests cannot expose.

These runs answer: **do independently correct systems remain correct when they coexist?**

Do not postpone focused acceptance until the whole generation pipeline is complete. A late full-world failure is expensive to localize and may hide multiple offsetting defects.

## Structural architecture tests

Source/reflection tests are appropriate for simple rules whose accidental violation would be costly, such as:

- simulation/presentation constructor capability boundary;
- Control/world dependency direction;
- generic consumers not importing concrete implementation classes;
- preparation/calibration packages not acquiring runtime mutation dependencies.

They are not replacements for semantic tests; they make architecture regressions fail immediately.

## Presentation

Pure presentation math and deterministic resolution should be unit-tested. Aesthetic choices such as palette balance and line weight require manual visual acceptance until a product-level screenshot/golden-image need justifies that tooling.

## Performance

Performance changes need representative telemetry or a benchmark-shaped workload. A faster implementation must preserve the same semantic tests.

See [Debug Scenarios](debug-scenarios.md) and [Correctness Harness](correctness-harness.md).
