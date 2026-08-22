# Contributing to EvoForge

`AGENTS.md` is the mandatory repository entry point for **every** human or AI-assisted change. Read it first.

Then read, in order:

1. [`docs/project-context.md`](docs/project-context.md);
2. [`docs/architecture.md`](docs/architecture.md);
3. the relevant current page under [`docs/systems/`](docs/systems/);
4. relevant accepted ADRs under [`docs/decisions/`](docs/decisions/);
5. the actual owner package, public contracts, tests and current dependencies.

Chat history, historical Journal entries and old branches are not current architecture.

## Before coding: required change declaration

A non-trivial change is not ready to implement until the contributor can state:

```text
semantic owner
architectural block type: OWNER | MECHANIC | KERNEL | PROJECTION | COMPOSITION
authoritative facts changed
public contracts touched
allowed dependencies
invariants that must remain true
expected scale / performance risk
focused verification evidence
normative documentation that must change
```

If the owner/type is unclear, resolve architecture before creating files.

## Branches

- normal production work: `feature/<focused-name>` from current `develop` -> PR to `develop`;
- uncertain/disposable research: `experiment/<focused-name>`;
- urgent accepted-baseline repair: `hotfix/<focused-name>` from `main`, then reconcile into `develop`;
- `develop -> main` only for an accepted milestone.

Do not routinely push directly to `main` or `develop`.

## Mandatory architecture gate

Every production change must preserve ADR-025 and `docs/architecture.md`:

- one authoritative owner per mutable fact;
- semantic ownership is the primary package axis;
- owner-local Genesis/physics/storage/runtime stay with the owner;
- cross-owner laws are explicit Mechanics and do not duplicate participant truth;
- Kernel is domain-neutral;
- Projections are rebuildable and never second truth;
- Composition wires blocks and owns no domain policy;
- dependencies use narrow typed public contracts and remain acyclic;
- no foreign `internal` imports;
- no service locators/universal contexts/global causal event bus;
- no generic dumping-ground packages;
- no new Gradle module merely to organize a simulation domain;
- replace independently meaningful algorithms without proliferating speculative interfaces.

If a feature requires breaking these rules, stop and make the architectural change explicit through an ADR instead of smuggling it into implementation.

## Mandatory green-checkpoint loop

```text
read current rules/state
        ↓
define one contract/invariant
        ↓
smallest coherent implementation
        ↓
nearest focused tests
        ↓
architecture checks
        ↓
affected integration/scale checks
        ↓
reconcile docs
        ↓
green coherent commit
```

Do not stack new scope on unexplained red CI. Do not weaken evidence merely because an implementation fails it.

A refactor claiming behavior preservation should separate mechanical package/type moves from semantic behavior changes whenever practical.

## Naming/file placement gate

A new file must have a semantic home before it exists. Use the decision table in `AGENTS.md`.

Forbidden vague dumping-ground names include `util`, `common`, `misc`, `helpers`, `shared`, `framework`, generic `Manager`, generic `Service` and universal `Context` bags.

Public names describe stable semantics; implementation names describe precise algorithms/representations. File names match their primary type/responsibility.

## Testing and performance gate

Use the nearest appropriate evidence:

- pure algorithm/value rule -> unit/property-style test;
- owner lifecycle/state -> owner/component test;
- replaceable seam -> substitution test;
- cross-owner behavior -> focused integration test;
- deterministic process -> replay/order test;
- physical flow -> conservation/bounds tests;
- architectural law -> executable architecture test;
- hot/unbounded-looking path -> representative scale/performance profile;
- aesthetic property -> manual visual acceptance only when automation cannot establish it.

Optimization is evidence-driven and must preserve the same semantic contracts. Performance-sensitive designs consider work count, allocation and memory bounds as well as elapsed time.

See [`docs/guides/testing.md`](docs/guides/testing.md).

## Documentation gate

Normative docs are part of the implementation contract.

When semantics, architecture, developer operation, formulas, dependencies, performance model or limitations change, update the owning normative page in the same PR.

System docs must explain the actual implemented model, including formulas/units/order/invariants/interactions/performance/limitations/code/tests/sources where relevant. Do not cite a paper as decoration or imply fidelity not present in code.

Historical Journal records are not rewritten into current truth.

See [`docs/guides/documentation.md`](docs/guides/documentation.md).

## Before merge to `develop`

- owner/block classification still matches the final diff;
- package/file names reveal responsibility;
- no new cycle/internal leak/duplicate authority exists;
- focused tests are green;
- full `./gradlew test --rerun-tasks --console=plain` is green for cross-system work;
- required scale/performance profiles are green;
- `npm run docs:build` is green when docs/site changed;
- temporary migration/probe/debug code is removed;
- normative docs and source pointers match the final code;
- manual visual/performance acceptance is recorded when required;
- PR remains Draft while any required architecture/test/docs checkpoint is incomplete.

Feature PRs are normally squash-merged to `develop`. Milestone `develop -> main` uses the accepted milestone policy in the [Development Workflow](docs/guides/development-workflow.md).
