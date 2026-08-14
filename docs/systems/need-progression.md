# Need Progression

## Purpose

Need Progression models time-driven increase of an already declared Need deficit.

The first production consumer is Hunger becoming stronger over simulation time. The subsystem is deliberately generic over open `NeedId` values and does not contain Hunger-, Thirst- or species-specific branches.

## Ownership

`NeedDefinitions` still declares which Needs exist on an object definition and their bounds:

```text
NeedId
maxLevel
initialLevel
```

`NeedSystem` remains the sole authoritative owner of mutable Need levels.

Need Progression adds a separate definition aspect:

```text
NeedProgressionDefinition
  NeedId
  baseAmount
  intervalTicks
```

`NeedProgressionSystem` owns only continuing progression processes:

```text
activation
scheduled evaluation
next evaluation tick
last evaluation trace
```

It does not store or directly own Need levels.

## Mutation boundary

The first real producer of increasing deficits justifies the narrow mutation capability:

```text
NeedDeficitIncrease.increase(object, need, amount)
```

`NeedSystem` implements that capability and clamps the applied amount to the Need's configured `maxLevel`.

There is no generic Need setter and no mutable state exposure.

## Effective progression boundary

`NeedProgressionSystem` does not know why an organism becomes hungry or thirsty faster or slower.

Each scheduled evaluation asks a `NeedProgressionRateResolver`:

```text
ObjectId + NeedProgressionDefinition
          ↓
NeedProgressionRateResolver
          ↓
effective deficit increase for this interval
```

The current runtime uses `IntrinsicNeedProgressionRateResolver`, which returns `baseAmount` unchanged.

Future mechanics such as activity/exertion, temperature, illness, sleep, pregnancy or other physiological state may own their own authoritative data and be composed behind a richer resolver without modifying `NeedProgressionSystem`.

The project intentionally does not define a universal physiology-modifier enum or permanent multiplier formula before real consumers exist.

## Scheduled semantics

Example:

```text
Need core:hunger
maxLevel = 100
initialLevel = 0

NeedProgression
baseAmount = 3
intervalTicks = 5
```

With the intrinsic resolver, Hunger increases by 3 every 5 simulation ticks until reaching 100.

When the Need is already at maximum, the progression process remains scheduled and records zero applied amount. This is current correctness semantics, not a performance promise; representative profiling must precede sleeping/batching optimizations.

A resolver may return zero to suppress progression for one interval. Negative values are rejected as invariant failures. Need reduction belongs to explicit satisfying/recovery mechanics rather than negative progression.

## Independent Need dynamics

Presence and time dynamics are intentionally separate.

An object may declare a Need without automatic progression, or multiple Needs with different schedules:

```json
{
  "needs": {
    "core:hunger": { "max": 100, "initial": 0 },
    "core:thirst": { "max": 100, "initial": 0 }
  },
  "needProgression": {
    "core:hunger": { "baseAmount": 2, "intervalTicks": 8 },
    "core:thirst": { "baseAmount": 4, "intervalTicks": 5 }
  }
}
```

The IDs remain open. Adding `mod:mana_deficit` or another Need does not require changing progression code.

A progression declaration for a Need that the object does not actually own is a configuration/invariant failure during runtime assembly.

## Diagnostics

`SimulationView.needProgression()` exposes read-only `NeedProgressionLookup`:

```text
has(object, need)
nextEvaluationTick(object, need)
lastEvaluation(object, need)
```

`NeedProgressionTrace` records:

```text
tick
NeedId
resolvedAmount
appliedAmount
levelAfter
maxLevel
```

These diagnostics are intended for the integrated living-Cow inspector and animation/debug layer.

## Agent relationship

Need Progression never calls Agent or Decision.

Flow is intentionally indirect:

```text
NeedProgressionSystem
    ↓ narrow mutation
NeedSystem authoritative deficit
    ↓ read on ordinary think
Agent opportunity evaluation
```

The current Agent scheduling remains unchanged. A Need change is observed on the agent's next normal think/recheck. Reactive wake-up is deferred until representative profiling proves it necessary.

## Current proofs

Headless coverage proves:

- deterministic scheduled deficit increase;
- strict clamping at Need maxLevel;
- multiple unrelated open NeedIds progressing independently;
- undeclared-Need progression failing configuration;
- injected resolvers suppressing or increasing effective progression without changing the system;
- an initially satisfied Cow becoming hungry and using the existing generic food opportunity flow without a Hunger-specific AI hook;
- deterministic definition compilation and freeze behavior.

## Explicitly deferred

Not implemented here:

- activity/exertion effects;
- temperature or illness effects;
- sleep/recovery dynamics;
- starvation damage or death;
- thresholds with bespoke physiological consequences;
- event-driven agent wake-up;
- visual state and animation.

Those features should be introduced by their first concrete consumers while preserving `NeedSystem` ownership and the resolver boundary.
