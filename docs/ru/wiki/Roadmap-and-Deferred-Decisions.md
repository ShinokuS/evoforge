# Дорожная карта и отложенные решения

EvoForge намеренно отделяет завершённый архитектурный фундамент от решений, которые должны дождаться реального consumer. Deferred item — не приглашение заранее строить speculative infrastructure.

## Текущая последовательность

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed structural Navigation
DONE  Production cardinal RampShape
DONE  Final geometry/navigation hardening and documentation
NOW   Control Backbone core + first PlaceTerrain vertical slice
NEXT  Scenario Harness
      Basic Movement
      Occupancy
      Pathfinder
      first agent vertical slice
```

Последовательность может измениться из-за реальной dependency, но новая infrastructure обычно приходит вместе с первым consumer, который доказывает её requirements.

## Control Backbone

Текущий узкий фундамент Control создаёт один external-intent path для Player, AI, scripts, scenarios и будущих adapters:

```text
external intent
    ↓
Command
    ↓
delivery
    ↓
CommandDispatcher
    ↓
handler
    ↓
authoritative domain APIs
    ↓
structured result
```

Первая delivery implementation синхронная, а первый concrete vertical slice — `PlaceTerrainCommand`.

Command не является обязательным внутренним RPC. После принятия intent внутренние процессы, например будущая world generation, erosion или продолжающиеся Actions, могут работать напрямую через узкие domain write APIs.

Queued/asynchronous delivery остаётся deferred. Она может переиспользовать те же Command/Handler/Dispatcher contracts, но должна явно определить deterministic ordering, момент flush и within-tick state visibility.

## Scenario Harness

Headless scenario layer позволит deterministic vertical slices организовать world state, submit commands, advance simulation time и assert results.

Это не вторая simulation framework: он должен использовать те же production control/system boundaries.

## Basic Movement

Movement станет первым consumer structural Navigation. Он решит, может ли и как concrete actor выполнить уже описанный structural edge.

Сюда относятся actor capability checks, movement timing и command/action semantics. Shape не должна поглощать эти concerns.

## Occupancy

Occupancy отдельно от structural terrain topology. Navigation может считать positions structurally adjacent, даже если destination временно занята другим object.

Representation deferred до Movement queries.

## Pathfinder

Pathfinding потребляет Navigation, а не определяет terrain topology. Первый Pathfinder создаст representative workload для измерения Navigation/Geometry/Terrain lookup throughput и allocation behavior.

Только после этого можно выбирать topology caching, packed coordinate keys, chunk-local arrays и другие low-level optimizations.

## Deferred world decisions

```text
exact valid world coordinate bounds
chunk and region dimensions
terrain packing
unloaded vs absent vs not-generated semantics
world generation
region save boundaries
persistence format
```

Эти решения связаны и должны проектироваться вместе на фазе streaming/generation/persistence.

## Deferred movement decisions

```text
actor capability model
occupancy representation
movement duration semantics
transition/path costs
involuntary falling
climbing/jumping/swimming/flying overlays
```

Falling требует особой осторожности: empty space сейчас никогда не valid structural edge. Если falling появится, это explicit involuntary mechanic/process, а не скрытая интерпретация missing terrain.

## Deferred Navigation decisions

```text
cache policy
cache invalidation lifecycle
path cost API
diagnostic explanation API
pathfinding algorithm
hierarchical pathfinding
path cache
background pathfinding snapshot/revision model
```

Текущий primitive `int transitions(x,y,z)` contract намеренно мал и не расширяется без consumer evidence.

## Deferred geometry decisions

Текущих `FullShape` и четырёх cardinal `RampShape` достаточно для текущего vertical slice. Пока не нужны:

```text
diagonal ramps
fractional surface heights
continuous slope geometry
multi-standing-position Shapes
stairs framework
bridge-specific Shape types
general orientation framework
```

Если будущая geometry потребует multiple standing positions, role law Shape и вывод Navigation read-window пересматриваются вместе.

## Решение lifecycle Landscape

Прежний geometry-override lifecycle gap теперь закрыт согласованной границей `LandscapeMutations`:

```text
placeTerrain   -> очищает stale override
replaceTerrain -> сохраняет override
removeTerrain  -> очищает override
```

`TerrainSystem` по-прежнему не зависит от `GeometrySystem`; `LandscapeSystem` координирует обоих owners сверху.

## Deferred simulation infrastructure

```text
final EventBus implementation
full object lifecycle orchestration
queued/asynchronous command batching and within-tick visibility policy
multithreading beyond one authoritative mutation thread
final RNG service before a real random consumer exists
AI planner family
renderer / Z-level UX
```

Это acknowledged requirements, не current tasks.

## Когда deferred становится current

Хотя бы одно:

```text
a production consumer cannot proceed without it
a correctness test proves the current contract insufficient
a representative workload measures a real performance problem
a vertical slice exposes an ownership ambiguity
persistence/network/tooling requires a stable external representation
```

“Может пригодиться позже” недостаточно.
