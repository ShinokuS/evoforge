# Autonomous Agents

## In plain language

Autonomous Agents are EvoForge's first proof that believable behavior can emerge from **ordinary world mechanics** instead of one script per species.

A Cow does not run `CowAI.findFood()` or `CowAI.findWater()`. Instead:

- its Needs become more urgent over time;
- its senses reveal only what it can currently perceive;
- world mechanics advertise opportunities such as usable plant stock or finite Water;
- one common deterministic Utility comparison decides which current opportunity matters most;
- Movement/MoveTo performs the physical travel;
- the source-owning mechanic performs the timed use and mutates its own finite resource;
- if no solution is perceived, the agent searches without being given hidden source coordinates.

The same decision system currently handles Hunger/food and Thirst/Water without a source-type switch.

## Current status

The production slice provides:

```text
Need progression
      ↓
motivation threshold
      ↓
3D sensory truth / PerceptionSnapshot
      ↓
mechanic-owned AgentOpportunityProviders
      ↓
cheap execution eligibility
      ↓
common deterministic Utility
      ↓
committed intent
      ├─ MoveTo InteractionSite -> provider-owned timed use
      └─ no usable opportunity -> semantic search demand
                                   -> visual sweep / relative exploration
```

This is a vertical slice, not a final general-intelligence architecture. It intentionally avoids long-term memory/belief, social planning, learning and species-specific behavior trees until real consumers require them.

## Ownership map

| Fact | Owner |
|---|---|
| object existence/definition | Object Repository |
| current XYZ | Spatial |
| physical facing | Orientation |
| sensory visibility | Vision |
| sensor-neutral perceived objects/cells | Perception |
| mutable Need deficits | Need System |
| autonomous Need progression | Need Progression |
| finite object-source amount | Consumable Stock |
| finite free liquid amount | Liquid/Water |
| source use duration/effect | owning opportunity provider + definitions |
| source regrowth | Growth |
| route/edge execution | MoveTo + Movement + Occupancy |
| selected intent | Agent System |
| unknown-source search state | Agent Search |

Agent decision does not mutate physiology, Water or stock directly. Providers/domains own those changes.

## Open semantic identifiers

Needs/capabilities use namespaced open IDs:

```text
NeedId("core:hunger")
NeedId("core:thirst")
CapabilityId("core:graze")
```

Adding another ID does not add a central enum case. The ID has meaning only because owning mechanics/definitions use it.

## Definition composition

Agent-capable object definitions can independently contribute aspects such as:

```text
agent
vision
needs
needMotivation
needProgression
needSolutionKnowledge
needSatisfaction
liquidDrink
movement / occupancy / waterWading
consumableStock
growth
```

This allows different content to reuse the same generic Agent loop while changing senses, physiology, locomotion and resources through data/owned mechanics.

## Need deficit and motivation

Need levels are deficits:

```text
0        = fully satisfied
maxLevel = maximum configured deficit
```

`NeedSystem` owns the mutable level.

A nonzero deficit is not automatically behaviorally urgent. `NeedMotivationDefinition` supplies an activation threshold. Only sufficiently motivated Needs participate in environmental satisfaction decisions/search.

Automatic increase is a separate [Need Progression](need-progression.md) process.

This separation allows, for example, a tiny Hunger deficit to exist without making the Cow immediately abandon another valid activity.

## Sensing and perception

### Vision owns visual truth

`VisionSystem` reads authoritative position, orientation, world cell contents and occlusion to compute visible cells/objects in 3D.

Horizontal field of view is evaluated from XY heading; vertical difference still participates in distance/line-of-sight.

Vision is definition data. A Cow scenario currently uses a broad `330°` FOV to model panoramic cattle vision; that is content configuration, not a Cow branch in Agent.

### Perception is sensor-neutral

Agent does not depend on Vision-specific APIs. It receives `PerceptionSnapshot` containing currently perceived cells and objects.

This means future hearing/smell could contribute facts behind the Perception boundary without rewriting decision logic to ask “was this seen or heard?” for every provider.

### No omniscient source queries

An opportunity provider may enumerate only from current Perception. A liquid provider sees perceived cells; an object-resource provider sees perceived objects.

There is no generic `findNearestFoodAnywhere()` or `findWaterCoordinate()`.

## Source-neutral opportunities

