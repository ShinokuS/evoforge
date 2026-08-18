# Autonomous Agents

## Purpose

Prove that autonomous behavior can emerge from composable world mechanics rather than species-specific scripts.

The current production slice demonstrates:

```text
Need progression -> motivation threshold
        ↓
3D Perception -> mechanic-owned opportunities
        ↓
cheap execution eligibility -> common deterministic Utility
        ↓                                      ↘ no concrete usable opportunity
committed intent                                semantic search demand
        ↓                                            ↓
MoveTo to explicit InteractionSite          visual sweep / relative exploration
        ↓                                            ↓
provider-owned timed use <--------------- discovery through Perception
        ↓
Need reduction + finite source mutation
        ↓
world mechanics continue independently
```

The same decision layer now handles finite plant food and finite free Water. There is no `CowAI`, `EatGrassAction`, `DrinkWaterAction`, source-type switch, global action enum or omniscient `findFood()` / `findWater()`.

The broader research direction remains non-normative in [Agent AI foundations](../notes/2026-08-14-agent-ai-foundations.md). This page describes implemented semantics only.

## Core boundary

```text
World truth
   ↓
Orientation + sensory mechanics
   ↓
PerceptionSnapshot
   ├─ perceived objects
   └─ perceived cells
   ↓
mechanic-owned AgentOpportunityProvider(s)
   ↓
AgentSystem
   ├─ concrete opportunity -> execution eligibility -> Utility -> MoveTo InteractionSite -> provider-owned use lifecycle
   └─ no usable opportunity -> semantic search demand -> AgentSearchSystem
```

Need progression, Water flow, precipitation, evaporation, finite stock and Growth are independent world processes. They change authoritative state; Agent observes the resulting present-tense world through ordinary perception/decision passes.

Simulation internally uses XYZ coordinates, but cognition/search is not given an omniscient global coordinate map or hidden source locations.

## Ownership

| Concern | Owner / contract |
| --- | --- |
| object existence / definition identity | `ObjectRepository` / `ObjectLookup` |
| physical XYZ | `SpatialSystem` / `TransformLookup` |
| physical facing | `OrientationSystem` / `OrientationLookup` |
| autonomous capabilities | `AgentDefinitions` |
| Vision parameters | `VisionDefinitions` |
| current visual truth | `VisionSystem` / `VisionLookup` |
| sensor-neutral perceived objects/cells | `PerceptionLookup` |
| Need definitions / mutable deficits | `NeedDefinitions` / `NeedSystem` |
| motivation thresholds | `NeedMotivationDefinitions` |
| autonomous Need progression | `NeedProgressionSystem` |
| finite object-source quantity | `ConsumableStockSystem` |
| finite free liquid quantity | `LiquidSystem` / liquid-specific lookup |
| object-source Need effects/use timing | `NeedSatisfactionDefinitions` + provider |
| liquid drinking capability/use timing | `LiquidDrinkDefinitions` + provider |
| physical interaction reach | `InteractionReachProfile` + `InteractionAccessResolver` |
| local mover arrival eligibility | `MoverDestinationAccessResolver` |
| source regrowth | `GrowthSystem` |
| semantic knowledge that a Need has environmental solutions | `NeedSolutionKnowledgeDefinitions` |
| selected autonomous intent | `AgentSystem` |
| current epistemic search state | `AgentSearchSystem` |
| relative exploration choice | `UnguidedExplorationPolicy` |
| search physical execution | `RelativeSearchLocomotion` |
| route/edge movement | MoveTo / Movement / Occupancy stack |
| decision/intent/search diagnostics | corresponding trace/lookups |

Decision does not mutate physiology or Water directly. Need does not choose behavior. Search does not own Movement. Presentation does not recreate AI truth.

## Open semantic identifiers

Needs and capabilities use open identifiers such as:

```text
NeedId("core:hunger")
NeedId("core:thirst")
CapabilityId("core:graze")
```

Adding another identifier does not require a central enum/switch. An identifier alone does not create behavior; its mechanic supplies the semantics.

## Definition composition

Autonomous content is composed from independent definition aspects rather than one universal Agent definition bag:

```text
object definition
  ├─ agent
  ├─ vision
  ├─ needs
  ├─ needMotivation
  ├─ needProgression
  ├─ needSolutionKnowledge
  ├─ needSatisfaction
  ├─ liquidDrink
  ├─ movement / traversal capability
  ├─ consumableStock
  └─ growth
```

This keeps sensing, physiology, locomotion, source capability, interaction semantics and regrowth independently extensible.

## Needs, motivation and progression

Need levels are deficit-oriented: `0` is satisfied and `maxLevel` is maximum configured deficit.

`NeedSystem` exclusively owns mutable levels. Current writes are narrow:

