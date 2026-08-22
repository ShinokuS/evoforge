# Development Workflow

This guide defines how EvoForge changes are designed, implemented, verified and merged. Root `AGENTS.md` and `docs/architecture.md` are mandatory prerequisites.

## 1. Context recovery before work

Every repository action begins by reading current sources in this order:

```text
AGENTS.md
  ↓
project-context.md
  ↓
architecture.md
  ↓
relevant systems/** + accepted ADRs
  ↓
current owner package + tests + imports
```

Do not begin from chat memory, an old branch or a historical Journal entry.

## 2. Classify the change before coding

Write down:

```text
Owner: <semantic owner>
Type: OWNER | MECHANIC | KERNEL | PROJECTION | COMPOSITION
Facts changed: <authoritative facts or none>
Public contracts touched: <exact capabilities>
Dependencies: <public contracts only>
Invariants: <what must remain true>
Scale/performance risk: <expected workload/memory behavior>
Evidence: <tests/profile/manual acceptance>
Docs: <normative pages affected>
```

If these cannot be answered, implementation has started too early.

### Placement decision

```text
owns mutable fact?                     -> semantic owner
behavior mutates one owner?            -> owner-local implementation
coordinates independent owners?        -> mechanics/<law>
domain-neutral execution?              -> kernel/<responsibility>
rebuildable derived representation?    -> projection area beside its semantic owner/consumer
wiring only?                            -> composition
otherwise                              -> resolve responsibility first
```

Never place code by analogy to an old confused package.

## 3. Branch model

```text
main      accepted milestone baseline
  ↑
develop   next integration milestone
  ↑
feature/* | experiment/*
```

- normal production work -> `feature/<focused-name>` from current `develop`;
- uncertain/disposable investigation -> `experiment/<focused-name>`;
- stable-baseline emergency -> `hotfix/<focused-name>` from `main`, then reconcile into `develop`;
- `develop -> main` only after milestone acceptance.

Architectural resets may use one Draft PR with multiple green checkpoint commits when splitting the work across independently merged PRs would leave `develop` in an intentionally incoherent architecture.

## 4. Green checkpoint contract

Development is a sequence of small understood checkpoints:

```text
one contract/invariant
      ↓
smallest coherent implementation
      ↓
nearest focused evidence
      ↓
architecture check
      ↓
green commit
      ↓
next contract
```

Rules:

1. one ownership/semantic question per checkpoint;
2. do not add new scope on unexplained red;
3. run the nearest focused test first;
4. preserve existing acceptance evidence for behavior-preserving refactors;
5. change tests because the contract changed, never because code cannot satisfy them;
6. keep experiments/probes out of final production history;
7. separate mechanical renames/moves from behavior changes where practical;
8. after focused green, escalate to affected integration/full/scale/docs checks according to scope.

## 5. Naming and file creation

Package and file names are architecture.

### Package names

Use semantic nouns (`terrain`, `liquid`, `movement`) or precise neutral mechanisms (`time`, `scheduling`).

Forbidden generic roots/dumping grounds:

```text
util common misc helpers shared framework managers services base
```

### Type names

Preferred patterns:

```text
<Concept>Id                     stable identity
<Concept>Definition             immutable authored semantic definition
<Concept>View                   semantic read capability/view
<Concept>Lookup                 actual key/coordinate lookup contract only
<Concept>Mutations              explicit grouped mutation capability when necessary
<Operation>Result/Attempt       operation-local outcome
<Concept>Index/Cache            rebuildable derived representation
<AlgorithmName><Role>           precise replaceable algorithm
<Scope>Assembly                 real wiring boundary only
```

Use `<Concept>System` only for a real runtime owner/process boundary; do not use `System` as a generic suffix.

Avoid vague `Manager`, `Utils`, `Common`, `Data`, `Stuff`, `Processor` and generic `Service` names.

One file contains one primary top-level responsibility and matches its primary type name.

## 6. Dependency design

Before adding an import across semantic blocks, ask whether the consumer needs the concrete type or only a narrower semantic capability.

Allowed:

```text
consumer -> owner public contract
```

Forbidden:

```text
consumer -> owner.internal.*
A -> B -> A
consumer -> universal context/service locator
```

