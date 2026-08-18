# Objects and Identity

## Purpose

Provide stable identity and existence for individual runtime objects without turning the repository into a bag of mechanics.

## Owns

`ObjectRepository` owns object creation/existence and `ObjectId` lifecycle. `WorldObject` is intentionally small: identity plus definition identity.

`ObjectId` uses slot/generation semantics so a stale id cannot silently refer to a newly created object that reused storage.

## Reads

Consumers use read-only object lookup capabilities where mutation is unnecessary.

## Does not own

Position, orientation, movement rate, active Movement/MoveTo state, Occupancy reservations, Needs, finite stock, Growth, Agent intent/search state, inventories or environmental facts. Those belong to their mechanic owners.

## Invariant

A mechanic that needs per-object mutable state stores it in the mechanic, keyed by `ObjectId`, rather than expanding `WorldObject` into a god object.

Definition identity may select immutable mechanic aspects/presentation bindings through their own registries; it does not turn `WorldObject` into a concrete-class behavior switch.