```text
satisfy(...)              reduce deficit
NeedDeficitIncrease       increase deficit
```

A positive deficit does not necessarily justify action. `NeedMotivationDefinition(NeedId, activationLevel)` states when environmental satisfaction becomes behaviorally relevant.

Automatic time dynamics are separate `NeedProgressionDefinition(NeedId, baseAmount, intervalTicks)` processes. Current rates are resolved through an external rate resolver so future activity/temperature/etc. can modify physiology without Hunger- or Thirst-specific branches.

See [Need Progression](need-progression.md).

## Vision, perception and verticality

Vision is an independent 3D sensory mechanic. `VisionSystem` reads authoritative position/facing, current cell contents and sight occlusion, producing immutable visible cells and visible objects across XYZ.

Horizontal FOV is evaluated from XY direction while vertical difference participates in range/line-of-sight. Same-column vertical cells can therefore be perceived without inventing a special Water sensor.

Decision consumes sensor-neutral `PerceptionLookup`, not Vision-specific APIs. A liquid provider sees only perceived cells; an object-source provider sees only perceived objects. Future hearing/smell can contribute behind the same perception boundary.

Movement changes physical facing after successful edge commit; presentation reads the same Orientation.

Vision shape is definition/content data, not Agent policy. The living-Cow scenarios use a broad `330°` horizontal FOV to model cattle as panoramic observers rather than giving them a human-like narrow forward cone. Generic Vision remains directional and other species/content can declare narrower or wider fields independently.

This distinction matters at shorelines. A Cow facing along the bank may physically stand next to drinkable Water behind its heading. With a narrow FOV that Water is correctly unknown and a farther visible shoreline can win; with the Cow's panoramic content profile the nearby Water enters Perception and ordinary Utility prefers its nearer interaction site. No lake- or Cow-specific branch exists in `AgentSystem`.

## Source-neutral opportunities

`AgentOpportunityProvider` is the narrow bridge from a mechanic into autonomous choice. Opportunities no longer assume that the source is an `ObjectId` or that the source coordinate is also the movement goal.

Each concrete opportunity carries:

```text
OpportunityTarget   mechanic-owned source identity
InteractionSite     physical standing coordinate for use
OpportunityEvaluation
```

Object food uses an object target. Free Water uses a liquid-cell target. `AgentSystem` does not branch on either concrete source type.

A provider can:

- enumerate opportunities from current Perception;
- evaluate a previously selected target/site;
- advertise motivation/search demand;
- start provider-owned use;
- expose whether that use remains active and its terminal completion.

After MoveTo reaches the selected `InteractionSite`, Agent asks the owning provider to start use. The provider owns duration and domain revalidation. Agent observes an opaque use action/completion rather than learning provider-specific verbs such as `EAT` or `DRINK`.

## Interaction access

Physical use distance is explicit data rather than an implicit rule such as "stand in the source cell".

The current shared profile `cardinalSameOrOneBelow()` permits:

```text
agent standing cell z=N
    -> cardinal target z=N
    -> cardinal target z=N-1 when required clearance is open
```

Diagonal reach is not allowed. The standing `InteractionSite` itself must contain physical free space, and a lower target requires the declared clearance above that target to be open.

This means a Cow standing at `z=1` can drink adjacent Water at `z=0` from the shore, and can drink an adjacent rain puddle at `z=1`, without climbing above the puddle or descending into the lower Water cell merely to use it.

The reach/profile boundary is generic. Future mechanics can declare different reach patterns without adding Cow- or Water-specific branches to Agent.

Interaction reach is not global route proof. Before committing an opportunity, Agent also asks the generic `MoverDestinationAccessResolver` whether the non-current standing site has at least one structurally valid incoming Navigation edge allowed by the mover's current traversal policy. This is a cheap necessary condition only: it rejects a site that cannot be entered at all, but it does not claim that the site is globally reachable from the actor's present location. MoveTo/Pathfinding remains the owner of real route search and execution revalidation.

## Timed use and finite sources

Object Need-satisfaction definition data can declare:

```text
amount             Need reduction on successful use
consumedQuantity   finite stock spent
useDurationTicks   provider-owned delay before commit
required capability (optional)
```

A positive-duration use does not mutate Need/stock at start. At completion the provider revalidates current authoritative state. Only successful completion applies finite stock reduction and Need satisfaction.

Liquid drinking similarly owns its use lifecycle, but consumption is volumetric. The configured physical cell volume maps simulation liquid amount to milliliters. A drink removes at most the requested volume from authoritative free liquid; Need relief is proportional to the volume actually removed. A small puddle can therefore be exhausted exactly without granting the benefit of a full drink.

If the same already-selected source remains desirable and available, Agent may continue another provider-owned use immediately without an artificial one-tick idle gap. This is continuation of an owned intent, not a new omniscient candidate scan.

