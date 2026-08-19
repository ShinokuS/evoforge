# Objects and Identity

## In plain language

An EvoForge object needs a stable answer to one basic question: **which individual thing are we talking about?** A Cow, tree or other runtime object can move, become hungry, grow, reserve space or disappear, but those mechanics should not all be stored inside one giant object record.

`ObjectRepository` therefore owns only identity/existence. Other systems attach their own state to the same `ObjectId`.

## Current status

Runtime objects are intentionally small. `WorldObject` carries identity plus definition identity. The repository owns creation/existence and uses slot/generation-style IDs so an old stale ID cannot silently start referring to a different object that later reused the same storage slot.

## Mental model

```text
ObjectRepository
  "object 42 exists and uses definition core:cow"
        │
        ├─ Spatial          "object 42 is at (x,y,z)"
        ├─ Movement         "object 42 is moving"
        ├─ Occupancy        "object 42 reserves a cell"
        ├─ Need system      "object 42 is thirsty"
        ├─ Stock/Growth     "object 42 has finite resource state"
        └─ Agent system     "object 42 has a current intent"
```

All of those systems may use the same identity without becoming part of identity ownership.

## Ownership and boundaries

### Owns

- `ObjectId` lifecycle;
- object existence;
- immutable association to object definition identity.

### Does not own

- XYZ position or orientation;
- Movement/MoveTo actions;
- Occupancy claims/reservations;
- Needs and agent intent/search state;
- finite consumable quantity or Growth progression;
- inventories;
- Terrain, Water, Soil or other environmental facts.

Consumers that only need to know whether an object exists use read-only lookup capabilities rather than mutable repository internals.

## Exact identity invariant

The important stale-reference property is conceptual:

```text
storage slot reused
+ generation changed
→ old ObjectId is not equal to the new object's identity
```

A caller may therefore hold an `ObjectId` without risking accidental aliasing to a later object merely because internal storage reused a slot.

The exact packed/storage representation is an implementation detail; the public semantic law is that identity survives storage reuse safely.

## Definitions versus instances

Object definition identity selects immutable configuration through mechanic-owned definition stores. For example, a Cow definition may contribute Movement, Vision, Need or Occupancy aspects.

That does **not** mean `WorldObject` dispatches behavior by concrete content name. Each mechanic resolves the aspects it owns.

## Invariants

- Exactly one repository owns runtime object existence.
- A stale object ID never silently aliases a newly created object.
- Per-object mutable mechanic state stays in the owning mechanic, keyed by `ObjectId`.
- Adding a new mechanic does not expand `WorldObject` into a god object.
- Definition identity is immutable configuration identity, not mutable object state.

## Interactions

[Spatial](spatial.md) is the authoritative position owner. [Movement](../traversal/movement.md), [Occupancy](../traversal/occupancy.md) and [Agents](../agents/agents.md) use `ObjectId` but keep their own state/semantics.

## Current limitations

The current identity model does not define persistence/network object IDs, distributed authority, multi-process IDs or content-pack migration. Those need explicit external-identity contracts when real consumers arrive.

## Code and tests

Primary implementation lives under:

```text
simulation/.../world/object/
```

Object/repository tests cover lifecycle, stale IDs and lookup behavior; integration tests exercise the same IDs across Spatial, Movement, agent and resource mechanics.

## Sources

**Internal EvoForge design.** The narrow identity-owner rule is a project architectural choice. No external entity-component framework is claimed as the implemented model.

See [Architecture](../../architecture.md), [Definitions](definitions.md), and [ADR-001: Authoritative ownership](../../decisions/001-authoritative-ownership.md).
