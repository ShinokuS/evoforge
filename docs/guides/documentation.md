# Documentation Guide

EvoForge documentation has two equally important jobs:

1. a person with no programming background should be able to understand **what the simulated system means and why it behaves that way**;
2. a developer should be able to recover **the exact current contracts, algorithms, equations, ownership, limitations, tests and sources** without reconstructing the project from chat history.

The documentation is therefore not a prose copy of Java files and not a high-level marketing overview. It is the human-readable model of the current implementation.

## Canonical owners

```text
project-context.md     current project baseline + context-recovery path
architecture.md        global cross-system rules
roadmap.md             milestone status + deliberately deferred scope
systems/*              exact current subsystem semantics and algorithms
decisions/*            durable accepted rationale (ADRs)
guides/*               contributor procedures
journal/*              historical/exploratory/audit/acceptance records
references.md          reusable external model/algorithm sources
```

Code/Javadoc/tests remain the exact implementation reference. Normative Markdown must agree with them but should explain meaning that is hard to recover by reading class names alone.

## Required System-page structure

Every normative page under `systems/` should be readable in this order. Small systems may merge adjacent sections, but the information must still exist.

### 1. `# System name`

Use the human concept, not an internal class name, as the title.

### 2. `## In plain language`

Explain the mechanic without programming vocabulary. Answer:

- What part of the world does this model?
- Why does EvoForge need it?
- Give one concrete example.

A reader should understand the purpose before seeing `Lookup`, `System`, `ppm`, interfaces or Java identifiers.

### 3. `## Current status`

State what is implemented **now** and what is only provisional/deferred. Do not describe a planned feature as if it exists.

### 4. `## Mental model / lifecycle`

Use a short diagram when useful:

```text
input fact
   ↓
owned transformation
   ↓
authoritative result
   ↓
downstream consumer
```

### 5. `## Ownership and boundaries`

Name the authoritative owner(s), read capabilities and important non-owners. Explicitly state common tempting mistakes such as “the visualizer does not own this”.

### 6. `## Exact model and algorithms`

Document the actual implemented algorithm, not just its class name.

Include, when relevant:

- units and coordinate conventions;
- deterministic ordering/tie-breaking;
- formulas;
- clamps/limits;
- fixed-point scales;
- scheduling cadence;
- failure/revalidation rules;
- storage semantics when they materially affect public behavior.

For example, do not write only “A* is used”. Explain what graph is searched, what `g`, `h` and `f` mean in EvoForge, why the heuristic is admissible, how ties/revisions are handled and how runtime Movement still revalidates the proposed route.

### 7. `## Invariants`

List laws that must remain true across refactors. These should correspond to tests where practical.

### 8. `## Interactions with other systems`

Explain causal boundaries so future work does not duplicate responsibilities.

### 9. `## Current limitations / deliberately absent`

Say what the system **does not** model. This prevents a future reader from mistaking missing behavior for undocumented behavior.

### 10. `## Code and tests`

Point to owning packages/classes and representative tests/audits. Do not dump every class name; identify the implementation entry points that let a future contributor verify the prose quickly.

### 11. `## Sources`

Classify each important external source:

- **Direct model** — production formula/algorithm intentionally implements this source family.
- **Algorithm lineage** — a standard algorithm is extended/adapted by EvoForge.
- **Conceptual influence** — research informs direction but is not implemented directly.
- **Internal EvoForge design** — no external solver/model is claimed; code/tests are primary.

Use [References](../references.md) for sources shared across pages. Write the equations EvoForge actually uses even when a paper is cited.

## Formula style

Always define symbols in ordinary language.

Bad:

```text
f = g + h
```

Better:

```text
f(n) = g(n) + h(n)

g(n) = exact accumulated transition cost from the start to node n
h(n) = conservative lower-bound estimate from n to the goal
```

If the implementation uses integer/fixed-point arithmetic, document the scale and rounding rule. A mathematically equivalent floating-point formula is not enough when rounding is part of determinism.