See [Consumable Stock](consumable-stock.md), [Growth](growth.md) and [Liquids](liquids.md).

## Semantic knowledge and unknown-source search

`NeedSolutionKnowledgeDefinitions` currently represents only the statement:

```text
"environmental solutions exist for NeedId X"
```

It does not contain source definition IDs, ObjectIds, liquid coordinates, routes or last-known positions.

When a motivated Need has no concrete perceived opportunity, a provider may emit `OpportunitySearchDemand`. `AgentSearchSystem` first performs a local visual sweep. If nothing is found, `UnguidedExplorationPolicy` chooses a deterministic coordinate-free relative target within the current visual horizon.

`RelativeSearchLocomotion` resolves that relative target against the current physical position, requires the target to belong to the fresh Vision snapshot used by search, and starts production MoveTo with a query-local constraint limited to that visible-cell snapshot.

Coordinates required by Pathfinding/Movement stay behind the execution boundary; they are not returned to cognition as hidden map knowledge.

## Common Utility decision scale

Providers do not return private final scores. They expose common evidence through `OpportunityEvaluation`:

```text
expectedBenefit
pressure
relief
travel
motivation
```

`AgentSystem` converts that evidence through the shared fixed-point `UtilityMath` and performs one deterministic ordering across all providers. Hard execution eligibility is applied before commitment: an opportunity whose standing site is already known unusable in the current local context, or whose site has no mover-permitted incoming edge, cannot win merely because its Utility is high.

Current travel evidence uses perceived distance to the candidate `InteractionSite`; it is intentionally not an A* route-cost query for every candidate. Within the same benefit/pressure conditions, a nearby currently usable site therefore wins over a farther perceived site. This keeps normal decision passes cheap and leaves authoritative global route proof to MoveTo.

Current tie-breaking is stable: Utility first, then distance, source-neutral target key, interaction-site coordinates and provider order. Hunger and Thirst therefore compete in one decision surface rather than in separate provider-specific priority systems.

This is intentionally a small Utility foundation, not a claim that the final long-term utility model is complete. If representative scenarios later show systematic cases where geometric distance misranks obstacles/detours, the existing disposable `MoveToPlanner` can support bounded route refinement for a small shortlisted candidate set. Running full Pathfinding for every perceived Water cell is deliberately not part of the current decision loop.

## Continuing intents and failure recovery

Current structural phases exposed through `AgentIntentTrace` are:

```text
MOVING_TO_OPPORTUNITY
USING_OPPORTUNITY
SEARCH_RELOCATION
```

These are lifecycle phases, not a closed action catalog. `USING_OPPORTUNITY` deliberately does not encode `EATING`, `DRINKING`, etc.

Once an opportunity is committed, Agent does not rescore unrelated motivations every poll while MoveTo or provider-owned use remains active. This keeps intent stable and prevents decision ping-pong during normal execution.

Failure scope follows the failed contract. A terminal MoveTo failure means that the physical standing site failed as a locomotion destination, so Agent quarantines that `(x,y,z)` site for the current local position context regardless of which provider/target happened to reference it. A provider-owned use failure remains exact `(provider,target,site)` state because use semantics can differ even at the same standing coordinate.

The local quarantine is cleared when the actor changes position, completes search relocation, successfully completes use, or reaches an idle retry boundary. It is therefore transient execution knowledge, not permanent map memory.

A MoveTo request may be accepted as an operation yet reach a terminal `NO_PATH`/edge-failure outcome synchronously during initial planning. Agent observes that terminal result before publishing a moving intent; an immediately failed route therefore does not create a one-poll phantom `MOVING_TO_OPPORTUNITY` state.

There is deliberately no generic rule such as "if stationary for N ticks, move", "if Thirst is full, escape", or a Cow/lake-specific fallback. Being stationary is not itself an AI failure. Recovery is driven by semantic eligibility, execution outcomes and search demand.

This is recovery, not a general interruption policy. Deliberate preemption of a still-valid committed intent by a newly urgent motivation remains future work that needs a concrete gameplay case.

## Determinism and scheduling

Determinism depends on stable perceived-source ordering, common Utility/tie breaks, simulation ticks instead of wall-clock time and deterministic exploration variation from stable identity/state.

Current scheduling is intentionally simple: Agent thinks on scheduled passes, polls active MoveTo/provider use/search work, and sleeps longer when idle. Need progression, Growth, precipitation, hydrology and evaporation run on their own schedules/processes.

Need changes do not currently push a special reactive wake-up into Agent. Representative profiling should precede more elaborate scheduling/index structures.

## Developer observability

`SimulationView` exposes read-only Orientation, Vision, Need, NeedProgression, ConsumableStock, Growth, Agent Decision/Search and MoveTo state. Decision/intent traces expose source-neutral target keys and explicit interaction sites.

