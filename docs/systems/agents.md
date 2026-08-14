# Autonomous Agents

## Purpose

The current agent subsystem is the first production proof that autonomous behavior can emerge from composable world mechanics rather than from a species-specific behavior script.

The first consumer is a Cow foraging slice. It deliberately proves only three things:

```text
A. current perception -> visible opportunity -> MoveTo -> interaction
B. a new compatible food source works without editing Cow/Decision code
C. competing visible sources are evaluated and one is selected deterministically
```

Persistent beliefs, memory and unknown-source search are not part of the current implemented contract. The broader research direction is preserved in [Agent AI foundations](../notes/2026-08-14-agent-ai-foundations.md).

## Current boundary

There is no `CowAI`, species behavior list or central action enum.

```text
Object definition
  ├─ AgentDefinition
  │    ├─ perception radius
  │    └─ capabilities
  └─ Need definitions
       └─ mutable runtime deficits

Perceived world objects
       ↓
registered AgentOpportunityProvider implementations
       ↓
current candidates
       ↓
deterministic evaluation
       ↓
selected intent
       ↓
production MoveTo
       ↓
provider revalidation/use
       ↓
authoritative mechanic mutation
```

Generic agent code does not know that a source is Grass, Hay or any other concrete content type. It knows only that a registered mechanic provider returned a currently valid opportunity.

## Ownership

| Concern | Authoritative owner / contract |
| --- | --- |
| object existence and definition identity | `ObjectRepository` / `ObjectLookup` |
| object XYZ | `SpatialSystem` / `TransformLookup` |
| objects currently present in a cell | `CellSpatialIndex` / `CellObjectLookup` |
| immutable autonomous-object composition | `AgentDefinitions` |
| immutable need declarations | `NeedDefinitions` |
| mutable per-object need deficit | `NeedSystem` |
| immutable need-satisfaction source data | `NeedSatisfactionDefinitions` |
| meaning of one opportunity family | its `AgentOpportunityProvider` implementation |
| currently selected autonomous intent | `AgentSystem` |
| locomotion to a selected source | existing `MoveToSystem` / Movement stack |
| developer-facing decision explanation | immutable `AgentDecisionTrace` observed through `AgentDecisionLookup` |

`AgentSystem` does not mutate need levels. `NeedSystem` does not decide behavior. An opportunity provider does not move an agent. `MoveTo` does not decide why a destination is desirable.

## Open semantic identifiers

Needs and capabilities use open identifiers:

```text
NeedId("core:hunger")
CapabilityId("core:graze")
```

They are intentionally not global enums. Adding a new semantic identifier does not require editing one project-wide switch or catalog enum.

An identifier alone does not define behavior. Its meaning comes from the mechanic that owns the corresponding definition/state/provider contract.

## Agent definition

The current `AgentDefinition` contains only:

```text
perceptionRadius
capabilities[]
```

Capabilities are sorted and unique in immutable runtime definition data.

Example data aspect:

```json
{
  "agent": {
    "perceptionRadius": 8,
    "capabilities": ["core:graze"]
  }
}
```

`AgentDefinitionCompiler` owns the `agent` definition aspect. The definition bootstrap remains composition-driven: a composition root registers the compiler when that definition source is used.

The first slice does not claim that all future agent cognition belongs inside `AgentDefinition`. Personality, knowledge, skills, relationships and other future semantics get their own owner when a real consumer requires them.

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

`NeedSystem` is the only owner of mutable need levels. It initializes state from immutable definition data and exposes read-only state through `NeedLookup`.

The current mutation is:

```text
satisfy(agent, need, amount)
  -> reduce deficit by min(current deficit, amount)
  -> return amount actually applied
```

Need decay/progression over time does not exist yet. Therefore the current Cow starts hungry because its initial definition state says so; this slice does not yet model becoming hungry later.

Example definition aspect:

```json
{
  "needs": {
    "core:hunger": {
      "max": 100,
      "initial": 60
    }
  }
}
```

`NeedDefinitionCompiler` sorts open need keys before compiling them so definition order is stable.

## Mechanics expose opportunities

`AgentOpportunityProvider` is the current narrow extension point between a mechanic and generic autonomous decision making:

```java
String id();

OpportunityEvaluation evaluate(
    ObjectId agentId,
    ObjectId sourceId,
    int distance);

OpportunityUseResult use(
    ObjectId agentId,
    ObjectId sourceId);
```

`evaluate(...)` answers only:

> Does this currently perceived source offer a meaningful possibility to this agent, and what provider-owned evidence should the generic selector compare?

Returning `null` means that this provider currently offers nothing for that agent/source pair.

