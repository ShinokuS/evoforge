# Stage 0 documentation closeout audit

- Type: Audit
- Status: Historical record
- Date: 2026-08-19
- Normative: No

## Context

After the Stage 0 world-generation architecture and package-ownership refactor was merged, the repository documentation still had several structural risks:

- `docs/systems` had become a long flat list that obscured ownership;
- Journal/decision records used inconsistent headings and metadata;
- some pages assumed technical knowledge before explaining the simulated concept;
- formulas/algorithm choices were often implicit in code rather than written down;
- external model/algorithm sources were scattered or absent;
- historical documents could look as authoritative as current system documentation;
- several older climate/bootstrap names and assumptions no longer matched production code;
- recovering the complete project direction still depended too much on conversation history.

The closeout goal was to make repository documentation sufficient for a new human contributor or a future AI session to reconstruct the current project, verify important mechanics against code/tests and continue from the correct next milestone.

## What was observed

### Information ownership needed to become explicit

The documentation set now uses distinct owners:

```text
project-context.md   fast current-state/context recovery
architecture.md      global cross-system laws
roadmap.md           milestone state + deliberate future scope
systems/*            current subsystem semantics and exact algorithms
decisions/*          durable rationale
journal/*            non-normative history/design/acceptance/audits
guides/*             contributor procedures
references.md        reusable external sources/model lineage
```

`docs/systems` is grouped by Foundations, Traversal, Agents, Environment, World Generation and Tooling. Development Journal material is grouped into Entries, Design Explorations, Acceptance Records and Audits.

### Current System pages needed two levels of explanation

Normative System pages were rewritten to start with ordinary-language meaning and then state exact ownership, lifecycle, equations/units, determinism, invariants, limitations, implementation entry points/tests and sources.

This avoids the false choice between “easy to read but vague” and “precise but understandable only to someone already familiar with the code”.

### Source attribution needed classification

The documentation now distinguishes:

- direct physical/model source;
- algorithm lineage;
- conceptual research influence;
- internal EvoForge design.

For example, the generated Soil hydraulic calibrator directly implements the Saxton–Rawls 2006 model family, while V12 terrain is explicitly an internal deterministic EvoForge synthesis model informed—but not implemented—from later terrain research papers.

### Several important stale assumptions were corrected

The audit reconciled documentation with current production facts including:

- `WorldGenerationIntent` has seven current normalized coordinates;
- revisions span V1–V12, while `WorldGenesis.current(...)` intentionally remains a V7 compatibility convenience and V12 is constructed explicitly where required;
- the current Atlas contains Genesis, Elevation, Geology, Climate Normals, Drainage, Hydrography and Surface Hydrology;
- preparation/runtime handoff is now `WorldPreparationAlgorithms` + `GeneratedWorldPreparation` -> immutable prepared world -> `GeneratedWorldRuntimeBootstrap`;
- current bootstrap still has compatibility initial-Water materialization even though canonical final initial Water belongs to Stage 7 after dry-world acceptance;
- generated surface morphology lives under `world.terrain.surface` and warm-up under `world.diagnostics.warmup`;
- finite Water drinking is already implemented through generic Agent opportunities;
- generated-world Soil variation is causal morphology/drainage formation + physical calibration rather than the older coordinate-local variation path;
- the V12 preview calls production generation and keeps random seeds exact/visible/reproducible.

### ADR history needed honest supersession

ADRs are globally numbered `001..021` with one common structure. Older climate/bootstrap ADRs whose concrete implementation evolved are marked `Superseded` instead of being rewritten to pretend the old type names were always current. ADR-021 is the current preparation/calibration boundary.

### Visual acceptance needed a durable repository record

A dedicated V12 acceptance record now preserves the fact that the Stage 0 base terrain was manually accepted and that later architecture changes were required to preserve it. This removes another piece of critical project context from chat-only memory.

## Outcome

The repository now has one explicit recovery path:

```text
1. Project Context
2. Roadmap
3. Architecture
4. owning System page
5. linked ADR(s)
6. References
7. Journal only for historical reasoning/acceptance/audits
```

The truth hierarchy is also explicit:

```text
production code + tests
        ↓
current normative documentation
        ↓
accepted ADRs
        ↓
Development Journal
        ↓
conversation history / prototypes
```

This closeout intentionally changes documentation structure and explanatory quality, not simulation/world-generation behavior.

## What became canonical

Stage 0 is considered completely closed only after this documentation PR passes repository documentation/CI checks and is merged.

The next implementation stage remains:

> **Stage 1 — Mountain Systems**

Stage 1 starts from the accepted V12 base morphology and the Stage 0 semantic-definition -> domain calibration -> versioned model -> typed replaceable algorithm -> generated fact architecture.

## Links forward

- [Project Context](../../project-context.md)
- [Architecture](../../architecture.md)
- [Roadmap](../../roadmap.md)
- [Documentation Guide](../../guides/documentation.md)
- [Decision Registry](../../decisions/)
- [V12 Visual Acceptance](../acceptance/v12-base-terrain-visual-acceptance.md)
