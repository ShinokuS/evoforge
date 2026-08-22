# ADR-025: Owner-first modular simulation architecture

- Status: Superseded in part by [ADR-026](026-semantic-capability-architecture.md)
- Scope: Repository-wide simulation architecture, package ownership and dependency direction

## Historical decision

ADR-025 corrected the earlier horizontal Gradle split (`foundation/world/generation/simulation`) by returning authoritative simulation code to one `:simulation` module and making semantic concepts more important than technical stages such as Generation, Physics or Storage.

That correction remains valid.

However, during the same Draft architecture reset a deeper issue was identified: ADR-025 required every production block to be exactly one of `OWNER`, `MECHANIC`, `KERNEL`, `PROJECTION` or `COMPOSITION`. That mutually-exclusive taxonomy can place a reusable capability under the first mechanic that consumes it.

For example, putting Occupancy, Navigation or Pathfinding inside Movement would make future Drop/Place/Spawn/Build/AI consumers depend on Movement internals or force later extraction/refactoring. The first consumer is not a valid ownership criterion.

ADR-026 therefore supersedes the rigid block taxonomy and introduces **independent semantic modules with consumer-neutral capabilities**. Architectural roles such as authority, capability, algorithm, projection and process are orthogonal rather than mutually exclusive.

## Still-valid decisions from ADR-025

These laws remain current and are incorporated by ADR-026:

- authoritative simulation code lives in one `:simulation` Gradle module;
- `core` is presentation/observer code and `lwjgl3` is launcher code;
- packages are organized primarily by semantic meaning, not global technical layers;
- one mutable fact has exactly one authoritative source;
- domain-specific Genesis/physics/storage stay with the semantic concept rather than in global technical trees;
- public semantic surfaces are narrow and implementation details are hidden;
- cross-module dependencies are explicit, typed and acyclic;
- Kernel remains domain-neutral;
- universal contexts/service locators/global causal event buses are forbidden;
- Composition owns wiring, not domain policy;
- performance representations remain hidden implementation details;
- architecture laws that can be checked mechanically should fail CI when violated.

## Superseded decisions

The following ADR-025 ideas are no longer canonical:

- every production block must have exactly one of five mutually-exclusive architectural types;
- reusable cross-owner functionality should automatically become a Mechanic;
- a reusable concept may be placed inside the feature/mechanic that first uses it;
- Navigation/Pathfinding/Occupancy should be grouped under Movement merely because Movement currently consumes them.

## Current authority

Read [ADR-026: Semantic capability architecture](026-semantic-capability-architecture.md), root `AGENTS.md`, and `docs/architecture.md` before changing package boundaries.

PR #132 remains Draft while the repository is reclassified under ADR-026.