`AgentOpportunityProvider` lets a world mechanic expose possible satisfaction without Agent learning concrete source type.

One opportunity contains conceptually:

```text
OpportunityTarget   source identity owned by provider
InteractionSite     standing XYZ where actor can use it
OpportunityEvaluation
```

An object food source can use an Object target. Free Water can use a liquid-cell target. Agent orders both through common evidence and does not branch on which target type it is.

Providers can:

- enumerate opportunities from current Perception;
- reevaluate a previously selected target/site;
- advertise motivation/search demand;
- start provider-owned timed use;
- report whether use remains active;
- expose terminal completion.

## Interaction site versus source location

The source coordinate and actor's standing coordinate are not necessarily the same.

Current shared reach profile `cardinalSameOrOneBelow()` supports cases like:

```text
actor standing z=N
  can use cardinal target z=N
  can use cardinal target z=N-1 when required clearance is open
```

No diagonal reach.

This lets a Cow:

- stand on shore at `z=1` and drink adjacent lower Water at `z=0`;
- stand beside a same-level rain puddle and drink it;
- use an object source from a valid neighboring InteractionSite.

The standing site itself must be physically free. Lower-target use requires declared clearance above the target.

Agent also asks `MoverDestinationAccessResolver` for a cheap **local necessary condition** before committing a non-current site: at least one structural incoming Navigation edge must be allowed by current mover traversal policy.

That does not prove a global route from the actor. Only MoveTo/Pathfinding owns that problem.

## Provider-owned timed use

Agent does not have verbs like `EAT`/`DRINK` in a closed action enum.

After MoveTo reaches the InteractionSite:

```text
Agent -> provider.startUse(...)
               ↓
provider owns duration + completion revalidation
               ↓
provider mutates authoritative source + Need through narrow capabilities
               ↓
Agent observes opaque completion
```

For finite object sources, `NeedSatisfaction` can define independently:

```text
amount              Need reduction
consumedQuantity    source stock spent
useDurationTicks    delay before commit
required capability optional
```

A timed use does not mutate Need/stock at start. Completion revalidates current state; only successful completion commits effects.

For Water drinking, the provider removes finite free-liquid volume. Need relief is proportional to actual volume removed, so an undersized puddle can be drained exactly without granting the benefit of a full requested drink.

If the same selected source remains desirable/available, a new provider-owned use can continue immediately rather than forcing an artificial idle think tick.

## Common Utility scale

Providers expose evidence, not private incomparable “scores”. `OpportunityEvaluation` contains common coordinates such as:

```text
expectedBenefit
pressure
relief
travel
motivation
```

`AgentSystem` converts them through shared fixed-point `UtilityMath` and orders all current opportunities deterministically.

Before Utility can win, a candidate must pass hard cheap execution eligibility. A site already known locally unusable cannot beat a usable candidate merely by a high score.

Current travel evidence is perceived geometric distance to the `InteractionSite`, **not one A* route search per candidate**. This keeps think passes cheap. Actual route existence is delegated to MoveTo only for the committed candidate.

Current deterministic tie-break order is conceptually:

```text
Utility
then distance
then stable source-neutral target key
then InteractionSite XYZ
then provider order
```

Therefore Hunger and Thirst compete on one decision surface instead of provider-specific priority code.

If future representative scenes prove geometric distance systematically misranks obstacle-heavy candidates, a bounded route refinement for a shortlist can be introduced through the existing planner boundary rather than pathfinding every perceived Water cell.

## Stable committed intents

Agent does not rescore every possible motivation on every poll while a valid committed operation is active.

Current high-level lifecycle phases are:

```text
MOVING_TO_OPPORTUNITY
USING_OPPORTUNITY
SEARCH_RELOCATION
```

These are lifecycle states, not a closed list of semantic actions. `USING_OPPORTUNITY` intentionally does not reveal “eat” versus “drink”.

Stable commitment reduces oscillation/ping-pong and lets provider/Movement lifecycles finish predictably.

General preemption—e.g. abandoning a valid activity because another Need suddenly becomes critical—is intentionally deferred until a concrete gameplay case defines interruption semantics.

## Failure recovery and local quarantine

Failure is remembered at the scope of the failed contract.

### Movement/site failure

A terminal MoveTo failure means the **physical standing site** failed as a locomotion destination. Agent temporarily quarantines that `(x,y,z)` for the current local-position context, regardless of which provider/target referenced it.

