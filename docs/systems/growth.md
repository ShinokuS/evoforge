# Growth

## Purpose

Model an object's intrinsic ability to restore a bounded consumable quantity over simulation time.

The first production consumer is plant biomass represented by `ConsumableStock`. This does not mean every future biological process, tree dimension, crop lifecycle or material transformation belongs to the same mechanic.

## Ownership

`GrowthDefinitions` owns immutable intrinsic configuration per object definition:

```text
baseAmount
intervalTicks
```

`GrowthSystem` owns the growth process lifecycle:

```text
GROWING
DORMANT_FULL
next scheduled evaluation when growing
last GrowthTrace
```

It does **not** own the quantity being grown. Mutable quantity remains authoritative in `ConsumableStockSystem`.

Growth receives only the narrow `ConsumableStockReplenishment` mutation capability; it has no generic stock setter.

## Effective growth boundary

`GrowthSystem` does not directly know about sunlight, precipitation, SoilMoisture, temperature, seasons or other environment state.

Before applying one scheduled pulse it asks a `GrowthRateResolver`:

```text
ObjectId + GrowthDefinition
          ↓
GrowthRateResolver
          ↓
effective amount for this interval
```

The current production runtime uses `IntrinsicGrowthRateResolver`, which returns `baseAmount`.

This is an intentional extension seam. EvoForge now has authoritative precipitation/SoilMoisture/Water mechanics, but they are **not yet coupled into plant Growth** merely because the data exists. A future environmental resolver may consume those owners through narrow read capabilities when a real plant/ecology milestone defines the semantics.

That future composition might look like:

```text
SoilMoistureLookup ─┐
TemperatureLookup ──┼─> environmental GrowthRateResolver -> GrowthSystem
LightLookup ─────────┘
```

The project does not predefine a universal `GrowthConditionType` enum or one permanent formula before the first real environmental-growth consumer establishes the required algebra.

## Scheduled and dormant semantics

For:

```text
baseAmount = 3
intervalTicks = 12
```

a non-full object is evaluated after 12 simulation ticks. With the current intrinsic resolver, each evaluation requests 3 stock units.

`ConsumableStockSystem` clamps actual replenishment to remaining capacity. When capacity is reached, Growth becomes `DORMANT_FULL` and schedules no further Growth evaluation.

```text
stock < capacity
    ↓
GROWING
    ↓ scheduled pulses
stock == capacity
    ↓
DORMANT_FULL
    ↓ no Growth task
stock is consumed
    ↓ narrow reduction notification
GROWING again
```

`ConsumableStockSystem` remains the sole quantity owner. After successful consumption it emits only a narrow `ConsumableStockReductionSink` notification through a composition-root relay. Growth reacts only for objects that have a Growth definition and are currently dormant.

A full plant therefore does not wake periodically merely to rediscover that it is full. This is simulation scheduling semantics, not a presentation optimization.

A non-full object whose resolver returns zero remains scheduled at its configured interval because the current Growth owner cannot know when an external condition will become favorable. A future condition mechanic may justify a more reactive wake/sleep contract.

## Definition aspect

Growth is an independent object-definition aspect, composed with finite stock rather than hidden inside it:

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

Grass, Clover and Dandelion therefore share one production Growth mechanic while differing in definition/presentation data.

A definition with Growth currently requires the runtime object to own `ConsumableStock`. Missing stock is configuration/invariant failure, not an expected world rejection.

## Diagnostics

`SimulationView.growth()` exposes read-only `GrowthLookup` facts such as process status, next evaluation tick and latest `GrowthTrace`.

`GrowthTrace` records the evaluation tick, resolved amount, actual applied amount and resulting quantity/capacity.

Current generic Surface UI does not render the older dedicated Growth/Need dashboard; these read contracts remain available to focused scenarios/tests and to any future dedicated Agent/ecology diagnostic UI.

## Invariants

- Growth never mutates stock except through `ConsumableStockReplenishment`;
- replenishment never exceeds stock capacity;
- a full Growth-enabled stock is dormant until real consumption wakes it;
- a resolver may return zero but not negative growth;
- decay/withering is not represented by negative Growth and requires its own semantics if introduced.

## Current proofs

Headless coverage includes scheduled restoration, strict capacity clamping, several plant definitions sharing one mechanic, invalid Growth-without-stock configuration, substituted resolver suppression/boosting, dormant-full scheduling, real stock consumption waking exactly that process, returning to dormancy at capacity and definition freeze/compilation behavior.

## Explicitly deferred

The following are not yet **Growth inputs**, even where a corresponding environment mechanic already exists:

- SoilMoisture / precipitation influence;
- sunlight/day-night;
- temperature/snow/seasons;
- nutrients/soil chemistry;
- fractional/sub-interval accumulation;
- plant age/life stages/reproduction/withering/death.

They should extend independent owners and the resolver boundary only when a real consumer defines the needed semantics.
