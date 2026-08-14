# 2026-08-14 — Agent AI foundations: from autonomous creatures to social people

**Status:** Design foundation note — non-normative, pre-implementation research

This note preserves the design direction, research, terminology, examples and open questions collected immediately before EvoForge's first autonomous-agent vertical slice. It is intentionally broader than the first Cow implementation.

The purpose is not to freeze an API before a real consumer exists. The purpose is to avoid losing the architectural reasoning that should guide future work on animals, people, insects and other autonomous objects.

Current normative rules still live in [`../architecture.md`](../architecture.md), current implemented subsystem contracts live in `../systems/`, and accepted durable choices may later be promoted into `../decisions/`. This note may become historically outdated as implementation evidence accumulates.

## Reading the status labels

This document uses four labels:

- **Direction** — high-confidence design direction that should guide implementation unless real evidence contradicts it.
- **Working hypothesis** — a concrete candidate design that must be proven by a real consumer before becoming a contract.
- **Deferred** — deliberately not required by the first Cow slice, but the current design should not make it unnecessarily difficult later.
- **Open question** — intentionally unresolved because current evidence is insufficient.

## The long-term objective

EvoForge should not contain a separate hard-coded "Cow AI", "Human AI", "Wolf AI" and "Ant AI".

The long-term goal is one conceptual architecture for autonomous objects:

```text
world mechanics + agent state + subjective knowledge
                    ↓
           available possibilities
                    ↓
     values / traits / relations / risk
                    ↓
              chosen intent
                    ↓
          planning when needed
                    ↓
          authoritative mechanics
                    ↓
                 outcome
```

A cow and a human differ mainly in which mechanics, capabilities, motivations, knowledge, relationships and reasoning depth they possess. They should not require fundamentally different top-level brains.

This means **the same cognitive language, not the same computational cost**. An insect may react almost entirely to current perception and a few drives. A cow may have physiological needs, limited spatial memory and herd/reproduction mechanics. A human may add social norms, property, contracts, work, law, reputation, long-term goals, communication and richer memory. Optional complexity is composed rather than imposed on every agent.

**Direction:** species/content definitions compose the subject; generic decision code must not branch on concrete species types.

## The central principle: mechanics create possibilities, not scripted species behavior

The strongest requirement is the plug-in property:

> Adding a new world mechanic should create new meaningful possibilities for compatible agents without editing a central brain or a species-specific behavior list.

This does **not** mean that mechanics are undescribed or that actions appear from nothing. The simulation must still define what is possible, what conditions apply, what authoritative state changes occur and what consequences an interaction has.

What we want to avoid is this:

```text
CowAI
  if hungry -> find grass
  if thirsty -> find water
  if tired -> sleep
```

or a prettier but still closed equivalent:

```text
CowBehaviors = [EatGrass, DrinkWater, Sleep, Mate, ...]
```

Instead, world mechanics expose possibilities. A compatible agent discovers and evaluates them.

For example:

```text
Grass mechanic
    offers a food-producing interaction
    requires an eater compatible with that food
    predicts nutrition gain / consumption consequences

Cow
    currently has high hunger pressure
    knows or perceives a concrete grass source
    is capable of eating it

Decision
    evaluates that opportunity
```

If a `Hay` mechanic is later added with equivalent useful consequences, a hungry cow should begin considering hay without a new `CowEatHayBehavior` and without changing the generic reasoner.

The same idea scales to humans. A hungry person with no food may encounter possible ways to improve the situation through unrelated mechanics: buy food, ask another person, accept charity, work for money, take a loan, steal, hunt, harvest or trade something else. The person should not contain a hand-authored "hungry poor person" script selecting these branches.

### Affordance and advertisement

Two related terms are useful but should not be conflated:

- **Affordance** — an action possibility arising from the relationship between an agent and the world. A rock may afford lifting to one actor but not another; food affords eating only to compatible consumers.
- **Advertisement** — a discoverable description of a useful possibility, often including predicted benefits/costs that decision-making can evaluate.

