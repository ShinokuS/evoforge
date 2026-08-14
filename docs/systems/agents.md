# Autonomous Agents

## Purpose

The agent subsystem proves that autonomous behavior can emerge from composable world mechanics rather than species-specific scripts.

The current production slice proves:

```text
perception -> opportunity generation -> deterministic choice -> intent -> MoveTo -> interaction
unresolved motivation -> semantic search demand -> visual search -> relative exploration -> discovery
finite source quantity -> availability -> interaction cost -> authoritative depletion
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
   ├─ concrete opportunity -> continuing intent -> MoveTo -> use
   └─ no opportunity -> semantic search demand
                         ↓
                    AgentSearchSystem
                         ├─ local visual sweep
                         └─ unguided relative exploration
```

Simulation may internally represent physical state with XYZ coordinates. Agent cognition/search does not receive a global coordinate map or an absolute self-position.

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
| immutable source Need effects | `NeedSatisfactionDefinitions` |
| bounded consumable source configuration | `ConsumableStockDefinitions` |
| mutable consumable source quantity | `ConsumableStockSystem` / `ConsumableStockLookup` |
| general knowledge that a Need has environmental solutions | `NeedSolutionKnowledgeDefinitions` |
| one opportunity family | its `AgentOpportunityProvider` |
| selected autonomous intent | `AgentSystem` |
| current epistemic search state | `AgentSearchSystem` |
| unguided exploration choice | `UnguidedExplorationPolicy` |
| search physical execution | `RelativeSearchLocomotion` |
| route/edge movement | MoveTo / Movement / Occupancy stack |
| decision diagnostics | `AgentDecisionTrace` |
| search diagnostics | `AgentSearchTrace` |

Decision does not mutate physiology directly. Need does not choose behavior. Search does not own movement. Presentation does not recreate simulation truth.

## Open semantic identifiers

Needs and capabilities use open IDs:

```text
NeedId("core:hunger")
CapabilityId("core:graze")
```

Adding another identifier does not require editing a central enum or switch. An ID alone does not create behavior; its mechanic supplies the meaning.

## Agent composition

`AgentDefinition` currently contains autonomous capabilities only.

Other properties remain independent definition aspects:

```text
object definition
  ├─ agent
  ├─ vision
  ├─ needs
  ├─ needSolutionKnowledge
  ├─ needSatisfaction
  └─ optional world-mechanic aspects such as consumableStock
```

This prevents `AgentDefinition` from becoming a universal bag of every sensory, physiological and cognitive property.

## Needs

`NeedSpec` defines:

```text
NeedId
maxLevel
initialLevel
```

Runtime levels are deficit-oriented:

```text
0         fully satisfied
maxLevel  maximum configured deficit
```

`NeedSystem` exclusively owns mutable deficits.

Current mutation:

```text
satisfy(agent, need, amount)
    -> reduce by min(current deficit, amount)
```

Need progression over time is not implemented yet.

## Vision and physical orientation

Vision is an independent sensory mechanic.

`VisionDefinition` currently supplies:

```text
range
horizontal FOV degrees
```

`VisionSystem` reads authoritative position, facing, current cell contents and `SightOcclusionLookup`, then produces immutable `VisionSnapshot` data containing cells and objects actually visible now.

Decision consumes the sensor-neutral `PerceptionLookup`, not Vision-specific APIs. Hearing or smell can later contribute behind that boundary without adding sense-specific branches to Decision.

Movement changes physical facing only after a movement edge successfully commits.

## Mechanics expose opportunities

`AgentOpportunityProvider` is the narrow bridge from one mechanic into autonomous decision:

```java
String id();
OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);
OpportunityUseResult use(ObjectId agentId, ObjectId sourceId);
List<OpportunitySearchDemand> searchDemands(ObjectId agentId);
```

`evaluate` receives only sources that reached current Perception. There is no repository-wide source scan.

`use` revalidates current world facts after locomotion before mutating domain owners.

`searchDemands` can state that an unresolved motivation has environmental solutions worth seeking without revealing a concrete source.

## Need satisfaction and finite source quantity

A source definition may advertise a Need effect and optionally require a capability.

`NeedSatisfaction` now keeps physiological effect and physical source cost separate:

```text
amount             Need deficit reduction
consumedQuantity   ConsumableStock units spent per successful use
```

A zero `consumedQuantity` means a persistent/non-depleting effect. A positive value requires the source instance to own `ConsumableStock`.

Eligibility includes:

```text
source carries NeedSatisfaction data
agent carries the relevant Need
current deficit > 0
required capability is present, if configured
finite source has at least consumedQuantity available
```

If a finite source is empty, it is not advertised as an opportunity. Arrival/use revalidates stock before mutation.

A satisfaction that declares positive consumption while its source has no consumable stock is a configuration/invariant failure, not a normal world outcome.

Grass, Hay and future compatible content remain data. Generic Decision does not know their names.

See [Consumable Stock](./consumable-stock.md) for quantity ownership and current stock semantics.

## General semantic knowledge

`NeedSolutionKnowledgeDefinitions` currently represents one narrow semantic fact:

```text
"this definition knows that environmental solutions exist for NeedId X"
```

Example:

```text
core:hunger -> environmental solutions exist
```

It does not contain Grass/Hay IDs, ObjectIds, XYZ coordinates, routes or last-known positions.

This is intentionally not a universal Knowledge framework. Other knowledge families should receive their own owner only when real consumers appear.

## Unknown-source search

When no concrete perceived opportunity exists, providers may emit:

```text
OpportunitySearchDemand
  motivation
  urgency
```

For the Cow proof this means approximately:

```text
"hunger is unresolved and I know environmental solutions exist"
```

not:

```text
"Grass is at (5, 0, 0)"
```

`AgentSystem` chooses among search demands deterministically and delegates the epistemic process to `AgentSearchSystem`.

### Local visual sweep

Search first observes the current heading and rotates through four 90-degree headings. Every heading is evaluated through ordinary authoritative Vision on a later scheduled think.

If a compatible source enters Perception, normal opportunity evaluation resumes and Search is cancelled.

### Unguided exploration

If the local sweep finds nothing, Search falls back to `UnguidedExplorationPolicy`.

The current `CorrelatedRandomWalkExplorationPolicy` receives only:

```text
stable agent identity
previous exploration heading
exploration-leg ordinal
current visual range
```

and returns an observer-relative request:

```text
SearchRelocationRequest(
    heading,
    distance
)
```

No XYZ is passed to the policy or stored in search state.

Directional persistence is favored; deterministic pseudo-random variation later chooses straight/left/right alternatives. Current weights are provisional algorithm parameters, not Cow semantics.

## Relative search locomotion

`RelativeSearchLocomotion` is the execution boundary between cognition and physical movement.

Flow:

```text
relative heading + requested distance
    ↓
face chosen heading
    ↓
read fresh VisionSnapshot
    ↓
inspect consecutive currently visible cells on that ray
    ↓
truncate to locally traversable visible extent
    ↓
production MoveTo to the resulting local destination
```

The adapter may read authoritative XYZ internally because Movement requires physical coordinates. Those coordinates are never returned to Search/Knowledge.

Search therefore cannot ask Pathfinder for an arbitrary destination through unknown world space.

## Continuing intents

`AgentSystem` owns long-running autonomous execution.

Current intent families are:

```text
opportunity intent
search relocation intent
```

While a MoveTo is active, no duplicate search relocation or opportunity use is issued. Completion is matched by `MoveToActionId` before continuing the domain process.

Intent interruption/competition is not implemented yet.

## Candidate evaluation

The first provider uses a deliberately provisional score:

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

This is not the final Utility AI algebra. Multiple real motivations are required before response curves and cross-motivation normalization are promoted into a stronger contract.

## Determinism

Current agent/search determinism depends on:

```text
stable perceived-object ordering
stable candidate tie breaks
stable search-demand ordering
simulation ticks, never wall-clock time
deterministic exploration variation from stable identity + ordinal
```

Authoritative hash-map iteration is not used as decision order.

## Scheduling

Current timings are intentionally simple implementation choices:

```text
first think                    +1 tick
active MoveTo                  poll each tick
active visual sweep            advance each tick
search exploration leg         one MoveTo per leg
idle/no search demand          recheck after 10 ticks
```

Representative profiling must precede specialized AI scheduling/index structures.

## Developer observability

`SimulationView` exposes read-only Orientation, Vision, Need, ConsumableStock, Decision, Search and MoveTo state.

The visualizer can show for a selected object:

```text
actual visible cells
actually visible objects
physical facing
active MoveTo route
decision/search diagnostics
```

`AgentDecisionTrace` records candidates and the winner. `AgentSearchTrace` records current/last search motivation and phase.

Agent debug scenarios currently include:

```text
Agents -> Cow Foraging
Agents -> Cow Visual Search
```

Both scenarios now use finite food stock; presentation never recreates Vision, Decision or Pathfinding logic.

## Current proofs

Headless coverage proves, among other invariants:

- visible compatible source -> MoveTo -> Need reduction;
- new compatible source definition works without Cow/Decision changes;
- stronger farther source can beat weaker nearer source;
- outside-range / behind-observer / occluded sources are not visual candidates;
- general Need-solution knowledge starts search without source identity/location;
- search discovers concrete food only through Perception;
- unguided exploration expands observation through multi-cell visible relative legs;
- exploration variation and candidate ties remain deterministic;
- finite source use decreases authoritative stock;
- empty finite source is no longer a candidate.

## Deferred work

Not required for the current first Cow foundation:

- plant growth/regrowth;
- Need progression / metabolism;
- water/thirst;
- cross-motivation Utility algebra;
- intent persistence/interruption;
- persistent beliefs / episodic or landmark memory;
- map/compass/tool-assisted navigation;
- hearing/smell;
- fluid simulation;
- advanced Vision/body geometry;
- AI-specific optimization before representative profiling.

The next direct world-mechanic consumer is plant growth: it will justify a narrow replenishment mutation for `ConsumableStockSystem` and make finite plant biomass recover over simulation time.
