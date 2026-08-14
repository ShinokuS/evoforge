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
   ├─ concrete opportunity -> autonomous intent -> MoveTo -> use
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
| unguided heading choice | `UnguidedExplorationPolicy` |
| relative-search physical execution | `RelativeSearchLocomotion` |
| long-range/edge movement | existing MoveTo / Movement / Occupancy stack |
| decision diagnostics | `AgentDecisionTrace` |
| search diagnostics | `AgentSearchTrace` |

The owners remain deliberately narrow. Decision does not mutate physiology; Need does not decide behavior; Search does not perform physical movement; presentation does not recompute perception.

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

This is intentionally not a universal Knowledge framework. Other knowledge families receive their own owner when real consumers appear; common structure is generalized only after evidence exists.

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

It asks an `UnguidedExplorationPolicy` only for a relative heading:

```text
previous search heading
+ deterministic search step ordinal
+ stable agent identity
    ↓
next relative heading
```

The current implementation is `CorrelatedRandomWalkExplorationPolicy`.

It models directional persistence: the first few exploration moves continue the existing heading; later deterministic pseudo-random choices favor straight movement and less frequently turn left/right. The current composition uses a provisional standard parameter set. Those numbers are algorithm parameters, not Cow/source semantics, and the policy is behind an interface so another exploration algorithm does not require modifying Search/Decision.

No world coordinate is passed to `UnguidedExplorationPolicy`.

The policy returns:

```text
SearchRelocationRequest(FacingDirection)
```

with no XYZ.

## Relative search locomotion

Search itself never translates its heading into absolute world coordinates.

`AgentSystem`, which owns continuing autonomous intents, delegates the relative request to `RelativeSearchLocomotion`.

That adapter is an execution boundary. It may read physical XYZ because Movement requires physical state, but it does not expose those coordinates back to Search or Knowledge.

Current behavior:

```text
relative heading
    ↓
read current physical transform internally
    ↓
inspect only local Navigation transitions
    ↓
choose one adjacent traversable cell
    ↓
production MoveTo to that adjacent cell
```

The local Z preference is same level, then upward/downward adjacent transition when geometry allows it.

Search therefore cannot ask Pathfinder to choose an arbitrary destination in unknown space. Exploration expands observation one local step at a time.

## Search relocation as a continuing intent

The relative exploration move is owned by `AgentSystem` as a distinct continuing process from an opportunity intent.

```text
search requests one relative move
    ↓
AgentSystem starts adjacent MoveTo
    ↓
while MoveTo active: no new search relocation is issued
    ↓
completion
    ↓
AgentSearchSystem.relocationFinished(...)
    ↓
new local observation cycle
```

If the local move is blocked/rejected, Search changes its relative heading and resumes visual exploration. It does not branch on a global Movement error catalog.

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
pure deterministic exploration heading generation
```

The correlated exploration fallback derives variation from stable agent identity plus search-step ordinal. It does not use nondeterministic runtime randomness.

## Scheduling

Current timings remain intentionally simple:

```text
first autonomous think       +1 tick
active MoveTo                poll each tick
active visual sweep          next heading each tick
relative exploration move    one adjacent MoveTo at a time
idle/no search demand        recheck after 10 ticks
```

These timings are current implementation, not permanent performance promises.

Representative scale profiling must precede specialized AI scheduling/index structures.

## Developer observability

`SimulationView` exposes read-only:

```text
OrientationLookup
VisionLookup
NeedLookup
AgentDecisionLookup
AgentSearchLookup
```

`AgentDecisionTrace` records candidates and winner.

`AgentSearchTrace` records current/last search motivation, phase, headings observed and facing.

The visualizer reads the authoritative `VisionSnapshot`; selected objects with Vision show:

```text
visible-cell overlay
visible-object frames
physical facing arrow
Vision/facing inspector data
```

Agent scenarios currently include:

```text
Agents -> Cow Foraging
Agents -> Cow Visual Search
```

Presentation does not recreate Vision or Decision logic.

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
- after an unsuccessful local sweep, the Cow expands search through relative physical movement without inventing a target;
- food outside the initial Vision is found only after physical exploration brings it into Vision;
- exact candidate ties remain deterministic.

## Spatial knowledge direction

The current fallback is deliberately primitive because no persistent spatial memory exists yet.

Future cognition should not need a global XYZ self-location. A richer agent may instead know relationships such as:

```text
"the settlement is beyond that hill"
"the water source is near the large tree"
"the hidden cache is beside this landmark"
```

This suggests future landmark/topological belief ownership rather than exposing `TransformLookup` to cognition.

Potential future guidance sources can include:

```text
remembered landmarks / places
socially learned locations
maps
compasses
signs
other navigation instruments
```

When such guidance exists, it should guide Search before the unguided exploration fallback. The fallback remains useful when the agent has no better spatial information.

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

The next major semantic layer after the current Cow foundation is no longer "make Search wander". Unguided exploration now exists. The next persistent-cognition problem is landmark/topological belief: remember meaningful places and relations without turning the agent into an XYZ-aware omniscient navigator.
