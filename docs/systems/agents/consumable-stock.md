# Consumable Stock

## In plain language

Consumable Stock represents a **finite amount carried by one object source**. A patch of edible vegetation can contain 8 stock units; a use can spend 1; Growth can later restore some amount up to capacity.

The system deliberately does not pretend every finite quantity in EvoForge is the same kind of resource. It is a small bounded integer stock owner reused only when another mechanic genuinely fits those semantics.

## Current status

The current production consumers are:

- finite object-based Need-satisfaction sources (food in the agent slice);
- Growth/regrowth of those sources.

Water is **not** stored in Consumable Stock; free liquids have their own volume/identity owner.

## Ownership

Immutable configuration:

```text
ConsumableStockDefinitions
  capacity
  initialQuantity
```

Mutable authoritative state:

```text
ConsumableStockSystem
  ObjectId -> current quantity
```

Read-only public projection:

```text
has(object)
quantity(object)
capacity(object)
```

Presentation/Agent decision cannot set quantity directly.

## Quantity semantics

Quantity is a non-negative integer simulation unit whose concrete meaning is chosen by the content/mechanic using the stock.

The stock subsystem itself does **not** claim that one unit universally equals:

- one kilogram;
- one litre;
- one calorie;
- one cubic centimetre.

If a future mechanic needs physical dimensional quantity, it should define that dimension explicitly rather than reinterpret all existing stock.

## Definition example

```json
{
  "consumableStock": {
    "capacity": 120,
    "initial": 75
  }
}
```

When `initial` is omitted it defaults to capacity under the current authoring contract.

A definition that declares Growth currently must also have the Stock capability expected by that Growth process; missing required owner configuration is an invariant/bootstrap failure.

## Narrow mutations

### Consumption

The owner exposes explicit semantic consumption:

```text
consume(object, exactQuantity)
```

A successful call spends real current source quantity.

### Replenishment

Growth introduced the first legitimate restoration consumer, so the owner also exposes:

```text
replenish(object, requestedQuantity)
```

through a narrow `ConsumableStockReplenishment` capability.

The actual added amount is:

```text
remaining = capacity - current
added     = min(requested, remaining)
new       = current + added
```

There is no public arbitrary `setQuantity()` and no universal mutable Resource interface.

## Need-satisfaction integration

A source's physiological effect and physical finite cost are independent values:

```text
NeedSatisfaction
  amount             = Need deficit reduced on success
  consumedQuantity   = stock spent on success
```

Example:

```text
source stock before  = 8
consumedQuantity     = 1
hunger reduction     = 15
```

One successful use can therefore remove one stock unit while reducing Hunger by 15 Need units.

`consumedQuantity == 0` represents a persistent/non-depleting source under the current generic contract.

A positive stock cost requires the source instance to own Consumable Stock. Missing stock is broken configuration, not ordinary “currently unavailable” world state.

## Opportunity lifecycle

A finite source should not be advertised as usable when:

```text
current quantity < required consumedQuantity
```

At actual provider-owned use completion, the provider rechecks current:

- source existence/availability;
- interaction access;
- required capability;
- Need deficit;
- stock quantity.

Only successful completion spends stock and reduces Need. Starting a timed use does not pre-consume stock.

This prevents two stale observations from granting resource that no longer exists.

## Growth interaction

Growth does not own quantity.

```text
GrowthSystem
  decides/resolves growth pulse
      ↓
ConsumableStockReplenishment
      ↓
ConsumableStockSystem
  clamps to capacity and returns actual added amount
```

When successful consumption reduces a full Growth-enabled source, Stock emits only a narrow reduction notification through composition so the dormant Growth process can wake. It does not transfer Growth ownership into the stock store.

See [Growth](growth.md).

## Invariants

- `0 <= quantity <= capacity`.
- Consumable Stock System is the only mutable quantity owner.
- Consumption/restoration happen through narrow semantic capabilities.
- Replenishment cannot exceed remaining capacity.
- Physiological benefit and source cost are independent.
- Evaluation does not advertise an under-stocked finite use.
- Timed use commits resource mutation only after completion revalidation.
- Growth decides *when/how much to request*; Stock decides/clamps authoritative quantity.

## Current limitations

Not modeled here:

- fractional/continuous quantity;
- physical mass/density/volume;
- nutritional chemistry;
- multi-cell/shared reservoirs;
- inventories/stacks/ownership transfer;
- automatic source destruction at zero;
- decay/spoilage.

Those are different mechanics even if they also use a word like “resource”.

## Code and tests

Primary code lives with the consumption/stock mechanics:

```text
simulation/.../world/mechanics/consumption/
```

Tests cover definition compilation, capacity/initial values, exact consumption, clamped replenishment, opportunity availability, timed-use revalidation and Growth wake-up through narrow notifications.

## Sources

**Internal EvoForge design.** This is a bounded integer resource-owner contract rather than an external economic/biological model.

See [Autonomous Agents](agents.md), [Growth](growth.md), [Need Progression](need-progression.md), and [Liquids](../environment/liquids.md) for a separate physically volumetric resource owner.
