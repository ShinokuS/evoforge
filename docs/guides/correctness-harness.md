# Correctness Harness

EvoForge uses exact reference solvers where a practical oracle exists and invariant/metamorphic tests where the system has no single exact expected output.

The correctness harness complements fixed regression scenarios; it does not replace them.

## Test pyramid

```text
pure mathematical properties
        ↓
subsystem invariant / metamorphic properties
        ↓
small generated integration worlds
        ↓
fixed acceptance scenarios
        ↓
manual visual smoke
```

A generated test must assert a durable contract rather than a temporary gameplay preference. For example, "a higher normalized Utility input must not lower the score" is a contract; "critical Thirst must always beat Hunger" is a balance policy and is not a generic correctness property.

## Reproducibility

Generated tests use fixed `SplittableRandom` seeds. A failure message must include enough context to reproduce the exact sample (`seed`, sample index and, where relevant, update/tick).

We deliberately do not add a property-testing framework yet. JUnit plus fixed deterministic seeds is sufficient while the generated domains are small. A dedicated library becomes justified when shrinking or richer generators materially improve failure diagnosis.

## Utility properties

The common Utility layer currently guarantees:

- normalized inputs and bounded scores;
- deterministic scoring;
- monotonicity in `pressure`, `relief` and `travel` usefulness when other scored terms are unchanged;
- monotonic ratio calculation;
- non-increasing travel usefulness as distance increases.

`OpportunityEvaluation.expectedBenefit` is currently provider evidence/diagnostic data and is not an independent term in `UtilityMath.score()`. Changing the scoring formula or making expected benefit an additional scoring dimension is a balance/decision-contract change and must be explicit rather than accidental.

Cross-motivation policy is tested separately through integration cases. A generated property must not silently encode one particular Hunger/Thirst weighting as a universal law.

## Agent properties

Current autonomous-agent correctness properties include:

- identical initial world state and inputs produce identical state/decision traces;
- a locally non-enterable interaction site cannot become the committed MoveTo target;
- a proven failed movement site is not retried indefinitely in the same local recovery context;
- an already committed, still-executing intent is not rescored merely because another opportunity becomes slightly better.

The last property describes the current commitment contract. It is not a complete future interruption policy. Deliberate preemption of a valid intent remains a separate design problem that requires a real consumer.

## Liquid / hydrology properties

Generated free-liquid tests check contracts that do not require a single expected cell-by-cell solution:

- total liquid mass is conserved through redistribution;
- every cell amount remains inside physical bounds;
- identical initial states produce identical solver traces;
- mirrored physical states remain physically equivalent within the deterministic integer model;
- bounded generated pools converge to dormancy;
- a dormant stable state is idempotent until an external mutation wakes it.

Fixed hydraulic regression tests remain responsible for exact known cases such as ramps, vertical one-cell-per-update behavior, multi-exit limiting and accepted deadbands.

## Failure policy

When a generated property fails:

1. reproduce the exact reported seed/sample;
2. reduce it to the smallest understandable deterministic regression when practical;
3. decide whether the failure exposes an implementation bug or an incorrectly stated property;
4. only then change production behavior or the property contract.

Randomness must never be used to turn a flaky behavior into a probabilistic CI gate.
