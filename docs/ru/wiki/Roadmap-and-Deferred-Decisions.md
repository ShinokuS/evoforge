# Дорожная карта и отложенные решения

EvoForge намеренно разделяет **реализованный фундамент**, **следующих конкретных consumers** и **идеи, которые известны, но ещё не обоснованы для реализации в коде**.

Отложенное решение должно жить в документации, а не в dormant infrastructure. Оно становится текущей задачей, когда этого требует реальный consumer, корректность, persistence boundary или измеренная performance-проблема.

## Текущая последовательность

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed structural Navigation
DONE  Production cardinal RampShape + hardening
DONE  Control Backbone + PlaceTerrain vertical slice
DONE  Production SimulationAssembly / SimulationRuntime / SimulationView
DONE  Test-only Scenario fixture
DONE  Timed adjacent Movement
DONE  SimulationStepper + Scheduler process binding
DONE  TransitionCost: terrain + Shape roles + grid length
DONE  Minimal live Z-level Visualizer
ACTIVE Procedural full-top-down cutaway / geometric exposure readability
NEXT  Occupancy
      Pathfinder
      observable Action completion/outcome
      first agent / Cow vertical slice
      deterministic World Generation
      representative profiling / optimization
```

Внутренняя архитектура будущего milestone должна появляться вместе с его первым реальным consumer. Сам факт, что milestone запланирован, не является основанием заранее реализовывать все его возможные подсистемы.

## Текущее направление presentation

Поиск готового landscape tileset заморожен на текущем этапе разработки.

Canonical development presentation:

```text
full top-down
logical simulation cell = 1 x 1
native procedural visual cell = 16 x 16 pixels
world geometry -> horizontal cut -> geometric exposure -> generated presentation
```

Simulation не содержит pixels, palettes или camera cutaway state.

Текущий Z language не зависит от absolute Z labels и не ghost-ит соседние floors постоянно:

```text
terrain на selected Z       -> solid body, пересекающий cut
terrain на selected Z - 1   -> current supported surface
open volume ниже            -> ближайший lower surface в debug depth
```

Presentation context выводится отдельно:

```text
bodyDepth
dropDepth
ceilingDistance
coverDepth
exposureDistance через open air до sky-connected exterior
```

Covered/body depth затемняются агрессивно; обзор вниз через open space получает более мягкий depth shading. Поэтому с возвышенности lower terrain остаётся читаемым, а глубокое underground пространство постепенно приближается к тёмному cutaway состоянию.

`VisibilityVolumeLookup` — presentation-side capability boundary. Текущий adapter трактует terrain как solid/opaque; будущие roofs, walls или large objects смогут участвовать через другой adapter/composition без изменения cutaway algorithm. Тест уже доказывает, что non-terrain opaque contributor корректно блокирует visibility.

`TerrainExtentLookup` — небольшой generic simulation read contract, позволяющий presentation узнать реальный vertical terrain extent вместо произвольной высоты мира. Rendering semantics он не содержит.

Acceptance scene включает четыре base Ramp directions, stacked mountain до standing `Z=4`, mountain cave с side mouth и настоящим roof, отдельную cavern под flat cap с настоящим vertical opening, deep shaft и successive higher Ramp transitions.

Ramp visual style теперь не зависит от slice context: используется один и тот же generated Ramp art, меняется только environmental shading.

Реализованный контракт описан в [Z-level Visualizer и процедурный ландшафт](Visualizer.md).

## Ближайший milestone: Occupancy

Occupancy намеренно остаётся отдельно от structural Navigation:

```text
Navigation      возможен ли structural transition?
TransitionCost какова его intrinsic cost?
Occupancy       свободно/занято/зарезервировано ли пространство сейчас?
Movement        может ли конкретный actor начать/закончить move?
```

Точная reservation model пока открыта. Первый реальный multi-agent conflict scenario должен определить, резервирует ли moving actor destination, продолжает ли занимать source, временно ли claim-ит оба или конфликт разрешается только на completion.

Temporary object occupancy не должна попадать в Navigation topology.

## Pathfinder

Pathfinder идёт после Occupancy, чтобы его первый production contract сразу учитывал и structural topology, и dynamic availability policy, реально используемую Movement.

Он должен потреблять:

```text
NavigationLookup
TransitionCostLookup
future Occupancy read contract
```

Он не должен изобретать вторую terrain-cost model. Route cost и authoritative Movement cost должны опираться на одинаковую `TransitionCost` semantics.

Первый Pathfinder также должен стать первым representative high-volume consumer Terrain/Geometry/Navigation/TransitionCost reads. Именно этот workload должен определять optimization decisions.

## Observable Action completion

До того как первый AI consumer начнёт последовательно связывать movement steps, результат in-flight Movement action должен стать наблюдаемым.

Сейчас invalidated Movement action может исчезнуть после completion-time revalidation, не оставляя outcome, по которому agent способен принять решение. Будущий contract должен дать реальному consumer success/failure reason без преждевременного universal Action framework или global EventBus.

## Первый agent / Cow vertical slice

Первый agent slice остаётся узким:

```text
simple need/goal
    ↓
choose target
    ↓
Pathfinder
    ↓
next adjacent edge
    ↓
existing timed Movement
    ↓
observe outcome
    ↓
