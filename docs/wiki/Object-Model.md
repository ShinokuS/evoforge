# Object Model

EvoForge uses real domain objects with stable runtime identity, but deliberately keeps mutable mechanics out of a universal `WorldObject` state bag.

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

## `WorldObject`

`WorldObject` is a domain object with identity and immutable definition identity. It is not intended to accumulate every possible mutable property.

Future mechanics such as health, hunger, inventory, AI state, reproduction, or disease should have specialized owners when they become real systems.

The presence of `ObjectId` provides a stable join key between those owners without requiring one giant mutable object.

## `ObjectFactory`

`ObjectFactory` is responsible for definition-backed creation. It combines repository identity allocation with an object definition catalog so creation cannot silently reference an unknown definition.

This separates content identity from repository slot management.

## Existence is independent of position

Object lifetime and spatial position are separate concerns:

```text
ObjectRepository   ObjectId -> existence / WorldObject
SpatialSystem      ObjectId -> XYZ
```

An object can conceptually exist without currently being positioned. Removing or moving spatial state must not implicitly redefine object identity unless a higher-level lifecycle action explicitly coordinates both owners.

## Repository is not a mechanics registry

The repository should not grow methods such as:

```text
getHealth(id)
getHunger(id)
getInventory(id)
getAIState(id)
```

Those would turn identity storage into a central mutable world database and violate one-owner-per-property.

Instead:

```text
HealthSystem      ObjectId -> health
InventorySystem   ObjectId -> inventory
...
```

with narrow read boundaries as needed.

## Lifecycle orchestration

Creating or deleting a fully featured object will eventually require coordinating several mechanics. That coordination belongs above the individual owners, likely through the Control/lifecycle orchestration layer.

Do not solve future lifecycle coordination by introducing direct circular dependencies between all systems.

## Performance

The current repository already avoids a hash lookup for primary identity resolution by using slot addressing. That does not mean every object mechanic should copy the same storage strategy immediately.

Each mechanic can choose arrays, maps, sparse sets, or other representations behind its semantic boundary once its density and workload are known.

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

These are semantic identity guarantees, not incidental implementation tests.
