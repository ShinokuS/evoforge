# Autonomous Agents

## Purpose

The agent subsystem proves that autonomous behavior can emerge from composable world mechanics rather than species-specific scripts.

The current production slice proves:

```text
perception -> opportunity generation -> deterministic choice -> intent -> MoveTo -> timed provider-owned use
Need progression -> motivation threshold -> search / opportunity relevance
unresolved motivation -> semantic search demand -> visual search -> relative exploration -> discovery
finite source quantity -> availability -> interaction cost -> authoritative depletion
Growth -> authoritative stock replenishment -> opportunity becomes available again
```

There is no `CowAI`, `EatGrassAction`, source-type switch, global action enum or omniscient `findFood()`.

The broader research direction remains non-normative in [Agent AI foundations](../notes/2026-08-14-agent-ai-foundations.md). This document describes implemented semantics only.

## Core boundary

```text
World truth
   ↓
Orientation + sensory mechanics
   ↓
PerceptionSnapshot
   ↓
mechanic-owned opportunity providers
   ↓
AgentSystem
   ├─ concrete opportunity -> MoveTo -> provider-owned use lifecycle
   └─ no opportunity -> semantic search demand
                         ↓
                    AgentSearchSystem
                         ├─ local visual sweep
                         └─ unguided relative exploration
```

Need progression and Growth are independent world processes. They change authoritative state; Agent observes the result on ordinary decision passes.

Simulation may internally represent physical state with XYZ coordinates. Agent cognition/search does not receive a global coordinate map or absolute self-position.

## Ownership

| Concern | Authoritative owner / contract |
| --- | --- |
| object existence and definition identity | `ObjectRepository` / `ObjectLookup` |
| physical XYZ | `SpatialSystem` / `TransformLookup` |
| physical facing | `OrientationSystem` / `OrientationLookup` |
| immutable autonomous capabilities | `AgentDefinitions` |
| immutable Vision parameters | `VisionDefinitions` |
| current visual truth | `VisionSystem` / `VisionLookup` |
| sensor-neutral current perception | `PerceptionLookup` |
| immutable Need declarations | `NeedDefinitions` |
| mutable Need deficit | `NeedSystem` / `NeedLookup` |
| immutable Need motivation thresholds | `NeedMotivationDefinitions` |
| immutable Need progression data | `NeedProgressionDefinitions` |
| continuing Need progression process | `NeedProgressionSystem` / `NeedProgressionLookup` |
| immutable source Need effects/use timing | `NeedSatisfactionDefinitions` |
| provider-specific opportunity-use process | owning `AgentOpportunityProvider` implementation |
| bounded consumable source configuration | `ConsumableStockDefinitions` |
| mutable consumable source quantity | `ConsumableStockSystem` / `ConsumableStockLookup` |
| general knowledge that a Need has environmental solutions | `NeedSolutionKnowledgeDefinitions` |
| selected autonomous intent | `AgentSystem` |
| current epistemic search state | `AgentSearchSystem` |
| unguided exploration choice | `UnguidedExplorationPolicy` |
| search physical execution | `RelativeSearchLocomotion` |
| route/edge movement | MoveTo / Movement / Occupancy stack |
| decision diagnostics | `AgentDecisionTrace` |
| continuing-intent diagnostics | `AgentIntentTrace` |
| search diagnostics | `AgentSearchTrace` |

Decision does not mutate physiology directly. Need does not choose behavior. Need progression does not call Agent. Search does not own movement. Presentation does not recreate simulation truth.

## Open semantic identifiers

Needs and capabilities use open IDs:

```text
NeedId("core:hunger")
CapabilityId("core:graze")
```

Adding another identifier does not require editing a central enum or switch. An ID alone does not create behavior; its mechanic supplies the meaning.

## Agent composition

`AgentDefinition` contains autonomous capabilities only. Other concerns are independent definition aspects:

```text
object definition
  ├─ agent
  ├─ vision
  ├─ needs
  ├─ needMotivation
  ├─ needProgression
  ├─ needSolutionKnowledge
  ├─ needSatisfaction
  ├─ consumableStock
  └─ growth
```

This prevents `AgentDefinition` from becoming a universal bag of sensory, physiological and cognitive properties.

## Needs, motivation and physiology progression

`NeedSpec` declares `NeedId`, `maxLevel` and `initialLevel`. Runtime levels are deficit-oriented: 0 is fully satisfied and `maxLevel` is maximum configured deficit.

`NeedSystem` exclusively owns mutable levels. Current owner mutations are intentionally narrow:

```text
satisfy(agent, need, amount)              -> reduce deficit toward 0
NeedDeficitIncrease.increase(...)         -> increase deficit toward maxLevel
```

A positive physiological deficit does **not** have to mean "act immediately". `NeedMotivationDefinition(NeedId, activationLevel)` is an independent definition aspect that declares when the deficit becomes strong enough for the current Need-satisfaction provider to advertise concrete opportunities or emit an environmental search demand.

If no motivation definition is present, the compatibility default is `activationLevel = 1`, preserving the earlier behavior for existing content. The threshold is generic and keyed by open `NeedId`; there is no Hunger-specific branch.

This separation is deliberate:

```text
Need level                 physiological truth
NeedMotivation threshold   autonomous relevance for this mechanic
```

It prevents agents from repeatedly interrupting themselves to solve tiny deficits such as Hunger = 1. A second release threshold / full hysteresis model is not implemented yet; it should be added only if a real competing-motivation consumer proves it necessary.

Automatic time dynamics remain a separate `NeedProgressionDefinition(NeedId, baseAmount, intervalTicks)` aspect. `NeedProgressionSystem` asks `NeedProgressionRateResolver` for the effective increase and then uses the narrow Need mutation capability.

Future activity, temperature, illness, sleep or other physiological state may influence a richer resolver without adding `if (HUNGER)` / `if (THIRST)` branches to progression code.

See [Need Progression](./need-progression.md).

## Vision and physical orientation

Vision is an independent sensory mechanic. `VisionDefinition` currently supplies range and horizontal FOV degrees.

`VisionSystem` reads authoritative position, facing, current cell contents and `SightOcclusionLookup`, then produces immutable `VisionSnapshot` data containing cells and objects actually visible now.

Decision consumes the sensor-neutral `PerceptionLookup`, not Vision-specific APIs. Hearing or smell can later contribute behind that boundary without adding sense-specific branches to Decision.

Movement changes physical facing only after a movement edge successfully commits.

## Mechanics expose opportunities

`AgentOpportunityProvider` is the narrow bridge from one mechanic into autonomous decision:

```java
String id();
OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);
OpportunityUseStartAttempt startUse(ObjectId agentId, ObjectId sourceId);
boolean isUseActive(ObjectId agentId);
OpportunityUseCompletion lastUseCompletion(ObjectId agentId);
List<OpportunitySearchDemand> searchDemands(ObjectId agentId);
```

`evaluate` receives only sources that reached current Perception during ordinary candidate generation. There is no repository-wide source scan.

After MoveTo reaches the selected source, Agent asks the provider to start use. The provider owns its own use duration and domain revalidation. Agent waits by opaque `OpportunityUseActionId` and verifies the matching terminal completion; it does not know whether the provider represents grazing, drinking or another future interaction.

A duration of zero is valid and preserves immediate interactions. A positive duration creates a real continuing process rather than a presentation-only delay.

`searchDemands` can state that an unresolved, sufficiently motivated Need has environmental solutions worth seeking without revealing a concrete source.

## Need satisfaction, use timing and finite source quantity

A source definition may advertise a Need effect and optionally require a capability:

```text
amount             Need deficit reduction
consumedQuantity   ConsumableStock units spent per successful use
useDurationTicks   provider-owned duration before completion
```

For a timed use, start does **not** mutate Need or source quantity. At the authoritative completion tick the provider revalidates:

