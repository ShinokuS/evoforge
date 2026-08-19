# Multi-agent movement conflicts belong above local occupancy

- Type: Entry
- Status: Historical record
- Date: 2026-08-13
- Normative: No

## Context

While exclusive Occupancy was being designed, larger future problems immediately appeared: two agents meeting in a one-cell corridor, repeated doorway blocking, groups trying to pass, yielding, swapping, pushing and deadlock/livelock.

The risk was to turn the first Occupancy contract into a speculative multi-agent planning framework.

## What was observed

The important distinction is:

```text
execution reservation
  a claim for the immediate step that has actually started

planning reservation
  a prediction that some future route may use space later
```

They solve different problems.

The current narrow reservation model therefore remained:

```text
accepted MoveStep
    ↓
reserve only immediate destination
    ↓
keep claim until that atomic step completes/fails
```

A longer path remains advice. If a later route cell changes before execution reaches it, route-level logic can retry, replan, wait or fail without Occupancy pretending that the future path was authoritative.

The design also exposed the accepted conservative “caterpillar” effect: a moving exclusive object keeps its Spatial source until completion while reserving its destination, so a close follower cannot claim the leader's source early.

Finally, the discussion reinforced that spatial presence, exclusive occupancy, future traversal influence and concealment are independent mechanic-specific properties rather than one universal “physical object” flag set.

## Outcome

No yielding, path-wide reservations, swaps or deadlock framework was added to Occupancy. Those remain future multi-agent policy/mechanics to introduce only when representative agent scenarios require them.

## What became canonical

Today:

- Occupancy owns immediate execution reservation only;
- `MoveTo` owns route-level intent and executes one atomic Movement edge at a time;
- Pathfinding routes are disposable advice;
- multi-agent conflict strategy remains above local Occupancy/Movement correctness.

## Links forward

- [Occupancy](../../systems/traversal/occupancy.md)
- [Movement](../../systems/traversal/movement.md)
- [Pathfinding](../../systems/traversal/pathfinding.md)
- [Autonomous Agents](../../systems/agents/agents.md)
