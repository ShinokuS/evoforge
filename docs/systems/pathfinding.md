# Pathfinding

## Purpose

Find disposable long-range spatial routes without becoming an authoritative world owner.

Pathfinding consumes the same traversal facts used by Movement:

```text
NavigationLookup
    directed structural edge exists?

TransitionCostLookup
    actor-independent intrinsic edge price

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

`PathSearch` is deliberately resumable. The budget is a deterministic count of node expansions, never wall-clock milliseconds. Faster and slower machines therefore do not receive different pathfinding semantics merely because one CPU reaches a time limit sooner.

Terminal states are:

```text
FOUND
NO_PATH
STALE
CANCELLED
```

`RUNNING` is the only non-terminal state.

`NO_PATH` is an expected domain result. `STALE` means traversal facts or a versioned query-local constraint changed while the search was suspended. `CANCELLED` means the consumer explicitly discarded unfinished computational work.

## Path query

`PathQuery` currently contains:

```text
source XYZ
goal XYZ
PathTransitionConstraint
```

The default constraint allows every structurally valid Navigation edge.

A constraint is advisory query policy layered after Navigation; it cannot create an edge that Navigation does not expose. Dynamic constraints may expose a revision. A resumable search captures that revision at start and becomes `STALE` if it changes between slices.

This seam is intentionally narrower than actor-specific locomotion or multi-agent planning. It allows a future consumer to filter current facts without creating alternate Navigation systems for cows, humans, doors or occupancy policy.

## Route value

`PathRoute` is immutable and disposable.

Its steps:

- exclude the source;
- include the goal;
- preserve XYZ for every step;
- expose the shared intrinsic total transition cost.

`source == goal` is `FOUND` with zero steps and zero cost.

A route is advice:

```text
Pathfinder route
    ↓
future MoveTo chooses next edge
    ↓
MoveStep revalidates Navigation + Occupancy
    ↓
Movement executes one timed edge
```

Later route cells are never execution reservations.

## Exact A*

`ExactAStarPathfinder` is the general exact/reference implementation.

It uses:

- `NavigationLookup` for directed neighbors;
- `TransitionCostLookup` for every accepted edge price;
- a deterministic heuristic;
- a primitive reusable search workspace;
- deterministic tie-breaking.

The search never branches on concrete Shape types and never has a private terrain price table.

### Deterministic neighbor order

`TransitionDirections` is the canonical ordered enumeration of all 26 immediate 3D directions. Generic graph consumers no longer need to invent their own nested direction loops.

Equal-cost frontier ties are resolved deterministically rather than relying on `HashMap` iteration order.

### Primitive workspace

The exact hot loop does not allocate a `Cell`/`Node` object per discovered search state.

The reusable workspace contains primitive arrays for:

```text
XYZ
g / h
parent
node state
binary heap
coordinate → node index table
```

The coordinate index is open-addressed and the frontier is an index heap. Workspace is returned to a small pool on `FOUND`, `NO_PATH`, `STALE` or `CANCELLED`; the final immutable route is the intentional result allocation.

This is a deliberate hot-path choice: path search is expected to touch thousands or more states per request, so per-node object garbage would be an obvious scalability cost rather than a speculative micro-optimization.

## Admissible lower bound

A* does not hard-code assumptions about terrain or future Shapes.

Traversal definitions maintain the minimum registered surface cost. Geometry exposes a conservative minimum traversal factor across Shapes currently present. `TransitionCostLowerBoundCalculator` derives a guaranteed positive global edge-cost floor from those authoritative cost-domain facts.

Current `PathHeuristics.chebyshev(...)` combines that floor with the minimum number of 26-neighbor steps to the goal. It is intentionally conservative but admissible. `PathHeuristics.ZERO` remains available as a Dijkstra/reference mode.

New Shapes that can return traversal factors below neutral must expose a conservative `minimumTraversalFactor()`. The generic Pathfinder still never recognizes their concrete class.

## Traversal revisions

A search may span multiple simulation cycles. Mixing topology/cost facts from different world revisions inside one route would be incorrect.

`LandscapeSystem` therefore exposes traversal-domain revision facts that change whenever accepted landscape/Shape mutation can alter Navigation or TransitionCost.

One change tracker stores:

```text
revision
latest changed XYZ
```

An exact search captures the revision at start. Any later change makes the suspended search `STALE`.

The latest changed coordinate is not event infrastructure and does not grant mutation to caches. It is a narrow read projection used by derived pathfinding caches for invalidation.

## Dynamic 3D hierarchy

The production runtime currently composes:

```text
PathHierarchyIndex
        +