```text
agent/source still exist
agent and source are still co-located
required capability still exists
Need still exists and remains unsatisfied
finite source still has enough stock
```

Only a successful completion consumes stock and satisfies the Need. If world facts changed while the interaction was active, completion is a structured unavailable result and no partial hidden mutation is performed.

After a successful completion, `AgentSystem` may continue the **same already-owned opportunity** immediately when the provider still evaluates that source as desirable and available. This is intent continuation, not a new omniscient candidate search: the concrete source was already selected and reached. It removes an artificial one-tick idle gap between consecutive timed uses while keeping the provider authoritative over whether another use may start.

When the motivation falls below its activation threshold, stock is insufficient, co-location is lost or another provider rule rejects continuation, the intent ends and normal decision scheduling resumes.

A zero `consumedQuantity` means a non-depleting effect. A positive value requires authoritative finite stock. Empty finite sources are not advertised.

Grass, Clover, Dandelion, Hay and future compatible content remain definition data. Generic Decision does not know their names.

See [Consumable Stock](./consumable-stock.md).

## General semantic knowledge

`NeedSolutionKnowledgeDefinitions` currently represents one narrow semantic fact:

```text
"this definition knows that environmental solutions exist for NeedId X"
```

It does not contain source definition IDs, ObjectIds, XYZ coordinates, routes or last-known positions.

This is intentionally not a universal Knowledge framework. Other knowledge families should receive their own owner only when real consumers appear.

## Unknown-source search

When no concrete perceived opportunity exists, providers may emit `OpportunitySearchDemand(motivation, urgency)` only when their own semantics say the motivation is currently worth pursuing.

For the Cow proof this means approximately:

```text
"hunger crossed my action threshold and I know environmental solutions exist"
```

not:

```text
"Grass is at (5, 0, 0)"
```

`AgentSystem` chooses among search demands deterministically and delegates the epistemic process to `AgentSearchSystem`.

Search first performs a local visual sweep. If nothing is found, `UnguidedExplorationPolicy` returns a coordinate-free relative target point as `offsetX/offsetY`, not an absolute world location and not a coarse `heading + distance` ray. The current correlated-random-walk policy uses stable identity, previous heading, exploration ordinal and visual range to sample a deterministic pseudo-random lattice cell on the outer ring of the visual horizon. A mild directional persistence bias remains, but the selected point can lie between the eight exact cardinal/diagonal rays.

`AgentSearchSystem` turns physical facing toward the selected relative point before execution. `RelativeSearchLocomotion` then resolves the relative offset against the current authoritative position, requires the endpoint to be present in the fresh Vision snapshot, and starts production `MoveTo` with a query-local path constraint that permits only cells from that same visible snapshot. Coordinates required by pathfinding and Movement remain behind this execution boundary and are never returned to cognition/search.

This is the first real consumer for constrained MoveTo advice. The constraint is advisory pathfinding input only; every committed edge still passes through ordinary Movement and Occupancy revalidation.

## Continuing intents

`AgentSystem` owns commitment/orchestration, not the domain semantics of provider use.

Current structural phases exposed through `AgentIntentTrace` are:

```text
MOVING_TO_OPPORTUNITY
USING_OPPORTUNITY
SEARCH_RELOCATION
```

These are lifecycle phases, not a closed catalog of actions. `USING_OPPORTUNITY` intentionally does not say `EATING`, `DRINKING`, `WORKING`, etc.

For timed provider use the trace exposes authoritative `startedTick` and `expectedCompletionTick`. Developer presentation can therefore visualize progress without inventing a wall-clock timer or knowing provider mechanics.

When one completed use is immediately continued against the same source, `USING_OPPORTUNITY` remains structurally continuous and its timing advances to the newly accepted provider use. Presentation therefore no longer needs to hide artificial gaps.

General interruption/competition between unrelated motivations is still deliberately deferred until multiple real motivations compete.

