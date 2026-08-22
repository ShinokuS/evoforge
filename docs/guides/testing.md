# Testing and Performance Strategy

EvoForge treats correctness, architecture, determinism and scale as separate properties that must all be verified. A green unit suite alone is not sufficient for a persistent causal simulation.

## Verification pyramid

```text
manual visual acceptance (only where necessary)
                ↑
representative scale/performance profiles
                ↑
focused cross-owner integration tests
                ↑
owner / deterministic / substitution / conservation tests
                ↑
pure algorithm and value tests

architecture fitness tests run across the structure itself
```

Use the **lowest/nearest level that can prove the contract**. Do not replace an owner test with a broad scenario merely because the scenario already exists.

## 1. Pure algorithm/value tests

Use fast deterministic tests for:

- mathematical transformations;
- typed values/units/clamps;
- parser/compiler rules;
- planners/solvers/selectors independent of live world state;
- deterministic tie breaking and edge cases;
- overflow/rounding/fixed-point rules.

When an algorithm has published lineage, tests should protect the EvoForge adaptation, not merely restate examples from the source.

## 2. Owner tests

Every authoritative owner should prove:

- lifecycle/create/remove semantics;
- exactly what fact it owns;
- invalid mutation rejection;
- revision/change semantics where exposed;
- storage replacement does not alter public meaning;
- projections do not become required truth;
- owner invariants after failure/rollback paths.

A storage implementation test does not replace the owner contract test.

## 3. Determinism tests

For deterministic behavior, verify as applicable:

```text
same authoritative inputs + compatible revision -> same authoritative output
```

Test independence from incidental factors:

- request order when semantically irrelevant;
- hash/map iteration order;
- cache eviction + reload;
- repeated materialization of overlapping coordinates;
- renderer/camera cadence;
- scheduling insertion order where order is not semantic;
- serialization/reload when persistence is introduced.

Random-looking world generation uses explicit stable seed/address inputs. Tests should compare exact facts/invariants, not screenshots alone.

## 4. Conservation and physical invariants

Physical transfers need invariants stronger than “looks plausible”.

For a closed transfer between stores A and B:

```text
before = A₀ + B₀
after  = A₁ + B₁

Δexternal = 0  =>  after = before
```

If external flux exists:

```text
A₁ + B₁ = A₀ + B₀ + input - output
```

Also test non-negativity, capacity bounds, overflow behavior, deterministic iteration and exact rounding rules where relevant.

## 5. Substitution tests

When replaceability is architectural, prove it.

A consumer/orchestrator should accept a small alternate/fake implementation through the semantic seam without branching on the production concrete class.

Substitution tests are particularly valuable for:

- world-generation algorithms;
- planners/pathfinders;
- owner storage backends;
- model/calibration policies;
- process strategies that genuinely vary independently.

Do not create a fake merely to justify an unnecessary interface.

## 6. Cross-owner integration tests

Integration tests exist at **real causal seams**, not as a default testing style.

Good examples:

- Movement coordinating Object/Geometry/availability/time;
- a hydrology transfer coordinating independent water-domain owners;
- Genesis handing generated facts into ordinary runtime owners;
- Agent intent going through public mechanic/world capabilities.

An integration test should state which owner boundaries are being proved.

## 7. Architecture fitness tests

Structural laws that can be checked mechanically must fail CI.

The architecture suite must progressively enforce at least:

- only `simulation`, `core`, `lwjgl3` as code/Gradle modules;
- simulation never depends on `core`, `lwjgl3` or libGDX presentation;
- no semantic component cycles;
- no cross-component import of `..internal..`;
- Kernel does not depend on domain owners/mechanics/agents;
- Owners do not depend on Agents/Mechanics/Presentation;
- Continuum does not depend on Terrain/Liquid/Soil/Atmosphere/Agent semantics;
- forbidden generic root packages do not reappear;
- package ownership rules remain consistent with ADR-026.

ArchUnit is the preferred Java-level enforcement tool when a rule is naturally expressible through classes/packages. Lightweight source/build tests may cover repository topology or conventions ArchUnit cannot see.

Architecture tests complement semantic tests; they do not prove behavior.

## 8. Regression tests

A bug fix should add the smallest test that would have failed before the fix when practical.

The regression should target the first incorrect semantic boundary rather than a downstream visual symptom.

Do not delete or weaken regression evidence because implementation changes make it inconvenient.

