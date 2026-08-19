# Need Progression

## In plain language

Need Progression makes an already-declared physiological deficit **grow as simulation time passes**. Hunger is the first example: a Cow can begin fully fed, then gradually become hungry even if nothing else happens.

The subsystem does not decide what the agent should do about the deficit. It only changes the authoritative Need level through a narrow mutation owned by the Need system. Agent decision later observes that changed level during its normal think cycle.

## Current status

The current model is generic over open `NeedId` values and contains no Hunger-, Thirst- or species-specific branches.

```text
NeedDefinition
  maxLevel
  initialLevel
      +
NeedProgressionDefinition
  baseAmount
  intervalTicks
      ↓
NeedProgressionRateResolver
      ↓
NeedProgressionSystem scheduled process
      ↓
NeedDeficitIncrease.increase(...)
      ↓
NeedSystem authoritative level
```

Production currently uses `IntrinsicNeedProgressionRateResolver`, which returns `baseAmount` unchanged.

## Ownership

### Need definition and mutable level

`NeedDefinitions` declares which Needs an object definition has:

```text
NeedId
maxLevel
initialLevel
```

`NeedSystem` is the sole owner of the current per-object Need level.

### Progression definition and process

`NeedProgressionDefinition` contains:

```text
NeedId
baseAmount
intervalTicks
```

`NeedProgressionSystem` owns only the continuing process state:

```text
activation
scheduled evaluation
next evaluation tick
latest NeedProgressionTrace
```

It does not store a second copy of the Need level.

## Narrow mutation boundary

Need Progression justified one explicit way to increase a deficit:

```text
NeedDeficitIncrease.increase(object, need, amount)
```

`NeedSystem` implements that capability and applies only the amount that still fits below the configured maximum.

Conceptually:

```text
remaining = maxLevel - currentLevel
applied   = min(requestedIncrease, remaining)
newLevel  = currentLevel + applied
```

There is no public arbitrary Need setter.

Need reduction remains a different semantic operation used by satisfaction/recovery mechanics.

## Effective progression resolver

`NeedProgressionSystem` does not know **why** Hunger or Thirst might progress faster in one circumstance than another.

Before each pulse it asks:

```text
ObjectId + NeedProgressionDefinition
        ↓
NeedProgressionRateResolver
        ↓
effective increase for this interval
```

Current production behavior is simply:

```text
effectiveIncrease = baseAmount
```

A future physiology milestone could compose activity, temperature, illness, sleep or other state behind the resolver while preserving `NeedSystem` ownership.

EvoForge deliberately does not invent a universal physiology-modifier enum or multiplier formula before those consumers exist.

## Scheduled semantics

Example:

```text
Need core:hunger
maxLevel    = 100
initialLevel = 0

NeedProgression
baseAmount    = 3
intervalTicks = 5
```

With the current intrinsic resolver:

```text
tick 0   hunger = 0
tick 5   hunger = 3
tick 10  hunger = 6
...
```

until the configured maximum is reached.

If only 2 points of room remain and a 3-point pulse is requested:

```text
requested = 3
applied   = 2
level     = maxLevel
```

The exact level therefore never exceeds its definition maximum.

## Behavior at maximum

Unlike Growth-at-full, current Need Progression **remains scheduled even when the Need is already at maximum**. A later evaluation resolves normally but may apply zero because no deficit capacity remains.

This is current correctness semantics, not a claim that periodic wake-up at max is the final optimal scheduling strategy.

If representative profiling later shows a meaningful cost, a sleep/wake design would need an explicit causal signal for when the Need can leave maximum again. That optimization should not be invented without evidence.

## Zero and negative resolver output

A resolver may return zero for one interval:

```text
resolvedAmount = 0
→ no level increase
→ process remains on its normal schedule
```

Negative values are invalid because this subsystem represents **progression of deficit**, not recovery. Recovery/satisfaction uses its own semantic mutation.

## Multiple independent Needs

Need presence and Need progression are separate aspects. An object may:

- own a Need that never progresses automatically;
- own several Needs with different rates/intervals;
- have one Need at maximum while another continues increasing.

Example:

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

The IDs are open semantic keys. Adding another Need does not require editing the progression algorithm.

A progression definition referring to a Need the object does not actually own is invalid configuration and fails rather than creating hidden state.

## Relationship to Agent decision

Need Progression never directly calls Agent:

```text
NeedProgressionSystem
      ↓ narrow authoritative mutation
NeedSystem
      ↓ ordinary read during scheduled think
AgentSystem
      ↓ opportunity/search decision if motivation threshold is met
```

This keeps physiology and decision scheduling independent.

There is currently no special “Need changed, wake Agent immediately” event. The Agent observes the new level on its next normal think/recheck. Reactive wake-up should be added only if real behavior/performance evidence requires it.

## Diagnostics

`SimulationView.needProgression()` exposes read-only process observations such as:

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

These are diagnostics; they do not create another Need owner.

## Invariants

- `NeedSystem` remains the only mutable Need-level owner.
- Progression changes level only through `NeedDeficitIncrease`.
- Levels never exceed configured `maxLevel`.
- Open `NeedId`s progress independently without central type switches.
- Resolver output is non-negative.
- Negative progression is not used as recovery.
- Progression does not directly invoke Agent behavior.
- Current at-maximum processes remain scheduled unless a future explicit optimization changes that contract.

## Current limitations

Not modeled here:

- exertion/activity-dependent rates;
- temperature/illness effects;
- sleep/recovery physiology;
- starvation/dehydration damage/death;
- bespoke threshold consequences;
- reactive Agent wake-up;
- rich biological homeostasis.

Those should be introduced through concrete physiology mechanics/readers while preserving Need ownership and the resolver boundary.

## Code and tests

Primary code lives with Need mechanics and progression processes under the agent/Need packages.

Coverage proves deterministic scheduled increase, strict max clamping, independent unrelated Need IDs, invalid undeclared-Need configuration, injected resolver behavior, definition compilation/freeze and integration where a previously satisfied Cow later becomes motivated and uses the existing generic opportunity flow.

## Sources

**Internal EvoForge design.** Current Need Progression is a deterministic scheduled deficit model, not a biological metabolism equation.

See [Autonomous Agents](agents.md), [Time and Scheduling](../foundations/time.md), [Definitions](../foundations/definitions.md), and [Consumable Stock](consumable-stock.md).
