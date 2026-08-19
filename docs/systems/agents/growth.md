# Growth

## In plain language

Growth restores a finite object source over simulation time. In the current vertical slice, plants regrow their `ConsumableStock`.

Growth decides **when growth should happen and how much should be requested**. Consumable Stock remains the owner of the actual quantity and clamps the result to capacity.

A full plant sleeps instead of waking forever just to rediscover that it is full.

## Current status

The production model is intentionally narrow:

```text
GrowthDefinition
  baseAmount
  intervalTicks
       ↓
GrowthRateResolver
       ↓
GrowthSystem process
       ↓
ConsumableStockReplenishment
       ↓
ConsumableStockSystem authoritative quantity
```

Current production uses `IntrinsicGrowthRateResolver`, so effective amount equals `baseAmount`.

Environmental growth effects are deliberately not coupled yet.

## Ownership

### Immutable definition data

`GrowthDefinitions` owns per-definition:

```text
baseAmount
intervalTicks
```

### Process state

`GrowthSystem` owns:

```text
GROWING / DORMANT_FULL
next scheduled evaluation
latest GrowthTrace
```

### Quantity

`ConsumableStockSystem` owns the actual current finite quantity.

Growth receives only the narrow `ConsumableStockReplenishment` mutation capability; it cannot arbitrarily set stock.

## Effective growth resolver

Growth intentionally does not directly read:

- precipitation;
- Soil moisture;
- temperature;
- light;
- seasons;
- nutrients.

Instead one replaceable boundary answers:

```text
ObjectId + GrowthDefinition
        ↓
GrowthRateResolver
        ↓
effective amount for this evaluation interval
```

Current production resolver:

```text
effectiveAmount = baseAmount
```

A future ecology milestone may compose an environmental resolver from narrow read capabilities when its actual model is known.

Example future composition, **not current behavior**:

```text
SoilLiquidLookup ─┐
TemperatureLookup ├─> EnvironmentalGrowthRateResolver
LightLookup ──────┘
```

EvoForge deliberately does not define a universal condition enum/formula before those real mechanics exist.

## Scheduled semantics

Example definition:

```text
baseAmount    = 3
intervalTicks = 12
```

If current stock is not full, Growth schedules evaluation 12 ticks later. With the intrinsic resolver it requests 3 units of replenishment.

Actual addition is clamped by Stock:

```text
actualAdded = min(resolvedAmount, capacity - currentQuantity)
```

## Dormancy at capacity

When quantity reaches capacity:

```text
stock == capacity
       ↓
Growth status = DORMANT_FULL
       ↓
no further Growth task scheduled
```

When real consumption later reduces the stock:

```text
ConsumableStockSystem successful reduction
       ↓ narrow ConsumableStockReductionSink notification
GrowthSystem checks whether object has Growth and is dormant
       ↓
resume GROWING / schedule next evaluation
```

Only the reduced relevant source wakes. The notification is composition glue; Stock still owns quantity and Growth still owns process state.

This is authoritative scheduling behavior, not a renderer optimization.

## Zero effective growth

A resolver may return zero for an interval. Negative growth is rejected because Growth models restoration; decay/withering needs separate semantics.

When a non-full source receives zero effective growth, current Growth remains scheduled at its normal interval because it has no generic knowledge of when external conditions might improve.

A future environmental condition system could justify event-driven sleeping/wake-up, but that contract is not predeclared.

## Definition example

```json
{
  "consumableStock": {
    "capacity": 100,
    "initial": 25
  },
  "growth": {
    "baseAmount": 2,
    "intervalTicks": 10
  }
}
```

Different plant definitions can share the same runtime Growth algorithm while changing stock/growth/presentation data independently.

## Invariants

- Growth never owns current stock quantity.
- Growth mutates quantity only through `ConsumableStockReplenishment`.
- Stock always clamps to capacity.
- Full Growth-enabled sources are dormant with no periodic Growth wake-up.
- Successful stock reduction can wake the exact dormant Growth process.
- Resolver output is non-negative.
- Decay is not represented as negative Growth.
- Environment is not read directly by `GrowthSystem`.

## Current limitations

Explicitly deferred as Growth inputs/mechanics:

- Soil moisture / rainfall;
- sunlight/day-night;
- temperature/snow/seasons;
- nutrients/chemistry;
- fractional sub-interval accumulation;
- age/life stages;
- reproduction;
- withering/death.

The existence of Water/Soil data in the engine is not enough reason to invent those causal formulas before the ecology stage defines them.

## Code and tests

Primary code:

```text
simulation/.../world/mechanics/growth/
simulation/.../world/mechanics/consumption/
```

Coverage includes scheduled restoration, capacity clamping, multiple plant definitions, invalid Growth-without-stock configuration, substituted resolvers, dormant-full behavior, exact wake-up after consumption and definition freeze/compilation.

## Sources

**Internal EvoForge design.** Current Growth is a deterministic bounded-stock restoration mechanic, not a biological plant-growth model.

See [Consumable Stock](consumable-stock.md), [Autonomous Agents](agents.md), [Time and Scheduling](../foundations/time.md), and [Hydrology](../environment/hydrology.md) for environmental systems that are intentionally not yet coupled into Growth.