The Surface inspector reads those authoritative projections. For autonomous objects it can show Needs, current lifecycle activity/target, Vision cell/object counts and the selected candidate's common Utility evidence without reconstructing decision semantics in presentation.

Failed autonomous opportunities emit sparse structured DEBUG `agent.opportunity_failed` events with the actor, provider, target, interaction site, failure stage and result code. Movement failures are coalesced by physical standing site within the local context, while provider-use failures retain exact opportunity scope.

The compact **Agents -> Living Cow Meadow** scenario combines:

```text
Hunger + Thirst progression
3D Vision / Search
common Utility competition
MoveTo + explicit interaction sites
provider-owned grazing and drinking
finite plant stock + Growth
finite free Water
cyclic precipitation + evaporation
edge lake + sparse rain-created puddles
multiple exclusive Cows
```

The lake is deliberately on the map edge. Meadow terrain is mostly absorbent, while a small deterministic set of shallow low-infiltration micro-basins produces temporary puddles during the rain window. Those puddles are consequences of the same Hydrology systems, not pre-seeded visual props.

The scenario Cow declares a shallow-water `WaterWadingProfile` as locomotion capability data. That content aspect lets it traverse small rainwater depths while rejecting the deeper lake as a movement destination; drinking from shore is still provided by the independent interaction-reach mechanic. This is definition/scenario composition, not a generic Agent branch.

A separate **Agents -> Living Cow Herd** scenario is the larger representative observation scene. It uses six Cows, substantially more regrowing forage, several rain basins and a broad interior elliptical lake. One Cow begins just north of the lake while facing along the shoreline so the scene explicitly exercises panoramic perception and nearest-shore interaction choice. The same Agent, Vision, Utility, Movement, Occupancy, Water and plant systems run unchanged; the scenario contributes only world/content composition.

## Current proofs

Headless/scenario coverage proves, among other invariants:

- visible compatible object source -> MoveTo -> timed use -> Need reduction;
- compatible new object-source definitions work without Cow/Decision code changes;
- outside-range/behind/occluded object sources are not visual candidates;
- semantic Need-solution knowledge can start search without source identity/location;
- search discovers concrete sources only through Perception;
- exploration is deterministic and relative rather than hard-coded absolute destinations;
- exploratory MoveTo is constrained to the visible-cell snapshot that validated the relative target;
- finite object-source use decreases authoritative stock and empty stock removes the opportunity;
- Growth restores finite stock independently;
- open NeedIds progress on independent schedules and clamp to configured maxima;
- motivation thresholds suppress trivial-deficit action;
- timed use does not mutate Need/source before provider completion;
- repeated still-desired use remains continuously committed;
- one common Utility scale allows Hunger and Thirst opportunities to compete deterministically;
- unperceived Water never becomes a liquid opportunity;
- a Cow at `z=1` can drink cardinal Water at `z=0` from a valid standing site;
- a Cow at `z=1` can drink a cardinal same-level puddle at `z=1`;
- a panoramic Cow already standing at the nearest valid north-shore site uses that Water before a farther simultaneously visible shore;
- diagonal and physically blocked interaction sites are rejected;
- partial puddle consumption preserves exact Water accounting and proportional Thirst relief;
- a locally mover-ineligible higher-ranked opportunity cannot win commitment over a usable fallback;
- one failed/occupied opportunity does not trap the agent when another candidate exists;
- several higher-ranked unusable opportunities cannot form a retry loop that starves a reachable fallback;
- movement-site failure scope is independent of which target references that standing site;
- multiple exclusive Cows remain non-overlapping while contending for a finite source;
- Living Cow Meadow produces sparse rain puddles, uses both puddle and lower edge-lake Water, continues grazing and returns to rain on the next climate cycle;
- Living Cow Herd keeps six exclusive Cows non-overlapping while several independently graze and drink from a larger shared world, including the interior lake.

No test defines an arbitrary maximum time that an Agent is allowed to remain stationary. Scenario acceptance is expressed through semantic world interactions and invariant outcomes instead.

## Deferred work

The immediate follow-up after this vertical slice is representative-scale profiling before introducing broader AI/world hot-path structures. The observed repeated-`NO_PATH` runtime burst justified the narrow eligibility/failure-scope correction above; it does not justify a general polling, watchdog or behavior-timeout framework.

Still deferred:

- deliberate interruption/preemption policy for a still-valid committed intent;
- separate activation/release thresholds if true hysteresis is required;
- persistent beliefs/episodic or landmark memory;
- map/compass/tool-assisted navigation;
- bounded route-cost refinement for a shortlisted set of candidates if representative scenes prove geometric travel evidence insufficient;
- hearing/smell;
- richer provider interactions and additional competing motivations;
- AI-specific scheduling/index/memory-layout optimization before representative profiling.
