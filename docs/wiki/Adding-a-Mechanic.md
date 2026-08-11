# Adding a Mechanic

A new mechanic is a new authoritative behavior/state domain, not merely a new content value. This guide explains how to introduce one without expanding central classes into universal state containers.

## First decide: content or mechanic?

If existing mechanics already express the desired behavior, add definition data only.

Example:

```text
new rock material with existing physical properties
    -> data

new independent temperature state that changes over time
    -> mechanic
```

Do not create a new runtime system for every content type, and do not add a field to `WorldObject` or a universal cell merely because the new value needs somewhere to live.

## Define authoritative ownership

Before choosing classes, answer:

> Which mutable facts does this mechanic own?

Examples:

```text
SpatialSystem    owns ObjectId -> XYZ
TerrainSystem    owns XYZ -> LandscapeDefinitionId | absence
```

A new mechanic should have one similarly clear ownership statement.

If you cannot say what the mechanic uniquely owns, the boundary is probably not ready.

## Define the narrow read contract

Consumers should normally depend on a read-only interface rather than the mutable system implementation.

Pattern:

```text
MechanicSystem   authoritative mutation
MechanicLookup   narrow read access
MechanicState    optional internal storage
```

The exact class split is not mandatory, but ownership and read dependency should remain clear.

## Definition integration

If the mechanic has immutable per-definition configuration, add a mechanic-specific definition compiler/store rather than expanding a universal definition object.

The normal flow is:

```text
source JSON aspect
    ↓
DefinitionAspectCompiler
    ↓
mechanic-owned compiled definition data
    ↓
runtime system references typed DefinitionId
```

Registration is explicit. Reflection or a giant central switch is not required.

## Avoid reverse dependencies

A mechanic may read another subsystem through its narrow lookup when the dependency is semantically one-way.

Do not create mutual system dependencies to coordinate lifecycle implicitly.

If two owners must change atomically, that usually indicates the need for an orchestration/command layer above both systems rather than direct callbacks between them.

## Mutation and events

Authoritative mutation occurs in the owner. If the change produces an event, the event describes the fact after mutation.

Do not make events hidden commands that other systems must consume in order for the original mutation to become valid.

## Scheduler integration

If the mechanic needs future activation, schedule a registered handler. The scheduler should not gain knowledge of the mechanic's domain type beyond a handler id and payload/task representation already supported by its generic contract.

Avoid mandatory per-tick scans of all objects or all terrain cells. Schedule only active processes when possible.

## Spatial queries

If the mechanic only needs object position queries, consume existing spatial lookup/index boundaries.

If it needs a domain-specific index, own that index inside the mechanic as derived state. Do not add unrelated query structures to `SpatialSystem` simply because they contain coordinates.

## Testing

A new mechanic should include:

```text
owner unit tests
read-contract tests
mutation invariant tests
definition compilation/loading tests when applicable
integration tests with each consumed boundary
determinism tests when ordering/randomness matters
lifecycle tests for create/remove/recreate paths
```

If the mechanic is performance-sensitive, first add a representative functional workload. Benchmark and optimize only after the semantic path is stable.

## Performance

Start with the clearest implementation that respects the scale envelope. If profiling later identifies allocation or lookup pressure, replace internal storage behind the existing semantic boundary.

Do not expose packed keys or specialized arrays in public contracts unless they are themselves domain semantics.

## Commands and Control

Once the Control Backbone exists, external changes should enter through Commands rather than presentation, AI, or scripts mutating the mechanic directly.

```text
Player / AI / Script / Scenario
            ↓
         Command
            ↓
       orchestration
            ↓
      MechanicSystem
```

This is especially important when one action must coordinate multiple authoritative owners.

## Documentation

Update the documentation level appropriate to the change:

```text
ARCHITECTURE.md        if a stable boundary/invariant changes
TECHNICAL_REFERENCE   for implementation/package/test details
Wiki                  for explanatory design and extension guidance
```

Avoid creating speculative Wiki pages for systems that do not exist yet; deferred decisions belong in [Roadmap and Deferred Decisions](Roadmap-and-Deferred-Decisions.md) until a consumer appears.
