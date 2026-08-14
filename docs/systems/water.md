# Water

## Purpose

Own finite authoritative liquid-water quantity in the shared discrete XYZ world without embedding water into Terrain, Geometry or a giant world-cell object.

This page describes the current finite-state foundation only. Redistribution/flow, precipitation, soil interaction, traversal effects and drinking are later slices.

## Ownership

Water is an independent landscape mechanic:

```text
TerrainState   XYZ -> terrain identity | absence
Geometry       XYZ terrain anchor -> Shape | default FullShape
WaterState     XYZ -> liquid volume | dry
```

Shared coordinates are interaction addresses; Water does not become a Terrain field.

`WaterSystem` is the authoritative mutation owner. `WaterLookup` exposes only current quantity:

```java
int amount(int x, int y, int z);
```

Dry/absent water is represented by `CellVolume.EMPTY` (`0`), not by a special water object.

## Quantity representation

Water quantity uses the same deterministic cell-volume scale introduced by Geometry:

```text
CellVolume.EMPTY = 0
CellVolume.FULL  = 1_000_000
```

A quantity is a fraction of one discrete cell volume. It does not yet define litres, density or world-cell dimensions.

Current storage is sparse: only positive quantities require entries. This is a replaceable representation, not a public promise about future chunking or packing.

## Geometry-derived capacity

When water is added, `WaterSystem` asks only the neutral `GeometryLookup` / `Shape.solidVolume()` contract at the same XYZ:

```text
free capacity
=
CellVolume.FULL - solid Shape volume
```

If Geometry has no Shape at that XYZ, the cell has one full cell of capacity.

Current examples:

```text
open cell             capacity = 1.0
FullShape anchor cell capacity = 0.0
RampShape anchor cell capacity = 0.5
```

Water contains no `instanceof FullShape` / `RampShape` branches. Any future Shape that provides a valid `solidVolume()` automatically participates in capacity calculation.

The current capacity equation accounts only for terrain Shape volume. Object displacement is a future additional consumer/constraint and must not be faked as terrain geometry.

## Mutation semantics

The current primitive operations are deliberately arithmetic rather than closed result enums:

```java
int addAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

They return the volume that actually moved.

`addAtMost`:

- accepts a non-negative requested volume;
- never exceeds current geometric free capacity;
- returns `0` when no volume can enter;
- preserves already stored quantity.

`removeAtMost`:

- accepts a non-negative requested volume;
- removes at most what is present;
- returns the actual removed quantity;
- removes the sparse storage entry when the cell becomes dry.

Negative requests are programming errors. Zero is a valid no-op.

This API is directly suitable for future finite sources and sinks: conservation accounting uses returned actual transfers rather than a catalog of rejection reasons.

## Conservation boundary

No current Water operation creates or destroys quantity invisibly:

```text
added amount   = exact returned addAtMost value
removed amount = exact returned removeAtMost value
```

A future closed flow step must preserve exact total quantity apart from explicit source/sink transfers.

## Geometry changes and displacement

A later Geometry/Terrain mutation can reduce the capacity of a cell that already contains water. `WaterSystem` does **not** respond by silently deleting the excess.

That case requires an explicit coordinated displacement/flow operation once the redistribution solver exists. Until then, production code must not treat raw geometry mutation into occupied liquid volume as a complete physical operation.

This follows the ownership rule: Geometry may change geometry; it does not own Water quantity. A future landscape coordinator can orchestrate both without creating a reverse dependency from Geometry to Water.

## Deliberately absent

The current foundation does not yet implement:

- transfer between neighboring cells;
- gravity or equilibrium;
- active/frontier scheduling;
- hydraulic boundary/opening geometry;
- sources/drains as scheduled processes;
- rain or sky exposure;
- soil moisture/absorption;
- evaporation;
- object displacement;
- traversal/pathfinding effects;
- water-body identity;
- Thirst/Drink interactions.

Those behaviors must be introduced by their first real consumer. In particular, navigation `transitionPorts()` are not fluid openings.

## Tests

Headless tests cover finite add/remove arithmetic, saturation, full/ramp capacity, generic Shape extensibility, dry sparse semantics, invalid requests and the no-silent-deletion ownership rule.

See [Geometry and Shape](geometry.md) and the [Water Foundation design note](../notes/water-foundation.md).
