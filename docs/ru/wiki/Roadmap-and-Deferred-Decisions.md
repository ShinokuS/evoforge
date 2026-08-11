# Дорожная карта и отложенные решения

EvoForge намеренно отделяет завершённый архитектурный фундамент от решений, которые должны дождаться реального consumer. Отложенная деталь — не повод заранее строить speculative infrastructure, но несколько крупных gameplay-милстоунов уже являются обязательными частями проекта.

## Текущая последовательность

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed structural Navigation
DONE  Production cardinal RampShape
DONE  Final geometry/navigation hardening and documentation
DONE  Control Backbone core + first PlaceTerrain vertical slice
DONE  Test-only Scenario fixture: arrange -> start -> submit/read
NEXT  Basic Movement: один соседний structural step
      first production simulation-step owner + Action/Scheduler consumer
      minimal visualization / Z-level debug view
      Occupancy
      Pathfinder
      first agent vertical slice
      World generation
```

Последовательность может измениться из-за реальной dependency, но перечисленные этапы — это запланированная работа, а не необязательные идеи. При этом их внутренняя архитектура должна появляться только вместе с первым consumer, который доказывает конкретные requirements.

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

## Scenario fixture

Первая scenario-layer намеренно является **test-only deterministic fixture**, а не simulation runtime.

Она разделяет две стадии:

```text
ScenarioBuilder
    -> вручную собирает маленький мир через контролируемые write-capabilities
    -> регистрирует test definitions
    -> start()

ScenarioHarness
    -> отправляет production Commands
    -> наблюдает read-only Terrain / Geometry / Navigation state
