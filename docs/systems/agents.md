# Autonomous Agents

## Purpose

The agent subsystem proves that autonomous behavior can emerge from composable world mechanics rather than species-specific scripts.

The current Cow slice now proves four connected capabilities:

```text
A. perceived opportunity -> deterministic choice -> MoveTo -> interaction
B. a new compatible source works without editing Cow/Decision code
C. current perception is real directed Vision with occlusion
D. unresolved motivation + general knowledge -> information search -> local exploration -> discovery
```

There is no `CowAI`, `EatGrassAction`, source-type switch or global action catalog.

The broader pre-implementation direction remains in [Agent AI foundations](../notes/2026-08-14-agent-ai-foundations.md). This document describes only implemented semantics.

## Core boundary

```text
World truth
   ↓
Orientation + Vision
   ↓
PerceptionSnapshot
   ↓
mechanic-owned opportunity providers
   ↓
AgentSystem
   ├─ concrete opportunity -> autonomous intent -> MoveTo -> interaction
   └─ no opportunity -> semantic search demand
                         ↓
                    AgentSearchSystem
                         ├─ local visual sweep
                         └─ unguided relative exploration
```

The simulation may internally represent physical state with XYZ coordinates. Agent cognition/search does not receive a coordinate map and does not store an absolute self-position.

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
| immutable need declarations | `NeedDefinitions` |
| mutable need deficit | `NeedSystem` / `NeedLookup` |
| immutable source need effects | `NeedSatisfactionDefinitions` |
| general knowledge that a Need has environmental solutions | `NeedSolutionKnowledgeDefinitions` |
| meaning of one opportunity family | its `AgentOpportunityProvider` |
| selected autonomous opportunity intent | `AgentSystem` |
| current epistemic search state | `AgentSearchSystem` |
| unguided exploration-leg choice | `UnguidedExplorationPolicy` |
| relative-search physical execution | `RelativeSearchLocomotion` |
| long-range/edge movement | existing MoveTo / Movement / Occupancy stack |
| current route diagnostics | `MoveToLookup.activeRoute(...)` |
| decision diagnostics | `AgentDecisionTrace` |
| search diagnostics | `AgentSearchTrace` |

The owners remain deliberately narrow. Decision does not mutate physiology; Need does not decide behavior; Search does not perform physical movement; presentation does not recompute perception or pathfinding.

## Open semantic identifiers

Needs and capabilities use open IDs rather than project-wide enums:

```text
NeedId("core:hunger")
CapabilityId("core:graze")
```

Adding another identifier does not require editing a central switch.

An ID alone does not create behavior. Meaning is supplied by the mechanic that owns definitions/state/opportunities for that ID.

## Agent composition

`AgentDefinition` currently contains autonomous capabilities only.

Vision, needs, source effects and knowledge are independent definition aspects:

```text
object definition
  ├─ agent
  ├─ vision
  ├─ needs
  ├─ needSolutionKnowledge
  └─ optional mechanic aspects such as needSatisfaction
```

This avoids turning `AgentDefinition` into a universal bag of every future cognitive, sensory and physiological property.

## Needs

A `NeedSpec` defines:

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

`NeedSystem` exclusively owns mutable deficit state.

Current mutation:

```text
satisfy(agent, need, amount)
    -> reduce by min(current deficit, amount)
```

Need progression over time is not implemented yet.

## Vision and physical orientation

Vision is an independent sensory mechanic.

A `VisionDefinition` currently supplies:

```text
range
horizontal FOV degrees
```

`VisionSystem` reads:

```text
authoritative XYZ
+ authoritative facing
+ VisionDefinition
+ CellObjectLookup
+ SightOcclusionLookup
```

and produces an immutable `VisionSnapshot` containing the cells and objects actually visible now.

The first occlusion adapter treats terrain occupying an intermediate sampled sight cell as opaque.

Decision does not depend directly on Vision. It consumes the sensor-neutral `PerceptionLookup`; today Vision provides that capability. Hearing/smell can later contribute through a perception composition boundary without adding sense-specific branches to Decision.

Movement changes physical facing only after a movement edge successfully commits.

## Mechanics expose opportunities

`AgentOpportunityProvider` is the narrow bridge from a mechanic into autonomous decision making.

```java
String id();
OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);
OpportunityUseResult use(ObjectId agentId, ObjectId sourceId);
List<OpportunitySearchDemand> searchDemands(ObjectId agentId);
```

`evaluate` receives only a source that reached current Perception. There is no repository-wide `findFood()`.

`use` revalidates current world facts after locomotion before mutating the mechanic.