## 9. Scale and performance tests

Performance is measured against representative workloads, not intuition.

### What to measure

Depending on the system:

- wall-clock latency/throughput;
- work count (visited cells/nodes/pages/entities/processes);
- allocations/temporary object count when measurable;
- resident entries/bytes;
- cache hit/miss/eviction/load counts;
- scheduler active/sleeping process counts;
- worst-case/percentile values across representative seeds/workloads.

### Scaling law

Every potentially unbounded structure/process should have an expected scaling relationship documented.

For example a bounded page cache should aim for:

```text
resident_memory ≈ O(cache_capacity)
```

not:

```text
resident_memory ≈ O(total_world_area)
```

A local query should be bounded by requested active work/resolution, not silently materialize the entire world.

### Performance gate policy

- first establish a reproducible workload;
- measure before introducing complex optimization;
- preserve the semantic contract/tests;
- keep a scale profile when regression risk is material;
- use generous stable CI thresholds for hard time limits and prefer deterministic work/memory counters where possible;
- never optimize by changing authoritative physics for off-screen/distant regions.

Data-oriented/ECS/packed/sparse/page representations are permitted behind owner contracts when evidence supports them.

## 10. Code coverage policy

Coverage is a diagnostic, not a correctness proof.

EvoForge uses JaCoCo line/branch coverage to expose untested production paths, but **does not chase a repository-wide percentage by writing low-value tests**.

The architecture-reset baseline measured on the full `:simulation` test suite was:

```text
line coverage:   85.33% (7809 / 9152)
branch coverage: 63.84% (3518 / 5511)
```

CI deliberately enforces slightly lower regression floors:

```text
line coverage >= 82%
branch coverage >= 60%
```

The gap from the measured baseline absorbs small instrumentation/refactor noise while still failing a material loss of executable evidence. Thresholds should be tightened deliberately when coverage improves; they must not be weakened merely to make a PR green.

Rules:

- new/changed semantic branches should normally receive direct evidence;
- critical owners, deterministic algorithms, failure/rollback paths and public contracts deserve stronger coverage than trivial accessors;
- coverage must never be increased by excluding difficult production code without an explicit reason;
- uncovered high-risk code is reviewed by semantic risk, not hidden behind a global average.

JaCoCo verification is part of the required `Gradle tests` CI job.

## 11. Focused visual acceptance

Visualizer scenarios are appropriate when a human must understand a causal chain or aesthetic result.

A scenario:

- uses real production systems for the behavior being accepted;
- may isolate initial conditions to make causality clear;
- never replaces the mechanic under test with a fake just to look correct;
- records what cannot be established automatically.

Presentation math/deterministic resolution should still be unit-tested. Palette/line-weight/aesthetic judgement may remain manual.

## 12. Generated-world audits

Representative generated worlds combine accepted subsystems across seeds/regions/time and check:

- deterministic replay;
- conservation/boundedness;
- generated-fact compatibility;
- long-running cross-owner interaction;
- memory/work scaling;
- unexpected coupling not visible in focused tests.

These audits complement focused tests; they are not where basic component defects should first be discovered.

## 13. Required test placement

Tests mirror the semantic owner/mechanic/kernel package they verify. Avoid a global technical test bucket except explicit repository-wide architecture/profile/scenario harnesses.

Representative scale profiles live in `:simulation` and are tagged/separated from ordinary fast unit tests.

## 14. Required commands

Normal full verification with the same coverage gate as CI:

```bash
./gradlew test :simulation:jacocoTestCoverageVerification --no-daemon --console=plain
```

Generate an inspectable local coverage report when needed:

```bash
./gradlew :simulation:jacocoTestReport --no-daemon --console=plain
```

Representative simulation/Continuum profiles:

```bash
./gradlew :simulation:scaleProfile --no-daemon --console=plain
```

Documentation build when docs/site change:

```bash
npm ci
npm run docs:build
```

The exact CI workflow is canonical when commands evolve; update this guide in the same change.

## 15. Acceptance rule

A change is not complete merely because tests pass. Required evidence is chosen from the contract:

```text
semantic correctness
+ architectural legality
+ deterministic invariants
+ scale/performance evidence where relevant
+ documentation consistency
+ manual visual evidence only where necessary
```

See [Architecture](../architecture.md), [Development Workflow](development-workflow.md) and [Correctness Harness](correctness-harness.md).