```

`start()` закрывает arrange-фазу. Запущенный harness не раскрывает raw authoritative mutators.

В первой версии специально **нет `advanceTicks()` и Scheduler orchestration**. Сейчас ни одна production-механика не планирует работу, поэтому определять tick/dispatch semantics в тестовом коде было бы преждевременно и сделало бы fixture владельцем simulation-step contract.

Когда Movement создаст первый реальный timed Action/Scheduler consumer, в production должен появиться небольшой владелец шага симуляции, который определит продвижение часов и порядок фаз. Harness затем будет только вызывать этот production API, а не самостоятельно трактовать, что такое tick.

Scenario-миры маленькие и рукотворные, потому что тест заранее знает правильный результат. Procedural world generation решает другие задачи: масштаб, устойчивость и будущую проверку gameplay/world-generation.

## Basic Movement

Movement — следующий gameplay-милстоун и первый consumer structural Navigation.

Первый slice должен быть намеренно маленьким: concrete actor пытается выполнить один соседний structural transition через Command boundary. **Pathfinder для этого не нужен.**

Movement должен впервые дать реальные требования к:

```text
actor movement capability
Spatial integration
MoveCommand / result semantics
transition revalidation
first Action, если движение занимает simulation time
first Scheduler consumer
production simulation-step ordering
movement diagnostics при отклонённом переходе
```

Shape и Navigation продолжают описывать structural topology и не должны поглощать actor-specific movement policy.

## Production simulation step и timed Actions

В проекте уже существуют `SimulationClock` и `Scheduler`, но ни одна production-механика пока не планирует работу.

Если Basic Movement потребует длительности, он станет первым consumer, который обоснует production-владельца шага симуляции. Название этого класса пока намеренно не фиксируется. Именно он должен в одном месте определять порядок фаз, например продвижение clock и Scheduler dispatch.

Scenario fixture и будущий GUI должны управлять этим production contract, но не определять его каждый по-своему.

Точная политика same-tick rescheduling остаётся deferred до появления первого timed Action, который покажет требуемое поведение.

## Минимальная визуализация

Первая visual/debug view — обязательный этап после Movement, но это ещё не проект финального renderer.

Её задача — сделать уже существующее spatial/navigation поведение наблюдаемым человеком. Начальный scope должен быть небольшим:

```text
отрисовка одного Z-level
переключение видимого Z-level
terrain / ramp geometry
позиции объектов
клик или инспекция клетки
structural transition mask / базовая диагностика
наблюдение за движением агента
```

Это development-инструмент. Финальная rendering architecture, art pipeline и полноценный Z-level UX остаются поздними задачами.

## Occupancy

Occupancy намеренно отделена от structural terrain topology. Navigation может считать две positions structurally adjacent, даже если destination временно занята другим object.

Точное representation остаётся deferred до тех пор, пока Movement не покажет необходимые queries и mutation semantics.

## Pathfinder

Pathfinding — обязательный более поздний milestone, но он идёт после Basic Movement, потому что именно движение является его первым реальным consumer.

Pathfinding потребляет Navigation, а не определяет terrain topology. Его API должен формироваться из реальных требований Movement: нужен ли полный route или следующий step, как выражаются unreachable/partial paths, а позже — как участвуют dynamic occupancy и costs.

Первый Pathfinder также создаст representative workload для измерения Navigation/Geometry/Terrain lookup throughput и allocations.

Только после этого стоит решать, нужны ли topology caching, packed coordinate keys, chunk-local arrays, hierarchical search и другие low-level optimizations.

## Первый agent vertical slice

После появления Basic Movement и Pathfinder первый agent slice сможет связать реальное object/controller intent с повторяющимся движением через structural Navigation.

Этот slice должен доказать end-to-end путь до проектирования широкого семейства AI planners.

## World generation

World generation — обязательный milestone проекта, но **не test fixture для корректности Movement или Pathfinder**.

Scenario tests используют рукотворные миры, потому что их ожидаемая topology и результат заранее известны. Procedural generation решает другие задачи:

```text
создание более крупных playable worlds
deterministic generation по explicit seed
проверка масштаба и robustness
первый реальный consumer authoritative randomness
конкретизация chunk/region/generated-state решений при необходимости
```

Точный generator design, noise model, biomes, caves, размеры regions и persistence boundaries остаются deferred до этой фазы. До неё уже должна существовать минимальная визуализация, чтобы сгенерированный мир сразу можно было наблюдать человеком.

## Deferred world-generation details

До фазы generation/streaming/persistence намеренно остаются открытыми:

```text
exact valid world coordinate bounds
chunk and region dimensions
terrain packing
unloaded vs absent vs not-generated semantics
RNG service and seed ownership details
region save boundaries
persistence format
```

Эти решения связаны и должны проектироваться вместе, когда появится реальный generation или persistence consumer.

## Deferred movement decisions

```text
full actor capability model
final occupancy representation
movement duration semantics beyond the first real Action
transition/path costs
involuntary falling
climbing/jumping/swimming/flying overlays
```

Falling требует особой осторожности: empty space сейчас никогда не является valid structural edge. Если falling появится, это должен быть explicit involuntary mechanic/process, а не скрытая интерпретация missing terrain.

## Deferred Navigation decisions

```text
cache policy
cache invalidation lifecycle
path cost API
diagnostic explanation API beyond the first real Movement need
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

Если будущая geometry потребует multiple standing positions, role law Shape и вывод Navigation read-window пересматриваются вместе, а не патчатся исключениями.

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
full renderer / final Z-level UX
final RNG architecture beyond the first real random consumer
AI planner family
```

Это acknowledged later requirements, а не current implementation tasks. Они не должны блокировать обязательные промежуточные milestones выше.

## Когда deferred detail становится current

Хотя бы одно:

```text
a production consumer cannot proceed without it
a correctness test proves the current contract insufficient
a representative workload measures a real performance problem
a vertical slice exposes an ownership ambiguity
persistence/network/tooling requires a stable external representation
```

“Может пригодиться позже” недостаточно. Сам milestone может быть обязательным, а его внутренняя деталь — совершенно правильно оставаться deferred до появления реального consumer.