`searchDemands` may say that an unresolved motivation has a class of environmental solutions worth seeking, without revealing any concrete source.

## First opportunity family: need satisfaction

A source definition may advertise that it can reduce a Need and optionally require a capability.

Grass, Hay and future compatible content are not known by name to generic Decision.

Eligibility currently depends on:

```text
source carries NeedSatisfaction data
agent carries the relevant Need
current deficit > 0
required capability is present, if configured
```

The current source effect is persistent. Quantity/depletion/harvesting are separate future resource mechanics.

## General semantic knowledge

`NeedSolutionKnowledgeDefinitions` represents only a narrow semantic fact:

```text
"this definition knows that environmental solutions exist for NeedId X"
```

Example:

```text
core:hunger -> environmental solutions exist
```

It does **not** contain:

```text
Grass/Hay definition IDs
ObjectIds
XYZ coordinates
last-known positions
routes
```

This is intentionally not a universal Knowledge framework. Other knowledge families receive their own owner only when real consumers appear; common structure is generalized only after evidence exists.

## Unknown-source search

When no concrete perceived opportunity exists, providers may emit `OpportunitySearchDemand`:

```text
motivation
urgency
```

For the Cow example this means approximately:

```text
"hunger is unresolved and I know environmental solutions exist"
```

not:

```text
"Grass is at (5, 0, 0)"
```

`AgentSystem` chooses among search demands deterministically and delegates the chosen epistemic process to `AgentSearchSystem`.

### Local visual sweep

The first search phase observes the current heading and rotates through four 90-degree headings.

Every heading is evaluated through ordinary authoritative Vision on the next scheduled think.

If a compatible source enters Perception, normal opportunity evaluation resumes and Search is cancelled.

### Unguided exploration

If the local sweep finds nothing, Search does not query a hidden map and does not invent a target.

It asks an `UnguidedExplorationPolicy` for an observer-relative exploration leg:

```text
previous search heading
+ deterministic exploration-leg ordinal
+ stable agent identity
+ current visual range
    ↓
relative heading + relative distance
```

The current implementation is `CorrelatedRandomWalkExplorationPolicy`.

It models directional persistence: the first few exploration legs continue the existing heading; later deterministic pseudo-random choices favor straight movement and less frequently turn left/right. Leg distance is also deterministic and bounded by the current visual range, so the agent normally moves several cells before another full local sweep instead of turning its head after every cell.

The current standard parameters are algorithm parameters, not Cow/source semantics, and the policy is behind an interface so another exploration algorithm does not require modifying Search/Decision.

No world coordinate is passed to `UnguidedExplorationPolicy`.

The policy returns:

```text
SearchRelocationRequest(
    FacingDirection heading,
    int distance)
```

with no XYZ.

## Relative search locomotion

Search itself never translates its relative leg into absolute world coordinates.

`AgentSystem`, which owns continuing autonomous intents, delegates the request to `RelativeSearchLocomotion`.

That adapter is an execution boundary. It may read physical XYZ because Movement requires physical state, but it does not expose those coordinates back to Search or Knowledge.

Before a leg starts, the agent physically faces the selected exploration heading. The locomotion adapter then reads a fresh authoritative Vision snapshot and resolves only the currently known ray:

```text
relative heading + requested distance
    ↓
face that heading
    ↓
fresh VisionSnapshot
    ↓
read current physical transform internally
    ↓
follow consecutive local Navigation transitions
only while every next cell is in current Vision
    ↓
truncate at the last visible/traversable cell
    ↓
production MoveTo to that local visible goal
```

The local Z preference is same level, then upward/downward adjacent transition when geometry allows it.

A request for six cells can therefore become a four-cell route if only four consecutive cells are currently visible/traversable. Search receives no absolute destination back.

Search cannot choose an arbitrary destination in unknown space. Exploration expands observation in multi-cell local legs whose destinations are inside current visual knowledge.

## Search relocation as a continuing intent

The relative exploration leg is owned by `AgentSystem` as a distinct continuing process from an opportunity intent.

```text
search requests one relative leg
    ↓
AgentSystem starts local visible MoveTo
    ↓
while MoveTo active: no new search relocation is issued
    ↓
completion
    ↓
AgentSearchSystem.relocationFinished(...)
    ↓
new observation / local sweep cycle
```

If the leg is blocked/rejected, Search changes its relative heading and resumes visual exploration. It does not branch on a global Movement error catalog.

## Candidate evaluation

The first provider still uses a deliberately provisional deterministic score:

```text
effectiveBenefit = min(currentDeficit, advertisedAmount)
score = max(1, effectiveBenefit * 1024 / (distance + 1))
```

