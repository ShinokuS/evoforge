# Architecture Decisions

Architecture Decision Records (ADRs) preserve **why** durable project rules exist. System pages describe current behavior; ADRs explain durable choices that still constrain it.

## How to read decisions

- ADR numbers are globally unique and never reused.
- `Status: Accepted` means the decision still applies unless a later ADR supersedes it.
- Removed ADR numbers remain intentionally vacant.
- System pages are the current behavioral truth.

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
| 013 | Simulation rate units |
| 022 | Green checkpoint development |
| 023 | Strict modular architecture and replaceable boundaries |
| 024 | Continuum large-world architecture |

ADRs 009–012 and 014–021 belonged to the retired dense V12–V15 generated-world architecture and were removed with that architecture. Their numbers are not reused; Git history remains the historical record.

All active decision files follow the common format defined in the [Documentation Guide](../guides/documentation.md).

## When a new ADR is required

Add an ADR when future work is likely to ask “why are we forbidden/required to do it this way?” Examples include ownership boundaries, determinism/versioning rules, cross-domain composition, persistence or workflow/release rules.

Do not create ADRs for ordinary implementation choices that can be replaced freely behind an existing contract.
