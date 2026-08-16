# Decision 009 — World genesis provenance and deterministic randomness

**Status:** Accepted

## Problem

Generated worlds need reproducible initial conditions without making generation order, Java library RNG behavior, chunk layout or a future save format part of simulation truth.

A single mutable pseudo-random sequence is fragile: inserting one unrelated generation call shifts every later result. Treating a seed as the whole world is also insufficient once generated facts evolve or generator code changes.

## Decision

World creation distinguishes the requested specification from the provenance of the resulting world.

`WorldSpec` describes what is requested before generation. Its first contract contains only inclusive finite `WorldBounds`; additional generation settings are added only when a real generator requires them.

`WorldGenesis` records the immutable `WorldSpec`, master seed, `GenerationRevision` and `RngRevision`. Generator and RNG revisions are separate compatibility dimensions.

World-generation randomness is a pure scoped function. `GenerationRandom` v1 samples from:

```text
master seed
+ generation stage id
+ random purpose id
+ three stable signed 64-bit scope coordinates
+ non-negative ordinal
→ deterministic long
```

Direct cell-scoped generation uses global XYZ values. Macro stages may instead use their own stable lattice coordinates; those sampling coordinates are random-scope identity, not a second authoritative world position. Stage and purpose identifiers are stable namespaced semantic keys.

Sampling order is not state: asking for another unrelated sample cannot shift an existing sample. `evoforge:rng-v1` is frozen by golden-vector tests. Widening the scope-coordinate API from `int` to `long` preserves every existing int-scoped v1 sample bit-for-bit. An unsupported RNG revision is rejected rather than silently interpreted with the current algorithm.

The seed and provenance reproduce generation inputs; they do not replace already-authored world facts in persistence. Once generation creates authoritative facts/state, later runtime mutation belongs to the relevant domain owner.

## Consequences

- adding an unrelated generation stage does not globally reshuffle random results;
- exact RNG behavior is versioned independently from higher-level generator algorithms;
- generation can be reproduced and debugged from stable semantic scopes;
- macro lattices do not require inventing region/chunk identities merely to address randomness;
- future saves can retain the provenance that authored their canonical world state;
- global XYZ remains authoritative world addressing without making chunk identity gameplay semantics;
- generator revisions must advance when the same declared inputs would intentionally author different facts.

## Rejected directions

A process-wide mutable `Random` was rejected because call order becomes hidden generation state.

Seed-only persistence was rejected because generator upgrades would rewrite an existing saved world's past.

Chunk/region keys, streaming lifecycle, packed storage, climate settings and calendar semantics are deliberately not defined by this decision. They require evidence from the World Atlas, materialization and time slices rather than being predicted by the genesis foundation.
