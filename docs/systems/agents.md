# Autonomous Agents

## Purpose

Prove that autonomous behavior can emerge from composable world mechanics rather than species-specific scripts.

The current production slice demonstrates:

```text
Need progression -> motivation threshold
        ↓
Perception -> opportunity generation -> deterministic choice
        ↓                         ↘ no concrete opportunity
concrete intent                    semantic search demand
        ↓                               ↓
MoveTo                           visual sweep / relative exploration
        ↓                               ↓
provider-owned timed use <------ discovery through Perception
        ↓
Need reduction + finite source depletion
        ↓
Growth restores source stock
```

There is no `CowAI`, `EatGrassAction`, source-type switch, global action enum or omniscient `findFood()`.

The broader research direction remains non-normative in [Agent AI foundations](../notes/2026-08-14-agent-ai-foundations.md). This page describes implemented semantics only.

## Core boundary

```text
World truth
   ↓
Orientation + sensory mechanics
   ↓
PerceptionSnapshot
   ↓
mechanic-owned AgentOpportunityProvider(s)
   ↓
AgentSystem
   ├─ concrete opportunity -> MoveTo -> provider-owned use lifecycle
   └─ no opportunity -> semantic search demand -> AgentSearchSystem
```

Need progression and Growth are independent world processes. They change authoritative state; Agent observes the result on ordinary decision passes.

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
| sensor-neutral perception | `PerceptionLookup` |
| Need definitions / mutable deficits | `NeedDefinitions` / `NeedSystem` |
| motivation thresholds | `NeedMotivationDefinitions` |
| autonomous Need progression | `NeedProgressionSystem` |
| finite source quantity | `ConsumableStockSystem` |
| source Need effects/use timing | `NeedSatisfactionDefinitions` + provider |
| source regrowth | `GrowthSystem` |
| semantic knowledge that a Need has environmental solutions | `NeedSolutionKnowledgeDefinitions` |
| selected autonomous intent | `AgentSystem` |
| current epistemic search state | `AgentSearchSystem` |
| relative exploration choice | `UnguidedExplorationPolicy` |
| search physical execution | `RelativeSearchLocomotion` |
| route/edge movement | MoveTo / Movement / Occupancy stack |
| decision/intent/search diagnostics | corresponding trace/lookups |

Decision does not mutate physiology directly. Need does not choose behavior. Search does not own Movement. Presentation does not recreate AI truth.

## Open semantic identifiers

Needs and capabilities use open identifiers such as:

```text
NeedId("core:hunger")
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
  ├─ consumableStock
  └─ growth
```

This keeps sensing, physiology, source capability and regrowth independently extensible.

## Needs, motivation and progression

Need levels are deficit-oriented: `0` is satisfied and `maxLevel` is maximum configured deficit.

`NeedSystem` exclusively owns mutable levels. Current writes are narrow:

```text
satisfy(...)              reduce deficit
NeedDeficitIncrease       increase deficit
```

A positive deficit does not necessarily justify action. `NeedMotivationDefinition(NeedId, activationLevel)` states when the current Need-satisfaction mechanic becomes behaviorally relevant. Absence of a motivation definition keeps the compatibility activation level of `1`.

Automatic time dynamics are separate `NeedProgressionDefinition(NeedId, baseAmount, intervalTicks)` processes. Current rates are resolved through an external rate resolver so future activity/temperature/etc. can modify physiology without Hunger-specific branches.

See [Need Progression](need-progression.md).

## Vision, perception and orientation

Vision is an independent sensory mechanic. `VisionSystem` reads authoritative position/facing, current cell contents and sight occlusion, producing immutable visible cells/objects.

Decision consumes sensor-neutral `PerceptionLookup`, not Vision-specific APIs. Future hearing/smell can therefore contribute behind the perception boundary.

Movement changes physical facing after successful edge commit; presentation reads the same Orientation.

## Mechanics expose opportunities

`AgentOpportunityProvider` is the narrow bridge from a mechanic into autonomous choice. A provider can:

- evaluate a perceived source;
- advertise motivation/search demand;
- start provider-owned use;
- expose whether that use remains active and its terminal completion.

Candidate generation sees only sources present in current Perception. There is no repository-wide search for matching content.

After MoveTo reaches a selected source, Agent asks the provider to start use. The provider owns duration and domain revalidation. Agent observes an opaque use action/completion rather than learning provider-specific verbs such as `EAT` or `DRINK`.

## Timed use and finite source quantity

Need-satisfaction definition data can declare:

```text
amount             Need reduction on successful use
consumedQuantity   finite stock spent
useDurationTicks   provider-owned delay before commit
required capability (optional)
```

A positive-duration use does not mutate Need/stock at start. At completion the provider revalidates agent/source existence, co-location, required capability, Need state and finite stock. Only successful completion applies the finite stock reduction and Need satisfaction.

