# Autonomous Agents

## Purpose

The agent subsystem is the first production proof that autonomous behavior can emerge from composable world mechanics rather than from species-specific behavior scripts.

The current Cow slice proves five concrete properties:

```text
A. actual perception -> opportunity -> MoveTo -> interaction
B. a new compatible source works without Cow/Decision code changes
C. competing perceived sources are evaluated deterministically
D. current perception is constrained by physical facing, FOV, range and occlusion
E. general solution knowledge can trigger information-seeking without revealing a concrete source
```

The implementation is intentionally smaller than the long-term AI design. Persistent beliefs, episodic memory, personality, social reasoning and multi-step planning remain later consumers.

## Core boundary

There is no `CowAI`, species behavior list, global `ActionType`, global `NeedType`, or central affordance switch.

```text
Object definition
  ├─ AgentDefinition              capabilities
  ├─ VisionDefinition             range + horizontal FOV
  ├─ NeedDefinitions              physiological deficits
  └─ NeedSolutionKnowledge        general solution knowledge

Physical world
  ├─ Spatial XYZ
  └─ Orientation / facing
          ↓
       Vision
          ↓
   PerceptionSnapshot
          ↓
AgentOpportunityProvider implementations
          ↓
current candidates / unresolved search demands
          ↓
        Decision
       /        \
 concrete       no concrete
 candidate      candidate
    ↓               ↓
 MoveTo         AgentSearch
    ↓               ↓
provider use    new perception
```

The generic agent code never asks for Grass, Hay or any other concrete content type. Concrete objects become relevant only when mechanic data makes them compatible with the current agent state.

## Ownership

| Concern | Authoritative owner / contract |
| --- | --- |
| object existence and definition identity | `ObjectRepository` / `ObjectLookup` |
| object XYZ | `SpatialSystem` / `TransformLookup` |
| physical facing | `OrientationSystem` / `OrientationLookup` + `OrientationMutations` |
| immutable vision configuration | `VisionDefinitions` |
| current visual visibility | `VisionSystem` / `VisionLookup` |
| sensor-neutral current perception | `PerceptionLookup` |
| immutable autonomous composition | `AgentDefinitions` |
| immutable need declarations | `NeedDefinitions` |
| mutable per-object need deficit | `NeedSystem` |
| immutable need-satisfaction source data | `NeedSatisfactionDefinitions` |
| general knowledge that a need has environmental solutions | `NeedSolutionKnowledgeDefinitions` |
| meaning of one opportunity family | its `AgentOpportunityProvider` |
| current autonomous intent | `AgentSystem` |
| current local information-seeking process | `AgentSearchSystem` |
| locomotion to a selected source | existing `MoveToSystem` / Movement stack |
| decision diagnostics | `AgentDecisionTrace` / `AgentDecisionLookup` |
| search diagnostics | `AgentSearchTrace` / `AgentSearchLookup` |

`AgentSystem` does not mutate need state, calculate line of sight, or change authoritative XYZ. `VisionSystem` does not decide what an agent wants. `AgentSearchSystem` does not invent a target. `MoveTo` does not know why a destination is desirable.

## Open semantic identifiers

Needs and capabilities use open identifiers:

```text
NeedId("core:hunger")
CapabilityId("core:graze")
```

They are not project-wide enums. Adding a new semantic identifier does not require editing a central switch.

An identifier alone does not define behavior. Meaning comes from the mechanic that owns the corresponding definition/state/provider contract.

## Agent composition

`AgentDefinition` currently owns only autonomous capabilities. Sensory parameters deliberately do not live in a generic agent property bag.

```json
{
  "agent": {
    "capabilities": ["core:graze"]
  },
  "vision": {
    "range": 8,
    "horizontalFovDegrees": 120
  }
}
```

`VisionDefinition` is independent because vision has its own semantics and can evolve without expanding `AgentDefinition`. The same rule applies to future personality, skill, memory and other real subsystems: they receive their own owner when a concrete consumer requires them.

## Needs

A `NeedSpec` currently defines:

```text
NeedId
maxLevel
initialLevel
```

Runtime semantics are deficit-oriented:

```text
0              fully satisfied
maxLevel       maximum configured deficit
```

`NeedSystem` is the only owner of mutable need levels. The current slice models an initially hungry Cow; need progression over time does not exist yet.

A source can advertise need satisfaction independently:

```json
{
  "needSatisfaction": {
    "core:hunger": {
      "amount": 35,
      "requiresCapability": "core:graze"
    }
  }
}
```

Grass and Hay therefore do not need special AI cases. They are ordinary object definitions carrying compatible mechanic data.

## Orientation

Facing is physical simulation state, separate from position.

```text
TransformLookup    -> where the object is
OrientationLookup  -> where the object faces
```

