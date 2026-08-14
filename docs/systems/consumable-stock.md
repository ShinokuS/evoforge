# Consumable Stock

## Purpose

Consumable Stock models a bounded quantity carried by an object instance when an interaction must spend real source quantity.

It is deliberately narrower than a universal resource/material framework. The current consumer is finite food; later mechanics may reuse the same bounded-stock owner only when their semantics genuinely fit.

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
- stock-aware opportunity availability;
- read-only diagnostics;
- independent data compiler.

Not implemented in this subsystem yet:

- regrowth/replenishment;
- continuous/fractional units;
- nutrition chemistry;
- mass, density or volume physics;
- shared reservoirs across multiple cells;
- automatic destruction when quantity reaches zero.

Plant regrowth is the next real consumer that will justify adding a narrow replenishment mutation instead of exposing a generic setter preemptively.