### Provider-use failure

A use failure may be provider/source specific, so it remains keyed by exact `(provider,target,site)`.

The transient quarantine clears when the actor moves, completes search relocation, successfully uses a source, or reaches the defined idle retry boundary.

This is local execution recovery, not persistent world memory.

An immediately terminal MoveTo planning failure is observed before publishing a moving intent, preventing a one-think “phantom moving” state.

## Unknown-source search

Semantic knowledge can say only:

```text
"Need X has environmental solutions"
```

It does not contain source IDs/coordinates/routes/last-known map locations.

If a motivated Need has no perceived usable opportunity, providers can emit `OpportunitySearchDemand`.

`AgentSearchSystem` then:

1. performs a local visual sweep;
2. if still empty, `UnguidedExplorationPolicy` chooses a deterministic **relative** target inside the current visual horizon;
3. `RelativeSearchLocomotion` resolves that relative target from the actor's current position;
4. the target must belong to the fresh Vision snapshot used by search;
5. MoveTo is started with a query constraint limited to that visible-cell snapshot.

Cognition therefore never receives a hidden omniscient global source coordinate just because Pathfinding itself needs coordinates internally.

## Scheduling and determinism

Agent thinks on scheduled passes, polls active MoveTo/provider-use/search work, and can sleep longer when idle.

Need progression, Growth, precipitation and hydrology run independently on their own schedules. Agent observes their resulting authoritative state on the next ordinary think/recheck.

Determinism relies on stable perception ordering, fixed-point Utility/tie rules, simulation ticks and deterministic exploration variation keyed from stable identity/state.

Need changes do not currently push reactive wake-up events into Agent. Add that only if representative profiling/behavior requires it.

## Observability

Read-only runtime projections expose Orientation, Vision, Need, Need Progression, Stock, Growth, Agent decision/search and MoveTo state.

Agent traces can show:

- current lifecycle phase;
- source-neutral target key;
- InteractionSite;
- Utility evidence;
- search state/failure diagnostics.

Presentation reads those facts instead of reimplementing AI logic.

The “Living Cow Meadow” and larger “Living Cow Herd” scenarios combine ordinary Hunger/Thirst, perception, Utility, MoveTo, Occupancy, finite plants/Growth, finite Water and rain/evaporation. Rain puddles are hydrology consequences, not AI props.

## Invariants

- No species/source-type switch in generic Agent decision.
- Agent sees only Perception, not omniscient world-source queries.
- Need, stock, Water and Movement remain authoritative in their owners.
- Providers own source semantics and timed-use lifecycle.
- Utility comparison uses a common deterministic evidence scale.
- InteractionSite is explicit and may differ from source coordinate.
- Path/Movement authority remains outside Agent.
- Search uses semantic knowledge + fresh perception, not hidden source coordinates.
- Committed valid intents are stable until their lifecycle requires reevaluation.

## Current limitations

Not yet implemented:

- persistent Belief/Memory/world-map knowledge;
- learning/conditioning;
- social communication/herd planning;
- hearing/smell;
- general action preemption policy;
- rich multi-step goal planning;
- sleep/reproduction/fear/combat physiology;
- coordinated multi-agent resource reservation/yielding.

These should be introduced as concrete mechanics behind existing perception/opportunity/movement boundaries rather than as a universal AI framework in advance.

## Code and tests

Primary code lives under:

```text
simulation/.../agents/
simulation/.../world/mechanics/interaction/
simulation/.../world/mechanics/consumption/
simulation/.../world/mechanics/growth/
```

and composes with Need/Movement/Pathfinding/Liquid systems.

Headless integration tests cover perception limits, generic providers, finite food/Water, timed use, common Utility competition, search without omniscience, deterministic exploration, failure recovery, wading/interaction sites and multi-agent contention.

## Sources

**Internal EvoForge design.** The current opportunity/Utility/committed-intent/search model is intentionally project-specific; EvoForge does not claim to implement a standard BDI, GOAP or behavior-tree framework.

See [Need Progression](need-progression.md), [Consumable Stock](consumable-stock.md), [Growth](growth.md), [Movement](../traversal/movement.md), [Water Traversal](../traversal/water-traversal.md), and the historical [Agent AI foundation journal entry](../../journal/entries/2026-08-14-agent-ai-foundations.md).