`OrientationSystem` is the authoritative owner. Objects with Vision receive an orientation during runtime assembly. Movement updates facing only after a movement step successfully commits; a failed movement does not silently rotate the object.

The current orientation is a 2D direction used by horizontal FOV. This contract is intentionally separate from renderer-facing sprite orientation.

## Vision

`VisionSystem` owns current visual visibility. It reads:

```text
ObjectLookup
TransformLookup
CellObjectLookup
OrientationLookup
VisionDefinitions
SightOcclusionLookup
```

For an observer with Vision, the current implementation evaluates cells inside a 3D Euclidean range sphere, applies horizontal FOV around authoritative facing, and rejects cells whose sampled sight line crosses an occluding cell.

`TerrainSightOcclusionLookup` is the first occlusion adapter: terrain occupying a sampled sight cell is opaque. `VisionSystem` depends only on `SightOcclusionLookup`, so walls, doors, vegetation or other future occluders can change the occlusion implementation without teaching Decision about concrete world types.

A `VisionSnapshot` contains the exact visible cells and visible objects. It is current sensory truth only; it is not memory or a belief store.

## Sensor-neutral perception

`AgentSystem` does not depend on `VisionSystem` directly. It reads `PerceptionLookup`:

```text
Vision today ─────────┐
future hearing ───────┼─> PerceptionLookup -> AgentSystem
future smell ─────────┘
```

Today `VisionSystem` also implements `PerceptionLookup`, so the current perceived object set is exactly the visual object set. This seam exists because multiple senses are already a real architectural requirement, not as a generic event framework.

The important invariant is:

> Candidate generation may use only the agent's current perception, never a hidden global object search.

`AgentSystem` iterates `PerceptionSnapshot.objects()` and offers those objects to registered opportunity providers. It does not enumerate `ObjectRepository` and there is no `findFood()` API.

## Opportunity providers

`AgentOpportunityProvider` is the mechanic-owned bridge into autonomous reasoning:

```java
String id();
OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance);
OpportunityUseResult use(ObjectId agentId, ObjectId sourceId);
List<OpportunitySearchDemand> searchDemands(ObjectId agentId);
```

`evaluate(...)` may only interpret a concrete perceived source. `use(...)` revalidates the chosen interaction after locomotion. `searchDemands(...)` may describe an unresolved motivation, but it does not provide a hidden source or location.

Normal world invalidation is returned as structured result data. Broken configuration/programming invariants remain exceptions.

## Current utility comparison

The first need-satisfaction provider uses a deliberately provisional integer score:

```text
effectiveBenefit = min(currentDeficit, advertisedAmount)
score = max(1, effectiveBenefit * 1024 / (distance + 1))
```

This proves competition between opportunities without freezing the final utility model.

Generic candidate ordering is deterministic:

```text
1. score descending
2. distance ascending
3. source ObjectId ascending
4. provider registration order
```

There is no random tie break and no dependence on hash iteration order.

## General solution knowledge

The first semantic-knowledge slice deliberately stores less information than a belief system.

```json
{
  "needSolutionKnowledge": {
    "needs": ["core:hunger"]
  }
}
```

For a Cow definition this means only:

> this kind of agent generally knows that an environmental solution for hunger exists.

It does **not** encode:

```text
Grass
Hay
ObjectDefinitionId of a source
source ObjectId
XYZ
last-seen location
```

`NeedSolutionKnowledgeDefinitions` is immutable definition-level baseline knowledge for the first consumer. Learned individual knowledge, confidence, false beliefs and episodic memory require separate mutable ownership later; they must not be hidden inside this definition store.

## Information-seeking search

When no concrete candidate is perceived, each opportunity provider may emit `OpportunitySearchDemand` values. The need-satisfaction provider emits one only when:

```text
the agent owns the need
current deficit > 0
general solution knowledge says the need has environmental solutions
```

A demand contains only:

```text
motivation
urgency
```

It contains no source identity or position.

`AgentSystem` deterministically selects the most urgent demand and delegates information acquisition to `AgentSearchSystem`.

The first search strategy is intentionally small: a local 360-degree visual sweep.

```text
current heading is already perceived
        ↓
turn to next cardinal heading
        ↓
next scheduled think perceives again
        ↓
repeat until four headings were observed
```

If a compatible source enters actual Vision during the sweep, normal candidate evaluation resumes, the current search is cancelled, and the agent starts the ordinary MoveTo intent.

If all four headings are observed without a source, the search records `LOCAL_SWEEP_EXHAUSTED`, leaves the agent at its current XYZ and returns to the normal idle recheck cadence. The current slice does not yet move between observation points.

This is intentional. Random wandering would hide the missing semantics. A later spatial exploration strategy should extend the same information-seeking process with explicit observation-point choice and movement, without adding `SearchForGrass` or world-truth lookup.

