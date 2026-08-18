# 2026-08-13 — Multi-agent movement conflicts belong above local occupancy

**Status:** Historical development note

Occupancy design raised a larger question than the first milestone needs to solve: what should happen when many autonomous objects interact while moving through the same changing world?

Examples include two agents walking toward each other in a one-cell corridor, several agents surrounding one mover, repeated mutual blocking at a doorway, one agent cutting across another agent's planned route, or different kinds of creatures being able to yield, push, swap, retreat or simply wait.

We deliberately did **not** turn those scenarios into the first Occupancy contract.

## Local execution reservation

The current reservation has one narrow job:

```text
an accepted concrete MoveStep
    → claims only its immediate destination
    → claim lives until that step completes or is interrupted
```

It does not reserve the rest of a future path.

This keeps correctness local. Two exclusive movers cannot start into the same destination at once, while later cells remain free to change before they become real execution steps.

## A path is advice

A future Pathfinder may return something like:

```text
A → B → C → D → E
```

Only `A → B` becomes authoritative when one-edge Movement successfully starts it. If another object occupies or reserves `C` before it becomes the next edge, the route may be discarded or reconsidered rather than forcing Movement to follow stale advice.

A future `MoveTo`/route owner is therefore the natural place to decide whether to:

```text
continue
wait briefly
retry
replan
abandon the destination
escalate the failure to the agent
```

Occupancy only reports the present fact. It does not decide strategy.

## Future conflict-resolution space

Real multi-agent play may justify additional policies such as:

- yielding or backing out of a bottleneck;
- priority based on current intent, definition capabilities or higher-level agent state;
- explicit swap/displacement between compatible actors;
- pushing when one actor has the physical/logical ability to move another;
- coordinated following so a column can advance through narrow space more efficiently;
- group/formation movement;
- detection and recovery from deadlock or livelock;
- bounded space-time planning reservations if repeated local replanning is measured as a real problem.

Those mechanisms should be introduced by the first real scenario that needs them. Different objects may reasonably resolve the same geometric conflict differently, so the eventual policy may depend on independent definition capabilities and current logical state rather than one universal collision rule.

## Execution reservation is not planning reservation

The distinction is important:

```text
Execution reservation
    current authoritative claim for a step that is actually starting

Planning reservation
    predicted future use of space by a route that may still change
```

They solve different problems and do not need to share one storage model or lifecycle.

## Known caterpillar effect

Current timed Movement keeps Spatial position at the source until completion while reserving the destination. This means a follower cannot immediately claim the leader's source in a one-cell corridor.

The resulting conservative “caterpillar” throughput is accepted for now. Fixing it by early source release or coordinated movement would change authoritative movement semantics, so it needs real multi-agent evidence first.

## One object can contribute to many mechanics

The Occupancy discussion also clarified a broader world-model principle.

Spatial presence, exclusive occupancy, traversal influence and concealment are independent properties. A bush is a useful example:

```text
Spatial presence       yes
exclusive Occupancy    no
traversal slowdown     possible future contribution
concealment            possible future contribution
```

A cow might be spatially present and exclusive while not providing either of the other effects. A sword may be spatially present but transparent to all of them.

The architecture should therefore compose mechanic-specific definition capabilities rather than branch on concrete content types or grow one universal “physical” flag set.

This note is intentionally non-normative. Current contracts live in the Occupancy and Movement system pages; these scenarios are reminders for future Pathfinder, MoveTo and agent work.
