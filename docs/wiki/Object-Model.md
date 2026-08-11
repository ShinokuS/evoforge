# Object Model

EvoForge uses real domain objects with stable runtime identity, but deliberately keeps mutable mechanics out of a universal `WorldObject` state bag.

Current timed Movement is an important concrete example of this rule: `WorldObject` still contains only identity + definition identity, while Movement capability, active action state, timing carry and Spatial position remain owned by separate systems.

## Identity

Every individual runtime object receives an `ObjectId`.

The current representation packs two non-negative integers into one `long`:

```text
high 32 bits -> generation
low  32 bits -> slot
```

Conceptually:

```text
ObjectId[slot:generation]
```

The slot gives efficient repository addressing. The generation protects against stale references when a removed slot is later reused.

## Why generation matters

Suppose object A occupies slot 7 with generation 3:

```text
A = ObjectId[7:3]
```

After A is removed, the repository increments the generation before reusing the slot. A future object B may become:

```text
B = ObjectId[7:4]
```

The old id `ObjectId[7:3]` remains dead even though the slot is occupied again.

This prevents a stale reference from silently targeting a different object.

The same property is important to mechanic state keyed by `ObjectId`: a later object reusing the slot must not inherit the old object's movement state merely because the slot number matches.

## `ObjectRepository`

`ObjectRepository` owns object identity and existence only.

Current internal storage uses parallel primitive/object arrays:

```text
WorldObject[] objects
int[] generations
int[] freeSlots
```

Slots are acquired from the free stack when possible; otherwise the repository grows. Creation supplies the allocated ObjectId to a factory function and verifies that the returned object uses exactly that id.

Removal clears the slot, increments its generation when possible, and returns the slot to the free list.

## Read boundary

Consumers that only need object existence depend on `ObjectLookup`, not the mutable repository implementation.

This allows internal repository representation to change without turning every mechanic into a repository collaborator.

Current Movement uses `ObjectLookup` to validate the object and obtain immutable definition identity. It does not receive mutation authority over `ObjectRepository` merely because it needs to move an existing object.

## `WorldObject`

`WorldObject` is a domain object with identity and immutable definition identity. It is not intended to accumulate every possible mutable property.

Current shape:

```text
WorldObject
    ObjectId
    ObjectDefinitionId
```

The presence of `ObjectId` provides a stable join key between specialized owners without requiring one giant mutable object.

Future mechanics such as health, hunger, inventory, AI state, reproduction, or disease should follow the same ownership rule when they become real systems.

## Current Movement as an ownership example

Timed Movement deliberately spans several owners without putting everything into `WorldObject`:

```text
ObjectRepository / WorldObject
    -> object exists
    -> immutable ObjectDefinitionId

MovementDefinitions
    -> ObjectDefinitionId -> MovementRate

MovementStateStore
    -> active MovementAction for ObjectId
    -> per-object fractional timing carry

SpatialSystem
    -> authoritative ObjectId -> XYZ

Scheduler
    -> when the MovementAction completion wakes
```

A move therefore does not mutate fields such as:

```text
object.x
object.y
object.z
object.speed
object.currentAction
object.moveProgress
```

because those facts have separate semantic owners.

This is not “data scattering” by accident. It is the one-owner-per-property rule applied to concrete runtime mechanics.

## Definition-backed movement capability

Ordinary self-propelled movement is configured on `ObjectDefinitionId` through the `movement` aspect:

```json
{
  "key": "core:walker",
  "aspects": {
    "movement": {
      "rate": 100
    }
  }
}
```

Compiled representation:

```text
ObjectDefinitionId -> MovementRate
```

Every object using that definition sees the same immutable rate. Runtime timing carry remains per `ObjectId` because it evolves independently for each instance.

This distinction is a useful general rule:

```text
same for every instance of the type
    -> definition data

changes independently for one runtime instance
    -> system-owned runtime state
```

See [Definitions](Definitions.md) and [Movement System](Movement-System.md).

## `ObjectFactory`

`ObjectFactory` is responsible for definition-backed creation. It combines repository identity allocation with an object definition catalog so creation cannot silently reference an unknown definition.

This separates content identity from repository slot management.

The test-only Scenario fixture currently uses the real `ObjectFactory` during arrange, then exposes only read/control behavior after `start()` rather than bypassing object ownership for runtime assertions.

## Existence is independent of position

Object lifetime and spatial position are separate concerns:

```text
ObjectRepository   ObjectId -> existence / WorldObject
SpatialSystem      ObjectId -> XYZ
```

An object can conceptually exist without currently being positioned. Removing or moving spatial state must not implicitly redefine object identity unless a higher-level lifecycle action explicitly coordinates both owners.

Current Movement reflects this distinction:

```text
object exists but no transform
    -> structured movement:not_placed rejection
```

An accepted MovementAction also does not create a second intermediate identity or position. The same `ObjectId` remains at the source Spatial coordinate until completion commit.

## Repository is not a mechanics registry

The repository should not grow methods such as:

```text
getHealth(id)
getHunger(id)
getInventory(id)
getMovementRate(id)
getMovementAction(id)
getAIState(id)
```

Those would turn identity storage into a central mutable world database and violate one-owner-per-property.

Instead:

```text
HealthSystem / state       ObjectId -> health
InventorySystem / state    ObjectId -> inventory
MovementStateStore          ObjectId -> active movement/timing state
SpatialSystem               ObjectId -> XYZ
...
```

with narrow read/write boundaries as needed.

## Lifecycle orchestration

Creating or deleting a fully featured object will eventually require coordinating several mechanics. That coordination belongs above the individual owners, likely through an explicit lifecycle orchestration layer when a real consumer establishes its requirements.

Current Movement exposes one consequence of the still-deferred full lifecycle problem: a forced object deletion while an action is sleeping will require an explicit cancellation/stale-process policy. The project does not solve that future concern by introducing circular dependencies between ObjectRepository, Movement, Spatial and Scheduler today.

## Performance

The current repository already avoids a hash lookup for primary identity resolution by using slot addressing. That does not mean every object mechanic should copy the same storage strategy immediately.

Each mechanic can choose arrays, maps, sparse sets, or other representations behind its semantic boundary once its density and workload are known.

`MovementStateStore` is currently optimized for correctness and direct per-object state ownership. Representative multi-agent workloads should determine whether its internal representation needs further data-oriented specialization.

## Testing invariants

Important object-model tests include:

```text
created object uses supplied ObjectId
null/invalid factories rejected
removed id becomes dead
reused slot gets newer generation
stale id does not resolve to new object
size tracks live objects
repository growth preserves identities
```

Movement integration adds complementary ownership evidence:

```text
object without transform cannot start ordinary movement
object definition without movement capability cannot start ordinary movement
active movement does not mutate ObjectRepository identity
Spatial remains authoritative for XYZ until scheduled completion
```

These are semantic guarantees, not incidental implementation tests.