## Intent and execution

When a concrete candidate wins:

```text
Agent decision
   ↓
MoveToSystem.start(...)
   ↓
Pathfinder
   ↓
MovementClaim
   ↓
per-edge Movement / Occupancy revalidation
```

The autonomous process calls the domain boundary directly rather than submitting an external `MoveToCommand`, preserving the architecture rule that Commands represent external intent while continuing internal processes remain domain-owned.

If movement fails, the intent ends and the agent later reconsiders. If movement reaches the source, the selected provider revalidates current facts before applying its mechanic mutation.

For need satisfaction, use currently requires both objects to remain alive and co-located, the capability/need relation to remain valid, and a positive remaining deficit.

## Scheduling

The first autonomous process is Scheduler-driven:

```text
activation -> first think after 1 tick
active MoveTo -> poll after 1 tick
active local visual search -> perceive again after 1 tick
idle / exhausted local search -> recheck after 10 ticks
terminal interaction -> reconsider after 1 tick
```

This polling cadence is a current implementation, not a long-term performance contract. Optimization follows representative profiling rather than speculative concurrency or AI-specific indexes.

## Developer observability

`SimulationView` exposes read-only:

```text
NeedLookup
AgentDecisionLookup
OrientationLookup
VisionLookup
AgentSearchLookup
```

`AgentDecisionTrace` records the candidate set and selected winner for a decision pass. `AgentSearchTrace` records provider, motivation, search status, headings observed and current facing.

The visualizer reads these authoritative diagnostics; it does not recompute AI semantics.

When an object with Vision is selected, `VisionDiagnosticRenderer` draws the same `VisionSnapshot` produced by simulation:

```text
soft highlight -> cells actually visible now
object frame   -> objects actually visible now
facing arrow   -> authoritative physical orientation
```

This makes the development UI answer the exact question: "what does this selected object currently see?"

Agent scenarios currently include:

- `Cow Foraging` — visible competing opportunities and deterministic winner;
- `Cow Visual Search` — Cow starts facing away from food, has only general hunger-solution knowledge, turns until Grass actually enters Vision, then selects it normally.

## Definition compilation

The current independent object-definition aspects are:

```text
agent                  -> AgentDefinitionCompiler
vision                 -> VisionDefinitionCompiler
needs                  -> NeedDefinitionCompiler
needSatisfaction       -> NeedSatisfactionDefinitionCompiler
needSolutionKnowledge  -> NeedSolutionKnowledgeDefinitionCompiler
```

`ObjectDefinitionBootstrap` remains generic and receives concrete compilers from its composition root. There is no central behavior catalog.

`SimulationAssembly` exposes focused programmatic helpers for headless tests and debug scenarios that populate the same runtime concepts.

## Tests that pin the current contract

Headless tests prove, among other cases:

- visible food is selected, reached through production MoveTo and satisfies hunger;
- a new Hay definition works without Cow/Decision changes;
- stronger farther food can beat weaker nearby food;
- food outside Vision range is not a candidate;
- food behind the Cow is not a visual candidate;
- opaque terrain blocks a source from Vision;
- semantic hunger-solution knowledge can start search without revealing the source;
- a source behind the Cow becomes selectable only after the sweep turns Vision toward it;
- without general solution knowledge, unseen food does not trigger search;
- a complete unsuccessful local sweep ends as `LOCAL_SWEEP_EXHAUSTED` without inventing a target or moving the Cow;
- equal candidates use stable ObjectId ordering;
- independent definition aspects compile and freeze their stores.

Scenario tests verify that developer diagnostics expose the same authoritative decision/search state used by simulation.

## Explicitly deferred

The following remain outside the current contract:

- need decay and physiological progression;
- persistent individual beliefs and episodic memory;
- learned semantic knowledge, confidence, forgetting and misinformation;
- movement between observation points / broader spatial exploration;
- hearing, smell and multi-sense perception aggregation;
- richer vertical vision/body/eye-height semantics and advanced occluders;
- personality, values, skill, relationships and moods;
- motivation sources broader than physiological needs;
- intent inertia/hysteresis and interruption policy;
- player/external-order versus autonomous-intent arbitration;
- generalized utility response curves/consideration algebra;
- GOAP/HTN/BT planning beneath selected intents;
- resource consumption, depletion, harvesting and production lifecycle;
- mating, pregnancy, birth, aging, lactation, excretion and herd/social behavior;
- AI-scale optimization before representative profiling.

The next search-specific extension should not add a Cow behavior. It should give the existing epistemic search process a spatial exploration strategy that chooses reachable observation points from known traversal facts, moves through the ordinary Movement stack, and keeps discovering concrete sources only through perception.
