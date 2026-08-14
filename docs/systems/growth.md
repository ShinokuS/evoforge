# Growth

## Purpose

Growth models an object's intrinsic ability to increase a bounded consumable quantity over simulation time.

The first production consumer is plant biomass represented by `ConsumableStock`. This is deliberately not a claim that every future biological process, tree dimension, crop lifecycle or material transformation is the same mechanic.

## Ownership

`GrowthDefinitions` owns immutable intrinsic configuration per object definition:

```text
baseAmount
intervalTicks
```

`GrowthSystem` owns only the continuing growth process:

```text
activation
scheduled evaluation
next evaluation tick
last GrowthTrace
```

It does **not** own the quantity being grown. Mutable quantity remains authoritative in `ConsumableStockSystem`.

Growth receives only the narrow `ConsumableStockReplenishment` mutation capability. No generic stock setter is exposed.

## Effective growth boundary

`GrowthSystem` does not know about sunlight, rain, snow, temperature, soil, seasons or weather.

Before applying one scheduled pulse it asks a `GrowthRateResolver`:

```text
ObjectId + GrowthDefinition
          ↓
GrowthRateResolver
          ↓
effective amount for this interval
```

The current runtime uses `IntrinsicGrowthRateResolver`, which simply returns `baseAmount`.

This seam is intentionally stronger than hard-coding a list of conditions into Growth. Future environmental mechanics should own their own authoritative state and expose narrow read capabilities. A richer resolver can compose those capabilities and return an effective amount without changing `GrowthSystem`.

Example future composition direction:

```text
SunlightSystem ───────┐
SoilMoistureSystem ───┤
TemperatureSystem ────┼─> environmental GrowthRateResolver ─> GrowthSystem
SnowCoverSystem ──────┘
```

The project intentionally does **not** define a universal `GrowthConditionType` enum, a central condition switch, or a permanent formula such as `sun * rain * temperature` before real environmental consumers exist. The first real condition will justify the composition algebra that fits its semantics.

## Scheduled semantics

A definition such as:

```text
baseAmount = 3
intervalTicks = 12
```

means the growth process is evaluated every 12 simulation ticks. With the current intrinsic resolver, each evaluation requests 3 quantity units.

`ConsumableStockSystem` clamps the actual addition to remaining capacity. Growth continues to be scheduled while full; an evaluation at capacity therefore records a positive resolved amount and zero applied amount.

This simple schedule is current correctness semantics, not a performance commitment. Representative profiling must precede event-driven sleeping or batch optimizations.

## Data aspect

Growth is an independent object-definition aspect:

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

Grass, Clover and Dandelion can therefore share the same production Growth code while differing only in definition data.

`GrowthDefinitionCompiler` is registered by the object-definition composition root like any other independent aspect. `ObjectDefinitionBootstrap` contains no Growth-specific branch.

## Diagnostics

`SimulationView.growth()` exposes read-only `GrowthLookup`:

```text
has(object)
nextEvaluationTick(object)
lastEvaluation(object)
```

`GrowthTrace` records:

```text
tick
resolvedAmount
appliedAmount
quantityAfter
capacity
```

These diagnostics are intended to feed the full living-world inspector and state visualization in the later integrated Cow slice.

## Invariants

A definition with Growth currently requires the object instance to own `ConsumableStock`. Missing stock is a configuration/invariant failure at runtime assembly, not an ordinary world outcome.

A resolver may return zero to suppress growth. Negative growth is rejected as an invariant failure; decay/withering is not silently represented as negative Growth and should receive its own semantics when needed.

## Current proofs

Headless tests cover:

- scheduled stock restoration;
- strict capacity clamping;
- different plant definitions sharing one Growth mechanic;
- Growth without a stock target failing configuration;
- a substituted resolver suppressing or boosting growth without modifying `GrowthSystem`;
- data-defined Growth compilation and freeze behavior.

## Explicitly deferred

Not implemented yet:

- sunlight or day/night influence;
- rain / soil moisture;
- temperature and snow cover;
- seasons;
- nutrient/soil chemistry;
- fractional/sub-interval growth accumulation;
- plant age, life stages, reproduction, withering or death;
- state-dependent presentation sprites and growth animation.

Those features should extend independent world mechanics and the resolver boundary only when they have real consumers.
