# Pathfinding

## Purpose

Find disposable long-range spatial routes without becoming an authoritative world owner.

Pathfinding consumes the same structural/cost facts used by Movement:

```text
NavigationLookup
    directed structural edge exists?

TransitionCostLookup
    actor-independent intrinsic edge price

PathTransitionConstraint
    optional query-local advisory filter

Pathfinder
    cheapest/admissible spatial route advice
```

A returned route does not reserve space, move an object or promise that later cells will still be available when execution reaches them.

## Public search model

The public boundary is algorithm-neutral:

```java
PathSearch search = pathfinder.begin(query);

while (search.status() == RUNNING) {
    search.advance(expansionBudget);
}
```

`PathSearch` is resumable. The budget is a deterministic count of node expansions, never wall-clock milliseconds, so CPU speed does not become simulation semantics.

Terminal states are:

```text
FOUND
NO_PATH
STALE
CANCELLED
```

`NO_PATH` is expected domain data. `STALE` means versioned traversal/query-local facts changed while the search was suspended. `CANCELLED` means the consumer discarded unfinished computational work.

## PathQuery and constraints

`PathQuery` contains source XYZ, goal XYZ and a `PathTransitionConstraint`.

The default constraint allows every structurally valid Navigation edge. A constraint can only filter; it cannot create an edge that Navigation does not expose.

A dynamic constraint may expose a revision. A resumable search captures that revision and becomes `STALE` if the constraint's relevant snapshot changes between slices.

Current production MoveTo uses this seam for mover-aware advisory filtering. Water wading is the first real dynamic consumer: it filters paths for a mover without changing Navigation topology. Relative Agent Search also composes a visible-cell constraint for its query.

## PathRoute

`PathRoute` is immutable and disposable.

Its steps:

- exclude the source;
- include the goal;
- preserve XYZ for every step;
- expose shared intrinsic total transition cost.

`source == goal` is `FOUND` with zero steps and zero cost.

Execution remains separate:

```text
Pathfinder route
    ↓
MoveTo selects next advised edge
    ↓
Movement revalidates Navigation + mover constraint + Occupancy
    ↓
one timed edge commits or fails
```

Later route cells are never execution reservations.

## Exact A*

`ExactAStarPathfinder` is the general exact/reference implementation.

It uses:

- `NavigationLookup` for directed neighbors;
- `TransitionCostLookup` for every accepted edge price;
- query-local constraint after structural validity;
- deterministic heuristic and tie-breaking;
- a primitive reusable search workspace.

The search never branches on concrete Shape types and never owns a private terrain price table.

### Direction order and workspace

`TransitionDirections` is the canonical ordered enumeration of all 26 immediate 3D directions. Equal-cost frontier ties are resolved deterministically rather than through hash iteration.

The exact hot loop uses primitive arrays for XYZ, costs, parents, state, heap and coordinate-to-node indexing rather than allocating a node object per discovered state. The workspace is pooled and released on every terminal outcome; the final immutable route is intentional result allocation.

## Admissible lower bound

A* does not hard-code terrain/Shape assumptions.

Traversal definitions maintain the minimum registered surface cost. Geometry exposes a conservative minimum traversal factor across current Shapes. `TransitionCostLowerBoundCalculator` derives a positive global lower bound, and `PathHeuristics.chebyshev(...)` combines it with the minimum number of 26-neighbor steps.

`PathHeuristics.ZERO` remains a Dijkstra/reference mode.

A new Shape with factors below neutral must expose a conservative `minimumTraversalFactor()`; generic Pathfinder still does not recognize its concrete type.

## Traversal revisions

A suspended search must not mix topology/cost facts from different accepted landscape revisions.

Landscape traversal change tracking exposes a monotonic revision plus the latest changed coordinate. Exact search captures the revision at start and becomes `STALE` if it changes before a later search slice.

The changed coordinate is a narrow read fact for derived cache invalidation, not an authoritative event bus.

Dynamic query-local constraints follow the same principle through their own optional revision.

## Dynamic 3D hierarchy

Production runtime composes:

```text
PathHierarchyIndex
        +
ExactAStarPathfinder
        ↓
HierarchicalPathfinder
```

The hierarchy is currently an **exactness-preserving reachability/cache preflight**, not portal HPA*.