A cycle is not accepted as “necessary coupling”; identify the wrong owner, invert/extract the smallest contract, or create the missing Mechanic.

## 7. Public API discipline

Default to package-private/internal implementation. `public` means an intentional semantic contract or required composition entry point.

Do not create an interface solely because a class exists. Create a replaceable seam when:

- more than one implementation is already meaningful; or
- the algorithm/process is independently meaningful and expected to vary without redefining the consumer.

The goal is a small public surface hiding substantial implementation detail.

## 8. Determinism checklist

For deterministic code, verify as applicable:

- stable ordering/tie breaking;
- no reliance on hash iteration order;
- random-looking behavior uses explicit deterministic seed/address inputs;
- cache eviction/reload does not change semantic results;
- camera/render/request order does not change world truth;
- overflow/rounding/fixed-point behavior is explicit;
- scheduler/process ordering is tested when causally meaningful.

## 9. Performance checklist

Before optimizing, define a representative workload and metric. Before adding a potentially unbounded structure/process, prove how it scales.

Review:

- asymptotic work with active world size;
- mandatory per-tick scans;
- allocations/boxing/temporary collections on hot paths;
- cache memory bounds and eviction;
- storage sparsity/density assumptions;
- work triggered by inactive/distant world regions;
- whether sleeping/analytical advancement can preserve exact semantics;
- whether a scale profile should become a regression guard.

A faster implementation must satisfy the same semantic tests.

## 10. Testing escalation

Typical order:

```text
unit/owner test
    ↓
substitution/determinism/conservation test
    ↓
focused cross-owner integration
    ↓
architecture fitness tests
    ↓
full ./gradlew test
    ↓
representative scaleProfile where relevant
    ↓
docs build
    ↓
manual visual acceptance when necessary
```

See [Testing Strategy](testing.md).

## 11. Debugging rule

When a scenario fails, identify **the first boundary that produces the wrong fact**.

Instrument/test that boundary directly. Do not add a downstream repair pass until the upstream output is understood and the repair is itself a deliberate owner/mechanic responsibility.

For a generated fact this normally means:

```text
semantic intent
  -> owner-local calibration/model policy
  -> deterministic algorithm
  -> generated fact
  -> authoritative owner handoff
  -> runtime interaction
  -> presentation
```

A visual symptom does not make presentation the owner of the defect.

## 12. Documentation in the same change

Update normative docs when the change affects semantics, architecture, operation, formula/units, performance contract, limitations or source lineage.

Implementation-only moves update code/test pointers and recovery paths where needed without rewriting semantic history.

See [Documentation Guide](documentation.md).

## 13. Pull request acceptance

Before leaving Draft state:

- final diff still matches declared owner/block type;
- no duplicate authority/cycle/foreign-internal dependency exists;
- naming/package placement is discoverable;
- replacement seams do not leak concrete implementations;
- focused and full required tests are green;
- architecture fitness tests are green;
- relevant scale/performance gates are green;
- docs build is green and normative docs match final code;
- temporary scripts/workflows/probes are removed;
- required manual visual/performance acceptance is complete.

Feature PRs are normally squash-merged to `develop`.

## 14. Milestone acceptance

`develop -> main` occurs only when the complete milestone is accepted:

1. required feature PRs merged/closed deliberately;
2. full test/architecture/scale gates green;
3. normative docs/roadmap reconciled;
4. required manual acceptance complete;
5. no known correctness defect hidden behind TODO;
6. final architecture audit passes.

Use an explicit merge commit for the milestone boundary so `main` records accepted milestones while `develop` retains coherent feature history. Tag the accepted `main` commit with the chosen immutable version.

## 15. Stop conditions

Stop adding feature scope when you encounter:

- duplicate owner state;
- a semantic package cycle;
- need for foreign internals;
- expanding universal context/service bag;
- generic orchestration special-casing every concrete implementation;
- a package that cannot be explained by one responsibility;
- unbounded work/memory without a scale model;
- normative docs that no longer explain the current system.

Repair the smallest architectural cause first.

See [ADR-022: Green checkpoint development](../decisions/022-green-checkpoint-development.md) and [ADR-025: Owner-first modular simulation architecture](../decisions/025-owner-first-modular-simulation.md).