Stable candidate order:

```text
1. score descending
2. distance ascending
3. source ObjectId ascending
4. provider registration order
```

This is not a claim that the final EvoForge utility model is solved. Multiple real motivations/providers are still required before response curves and consideration algebra are promoted into a stronger contract.

## Determinism

Current agent/search determinism relies on:

```text
stable perceived-object ordering
stable candidate tie breaks
stable search-demand ordering
simulation ticks, never wall-clock time
pure deterministic exploration-leg generation
```

The correlated exploration fallback derives directional and leg-length variation from stable agent identity plus exploration ordinal. It does not use nondeterministic runtime randomness.

## Scheduling

Current timings remain intentionally simple:

```text
first autonomous think       +1 tick
active MoveTo                poll each tick
active visual sweep          next heading each tick
relative exploration         one multi-cell MoveTo leg at a time
idle/no search demand        recheck after 10 ticks
```

These timings are current implementation, not permanent performance promises.

Representative scale profiling must precede specialized AI scheduling/index structures.

## Developer observability

`SimulationView` exposes read-only:

```text
OrientationLookup
VisionLookup
MoveToLookup
NeedLookup
AgentDecisionLookup
AgentSearchLookup
```

`AgentDecisionTrace` records candidates and winner.

`AgentSearchTrace` records current/last search motivation, phase, headings observed and facing.

`MoveToLookup.activeRoute(...)` exposes the immutable current route for diagnostics without letting presentation recompute pathfinding.

The generic visualizer shows for the selected object:

```text
visible-cell overlay, when Vision exists
visible-object frames
physical facing arrow
active MoveTo route cells and goal
Vision/facing inspector data
```

The route overlay is reason-agnostic: it works for search exploration, movement toward a selected opportunity and future MoveTo consumers alike.

Agent scenarios currently include:

```text
Agents -> Cow Foraging
Agents -> Cow Visual Search
```

`Cow Visual Search` uses a larger field and Vision radius. Grass starts well outside initial Vision, so the scenario visibly demonstrates local sweep -> multi-cell exploration route -> new Vision -> concrete Grass selection.

Presentation does not recreate Vision, Decision or Pathfinding logic.

## Current proofs

Headless tests now cover:

- visible Grass -> MoveTo -> authoritative Hunger reduction;
- plug-in Hay works without Cow/Decision edits;
- farther higher-benefit source can win;
- outside-range source is not initially known;
- source behind the Cow is excluded by FOV;
- opaque terrain blocks sight;
- semantic Need-solution knowledge can start Search without a concrete source;
- local visual sweep discovers a source only after turning toward it;
- without general solution knowledge, unseen food does not trigger Search;
- after an unsuccessful local sweep, the Cow expands search through a multi-cell relative physical leg without inventing a target;
- food outside the initial Vision is found only after physical exploration brings it into Vision;
- exploration heading and leg length are deterministic for stable identity/ordinal;
- exact candidate ties remain deterministic.

## Future spatial knowledge

Persistent spatial memory is **not required by the current Cow slice**.

If later mechanics need remembered places, cognition still should not require a global XYZ self-location. A richer agent may instead know relationships such as:

```text
"the settlement is beyond that hill"
"the water source is near the large tree"
"the hidden cache is beside this landmark"
```

That would justify future landmark/topological belief ownership rather than exposing `TransformLookup` to cognition.

Potential future guidance sources can include remembered landmarks/places, socially learned locations, maps, compasses, signs or other navigation instruments. When a real consumer appears, those sources can guide Search before the unguided exploration fallback.

No persistent Memory framework is introduced merely to complete the current animal-foraging loop.

## Explicitly deferred

Not yet implemented:

- need progression / metabolism;
- finite consumable resource quantity;
- plant growth/regrowth;
- water volume/fluid behavior;
- persistent individual beliefs and episodic memory;
- landmark/topological spatial memory;
- learned knowledge, confidence, forgetting and misinformation;
- map/compass/tool-assisted navigation;
- hearing, smell and multi-sense aggregation;
- personality, values, skills, relations and mood;
- motivation sources broader than physiological Needs;
- generalized utility response curves / consideration algebra;
- intent interruption and external-order arbitration;
- GOAP/HTN/BT planning;
- advanced Vision/body/eye-height semantics;
- AI-specific optimization before representative profiling.

The current search foundation is sufficient for the first living Cow loop without persistent memory. The next simulation layer should therefore return to world mechanics: finite food quantity, plant regrowth and physiology progression, rather than expanding cognition for its own sake.
