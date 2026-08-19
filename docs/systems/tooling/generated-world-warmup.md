# Generated World Warm-up

## In plain language

Warm-up is a developer tool that says: **run this generated world normally until these exact simulation ticks, and take diagnostic snapshots along the way**.

It does not have a secret faster Water model, does not call rain/flow systems directly, and does not decide when a world is “settled”. It repeatedly uses the ordinary production `SimulationStepper`, so a warmed-up world obeys the same rules as any other runtime.

## Current status

The implementation now lives under:

```text
simulation/.../world/diagnostics/warmup/
```

after the Stage 0 package-ownership cleanup. The old `world.warmup` package no longer exists.

The contract is:

```text
GeneratedWorldRuntime
      +
absolute checkpoint ticks [t0,t1,...]
      ↓
GeneratedWorldWarmup
      ↓ repeatedly
SimulationRuntime.stepper().advance()
      ↓ at each requested tick
GeneratedWorldDiagnosticsProbe
      ↓
List<GeneratedWorldDiagnostics>
```

## Checkpoint rules

Checkpoint ticks:

- are absolute simulation ticks;
- must be non-negative;
- must be strictly increasing;
- may start at the runtime's current tick;
- may not request a tick in the past.

At each checkpoint the probe records facts; it does not mutate the world.

## No alternate simulation path

Warm-up never directly invokes:

- precipitation;
- evaporation;
- liquid flow;
- Soil infiltration;
- Growth/Needs;
- any other mechanic.

Every transition between checkpoints is ordinary:

```text
SimulationStepper.advance()
    ↓
clock advance + scheduler dispatch
    ↓
normal domain processes
```

Therefore warming from tick 0 to 50 means “execute 50 production ticks”, not “approximate 50 ticks by a special equilibrium solver”.

## No implicit equilibrium or quality policy

Warm-up currently has no rule such as:

```text
Water delta < threshold
flooding below X%
Soil moisture near target
terrain quality > score
```

Those are evaluator/calibration/acceptance questions, not time-advancement semantics.

Warm-up cannot declare a world viable, stable, realistic or balanced. It only returns comparable facts at requested times.

## Mandatory deterministic smoke matrix

The regular headless generated-world tests use small worlds and fixed seeds so replay remains cheap:

```text
0
1
42
991
123456789
```

Representative smoke checkpoints include:

```text
0, 10, 25, 50
```

The suite independently recreates the same world and compares the entire diagnostic trace.

Those exact seeds/ticks are **developer test inputs**, not gameplay presets and not a universal production warm-up duration.

## Representative Generated World Audit

A larger developer workload is available through:

```text
./gradlew :simulation:generatedWorldAudit
```

Current defaults:

```text
side  = 32 cells
ticks = 100
vertical bounds = -32..32
seeds = 0, 1, 42, 991, 123456789
```

The command emits both:

```text
event=world.generated.terrain-materials ...
event=world.generated.audit ...
```

so the initial generated composition and later runtime Water/Soil state can be compared without teaching diagnostics to interpret balance.

Developer workload can be overridden, for example:

```text
./gradlew :simulation:generatedWorldAudit \
  -Devoforge.generated.audit.ticks=500 \
  -Devoforge.generated.audit.side=64
```

The current audit guard constrains side to `8..128`; this is a tooling/resource limit, not an engine world-size contract.

## GitHub Actions relationship

`.github/workflows/generated-world-audit.yml` runs the same Gradle audit path when relevant generated-world/runtime code changes and supports manual workload overrides.

The workflow does not contain another world generator or balance model. It exists to make deterministic checkpoint evidence visible in CI logs.

Ordinary CI tests remain correctness gates; the audit is additional observability.

## Current Stage 0 evidence

The Stage 0 world-generation architecture/refactor was checked with exact-head Generated World Audit in addition to normal CI and manual V12 preview acceptance.

This is the intended pattern for later stages:

```text
headless deterministic invariants
+
representative generated-world diagnostics
+
manual visual acceptance where aesthetics matter
```

No one evidence type substitutes for the others.

## Invariants

- Warm-up advances only through `SimulationStepper`.
- Warm-up owns no world state or process semantics.
- Checkpoint sequence is explicit, absolute and deterministic.
- Diagnostic capture does not itself advance/mutate state.
- Replaying same generated/runtime inputs yields the same trace.
- Warm-up has no hidden equilibrium/quality threshold.
- Developer workload limits are not simulation laws.

## Current limitations

Warm-up currently does not:

- stop on dynamic predicates;
- evaluate balance/viability;
- extrapolate long-term state analytically;
- accelerate sleeping processes beyond ordinary Scheduler semantics;
- create performance benchmarks with wall-clock pass/fail thresholds.

A future evaluator may consume traces, but evaluation should remain a separate typed responsibility.

## Code and tests

Primary implementation/tests:

```text
simulation/.../world/diagnostics/warmup/GeneratedWorldWarmup.java
simulation/.../world/diagnostics/warmup/*Warmup*Test.java
simulation/.../world/diagnostics/warmup/*Audit*Test.java
```

## Sources

**Internal EvoForge tooling design.** Warm-up is deliberately ordinary runtime stepping plus observation.

See [Generated World Diagnostics](generated-world-diagnostics.md), [Generated World Runtime](../world-generation/generated-world-runtime.md), [Time and Scheduling](../foundations/time.md), and [ADR-019](../../decisions/019-generated-world-warmup-is-explicit-observation.md).
