# Agent AI foundations: from autonomous creatures to social people

- Type: Design exploration
- Status: Historical record
- Date: 2026-08-14
- Normative: No

## Context

This research/design note was written immediately before EvoForge's first autonomous-agent vertical slice. Its scope was deliberately broader than the first Cow implementation: the goal was to identify a direction that could later scale from insects/animals to people without hard-coding one “brain” per species.

The note distinguished **Direction**, **Working hypothesis**, **Deferred** and **Open question** so future ideas would not be mistaken for already accepted APIs.

## What was explored

### One conceptual architecture, optional complexity

The long-term direction was:

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

A Cow and a human should differ mostly in composed capabilities, motivations, knowledge, relationships and reasoning depth—not because `CowAI` and `HumanAI` contain unrelated top-level script trees.

### Mechanics create possibilities

The strongest design direction was that useful behavior should be distributed between the subject and the environment/mechanics. A hungry Cow should not need a hard-coded `EatGrass` branch if a compatible new source can expose the same relevant satisfaction opportunity through a generic contract.

Historical inspirations recorded at the time included Will Wright/Don Hopkins' published Sims motive prototype **The Soul of The Sims** (<https://www.donhopkins.com/home/images/Sims/>) and **Using Behavior Objects to Manage Complexity in Virtual Worlds** (<https://arxiv.org/abs/1508.00377>). These were inspirations, not claimed implementation sources.

### World truth must differ from agent truth

A major design boundary was:

```text
Authoritative World Truth
          ↓
      Perception
          ↓
  Beliefs / Knowledge
          ↓
       Decision
```

An agent should not know a source coordinate merely because Spatial knows it. Lack of concrete knowledge is a legitimate state that can itself motivate search/exploration.

The note distinguished semantic/general knowledge, current observations, episodic/spatial beliefs and inference/hypothesis, but deliberately deferred a universal belief/memory graph.

Research links preserved from the exploration included Jeff Orkin's F.E.A.R./GOAP architecture paper (<https://ojs.aaai.org/index.php/AIIDE/article/view/18724>) and IO Interactive's Hitman AI talk (<https://www.gdcvault.com/play/1019353/Creating-the-AI-for-the>).

### Motivation is broader than physiology

The note warned against making every future reason to act a `Need` merely because Hunger/Thirst were first. Future motivation may also come from fear/emotion, values, obligations, roles, authority, relationships, long-term goals and orders.

### Utility for “what now”, planning for “how”

Utility-style arbitration was identified as the leading direction for choosing among current possibilities:

```text
perceived/known opportunities
        ↓
eligibility gates
        ↓
normalized considerations
        ↓
comparable utility
        ↓
stable deterministic selection
```

Relevant inspirations included The Sims 3 personality/autonomy talk (<https://gdcvault.com/play/1012805/Modeling-Individual-Personalities-in-The>), Dave Mark's architecture talk (<https://gdcvault.com/play/1018040/Architecture-Tricks-Managing-Behaviors-in>), Dill/Mark's mathematical-modeling talk (<https://www.gdcvault.com/play/1015683/Embracing-the-Dark-Art-of>) and ArenaNet's **Building a Better Centaur** (<https://www.gdcvault.com/play/1021848/Building-a-Better-Centaur-AI>).

The note deliberately did not freeze a universal numeric formula. Planning was treated as separate:

```text
utility/arbitration = what is worth pursuing now?
planner             = how can the committed outcome be achieved?
```

### Commitment prevents oscillation

Instantaneous utility should not necessarily replace a valid current intent every tick. Commitment/inertia was identified as a necessary future-facing rule for believable stable behavior.

## Outcome

The first implemented Agent slice proved only the smallest concepts needed by a real consumer:

- sensor-neutral Perception;
- mechanic-owned source-neutral opportunities;
- explicit `InteractionSite`;
- common deterministic Utility evidence;
- stable committed intents;
- provider-owned timed use;
- relative unknown-source Search without hidden coordinates;
- MoveTo/Movement as physical execution authority.

It did **not** introduce persistent Belief/Memory, social cognition, a universal GOAP planner, personality/value systems or a universal action/behavior registry.

## What became canonical

The implemented model is described in [Autonomous Agents](../../systems/agents/agents.md). The enduring principles are:

1. generic decision code does not branch on concrete species/source type;
2. world mechanics/providers expose useful possibilities;
3. perception limits knowledge;
4. one common deterministic Utility surface compares heterogeneous opportunities;
5. committed intents are stable;
6. planning/execution remain separate from decision;
7. richer cognition is added only when a real consumer proves the needed contract.

The research links above remain historical inspiration, not evidence that EvoForge directly implements Sims smart objects, GOAP, behavior trees or another published AI framework.

## Links forward

- [Autonomous Agents](../../systems/agents/agents.md)
- [Need Progression](../../systems/agents/need-progression.md)
- [Movement](../../systems/traversal/movement.md)
- [Pathfinding](../../systems/traversal/pathfinding.md)
- [Architecture](../../architecture.md)