reconsider / continue
```

`MoveTo` не должен слепо исполнять один immutable whole path через меняющийся мир. Agent должен иметь возможность пересмотреть решение после authoritative movement outcome и изменений мира.

Широкое семейство AI planners не выбирается до того, как этот vertical slice покажет реальные требования decision loop.

## Deterministic World Generation

World generation идёт после первого observable agent slice и не используется как correctness fixture для Movement или Pathfinder.

Scenario tests остаются hand-authored, потому что ожидаемая topology известна заранее. Generated worlds решают другие задачи:

```text
larger playable worlds
seeded robustness testing
scale testing
first authoritative RNG consumer
pressure on chunk/region/load-state decisions
```

До этого milestone остаются deferred:

- noise/height/biome algorithms;
- caves и underground generation;
- размеры chunks и regions;
- unloaded vs absent vs not-generated state;
- authoritative RNG ownership и stream policy;
- generation/persistence boundaries;
- world-coordinate limits и packed-coordinate representation.

Эти решения должны проектироваться вместе, когда generation действительно начнёт их требовать.

## Deferred presentation decisions

Следующие вещи зафиксированы, но намеренно отсутствуют в текущем коде:

- explicit adjacent-layer X-ray/build mode;
- real roofs/structures/large objects как contributors `VisibilityVolumeLookup`, когда эти entities появятся;
- partial optical transmission для glass, water, smoke или foliage;
- authoritative actor LOS и lighting; camera cutaway exposure не должна превращаться в agent perception;
- дополнительные procedural materials: dirt, stone, sand, snow, water;
- priority/layered transitions между несколькими terrain materials;
- альтернативные procedural palettes или visual styles;
- крупные anchored sprites для trees, creatures, buildings и equipment;
- procedural character/object generation до появления первого gameplay consumer;
- external или hand-authored visual packs за той же presentation boundary;
- dual-grid / marching-squares resolver для будущего visual language, которому он действительно понадобится;
- richer shadows/compositing passes;
- generated-atlas/export tooling для отдельного art review;
- visual tile caches, dirty regions и chunk render storage до profiling evidence.

Normal view намеренно не рисует полный прозрачный upper floor. Будущий X-ray/build mode должен оставаться explicit и не менять input или simulation semantics.

Binary presentation `solid/opaque` достаточно для текущего terrain-only consumer. Не нужно превращать его в simulation-wide optics до появления gameplay consumer, которому действительно понадобится transmission/opacity.

## Deferred Movement decisions

Текущий timed Movement намеренно узкий. Deferred:

- final occupancy/reservation semantics;
- early cancellation;
- reactive wake-up при terrain/geometry mutation;
- actor-specific terrain affinity и locomotion modes;
- involuntary falling;
- climbing, jumping, swimming и flying;
- multi-step `MoveTo` ownership и route lifecycle;
- continuous presentation interpolation.

Falling должен оставаться explicit involuntary mechanic/process. Empty space не превращается скрыто в ordinary Navigation edge.

## Deferred Navigation / pathfinding infrastructure

Не добавляется без representative consumer:

- persistent Navigation cache;
- cache invalidation lifecycle;
- path cache;
- hierarchical pathfinding;
- packed topology storage;
- background pathfinding и snapshot/revision protocol;
- rich Navigation explanation objects на hot lookup path.

Текущий primitive transition-mask read boundary остаётся малым, пока реальный debugging или Pathfinder consumer не докажет необходимость дополнительного semantic query.

## Deferred geometry decisions

`FullShape` и четыре cardinal `RampShape` orientation достаточны для текущих mechanics.

Deferred:

- diagonal ramps;
- fractional surface heights;
- continuous slopes;
- отдельный stairs framework;
- bridges/suspended support;
- multi-standing-position Shapes;
- generalized orientation framework.

Если третий реальный Shape нарушит текущий one-supported-position assumption, нужно пересмотреть Shape support ownership, Navigation и TransitionCost вместе, а не патчить одну систему.

Будущий Shape также может описывать occupied/occluding volume богаче, чем текущий whole-cell terrain adapter. Volume adapter расширяется только после появления такого Shape; sub-cell geometry framework заранее не вводится.

## Deferred Control / runtime infrastructure

Сейчас существует только synchronous external command submission.

Deferred:

- queued/asynchronous gateway;
- within-tick command flush semantics;
- multithreaded authoritative mutation;
- general EventBus;
- universal Action framework;
- networking/persistence-facing command representation.

Будущий queued gateway должен явно сохранить или переопределить deterministic ordering и within-tick state visibility. Transport semantics не должны незаметно менять simulation semantics.

## Performance watch points

Текущий код намеренно сохраняет простые sparse lookup paths и вычисляет visual topology/cutaway state для camera range по требованию.

До изменения representation нужно измерить:

```text
Terrain / Geometry sparse lookup allocation
Navigation local resolver throughput
TransitionCost lookup throughput
Pathfinder expansion cost
active Movement/Scheduler scale
procedural surface + cutaway frame cost
exposure BFS cost
per-column top-opaque scan across sparse/deep Z ranges
```

`TerrainSystem` теперь инкрементально поддерживает точный global vertical extent. Текущий presentation adapter всё ещё может проходить этот Z-range для каждого XY, чтобы найти top opacity. Если representative worlds покажут, что это дорого, оптимизация должна появиться за `VisibilityVolumeLookup` через per-column query/cache, а не менять semantics.

Packed coordinates, chunk-local arrays, dirty visual regions и прочая DOD representation остаются profiling-driven.

## Правило принятия deferred решения

Отложенная идея становится active design, когда выполняется хотя бы одно условие:

```text
a production consumer cannot proceed without it
an invariant/correctness test proves the current contract insufficient
a representative workload measures a real performance problem
persistence/network/tooling requires a stable external representation
a vertical slice exposes an ownership ambiguity
```

«Может пригодиться потом» недостаточно.
