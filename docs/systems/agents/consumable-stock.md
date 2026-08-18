# Consumable Stock

## Purpose

Consumable Stock models a bounded quantity carried by an object instance when interactions or world processes must spend or restore real source quantity.

It is deliberately narrower than a universal resource/material framework. The current consumers are finite food and plant regrowth; later mechanics may reuse the same bounded-stock owner only when their semantics genuinely fit.

## Ownership

`ConsumableStockDefinitions` owns immutable per-definition stock configuration:

```text
capacity
initialQuantity
```

`ConsumableStockSystem` is the authoritative owner of mutable per-object quantity.

`SimulationView.consumableStocks()` exposes only `ConsumableStockLookup`:

```text
has(object)
quantity(object)
capacity(object)
```

Presentation and autonomous decision never mutate stock directly.

## Mutation boundaries

Consumption remains an explicit owner mutation:

```text
consume(object, exactQuantity)
```

The Growth subsystem introduced the first legitimate restoration consumer, so stock now also exposes the narrow mutation capability:

```text
ConsumableStockReplenishment.replenish(object, requestedQuantity)
```

It adds at most the remaining capacity and returns the actual quantity added.

There is still no generic `setQuantity`, arbitrary mutable state exposure or universal Resource mutation interface. Future systems that need a different semantic mutation should receive an equally narrow capability rather than bypassing the owner.

## Quantity semantics

Quantity is an integer simulation unit selected by content/mechanic definitions. The system does not claim that every stock unit is kilograms, liters, calories or any other universal physical unit.

A definition can therefore choose a useful granularity for the current mechanic while future physical systems remain free to introduce stronger dimensional semantics when real consumers require them.

## Need satisfaction integration

`NeedSatisfaction` separates two values:

```text
amount             physiological Need reduction
consumedQuantity   authoritative source stock spent by one use
```

Example:

```text
source stock:       8 units
one use spends:     1 unit
hunger reduction:  15
```

These values are intentionally independent.

A satisfaction with `consumedQuantity == 0` remains persistent/non-depleting. A positive `consumedQuantity` requires the source instance to own `ConsumableStock`; missing stock is a configuration/invariant failure rather than a normal unavailable-world result.

Evaluation does not advertise a finite opportunity when current quantity is below its required consumption amount.

At use time the provider revalidates co-location, capability, Need deficit and stock availability before consuming stock and mutating the Need owner.

## Growth integration

Growth does not own stock quantity. `GrowthSystem` resolves how much growth should occur, then requests replenishment through `ConsumableStockReplenishment`.

```text
GrowthSystem
    ↓ resolved amount
ConsumableStockReplenishment
    ↓
ConsumableStockSystem
    ↓ clamps to capacity
actual quantity added
```

Environmental conditions remain outside Consumable Stock entirely.

See [Growth](./growth.md) for growth scheduling and future environmental influence boundaries.

## Data aspects

Stock is an independent definition aspect:

```json
{
  "consumableStock": {
    "capacity": 120,
    "initial": 75
  }
}
```

`initial` defaults to `capacity` when omitted.

A need effect may declare its stock cost independently:

```json
{
  "needSatisfaction": {
    "core:hunger": {
      "amount": 35,
      "consumesQuantity": 4,
      "requiresCapability": "core:graze"
    }
  }
}
```

No Grass/Hay/source-type switch exists in the runtime.

## Current boundaries

Implemented now:

- bounded capacity and initial quantity;
- authoritative per-instance quantity;
- exact quantity consumption;
- capacity-clamped narrow replenishment;
- stock-aware opportunity availability;
- read-only diagnostics;
- independent data compiler.

Not implemented in this subsystem:

- continuous/fractional units;
- nutrition chemistry;
- mass, density or volume physics;
- shared reservoirs across multiple cells;
- automatic destruction when quantity reaches zero.

Growth/regrowth is a separate subsystem and does not change Consumable Stock ownership.