## Candidate evaluation

The first provider still uses a provisional score:

```text
effectiveBenefit = min(currentDeficit, advertisedAmount)
score = max(1, effectiveBenefit * 1024 / (distance + 1))
```

Stable order:

```text
1. score descending
2. distance ascending
3. source ObjectId ascending
4. provider registration order
```

This is not final Utility AI algebra. Hunger + Thirst will provide the first real consumer for cross-motivation comparison.

## Determinism and scheduling

Current determinism depends on stable perceived-object ordering, stable candidate/search tie breaks, simulation ticks instead of wall-clock time, deterministic exploration variation from stable identity + ordinal, and provider use scheduling through ordinary `ProcessScheduler` bindings.

Current scheduling remains intentionally simple:

```text
Agent first think                +1 tick
active MoveTo                    poll each tick
active provider use              poll each tick
active visual sweep              advance each tick
idle/no search demand            recheck after 10 ticks
Need progression                 its definition interval
Growth                           its definition interval
```

Need changes do not currently trigger a special Agent wake-up. Representative profiling must precede reactive wake-up or specialized scheduling/index structures.

## Developer observability

`SimulationView` exposes read-only Orientation, Vision, Need, NeedProgression, ConsumableStock, Growth, Decision, Search and MoveTo state.

`AgentDecisionLookup.currentIntent()` exposes the structural committed phase and authoritative use timing. The visualizer reads these contracts directly; it does not reconstruct AI or interaction timing.

The integrated **Agents -> Living Cow Cycle** scenario combines:

```text
Hunger progression + motivation threshold
Vision / Search
candidate scoring + winner
MoveTo route
provider-owned timed grazing
finite plant stock
Growth/regrowth
state-dependent creature/vegetation presentation
full selected-object inspector
```

The current acceptance meadow deliberately places every food source outside the Cow's initial Vision. A headless scenario test requires actual `EXPLORING`, physical displacement from the start, later perception of a source and only then a real timed use.

Grass, Clover and Dandelion share production mechanics while differing only by definition/presentation data.

## Current proofs

Headless coverage proves, among other invariants:

- visible compatible source -> MoveTo -> Need reduction;
- new compatible source definition works without Cow/Decision changes;
- outside-range / behind-observer / occluded sources are not visual candidates;
- general Need-solution knowledge starts search without source identity/location;
- search discovers concrete food only through Perception;
- unguided exploration samples deterministic pseudo-random relative cells on the outer Vision ring instead of collapsing to eight exact rays;
- exploratory MoveTo is constrained to the fresh visible-cell snapshot used to validate the selected relative point;
- finite source use decreases authoritative stock and empty stock removes the opportunity;
- plant Growth restores finite stock independently;
- open NeedIds progress on independent schedules and clamp at their configured maxima;
- a configured Need motivation threshold prevents action at trivial deficits and starts ordinary autonomous flow once crossed;
- an initially satisfied Cow can become meaningfully hungry and enter the existing generic food interaction flow without a Hunger-specific AI hook;
- timed use leaves Need and stock unchanged before the provider completion tick and applies both mutations only at authoritative completion;
- still-desired repeated use of the same source remains in `USING_OPPORTUNITY` without an artificial idle gap;
- Living Cow starts with no visible food and must enter exploration before its first feeding;
- `useDurationTicks` and `needMotivation` are independent definition data.

## Deferred work

After the integrated Cow acceptance slice, the next direct consumer is Water + Thirst, followed by real cross-motivation Utility competition and then richer intent persistence/interruption.

Still deliberately deferred:

- separate activation/release thresholds if true hysteresis is later required;
- persistent beliefs / episodic or landmark memory;
- map/compass/tool-assisted navigation;
- hearing/smell;
- multi-cell fluid evolution;
- advanced Vision/body geometry;
- AI-specific optimization before representative profiling.

Persistent memory, richer senses and fluid evolution remain separate research milestones rather than prerequisites for the current living-world path.