If the same already-selected source remains desirable and available, Agent may continue another provider-owned use immediately without an artificial one-tick idle gap. This is continuation of an owned intent, not a new omniscient candidate scan.

See [Consumable Stock](consumable-stock.md) and [Growth](growth.md).

## Semantic knowledge and unknown-source search

`NeedSolutionKnowledgeDefinitions` currently represents only the statement:

```text
"environmental solutions exist for NeedId X"
```

It does not contain source definition IDs, ObjectIds, XYZ coordinates, routes or last-known positions.

When a motivated Need has no concrete perceived opportunity, a provider may emit `OpportunitySearchDemand`. `AgentSearchSystem` first performs a local visual sweep. If nothing is found, `UnguidedExplorationPolicy` chooses a deterministic coordinate-free relative target within the current visual horizon.

`RelativeSearchLocomotion` resolves that relative target against the current physical position, requires the target to belong to the fresh Vision snapshot used by search, and starts production MoveTo with a query-local constraint limited to that visible-cell snapshot.

Coordinates required by Pathfinding/Movement stay behind the execution boundary; they are not returned to cognition as hidden map knowledge.

## Continuing intents

Current structural phases exposed through `AgentIntentTrace` are:

```text
MOVING_TO_OPPORTUNITY
USING_OPPORTUNITY
SEARCH_RELOCATION
```

These are lifecycle phases, not a closed action catalog. `USING_OPPORTUNITY` deliberately does not encode `EATING`, `DRINKING`, etc.

Timed provider use exposes authoritative start/expected-completion ticks for diagnostics. Repeated use against the same still-owned source remains structurally continuous.

General interruption/competition between unrelated motivations is deliberately deferred until more than one real motivation competes.

## Candidate evaluation

The first provider still uses a provisional deterministic score based primarily on effective Need benefit and perceived distance, with stable tie-breaking by distance/source/provider order.

This is not the final Utility AI model. Hunger + Thirst is the intended first real consumer for cross-motivation comparison.

## Determinism and scheduling

Determinism depends on stable perceived-object ordering, deterministic scoring/tie breaks, simulation ticks instead of wall-clock time and deterministic exploration variation from stable identity/state.

Current scheduling is intentionally simple: Agent thinks on scheduled passes, polls active MoveTo/provider use/search work, and sleeps longer when idle. Need progression and Growth run on their own definition intervals.

Need changes do not currently push a special reactive wake-up into Agent. Representative profiling should precede more elaborate scheduling/index structures.

## Developer observability

`SimulationView` exposes read-only Orientation, Vision, Need, NeedProgression, ConsumableStock, Growth, Agent Decision/Search and MoveTo state. These are the authoritative diagnostic contracts available to tooling.

The **Agents -> Living Cow Cycle**, `Cow Foraging` and `Cow Visual Search` scenarios remain focused human-observable integrations. The current generic Surface inspector intentionally concentrates on selected object identity/position/movement plus cell/hydrology facts; it no longer presents the older large Need/stock/Decision text dashboard. Agent semantics remain observable through their read projections, Vision/Move route overlays, focused scenarios and headless traces/tests. A future richer Agent inspector should consume those same projections rather than reconstructing AI state.

The integrated Living Cow Cycle demonstrates:

```text
Hunger progression + motivation threshold
Vision / Search
candidate choice
MoveTo route
provider-owned timed grazing
finite plant stock
Growth/regrowth
state-dependent creature/vegetation presentation
```

Every food source starts outside initial Vision in the acceptance world, so exploration and physical relocation must occur before the first real feeding.

## Current proofs

Headless/scenario coverage proves, among other invariants:

- visible compatible source -> MoveTo -> timed use -> Need reduction;
- compatible new source definitions work without Cow/Decision code changes;
- outside-range/behind/occluded sources are not visual candidates;
- semantic Need-solution knowledge can start search without source identity/location;
- search discovers concrete sources only through Perception;
- exploration is deterministic and relative rather than eight hard-coded absolute destinations;
- exploratory MoveTo is constrained to the visible-cell snapshot that validated the relative target;
- finite source use decreases authoritative stock and empty stock removes the opportunity;
- Growth restores finite stock independently;
- open NeedIds progress on independent schedules and clamp to configured maxima;
- motivation thresholds suppress trivial-deficit action;
- timed use does not mutate Need/stock before the provider completion tick;
- repeated still-desired use remains continuously committed;
- Living Cow begins with no visible food and must explore before feeding.

## Deferred work

The next direct consumer remains Water + Thirst, followed by real cross-motivation Utility competition and richer intent persistence/interruption.

Still deferred:

- separate activation/release thresholds if true hysteresis is required;
- persistent beliefs/episodic or landmark memory;
- map/compass/tool-assisted navigation;
- hearing/smell;
- richer provider interactions and competing motivations;
- AI-specific optimization before representative profiling.