The Sims is the important historical inspiration for putting useful interaction knowledge in the environment rather than encoding every use in the character. Will Wright's early Sims motive prototype also demonstrates the core feedback-loop idea: internal drives change which environmental possibilities become attractive. The original prototype has been published by Don Hopkins with Wright's permission: [The Soul of The Sims](https://www.donhopkins.com/home/images/Sims/).

A later academic/industry treatment, including authors from open-world game development, generalizes "smart objects" into **behavior objects** that encapsulate related behavior knowledge in the environment to reduce hard-coded NPC complexity: [Using Behavior Objects to Manage Complexity in Virtual Worlds](https://arxiv.org/abs/1508.00377).

**Direction:** intelligence should be distributed between the subject and the mechanics/environment. The agent should not need concrete knowledge of every object type that may become useful.

**Working hypothesis:** EvoForge mechanics will expose typed opportunity/consequence descriptions through narrow registration/binding points. The exact API and representation must be introduced by real mechanics rather than invented globally now.

## One agent is not one `Brain` object

The architecture should resist a monolithic mutable brain that owns every psychological fact.

Different facts have different semantic owners:

```text
Needs / drives       physiological or other changing pressures
Capabilities         what the object can actually do
Skills               how effectively it can do something
Traits               stable behavioral tendencies
Values               what it considers important / acceptable
Relations            subject-specific social state
Perception           what is observable now
Beliefs / knowledge  what the subject currently thinks is true
Memory               retained observations/events/knowledge
Intent               what the subject is currently committed to doing
Planning             how to reach a chosen desired state, when needed
```

Not every first implementation needs all of these systems. The distinction matters because future mechanics should be able to extend one semantic dimension without turning a central agent class into a mutable bag of unrelated fields.

This follows EvoForge's existing one-authoritative-owner and narrow-capability discipline.

## World truth is not agent truth

A core design decision is that autonomous agents must not reason directly from omniscient `World` state merely because the simulation can access it.

```text
Authoritative World Truth
          ↓
      Perception
          ↓
  Beliefs / Knowledge
          ↓
       Decision
```

A guard should not know a criminal's current position simply because `Spatial` knows it. A cow should not know the coordinates of food it has never perceived. A person who did not see a theft should not automatically know the thief.

**Direction:** decision-making consumes the subject's information, not unrestricted authoritative truth.

This is important for more than realism. It is what eventually allows search, uncertainty, witnesses, misinformation, investigation, rumors, deception and learning to exist as real mechanics instead of decorative scripts.

### General knowledge vs concrete knowledge

The cow example exposes an important distinction.

A cow may know a general fact:

```text
grass exists
some grass is edible
food reduces hunger
```

without knowing a concrete fact:

```text
there is edible grass at (12, 7, 0)
```

Likewise, a guard may know:

```text
a suspect exists
suspects can flee
```

while only believing that the concrete suspect was last observed near a market.

A useful long-term model therefore distinguishes at least conceptually:

- **semantic/general knowledge** — facts about classes of things, relationships, procedures or world rules;
- **current observations** — information perceived now;
- **episodic/spatial beliefs** — concrete remembered facts such as a last-known position;
- **inference/hypothesis** — conclusions that are plausible but not directly observed.

**Working hypothesis:** knowledge will eventually be represented explicitly enough that stale, missing and possibly false beliefs can differ from world truth. The first Cow slice should prove the information boundary without prematurely designing a universal knowledge graph.

## Not knowing where something is can itself create behavior

If an agent has a desired change but knows no concrete means currently available, that does not imply doing nothing or cheating by querying world truth.

It can create an **information-seeking intent**:

```text
hunger rises
    ↓
food would reduce hunger
    ↓
no known concrete food source
    ↓
search / explore for food information
    ↓
new perception
    ↓
new belief about concrete food
    ↓
normal opportunity evaluation
```

This is the same broad reasoning shape as a guard searching for someone whose exact location is unknown: the target is known conceptually, while its location must be acquired through search/perception.

F.E.A.R.'s planning work is relevant because Jeff Orkin explicitly describes shared working memory and replanning from knowledge gained through failures; the AI changes plans as its knowledge changes. See [Agent Architecture Considerations for Real-Time Planning in Games](https://ojs.aaai.org/index.php/AIIDE/article/view/18724).

IO Interactive's shipped AI work is another useful reference for layered perception/spatial reasoning in believable sandbox NPCs: [Creating the AI for the Living, Breathing World of Hitman: Absolution](https://www.gdcvault.com/play/1019353/Creating-the-AI-for-the).

**Direction:** lack of concrete knowledge is a legitimate simulation state. Generic reasoning must not silently bypass it with omniscient lookup.

**Working hypothesis:** the first explicit search behavior should emerge from "desired effect known, concrete source unknown" rather than from a cow-specific `SearchForGrass` rule.

## Motivation is broader than `Need`

The first Cow consumer will naturally start with physiological needs. The long-term architecture must not assume that all motivation is a Need.

Humans may act because of:

```text
physiological need
fear / immediate emotion
personal value
promise / obligation
job responsibility
social role
law / authority
relationship
long-term aspiration
revenge
external order
current committed plan
```

Calling all of these `NeedDef` would make the first model artificially dictate the future ontology.

A more durable conceptual pipeline is:

```text
Motivation source
      ↓
desired change / pressure
      ↓
possible intents / opportunities
```

**Direction:** physiological Needs are one motivation source, not the universal base class for every future reason to act.

The Cow slice should therefore implement a real Need without creating a universal "everything is a need" abstraction.

## Personality should shape evaluation, not become another behavior tree

Two people in the same world state should be able to choose differently because their internal evaluation differs.

A person with strong respect for law may heavily penalize theft. A risk-seeking person may discount danger. A proud person may dislike begging. A compassionate person may accept a costly action to help someone else. A greedy person may overvalue personal acquisition.

The important distinction is between **hard eligibility** and **soft preference**.

Hard eligibility examples:

```text
physically incapable of lifting object
cannot metabolize this food
lacks required tool/skill threshold
interaction is structurally impossible
```

Soft evaluation examples:

```text
honesty makes theft unattractive
fear makes dangerous paths unattractive
attachment makes helping this person attractive
pride makes begging unattractive
```

A highly honest person is usually still physically capable of stealing. Therefore honesty should not normally remove the opportunity from the world; it changes the utility the person assigns to it. Extreme traits/states may legitimately become hard constraints, but that should be their own declared semantics rather than a generic `if morality < X` rule.

The Sims 3 is an especially relevant shipped precedent. One explicit design goal was for each Sim's individual personality to be clearly manifested in autonomous behavior, and the social system was fully data-driven enough for content authors to define hundreds of interactions and thousands of production rules. The talk also demonstrates in-game visualization tools used to refine behavior: [Modeling Individual Personalities in The Sims 3](https://gdcvault.com/play/1012805/Modeling-Individual-Personalities-in-The).

**Direction:** traits, values, relationships and state contribute independent evaluation semantics. Generic decision code should not contain species/personality-specific branches.

## Plausible bounded reasoning, not perfect optimization

EvoForge should not aim for agents that always choose the mathematically globally optimal action using perfect information.

Believable mistakes should primarily arise from real causes:

- incomplete perception;
- stale or wrong beliefs;
- limited memory;
- limited planning horizon;
- imperfect skill/knowledge;
- personality/value biases;
- current emotion or physiological pressure;
- uncertainty in predicted consequences;
- deterministic but individual variation where appropriate.

A random `10% chance to make a bad decision` is a poor substitute for these mechanisms.

**Direction:** agents should be rational relative to their own bounded information and evaluation, not omniscient optimal solvers.

This allows a decision to be understandable and still wrong in hindsight.

## Candidate generation and utility evaluation

Utility AI is the current leading direction for top-level arbitration because it separates **what possibilities exist** from **how desirable each possibility is right now**.

The central shape is:

```text
perceived/known opportunities
        ↓
eligibility gates
        ↓
considerations
        ↓
response curves / normalized inputs
        ↓
comparable utility
        ↓
stable deterministic selection
```

Dave Mark's "infinite axis" work focuses on modular reasoners and utility considerations rather than manually growing brittle behavior structures: [Architecture Tricks: Managing Behaviors in Time, Space, and Depth](https://gdcvault.com/play/1018040/Architecture-Tricks-Managing-Behaviors-in).

Kevin Dill and Dave Mark also present utility functions as a way to express nuanced behavior through mathematical modeling: [Embracing the Dark Art of Mathematical Modeling in AI](https://www.gdcvault.com/play/1015683/Embracing-the-Dark-Art-of).

ArenaNet's Guild Wars 2 work is particularly relevant to EvoForge's scale/extensibility goals: its developers describe a modular utility-based architecture created to support hundreds of agent types and many possible actions while reducing both hand-authored brittleness and processing cost: [Building a Better Centaur: AI at Massive Scale](https://www.gdcvault.com/play/1021848/Building-a-Better-Centaur-AI).

### What a utility candidate may eventually consider

For a food opportunity:

```text
current hunger urgency
expected nutrition gain
distance / time cost
risk
certainty that food is still present
current commitment / interruption cost
preferences
```

For a human theft opportunity:

```text
expected resource/goal benefit
probability of success
risk of being witnessed/caught
law/value violation
relationship cost to victim
urgency of the underlying need/goal
alternative opportunities
```

The generic reasoner should not need to know that the candidate is "stealing bread". Specialized mechanic/evaluator bindings contribute the semantic facts and considerations that are actually relevant.

### Score composition is not frozen yet

The companion research strongly favors response curves and a geometric-mean-style combination to avoid the classic problem where multiplying many `[0,1]` considerations automatically punishes candidates that happen to have more considerations.

A possible future form is conceptually:

```text
candidate utility
    = aggregate(relevant consideration scores)
      × commitment/inertia adjustment
```

Hard gates should remain separate from soft scores so that "impossible" does not become merely "very unattractive."

**Working hypothesis:** use normalized considerations with explicit response curves; evaluate geometric mean or a similarly count-neutral composition in the first real multi-consideration consumer.

**Open question:** exact numeric representation (fixed-point vs carefully deterministic floating point), score aggregation, curve schema and compensation strategy.

## Intent and commitment

The highest instantaneous utility should not automatically become an instruction every tick. Otherwise near-equal candidates cause oscillation:

```text
go to food A
switch to food B
switch back to A
switch again...
```

Agents need some form of commitment/inertia/hysteresis so that a challenger must be meaningfully better or the current intent must fail/become invalid before switching.

This should be semantic rather than accidental state hidden inside the scorer.

**Direction:** decision and current commitment are distinct concepts.

**Working hypothesis:** current intent contributes deterministic inertia/hysteresis to arbitration. Exact thresholds should be proven by scenarios.

## Utility answers "what now"; planning answers "how"

Utility selection and planning solve different problems.

```text
Utility / arbitration
    Which desired outcome or opportunity is worth pursuing now?

Planner
    Given that commitment, which sequence of actions can achieve it?
```

F.E.A.R. is the classic GOAP reference. Orkin describes planning as searching for a sequence of actions that satisfies a goal, with modular goals/actions improving reuse and allowing dynamic replanning when the world invalidates a plan. See [Agent Architecture Considerations for Real-Time Planning in Games](https://ojs.aaai.org/index.php/AIIDE/article/view/18724).

Kingdom Come: Deliverance II provides a very recent shipped hybrid. Warhorse describes Modular Behavior Trees specifying a desired NPC state while GOAP finds the actions needed to move the NPC from current state to that desired state: [Combining GOAP and MBTs to Create NPCs' Behaviors for Kingdom Come: Deliverance II](https://gdcvault.com/play/1035576/Game-AI-Summit-Combining-GOAP).

This is close to the separation EvoForge wants:

```text
motivation / utility
        ↓
chosen desired change
        ↓
planner only if achieving it requires a chain
        ↓
primitive domain actions
```

**Direction:** do not use GOAP as the universal top-level chooser of what an agent should care about.

**Deferred:** no generic GOAP/HTN planner in the first Cow slice. Add planning when the first real goal cannot be represented as a direct opportunity + execution sequence without hand-written branching.

## Behavior Trees and HTN are useful, but not the top-level plug-in answer

Behavior Trees are excellent when designers need explicit predictable control flow, visual debugging and authored sequences. HTN is excellent when designers know reusable decompositions/methods for how a task should be performed.

Their weakness for EvoForge's central requirement is that adding a new world mechanic usually requires editing or inserting authored structure before existing agents can use it.

They may still be valuable below or beside the general reasoner:

- BT/MBT for strongly authored roles, combat micro-behavior or external override policies;
- HTN for reusable known-good task decompositions;
- utility for open arbitration among independently contributed opportunities;
- GOAP for dynamic composition of multi-step means.

**Direction:** do not commit the whole project to one fashionable AI algorithm. Use separate algorithms for the separate questions they are good at answering.

## BDI is a useful conceptual lens, not a required engine

The Belief–Desire–Intention vocabulary maps well onto the desired separation:

```text
Beliefs      what the subject thinks is true
Desires      pressures / wanted states
Intentions   committed current pursuits
```

That vocabulary is useful for reasoning about ownership and semantics. It does not mean EvoForge must implement a literal academic BDI framework.

**Direction:** preserve the conceptual distinction between knowledge, motivation and commitment regardless of eventual class names.

## External orders vs autonomy

This is deliberately unresolved.

RimWorld is a useful practical comparison: drafting gives the player strong direct control and suppresses ordinary autonomous work/need behavior, while mental breaks or incapacity can remove that control. The community-maintained reference is [Drafting — RimWorld Wiki](https://rimworldwiki.com/wiki/Draft).

A future EvoForge model may have several sources of intent:

```text
autonomous reasoning
player order
social order / superior
law / institutional duty
reflex / emergency
mental state
current contractual commitment
```

Then an arbitration policy can decide which source currently has authority instead of toggling a permanent `AI_ON / PLAYER_CONTROLLED` flag.

**Open question:** exact semantics of player control and which internal states may override it.

**Deferred:** do not build intent-source arbitration for the first Cow unless a real control consumer requires it.

## Internal AI is not an external Command client

EvoForge already has a global architecture rule: Commands represent **external intent**. Continuing internal domain processes do not use Control as internal RPC.

Therefore the autonomous decision layer should not generate `Command` objects merely to call its own simulation.

Conceptually:

```text
Player / Script / Scenario
        ↓
      Command
        ↓
  domain operation

Agent decision process
        ↓
  domain operation directly
```

Both paths should ultimately reach the same authoritative mechanic semantics where appropriate, but the internal AI does not pretend to be an external controller.

For locomotion this means future autonomous intent should reuse the existing Movement/MoveTo domain API rather than inventing an AI-specific movement implementation.

## Scheduling and scale

Full decision evaluation should not run for every agent every simulation tick merely because rendering does.

The eventual architecture should exploit EvoForge's scheduler and observer-independent simulation:

```text
agent has no reason to reconsider
        ↓
decision process sleeps
        ↓
relevant state threshold/event occurs
        ↓
wake and reconsider
```

Examples of future wake causes:

- a need crosses an urgency threshold;
- the current intent completes or fails;
- a relevant perceived event occurs;
- an important belief changes;
- an external order arrives;
- a scheduled long-term commitment becomes due.

A need with predictable monotonic decay may even be able to predict its next meaningful threshold and schedule that wake directly.

**Direction:** computational work follows causal relevance, not camera distance or presentation visibility.

**Working hypothesis:** agent decision processes will self-schedule and wake on relevant semantic changes instead of polling every tick.

**Open question:** first implementation cadence, wake coalescing, dirty/event subscriptions and budgets for very large populations.

## Determinism requirements

Agent reasoning must remain reproducible.

At minimum:

- candidate enumeration has a stable order;
- tie-breaking uses stable semantic keys, never `HashMap` iteration order;
- random variation, if eventually useful, comes from explicit deterministic streams;
- scoring applies operations in a deterministic order;
- the same world/input sequence produces the same decision trace;
- optimization may reduce work but may not change authoritative behavior according to camera/observer distance.

This is stricter than many shipped games and is intentionally EvoForge-specific.

## Explainability is a first-class mechanic-development requirement

Once an agent has dozens or hundreds of motivations, considerations, beliefs and mechanics, observing the final action is not enough.

The developer must be able to answer:

```text
What does this agent currently want?
What does it believe?
Which possibilities did it consider?
Which possibilities were ineligible, and why?
What scores did the remaining candidates receive?
Why did the winner beat the alternatives?
What is the current intent/plan?
What happened when execution failed?
```

The Sims 3 personality talk explicitly demonstrates in-game visualization tools used to test and refine autonomous behavior. Large shipped AI systems have made the same tooling investment; for example Massive Entertainment's GDC talk focuses on making complex NPC behavior understandable and debuggable through shared tools: [AI Behavior Editing and Debugging in Tom Clancy's The Division](https://www.gdcvault.com/play/1023382/AI-Behavior-Editing-and-Debugging).

**Direction:** observability arrives with the first agent mechanics, not after AI becomes complicated.

### Structured Decision Trace

A future trace should be structured data rather than presentation text. Conceptually:

```text
DecisionTrace
  tick
  agent id
  motivation snapshot
  relevant beliefs/observations
  candidates
    eligibility/gates
    consideration inputs
    normalized scores
    final utility
  incumbent intent / inertia
  winner
  chosen intent
```

Presentation may render this into readable explanations, tables and overlays.

### AI Inspector

A selected autonomous object should eventually expose a developer view similar to:

```text
Cow #41

STATE
  hunger: high

WORLD TRUTH                     AGENT BELIEF
  hay #9 at (20,4,0)            unknown
  grass #17 at (12,6,0)         last seen tick 12,840

CANDIDATES
  eat grass #17
    hunger benefit   0.91
    distance         0.83
    certainty        0.72
    final            0.81

  search for food
    urgency          0.91
    known source     low
    final            0.47

WINNER
  eat grass #17

INTENT
  reach and consume grass #17
```

For a human, the same tool might show why stealing lost to begging or why an urgent threat overrode a work commitment.

The **World Truth vs Agent Belief** comparison is especially valuable for diagnosing behavior that is correct relative to the subject's information but surprising to the developer.

## The first Cow vertical slice

The first implementation should prove the architecture incrementally rather than implement a complete cow life cycle.

The cow is a test subject, not a special AI architecture.

### Slice A — visible food closes one causal loop

Minimum semantics:

```text
Cow has hunger state
        ↓
hunger changes over simulation time
        ↓
Cow perceives a food opportunity
        ↓
Decision evaluates that opportunity
        ↓
Cow commits to it
        ↓
existing MoveTo moves the cow
        ↓
food mechanic executes consumption
        ↓
hunger changes because eating succeeded
```

Only one real physiological Need is required initially: hunger.

The point is to prove ownership boundaries, candidate generation, utility selection, internal intent and execution through existing Movement.

### Slice B — plug-in proof

Add a second food source/mechanic, for example hay, **without changing generic decision code or cow-specific reasoning**.

A hungry cow should automatically consider both sources because both expose useful compatible consequences.

This is the milestone's most important architectural test.

### Slice C — choice proof

Create competing options:

```text
Grass: smaller nutrition, near
Hay:   larger nutrition, far
```

The selected option should follow declared considerations rather than hard-coded food priority.

This is where response curves, distance/time cost and stable tie-breaking become observable.

### Slice D — information-seeking proof

The cow knows that food exists conceptually but has no known concrete source.

It should not query all world food coordinates. Instead it chooses a generic search/exploration intent, moves through the world, perceives a food source, updates its knowledge and then reevaluates normally.

No `SearchForGrass` behavior should exist.

### Explicitly deferred from the first Cow milestone

Do not require all of the following before the architecture is proven:

- thirst;
- sleep/rest;
- defecation/manure;
- milk/lactation;
- mating;
- pregnancy;
- birth;
- genetics;
- aging;
- herd/social behavior;
- rich long-term memory;
- moods;
- full personality;
- GOAP/HTN;
- player-order arbitration.

Each later mechanic should act as a fresh plug-in test rather than as a reason to rewrite the cow brain.

## Future life-cycle mechanics should compose, not branch the brain

The cow example gives useful long-term expectations.

Possible future composition:

```text
Reproduction mechanic
    creates mating opportunities between compatible subjects

Pregnancy state
    changes physiology / available opportunities / risk evaluation

Birth process
    creates offspring through authoritative object/life mechanics

Lactation
    production state affected by physiology and reproduction

Defecation
    physiological pressure + relieving opportunity + world byproduct
```

A pregnant cow should behave differently because pregnancy changes real state, motivations, capabilities and opportunity evaluation — not because generic decision code contains `if (cow.isPregnant())`.

The same pattern later applies to humans: employment, property, crime, law, family, contracts and government become independent mechanics contributing facts, opportunities, constraints and consequences.

## Human example: the same architecture at higher semantic depth

Suppose a person is seriously hungry and owns no food.

Known possibilities may include:

```text
buy food
ask a friend
beg a stranger
work for money
borrow money
steal food
hunt/forage
trade another possession
```

Whether each option exists comes from world mechanics and the person's knowledge/capabilities.

Its evaluation may depend on:

```text
hunger urgency
money and property
known people and relationships
law / expected punishment
honesty / values
pride
risk tolerance
skill
travel time
confidence in information
current obligations
```

Two people can therefore choose differently without separate behavior scripts.

A low-lawfulness, risk-tolerant person may find theft attractive. A proud but law-abiding person may prefer work over begging. A desperate parent may violate a normally strong value because the dependent child's need contributes much higher urgency.

The important property is that these outcomes emerge from independent semantic contributions that can be inspected individually.

## Guard/search example: known entity, unknown location

A guard receives credible information that a wanted person exists, but does not know the person's current position.

The guard may have:

```text
semantic knowledge:
  this person is wanted
  finding wanted persons is part of my role

concrete belief:
  last seen near the market at tick T

unknown:
  current position
```

A search intent can then use the last-known location and acquire new observations. Other guards may possess different information, eventually enabling communication/rumor/evidence mechanics without changing the fundamental `World → Perception → Belief → Decision` boundary.

## What we deliberately reject for now

### Species-specific decision trees

Rejected because every new mechanic would require editing each affected species/brain and generic code would grow concrete-type knowledge.

### One giant global action/goal enum

Rejected because it becomes a central extension switch and violates EvoForge's open composition discipline.

### Pure Behavior Tree as the universal brain

Rejected as the primary architecture because new independently contributed mechanics still require authored tree placement/control flow. BTs remain useful for bounded authored subproblems.

### Pure GOAP as the universal brain

Rejected as the top-level architecture because planning answers how to satisfy an already-chosen desired state; it does not by itself provide the full motivation/personality arbitration model EvoForge needs.

### Pure HTN as the universal brain

Rejected as the top-level architecture because method/decomposition authoring gives excellent control but weaker spontaneous plug-in composition.

### Omniscient world queries from decision code

Rejected because they erase search, uncertainty, perception and subjective knowledge.

### Random stupidity

Rejected as the primary source of mistakes. Error should emerge from bounded knowledge and evaluation; seeded randomness may later provide controlled variation where semantically justified.

### LLM/generative-agent reasoning as authoritative simulation logic

Deferred/rejected for the current project direction because authoritative behavior must be deterministic, performant, inspectable and replayable at simulation scale. Natural-language generation may someday become a presentation/tooling layer, but it is not the current decision core.

## Tests the architecture should eventually prove

The first agent work should build a regression suite around semantic properties, not implementation details.

### Plug-in mechanic test

Register a second compatible food source. A hungry cow begins considering it without modifying generic decision code.

### No-concrete-species dependency test

Generic agent/decision packages must not reference Cow/Human/Grass concrete implementation types.

### Subjective-knowledge test

Food that exists in world truth but has never been perceived/learned is not selected as a known target.

### Information-seeking test

When the cow knows that food is a possible solution but knows no concrete source, it chooses search/exploration rather than omniscient targeting.

### Determinism test

Same initial world + same event/input sequence produces identical structured decision traces.

### Utility monotonicity tests

For a fixed opportunity, increasing hunger urgency should not make a nutrition-producing interaction less attractive unless another explicitly modeled factor changes.

### Personality/value modulation test

Future human candidates should change ranking when relevant values/traits change, without adding behavior branches.

### No-dither test

Near-equal candidates do not cause pathological rapid switching while the incumbent intent remains valid.

### Execution failure test

A target disappearing, becoming occupied or becoming invalid causes clean intent failure/reconsideration through existing domain result semantics rather than corrupting agent state.

### Trace completeness test

A developer can inspect enough structured data to reconstruct why a candidate won or was gated out.

## Research references and what we take from them

These sources are precedents and evidence, not specifications EvoForge must copy literally.

### The Sims / The Sims 3

- [The Soul of The Sims — Will Wright's 1997 motive prototype, published by Don Hopkins with permission](https://www.donhopkins.com/home/images/Sims/) — motive feedback loops and early simulation thinking.
- [Modeling Individual Personalities in The Sims 3 — Richard Evans, GDC 2010](https://gdcvault.com/play/1012805/Modeling-Individual-Personalities-in-The) — autonomous personality expression, data-driven social behavior and in-game visualization/debugging.

Takeaway: put useful interaction semantics in data/environment and make internal state/personality alter autonomous selection. Tooling is part of AI design.

### Utility AI / Guild Wars 2

- [Architecture Tricks: Managing Behaviors in Time, Space, and Depth — GDC 2013](https://gdcvault.com/play/1018040/Architecture-Tricks-Managing-Behaviors-in) — modular/infinite-axis utility reasoning.
- [Embracing the Dark Art of Mathematical Modeling in AI — GDC 2012](https://www.gdcvault.com/play/1015683/Embracing-the-Dark-Art-of) — response-function thinking for nuanced behavior.
- [Building a Better Centaur: AI at Massive Scale — ArenaNet/Dave Mark, GDC 2015](https://www.gdcvault.com/play/1021848/Building-a-Better-Centaur-AI) — modular utility AI at large content/agent scale, with designer tooling and reduced hand-authored brittleness.
- [Game AI Pro](https://www.gameaipro.com/) — practical industry chapters on utility theory, considerations, modular AI, behavior trees, planning, debugging and AI LOD.

Takeaway: utility is a strong fit for open, composable top-level arbitration, but scoring math and authoring/debugging tools require deliberate design.

### Planning / F.E.A.R.

- [Agent Architecture Considerations for Real-Time Planning in Games — Jeff Orkin, AIIDE 2005](https://ojs.aaai.org/index.php/AIIDE/article/view/18724) — practical planning, modular goals/actions, working memory, caching and real-time cost constraints.

Takeaway: planning gives modular dynamic means/replanning, but it has real computational cost and should solve sequencing rather than replace motivation.

### Kingdom Come: Deliverance II

- [Combining GOAP and MBTs to Create NPCs' Behaviors for Kingdom Come: Deliverance II — Warhorse, GDC 2025](https://gdcvault.com/play/1035576/Game-AI-Summit-Combining-GOAP) — MBTs specify desired NPC state and GOAP finds actions to reach it.

Takeaway: a shipped modern open-world game independently validates separating desired-state selection from action-sequence planning.

### Smart/behavior objects

- [Using Behavior Objects to Manage Complexity in Virtual Worlds — Černý, Plch, Marko et al., 2015](https://arxiv.org/abs/1508.00377) — environment-side encapsulation inspired by smart objects for managing open-world behavior complexity.

Takeaway: distributing behavior knowledge into interactable mechanics/environment is a serious architectural technique, not merely a Sims curiosity.

### Hitman / spatial reasoning

- [Creating the AI for the Living, Breathing World of Hitman: Absolution — IO Interactive, GDC Europe 2013](https://www.gdcvault.com/play/1019353/Creating-the-AI-for-the) — multiple AI/software layers for believable sandbox NPC behavior.

Takeaway: believable open-world behavior benefits from explicit layered perception/spatial reasoning rather than one omniscient decision function.

### AI tooling

- [AI Behavior Editing and Debugging in Tom Clancy's The Division — Massive Entertainment, GDC 2016](https://www.gdcvault.com/play/1023382/AI-Behavior-Editing-and-Debugging) — tools for understanding and debugging complex NPC behavior as content scale grows.

Takeaway: explainability and inspection are infrastructure for AI development, not optional polish.

### RimWorld control reference

- [Drafting — RimWorld Wiki](https://rimworldwiki.com/wiki/Draft) — secondary/community documentation illustrating a strong external-control mode that overrides ordinary autonomy but can itself be broken by special mental/incapacitated states.

Takeaway: external order vs autonomy is a real design problem with multiple legitimate policies; EvoForge should not freeze it before its first consumer.

## Current synthesis

The current best high-level model is:

```text
                    AUTHORITATIVE WORLD
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
   world mechanics                        Perception
        │                                     │
        │                              Beliefs / Knowledge
        │                                     │
        └──── opportunities / effects ─────────┤
                                              │
Motivations ──────────────────────────────────┤
Needs ────────────────────────────────────────┤
Traits / Values ──────────────────────────────┤
Relations / Skills / Current State ───────────┤
                                              ▼
                                      Candidate generation
                                              │
                                      eligibility gates
                                              │
                                       utility evaluation
                                              │
                                      intent / commitment
                                              │
                                  planner only when necessary
                                              │
                                      domain execution
                                              │
                                            outcome
                                              │
                              perception / belief / state changes
```

This is a **synthesis tailored to EvoForge**, not a single published architecture copied from one game.

Its key promise is:

> New mechanics expand the possibility space. Compatible agents evaluate those possibilities through their own state and subjective model of the world. New mechanics should therefore create new emergent behavior without rewriting a central brain.

## Promotion rule

Nothing in this note becomes a permanent project law merely because it sounds elegant.

As Cow work proceeds:

1. implement the smallest real semantic consumer;
2. make the behavior observable and testable;
3. test plug-in extension with a second mechanic;
4. identify which conceptual boundaries survive real code;
5. promote durable cross-system rules into `architecture.md` or a decision record;
6. leave failed/revised ideas here as historical context.

The target is not to predict the final human mind architecture today. The target is to make each new mechanic evidence for a system that can grow from a hungry cow into a society without accumulating species-specific scripts or losing the ability to explain why an agent acted.