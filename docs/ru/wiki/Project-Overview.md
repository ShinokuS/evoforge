# Обзор проекта

## Что такое EvoForge

EvoForge — проект детерминированной эмерджентной симуляции. Архитектура рассчитана на мир, где множество независимых механик взаимодействуют без необходимости при каждой новой функции менять центральный тип объекта, гигантскую структуру клетки или универсальный update-loop.

Целевой масштаб — примерно миллион persistent objects, более ста тысяч positioned objects и порядка десяти тысяч одновременно активных agents. Это архитектурные ориентиры масштаба, а не обещания производительности. Они нужны, чтобы сразу отбрасывать designs с mandatory global scans или per-tick работой, пропорциональной всем persistent entities.

## Что проект оптимизирует

Главные цели — semantic stability, deterministic behavior, extensibility и measured performance. Проект предпочитает narrow interfaces и explicit owners центральным mutable registries. Оптимизация начинается с устранения unnecessary work, затем используются locality/indexes, а specialized data-oriented structures появляются только для proven hot paths.

Подсистема считается удачной, если новая mechanic использует public contract, не зная storage representation или concrete implementation types.

## Чем EvoForge намеренно не является

EvoForge не строится как:

- pure ECS, где каждое property обязано быть component table;
- universal `WorldCell` со всеми environmental mechanics;
- система, где каждый object получает `update(dt)` каждый frame;
- universal physics engine, диктующий abstractions всему gameplay;
- central type switch, знающий каждый object, terrain type, Shape, Command или timed process;
- command bus, через который обязана проходить каждая internal mutation;
- generic Action framework, владеющий всеми timed mechanics;
- framework, заранее реализующий speculative infrastructure без real consumer.

Selective data-oriented design допустим и ожидается в measured hot paths, но это implementation technique, а не domain model.

## Технологическая база

Текущая база — Java 21, presentation-модули libGDX 1.14.x и pure-Java module `simulation`. `simulation` не зависит от libGDX и тестируется headless.

```text
simulation/  authoritative domain and simulation code
core/        libGDX application / presentation layer
lwjgl3/      desktop launcher
assets/      data definitions and presentation assets
```

Authoritative state не должен переезжать в `core` или `lwjgl3` ради удобства.

## Высокоуровневая модель исполнения

EvoForge сочетает несколько architectural ideas:

```text
OO domain model
+ immutable composition-driven definitions
+ specialized mutable state owners
+ scheduler/event-driven execution
+ external-intent Command boundary
+ narrow coordinated domain write capabilities
+ deterministic authoritative mutation
+ indexed spatial/world queries
+ actor-independent structural topology and edge cost
+ selective DOD after profiling
```

Objects — real domain objects со stable identity, но mutable mechanics не накапливаются внутри `WorldObject`. Definitions описывают immutable composition. Systems владеют authoritative runtime properties. Scheduler управляет временем работы, но не domain semantics.

External Player/AI/script/scenario intent сходится к Control Commands. Internal simulation processes не обязаны возвращаться в Command и могут использовать explicitly granted narrow domain APIs.

Timed Movement — первый concrete proof этой модели: synchronous external command может запустить long-lived `MovementAction`; Scheduler позже продолжает domain process напрямую, не превращая completion в another internal Command.

## Декомпозиция мира

Current World разделён на object и landscape domains.

```text
WORLD
├── Objects
│   ├── identity / existence
│   ├── definitions
│   ├── Spatial position
│   └── timed Movement state
│
└── Landscape
    ├── terrain material/content
    ├── coordinated LandscapeMutations boundary
    ├── Geometry / Shape topology
    └── landscape traversal definitions
```

Оба domains используют одно integer XYZ address space, но не одного storage owner. Terrain не превращается в `WorldObject` только потому, что занимает coordinates.

## Structural Movement model

Current movement chain намеренно разделяет разные вопросы:

```text
Navigation
    -> является ли A -> B valid directed structural neighbor edge?

TransitionCost
    -> какова actor-independent intrinsic price valid edge?

MovementRate
    -> как быстро object переводит cost в simulation time?

MovementAction
    -> wait, revalidate и commit Spatial или interrupt
```

Navigation зависит только от Geometry и не знает actor identity, cost или Pathfinder.