## Source discipline

A paper citation must never be used to make the implementation sound more physically accurate than it is.

When adding a source:

1. verify the primary/original publication when possible;
2. say exactly what EvoForge borrows;
3. say what EvoForge changes or omits;
4. if the model is project-specific, label it project-specific instead of searching for a vaguely related citation.

Reusable references belong in `references.md`.

## Decision records (ADR format)

Decision files remain flat and globally numbered:

```text
001-authoritative-ownership.md
002-shape-transition-algebra.md
...
021-world-preparation-and-calibration-boundary.md
```

No duplicate numbers are allowed.

Every ADR uses this structure:

```text
# ADR-NNN: Short decision title

- Status: Accepted | Superseded | Deprecated
- Scope: short domain label
- Decision: one-sentence summary

## Context
What problem/ambiguity existed?

## Decision
What rule was chosen?

## Why
Why is this preferable here?

## Consequences
What becomes easier/harder or required?

## Alternatives considered
What was rejected and why?

## Current implementation
Where is the decision visible in code/tests/docs today?

## Related documentation
Links to owning System/Architecture pages.
```

Do not rewrite history to pretend alternatives were never considered, but do reconcile `Current implementation` if the code later moves.

## Development Journal format

The Journal is explicitly non-normative history and is grouped by purpose:

```text
journal/entries/       dated development narratives
journal/design/        exploratory design records that may be superseded
journal/acceptance/    manual visual/behavior acceptance records
journal/audits/        point-in-time repository/system audits
```

Every Journal file begins with:

```text
# Clear descriptive title

- Type: Entry | Design exploration | Acceptance | Audit
- Status: Historical record | Current acceptance baseline
- Date: YYYY-MM-DD, YYYY-MM, or "not recorded"
- Normative: No
```

Then use:

```text
## Context
## What was observed / explored
## Outcome
## What became canonical
## Links forward
```

A Journal record may describe an old implementation accurately for its time. Add a forward link to the current System/ADR when useful rather than silently rewriting historical facts into modern facts.

## When to edit documentation

### New subsystem

Normally ship together:

```text
code + tests
systems/<group>/<name>.md
roadmap update if milestone state changed
ADR only if a durable architectural choice needs explanation
```

### Existing semantics changed

Update the owning System page in the same PR. If a global architectural rule changes, update `architecture.md`; if the reason must survive, add/supersede an ADR.

### Developer operation changed

Update the relevant Guide. Operational docs are normative even when simulation semantics do not change.

### Implementation-only refactor

Do not churn semantic docs merely because packages/classes moved. Update `Code and tests` pointers and context-recovery paths when a move would otherwise make docs misleading.

### Exploration / experiment

Use the Journal. Do not present an experiment as current System truth.

## Milestone documentation audit

Before a milestone is considered complete:

1. reconcile `project-context.md` and `roadmap.md` with actual state;
2. review every System page touched by changed semantics;
3. inspect global Architecture only for real cross-system changes;
4. verify guides/commands/UI instructions against current tooling;
5. ensure durable decisions have unique, standardized ADR records;
6. verify cited formulas/sources match what code actually implements;
7. build VitePress and fail on broken links;
8. run ordinary CI + relevant generated/diagnostic audits;
9. preserve historical Journal records while adding forward links where needed.

A green docs build proves structure/links, not semantic truth. Semantic truth requires the code/test comparison.

## Context-recovery rule

If a future contributor or AI session cannot determine the project's current direction after reading:

```text
project-context.md
architecture.md
roadmap.md
relevant systems/* page
```

then the documentation set is incomplete. Add the missing current-state information to the owning normative page instead of relying on chat history.

## Publication

Repository Markdown is canonical. VitePress/GitHub Pages is generated presentation. There is no separate Wiki or translation mirror.

See [Development Workflow](development-workflow.md), [Testing](testing.md) and [References](../references.md).