`PathHierarchyIndex` partitions space into replaceable 3D clusters (current standard configuration `8 x 8 x 8`). It derives directed inter-cluster reachability from authoritative Navigation at cluster boundaries.

```text
Navigation = truth
PathHierarchyIndex = acceleration cache
```

A missing coarse route can safely prove `NO_PATH`: any real immediate-neighbor route would have to induce a cluster crossing sequence.

A positive coarse route cannot prove source/goal internal connectivity, so it always delegates to exact search before returning a route. The hierarchy never fabricates a cell path.

### Cache invalidation

The hierarchy synchronizes against traversal revision on read.

If one revision was observed, it locally drops cached source clusters whose Navigation queries could depend on the changed anchor under current read locality. If several revisions were missed and only the newest coordinate is available, it conservatively clears globally.

This preserves exactness without making Landscape push authoritative events into Pathfinding caches.

## MoveTo execution model

Current production `MoveToSystem` is the first long-range execution consumer.

Its path search is computational work, not actor travel time: current production advances the search in deterministic expansion chunks to a terminal result without advancing simulation time between chunks, then executes route edges through ordinary Movement.

MoveTo holds one locomotion claim across planning and child edges. Route advice can become unusable later; each concrete edge is revalidated independently.

Route-level cancellation is also separate from Pathfinder cancellation. Cancelling MoveTo stops future route execution after at most the current accepted Movement edge; `PathSearch.cancel()` merely discards unfinished computational search work.

## Occupancy boundary

Occupancy is intentionally not converted into structural Navigation or permanent hierarchy connectivity.

A structurally valid route may later encounter an occupied/reserved immediate destination. Current MoveTo terminates when a required concrete edge cannot start/commit; automatic waiting/replanning/yielding remains future Movement/agent policy.

A query-local constraint may observe dynamic availability when a consumer explicitly wants that advisory policy. If the fact can change while a search is genuinely suspended, the constraint must expose revision semantics.

Execution reservation and route planning remain separate concerns.

## World bounds

Pathfinder has no special coordinate-edge rule. When a runtime configures finite `WorldBounds`, shared Geometry presents outside coordinates as closed `FullShape`; Navigation therefore exposes no structural route through the boundary.

Unconfigured runtimes retain unbounded coordinate semantics. Future generated/unloaded/streamed world state must not be silently treated as ordinary empty path space.

## Metrics

Every `PathSearch` exposes algorithm-neutral counters such as expanded nodes, generated transitions, relaxations/reopens and peak frontier.

The hierarchy separately exposes cache diagnostics including cached clusters, hits/misses, rebuilt clusters, Navigation queries and local/global invalidations.

These are profiling facts, not gameplay semantics.

## Separate future problem domains

The current `Pathfinder` interface represents one-agent spatial route search. Temporal conflict planning, bounded multi-agent coordination and group/flow navigation are different problem domains even if they share traversal facts.

Do not turn the spatial Pathfinder API into one giant mode switch for all of them.

## Tests and visual diagnostics

Headless coverage includes weighted optimality, deterministic expansion budgets/ties, zero-step success, `NO_PATH`, constraint filtering/staleness, explicit search cancellation, traversal-revision staleness, hierarchy exact preflight/invalidation, production composition, multi-Z Ramp routing and heuristic-versus-Dijkstra optimal-cost comparison.

Additional integration proves mover-aware Water constraints can steer MoveTo around currently over-deep destinations while authoritative Movement still revalidates real execution.

Focused visualizer scenarios demonstrate straight routes, structural/weighted detours, multi-Z ramps, unreachable goals, hierarchy boundaries and dynamic invalidation.

## Deferred by evidence, not architecture

- portal/multi-level hierarchical refinement for reachable-route acceleration;
- persistent route caching;
- incremental D*/LPA*-style replanning;
- JPS/JPS-3D specializations where graph properties permit correct pruning;
- background pathfinding threads;
- path-wide/space-time reservations and SIPP-like temporal planning;
- WHCA*/CBS-like bounded multi-agent coordination;
- flow-field/group navigation;
- broader actor-specific locomotion/terrain affinity beyond current query-local Water wading and search constraints.

These additions must preserve Navigation truth, TransitionCost ownership, disposable-route semantics and the algorithm-neutral `PathSearch` lifecycle.
