# Pathfinding

## In plain language

Pathfinding answers: **what route should an actor try to follow to reach a distant coordinate?**

It does not move the actor and it does not reserve the route. A returned path is disposable advice based on a snapshot of current traversal facts. `MoveTo` later executes one edge at a time through authoritative Movement, which rechecks the world before each real step.

This distinction lets the world change while an actor is traveling without pretending an old path is still guaranteed.

## Current status

Production pathfinding is built from:

```text
NavigationLookup
  directed structural edges
        +
TransitionCostLookup
  actor-independent intrinsic edge prices
        +
optional PathTransitionConstraint
  query-local advisory filter
        ↓
Pathfinder / resumable PathSearch
        ↓
PathRoute or terminal result
```

The exact/reference solver is deterministic A*. Production wraps it with an exactness-preserving dynamic 3D coarse reachability/cache preflight (`HierarchicalPathfinder` + `PathHierarchyIndex`).

## Public search lifecycle

Search is explicitly resumable:

```java
PathSearch search = pathfinder.begin(query);

while (search.status() == RUNNING) {
    search.advance(expansionBudget);
}
```

The budget is a **number of node expansions**, not wall-clock milliseconds. Therefore faster hardware does not change search semantics.

Terminal statuses:

```text
FOUND
NO_PATH
STALE
CANCELLED
```

- `FOUND` — exact route produced.
- `NO_PATH` — expected domain result; no valid route under current search facts.
- `STALE` — a versioned traversal/constraint fact changed while a resumable search was suspended.
- `CANCELLED` — consumer discarded unfinished computational work.

## PathQuery

A query contains:

```text
source XYZ
goal XYZ
PathTransitionConstraint
```

The default constraint allows every structurally valid Navigation edge.

A constraint can only **remove** candidate edges. It can never invent an edge Navigation does not expose.

Current real uses include:

- mover-aware Water-wading filtering for MoveTo;
- visible-cell filtering for relative Agent Search.

A dynamic constraint may expose a revision so a genuinely suspended search can detect that its snapshot is no longer consistent.

## PathRoute semantics

A `PathRoute` is immutable and disposable.

Its step list:

- excludes source;
- includes goal;
- preserves full XYZ for every step;
- carries total intrinsic `TransitionCost`.

Special case:

```text
source == goal
→ FOUND
→ zero steps
→ zero total cost
```

A path does not reserve Occupancy and does not authorize Movement.

## Exact A* algorithm

`ExactAStarPathfinder` follows standard A* shortest-path search over EvoForge's directed weighted 3D Navigation graph.

For node `n`:

```text
g(n) = exact accumulated TransitionCost from source to n
h(n) = admissible lower-bound estimate from n to goal
f(n) = g(n) + h(n)
```

The frontier always expands the deterministically best candidate according to the solver's stable priority/tie rules.

For each expanded cell:

1. read `NavigationLookup.transitions(x,y,z)`;
2. enumerate valid immediate directions in canonical `TransitionDirections` order;
3. apply the query-local constraint;
4. get exact `TransitionCost` for the directed edge;
5. relax/update the neighbor when a cheaper `g` is found;
6. continue until the goal is finalized or the frontier is empty.

No concrete Shape/material price table exists inside the pathfinder.

### Deterministic workspace

The hot search loop uses primitive reusable arrays for discovered coordinates, `g/f`-related state, parents, node state, heap/frontier and coordinate indexing rather than allocating one object per node.

Workspace is pooled/released on terminal completion. The final immutable `PathRoute` is intentional result allocation.

Canonical direction order and stable heap/node ordering prevent hash-map iteration from deciding equal-cost route output.

## Heuristic

EvoForge has two important heuristic modes.

### Zero heuristic

```text
h(n) = 0
```

This makes the search equivalent to uniform-cost/Dijkstra-style expansion and is used as a reference mode.

### Chebyshev lower-bound heuristic

Because Navigation moves through any of 26 neighboring directions, the minimum number of immediate steps needed to close coordinate deltas is:

```text
steps(n) = max(|goalX-x|, |goalY-y|, |goalZ-z|)
```

If every valid edge costs at least positive lower bound `L`, the heuristic is:

```text
h(n) = steps(n) * L
```

`TransitionCostLowerBoundCalculator` derives `L` from current registered surface costs and conservative minimum Shape traversal factors.

Why admissible?

Any route must contain at least `steps(n)` immediate transitions, and each such transition costs at least `L`. Therefore this estimate cannot exceed the true cheapest remaining cost.

If a future Shape can use a factor below the currently known minimum, the Geometry-side lower-bound contract must expose that fact so A* optimality remains valid.

## Traversal revisions and staleness

A resumable exact search captures current traversal revision at start.

