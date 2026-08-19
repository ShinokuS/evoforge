# Architecture Decisions

Architecture Decision Records (ADRs) preserve **why** durable project rules exist. System pages describe the current behavior; ADRs explain the choice that created or constrained that behavior.

## How to read decisions

- ADRs are globally numbered and never reuse a number.
- `Status: Accepted` means the decision still applies unless a later ADR explicitly supersedes it.
- Moving a class/package does not invalidate an ADR if the semantic decision is unchanged; update its `Current implementation` pointers instead.
- An ADR is not a substitute for a System page. The System page remains the current behavioral truth.

## Registry

| ADR | Decision |
|---|---|
| 001 | Authoritative ownership |
| 002 | Shape transition algebra |
| 003 | Command boundary |
| 004 | Typed presentation bindings |
| 005 | Development branching model |
| 006 | Runtime logging |
| 007 | Liquid transport and composition boundary |
| 008 | Completion-driven agent wakeups |
| 009 | World genesis provenance and randomness |
| 010 | World Atlas generated facts |
| 011 | World-generation algorithm contracts |
| 012 | Closed-world drainage topology |
| 013 | Simulation rate units |
| 014 | Hydrologic climate normals |
| 015 | Hydro-climate runtime forcing |
| 016 | Atlas terrain materialization |
| 017 | Generated-world diagnostic audits |
| 018 | Generated-world runtime bootstrap |
| 019 | Generated-world warm-up is explicit observation |
| 020 | Terrain palettes hide generated complexity |
| 021 | World preparation and calibration boundary |
| 022 | Green checkpoint development |
| 023 | Strict modular architecture and replaceable boundaries |

All decision files are being normalized to the common format defined in the [Documentation Guide](../guides/documentation.md).

## When a new ADR is required

Add an ADR when future work is likely to ask “why are we forbidden/required to do it this way?” Examples include ownership boundaries, determinism/versioning rules, cross-domain composition, or workflow/release rules.

Do not create ADRs for ordinary implementation choices that can be replaced freely behind an existing contract.