`use(...)` revalidates and applies the selected interaction after locomotion. Normal world invalidation is a structured `OpportunityUseResult`; broken configuration/programming invariants remain exceptions.

This interface is intentionally smaller than a generalized action/planning framework. A future mechanic with genuinely different semantics may provide another implementation without adding a case to `AgentSystem`.

## First provider: need satisfaction

`NeedSatisfactionOpportunityProvider` is the first concrete provider.

A source definition may advertise that it can reduce one need deficit:

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

A source is currently eligible when all required facts hold:

```text
source definition has NeedSatisfaction data
agent definition is autonomous
required capability is present, if one is required
agent actually owns the advertised NeedId
current deficit > 0
```

No Grass/Hay names occur in generic decision code or in this provider. Grass and Hay are just object definitions carrying compatible mechanic data.

The current source is persistent. Using it does not consume, deplete or destroy the source. Resource quantity, harvesting, depletion and consumption ownership are separate future mechanics rather than hidden behavior inside the first opportunity contract.

## Current perception

The first slice has **current perception only**.

`AgentSystem` reads the agent's authoritative XYZ and performs a deterministic bounded scan through `CellObjectLookup` around that position. It does not enumerate `ObjectRepository` and does not ask a global `findFood()` query.

The current range shape is a Chebyshev cube:

```text
max(|dx|, |dy|, |dz|) <= perceptionRadius
```

Each encountered object is considered at most once per decision pass. Every registered opportunity provider may evaluate that perceived object.

Consequences:

- a food source outside the perception radius is not a candidate;
- the agent has no current way to remember a source after it leaves perception;
- the agent has no current general knowledge such as "grass exists somewhere";
- there is no line-of-sight/occlusion rule yet;
- camera visibility is irrelevant and cannot affect perception.

The current bounded cell scan is a semantic implementation, not a permanent storage/index promise. A specialized range index may replace it later if representative profiling proves this scan hot while preserving the same authoritative perception semantics.

## Candidate evaluation

The first provider uses a deliberately provisional score. It exists to prove competition between opportunities, not to freeze EvoForge's final utility mathematics.

For one need-satisfaction advertisement:

```text
effectiveBenefit = min(currentDeficit, advertisedAmount)

score = max(
    1,
    effectiveBenefit * 1024 / (distance + 1)
)
```

The integer scale keeps evaluation deterministic and avoids making floating-point curve design part of the first slice.

A farther source can therefore beat a nearby weak source when its expected benefit is sufficiently larger.

The generic candidate order is stable:

```text
1. score descending
2. distance ascending
3. source ObjectId ascending
4. provider registration order
```

There is no random tie break and no dependence on `HashMap`/`HashSet` iteration order.

Response curves, geometric-mean consideration aggregation, personality modifiers, risk, hysteresis and richer utility semantics remain design hypotheses until additional real motivations/providers give enough evidence to choose a contract.

## Intent and execution

When a candidate wins, `AgentSystem` starts the existing production `MoveToSystem` directly:

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

The autonomous process does **not** submit a `MoveToCommand` through Control. This preserves the existing global rule:

> Commands are external intent; internal continuing processes call their domain boundaries directly.

The selected opportunity becomes an `ActiveIntent` owned by `AgentSystem`. It records only the provider/source/MoveTo identity needed to continue this first interaction.

If movement fails, the intent ends and the agent reconsiders on a later scheduled pass. `AgentSystem` does not branch on Movement result-code catalogs and does not implement automatic MoveTo replanning/waiting policy.

If the goal is reached, the selected provider receives `use(agent, source)`. The provider revalidates current world facts before mutating its own mechanic. For need satisfaction this includes current object existence, transforms, co-location, capability, need presence and remaining deficit.

The route/arrival result therefore does not make a stale opportunity authoritative.

## Scheduling

The current autonomous process is Scheduler-driven rather than updated every render frame.

Current timings are intentionally simple:

```text
activation -> first think after 1 simulation tick
active MoveTo intent -> recheck after 1 tick
no candidate -> recheck after 10 ticks
terminal interaction -> reconsider after 1 tick
```

While travelling, the first implementation polls `MoveToLookup` once per tick. This is a conscious simplicity choice, not a long-term performance guarantee.

A direct MoveTo completion callback was considered for this slice and deliberately not introduced. `MoveTo.start()` may terminate synchronously during planning/first-edge start, so adding an external callback would change MoveTo reentrancy and failure-cleanup semantics. There is not yet a representative agent workload proving that one lookup/scheduled activation per active traveller is a hot path.

The optimization rule remains:

```text
instrument representative agents
    -> identify actual scheduling/perception hot paths
    -> optimize behind the existing semantic contracts
```

Future threshold-predicted wake times and event-driven early wakeups remain expected directions, not current behavior.

## Decision trace and developer observability

Every decision pass stores an immutable `AgentDecisionTrace`:

```text
tick
agentId
all evaluated candidate traces
selected candidate, if any
```

Each `AgentCandidateTrace` currently includes:

```text
provider id
source object id + XYZ
distance
expected benefit
score
motivation label
```

`SimulationView` exposes this through read-only `AgentDecisionLookup`, alongside read-only `NeedLookup`.

The visualizer does not recompute AI reasoning. The `Agents -> Cow Foraging` scenario reads the authoritative trace and shows:

```text
current hunger
current target
candidate count
winner
expected benefit
distance
score
```

Alternative candidates use warning markers; the selected winner uses a goal marker. This is the first small piece of the future AI Inspector described in the research note.

## Cow proof

The production headless slice proves these cases.

### Visible food

```text
Cow hunger = 80
Grass perceived at x=2
Grass advertises hunger reduction 30

Cow decision
  -> selects Grass
  -> production MoveTo
  -> reaches source
  -> provider revalidates
  -> NeedSystem changes hunger 80 -> 50
```

### Plug-in Hay

A separate Hay definition advertises the same need through the same provider contract.

No Cow-specific behavior or `AgentSystem` code changes are needed. The hungry Cow selects and uses Hay because the mechanic data makes it a compatible opportunity.

This is the first direct proof of the plug-in requirement.

### Competing sources

```text
near Grass: benefit 10
far Hay:    benefit 60
```

The current scoring selects the farther Hay when its benefit outweighs distance. The visualizer scenario demonstrates the same type of competition with both candidates visible in the authoritative trace.

### No omniscient source lookup

Food outside the Cow's current perception radius does not appear in the candidate set and does not cause movement.

This is only the absence of omniscience. It is **not yet** the richer behavior "I know food exists but do not know where, therefore search for it".

## Definition compilation

The first slice adds three independent object-definition compilers:

```text
agent                 -> AgentDefinitionCompiler
needs                 -> NeedDefinitionCompiler
needSatisfaction      -> NeedSatisfactionDefinitionCompiler
```

They are independent `DefinitionAspectCompiler<ObjectDefinitionId>` implementations. `ObjectDefinitionBootstrap` remains generic and receives concrete compilers from its composition root.

There is deliberately no central:

```text
AffordanceType
ActionType
NeedType enum
Cow behavior registry
```

A future mechanic that cannot honestly fit `NeedSatisfactionOpportunityProvider` should introduce its own definition semantics/provider rather than expanding a central type switch.

`SimulationAssembly` also exposes focused programmatic configuration helpers for headless/scenario composition. Both routes populate the same runtime definition/state concepts.

## Testing

Current headless integration tests prove:

- a hungry Cow perceives a visible source, reaches it through production `MoveTo`, and changes the authoritative need;
- a newly defined Hay source is used without editing Cow/Decision code;
- a farther high-benefit source can beat a nearby weak source;
- a source outside current perception does not become known through global state;
- equal candidates use a stable ObjectId tie break;
- agent/need/need-satisfaction definition aspects compile independently and freeze their stores.

The visualizer scenario test verifies that the Cow scenario exposes the same authoritative two-candidate decision and winner diagnostics used by presentation.

## Explicitly deferred

The following are intentionally outside the current contract:

- need decay and physiological progression;
- persistent `Belief` / memory ownership;
- general semantic knowledge such as "food exists";
- information-seeking and unknown-source search;
- line of sight, hearing, smell or uncertainty;
- personality, values, skill, relationships and moods;
- motivation sources broader than physiological needs;
- intent inertia/hysteresis and interruption policy;
- player/external-order versus autonomous-intent arbitration;
- generalized utility response curves/consideration algebra;
- GOAP/HTN/BT planning beneath selected intents;
- resource consumption, depletion, harvesting and production lifecycle;
- mating, pregnancy, birth, aging, lactation, excretion and herd/social behavior;
- behavior-scale optimization or AI-specific spatial indexes before profiling.

The next conceptual proof from the research note is the first persistent subjective-knowledge slice:

```text
motivation exists
+ no concrete source is currently known
+ general knowledge says a source category exists
-> information-seeking/search intent
-> perception discovers a concrete source
-> ordinary opportunity evaluation resumes
```

That slice must introduce its own knowledge/belief ownership rather than giving `AgentSystem` hidden access to world truth.