TransitionCost объединяет обе supporting landscape cells, departure/arrival traversal contribution каждого Shape и discrete grid direction length. Current model actor-independent.

Movement переводит cost в ticks с deterministic per-object fractional carry. Object остаётся authoritative в source cell до successful scheduled completion.

Future Pathfinder обязан потреблять те же Navigation + TransitionCost semantics вместо собственной второй topology/price model.

## Time model

Simulation time discrete и authoritative.

```text
SimulationClock
    -> current tick

SimulationStepper
    -> advance one production tick

Scheduler
    -> activate due domain processes in deterministic order
```

Current production one-tick order:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Presentation FPS не является simulation time. Future 1x/2x/5x presentation speed должен менять скорость advancement production ticks в real time, а не `MovementRate` или edge cost.

## Determinism

При одинаковом authoritative initial state, submitted command sequence и authoritative random state simulation должна выдавать одинаковый supported result. Behavior не зависит от unspecified `HashMap` order, uncontrolled random sources, thread timing или direct World mutation worker threads.

Timed Movement и TransitionCost уже имеют concrete deterministic numeric behavior:

```text
fixed-point integer edge-cost arithmetic
stable Scheduler ordering
per-object movement timing carry
minimum one-tick movement duration
production tick semantics independent of caller batching/FPS
```

Current synchronous Control delivery немедленно выполняет submitted handler. Для timed Movement это означает immediate start action, но completion происходит позже через simulation time.

Cross-platform bit-identical floating-point semantics пока не обещаются. Current Movement/TransitionCost вообще не использует floating point в authoritative timing/cost arithmetic.

## Философия расширения

Проект различает new content и new mechanic.

Если existing mechanics уже выражают новый object/landscape type, добавляются definition data. Например новый landscape material может задать другое positive `traversal.cost` без изменений Movement code.

Если требуется genuinely new runtime behavior, вводится specialized mechanic со своим definition compiler/state owner/system и tests. Нельзя расширять central `WorldObject`, `TerrainSystem`, Scheduler или registry только потому, что туда удобно добавить field/switch.

Для geometry новый Shape — новая implementation `Shape`. Navigation и `TransitionCostCalculator` не получают `instanceof NewShape` или switch по known Shape types. Если Shape имеет intrinsic actor-independent traversal effect, contribution добавляется локально по тому же departure/arrival role law.

Для external intent новая Command добавляет typed command/result/handler в appropriate Control use-case. `CommandDispatcher` не получает central domain switch. Internal mechanics не должны создавать Commands ради call другой system.

Для timed mechanics domain process state остаётся в domain и обычно связывается со Scheduler через один `ProcessScheduler`/handler family, а не universal `ActionSystem`.

## Текущее состояние

Completed foundations/vertical slices:

```text
Object identity and repository
Definition loading and aspect compilation
SimulationClock and Scheduler
SimulationTime / ProcessScheduler / BoundProcessScheduler
production SimulationStepper
Discrete XYZ object positioning and spatial indexes
Landscape definitions and terrain storage
Coordinated LandscapeMutations lifecycle boundary
Geometry abstraction and Shape contract
TransitionMask / TransitionPorts / TransitionComposition
FullShape
Cardinal RampShape
Directed structural Navigation resolver
GridTransitionLength
Control Backbone core and synchronous delivery
PlaceTerrainCommand vertical slice
deterministic test-only Scenario fixture
movement.rate object capability
Timed MoveStep MovementAction lifecycle
completion-time Movement revalidation
persistent fractional movement timing carry
landscape traversal.cost
Shape departure/arrival traversal factors
actor-independent TransitionCostCalculator
```

Следующий required milestone — minimal Z-level visual/debug view, чтобы terrain, ramps, objects, Navigation и discrete timed Movement стали непосредственно observable человеком.

Далее planned: Occupancy, Pathfinder, first agent vertical slice и World generation.

Known Movement gaps explicit: destination reservation, early cancellation, reactive wake-up при world mutation, actor-specific surface affinity и multi-step `MoveTo` остаются deferred до своих consumers.

См. [Movement System](Movement-System.md) для подробного movement/cost contract, [Control Backbone](Control-Backbone.md) для external-intent model, [Время и Scheduler](Time-and-Scheduler.md) для timed-process binding и [Дорожную карту](Roadmap-and-Deferred-Decisions.md) для remaining deliberate gaps.