ExactAStarPathfinder
        ↓
HierarchicalPathfinder
```

The current hierarchy is deliberately an **exactness-preserving reachability/cache preflight**, not a claim to full portal HPA*.

### Cluster index

`PathHierarchyIndex` partitions space into replaceable 3D clusters. The current production configuration is `8 × 8 × 8` cells.

For one cluster it derives directed outgoing cluster transitions by inspecting authoritative Navigation on boundary cells. Because real movement edges are immediate, only boundary source cells can cross to another cluster.

The index is derived state:

```text
Navigation = truth
PathHierarchyIndex = acceleration cache
```

A cached cluster edge means at least one real Navigation edge crosses that boundary. The index never creates a new cell transition or owns topology.

### Exactness rule

A missing coarse route can safely prove `NO_PATH`: any real cell route would necessarily induce a sequence of crossed clusters.

A present coarse route cannot by itself prove that source and goal are connected through the internal cells of those clusters. Therefore a positive coarse result always delegates to the exact Pathfinder before a route is returned.

This gives safe early reachability rejection and a stable hierarchy/invalidation foundation without returning approximate or fabricated routes.

If representative reachable searches later justify portal macro-edges or multi-level refinement, those remain implementation changes behind the same `Pathfinder` contract.

### Cache invalidation

The hierarchy polls traversal revision when read.

If exactly one revision was observed since its last synchronization, it locally removes only cached source clusters whose Navigation queries could have depended on the changed anchor under current Navigation read locality.

If multiple revisions were missed, only the newest coordinate is known, so the cache conservatively clears globally.

This avoids an authoritative EventBus/listener dependency while never keeping known-stale hierarchy facts.

## Metrics

Every `PathSearch` exposes algorithm-neutral counters:

```text
expanded nodes
generated transitions
relaxed nodes
reopened nodes
peak frontier
```

The hierarchy separately exposes cache diagnostics:

```text
cached clusters
cache hits / misses
rebuilt clusters
Navigation queries
local invalidations
global invalidations
```

These are diagnostic/profiling facts, not gameplay semantics.

## Occupancy boundary

Occupancy is intentionally not converted into Navigation topology or permanent hierarchy connectivity.

A structurally valid route may later encounter an `OCCUPIED` or `RESERVED` immediate destination. Future MoveTo/agent policy decides whether to wait, retry or request another disposable route.

A query-local constraint can observe dynamic availability when a consumer explicitly wants that policy. If it reads mutable facts across a resumable search, it must expose a revision so the result cannot mix snapshots.

Execution reservation and route planning remain separate concerns.

## Separate future problem domains

The `Pathfinder` interface represents one-agent spatial route search. It is not intended to absorb every navigation problem.

Future needs remain conceptually distinct:

```text
Spatial Pathfinder
    A* / hierarchical / JPS-like / incremental strategies

Temporal Pathfinder
    safe-interval / space-time planning when timing itself is part of the query

Multi-Agent Planner
    coordinated conflict planning for a bounded group

Group navigation
    flow-field / formation-oriented strategies
```

These may share low-level traversal facts but should not become one giant switch-filled API.

## Testing and diagnostics

Headless coverage includes:

- weighted route chooses lower total TransitionCost rather than fewer cells;
- deterministic expansion budgets and tie-breaking;
- `NO_PATH` and zero-step success;
- query constraint filtering and constraint staleness;
- explicit cancellation;
- traversal-revision staleness;
- hierarchy directed reachability and exact refinement;
- hierarchy local/global invalidation behavior;
- production runtime composition;
- production two-level Ramp route across Z;
- representative open-grid comparison showing a strong admissible heuristic reduces expansions without changing optimal cost.

Focused visualizer scenarios separately demonstrate straight routing, structural detours, weighted detours, multi-Z ramps, unreachable goals, hierarchy boundaries and dynamic invalidation.

## Deferred by evidence, not architecture

The current contracts deliberately leave room for, but do not yet implement:

- portal/multi-level hierarchical refinement for reachable-route acceleration;
- persistent route caching;
- incremental D*/LPA*-style replanning;
- JPS/JPS-3D specializations where graph properties permit correct pruning;
- background pathfinding threads;
- path-wide/space-time reservations;
- SIPP-like temporal planning;
- WHCA*/CBS-like multi-agent coordination;
- flow fields/group navigation;
- actor-specific locomotion/terrain affinity.

These additions do not require changing Navigation truth, TransitionCost ownership, the disposable-route rule or the public algorithm-neutral Pathfinder lifecycle.