If accepted Landscape/Geometry traversal facts change before a later `advance(...)`, the search returns `STALE` rather than combining nodes evaluated under two different world snapshots.

Query-local dynamic constraints can use the same idea through their own revision.

This is not a global event bus. The revision is a narrow observation that protects search consistency.

## Production hierarchy

Production wraps exact A* with a coarse `PathHierarchyIndex`.

Current standard cluster size:

```text
8 x 8 x 8 cells
```

The hierarchy derives directed inter-cluster reachability from authoritative Navigation at cluster boundaries.

```text
Navigation = truth
PathHierarchyIndex = derived acceleration cache
Exact A* = final route authority
```

### What a coarse result can prove

If the coarse directed cluster graph has **no route**, there cannot be a cell-level immediate-neighbor route crossing those clusters, so `NO_PATH` can be returned safely.

If the coarse graph **does have a route**, that does not prove the source and goal are connected inside their clusters. Production always delegates to exact A* before returning `FOUND`.

The hierarchy therefore cannot fabricate a path or make search approximate.

### Cache invalidation

Hierarchy reads current traversal revision.

- If exactly one revision/change coordinate was observed, it invalidates the conservative set of source clusters whose Navigation reads could depend on that changed anchor.
- If multiple revisions were missed and only the latest coordinate is available, it clears globally.

This keeps cache correctness without forcing Landscape to push authoritative events into Pathfinding.

## Mover/environment constraints

Current Water wading demonstrates why actor restrictions are query-local rather than Navigation topology.

```text
same structural edge
Cow A maxDepth=0.2 -> may be filtered
future swimmer      -> could remain structurally valid
```

Production MoveTo composes the mover constraint into its `PathQuery`, so already-deep Water can be avoided when a dry route exists.

The path is still advice. Movement checks the same semantic constraint again at edge start and completion because Water can change after planning.

## Occupancy boundary

Dynamic Occupancy does not become permanent structural connectivity.

A route may cross a cell that is currently free but later becomes reserved/occupied. Current MoveTo terminates if a required edge cannot start/commit; automatic waiting/replanning/yielding is deferred.

A consumer may add a query-local availability constraint if it genuinely wants current Occupancy as advisory search policy, but execution reservation remains separate from path computation.

## Computational time versus simulation time

Current MoveTo advances `PathSearch` in deterministic chunks until terminal **without advancing simulation ticks between chunks**.

Therefore CPU/pathfinding effort does not make an actor physically slower/faster in simulation time.

A future background/resumable-across-ticks solver must define snapshot/revision/scheduling semantics explicitly.

## Metrics

Every search exposes algorithm-neutral profiling counters such as:

- expanded nodes;
- generated transitions;
- relaxations/reopens;
- peak frontier.

The hierarchy also reports cache hits/misses, cached/rebuilt clusters, Navigation queries and invalidation counts.

These are diagnostics, not gameplay values.

## Invariants

- Pathfinding reads Navigation/TransitionCost; it never owns another topology/price truth.
- Path routes are advice and never reserve/move actors.
- Query constraints only filter structural edges.
- Deterministic expansion budgets/ties produce replayable search behavior.
- `h` must remain admissible for exact A*.
- A suspended search cannot mix incompatible traversal/constraint revisions.
- Hierarchy negative results may prove `NO_PATH`; positive results still require exact search.
- Occupancy/execution remains separate from spatial route computation.

## Current limitations

Deferred specializations/problem domains include:

- portal/multi-level hierarchy for positive-route acceleration;
- persistent route caches;
- incremental D*/LPA*-style replanning;
- JPS specializations where graph assumptions can be proven;
- background search threads;
- space-time/SIPP-style temporal planning;
- WHCA*/CBS-like multi-agent coordination;
- flow fields/group navigation;
- richer locomotion affinities.

These are not modes to add to one giant API preemptively.

## Code and tests

Primary code:

```text
simulation/.../world/pathfinding/
```

Representative tests compare exact A* cost against zero-heuristic reference search, verify deterministic expansion budgets/ties, staleness/cancellation, constraints, 3D ramps, hierarchy preflight/invalidation and production MoveTo integration.

## Sources

**Algorithm lineage:** Hart, Nilsson & Raphael (1968), “A Formal Basis for the Heuristic Determination of Minimum Cost Paths”, is the A* lineage.

**Algorithm lineage/reference:** Dijkstra (1959) is the classic shortest-path/uniform-cost basis corresponding to the `h=0` reference behavior.

EvoForge's resumable search lifecycle, deterministic primitive workspace, 3D traversal contracts and dynamic hierarchy are project-specific extensions.

See [References](../../references.md), [Navigation](navigation.md), [Transition Cost](traversal-cost.md), [Movement](movement.md), and [Water Traversal](water-traversal.md).
