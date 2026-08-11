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
DONE  Timed Basic Movement: один соседний structural transition
DONE  first production SimulationStepper + Scheduler process binding
NEXT  TransitionCost model: terrain + Shape traversal + grid length
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

Первая delivery implementation синхронная. Concrete vertical slices теперь включают `PlaceTerrainCommand` и `MoveStepCommand`.

Command не является обязательным внутренним RPC. После принятия intent внутренние процессы, например продолжающиеся Movement Actions, будущая world generation или erosion, могут работать напрямую через узкие domain write APIs.

Queued/asynchronous delivery остаётся deferred. Она может переиспользовать те же Command/Handler/Dispatcher contracts, но должна явно определить deterministic ordering, момент flush и within-tick state visibility.

## Scenario fixture

Scenario-layer по-прежнему намеренно является **test-only deterministic fixture**, а не simulation runtime.

Она разделяет две стадии:

```text
ScenarioBuilder
    -> вручную собирает маленький мир через controlled write-capabilities
    -> регистрирует test definitions
    -> создаёт и размещает test objects
    -> start()

ScenarioHarness
    -> отправляет production Commands
    -> двигает production SimulationStepper
    -> наблюдает read-only Object / Transform / Terrain / Geometry / Navigation state
```

`start()` закрывает arrange-фазу. Запущенный harness не раскрывает raw authoritative mutators.

Movement создал первый реальный timed Scheduler consumer, поэтому harness теперь имеет `advance()` и `advanceTicks(n)`. Эти методы не вводят test-only time semantics: они делегируют production `SimulationStepper`.

Scenario-миры остаются маленькими и рукотворными, потому что тест заранее знает правильный результат. Procedural world generation решает другие задачи: масштаб, устойчивость и будущую проверку gameplay/world-generation.

## Timed Basic Movement

Первый Movement slice теперь concrete и намеренно узкий: object с compiled `movement.rate` capability может начать один соседний structural transition через `MoveStepCommand`. Pathfinder не участвует.

Lifecycle:

```text
MoveStepCommand
    ↓
validate object capability / placement / adjacency / Navigation
    ↓
create MovementAction
    ↓
schedule completion after deterministic duration
    ↓
object authoritative остаётся в source
    ↓
completion-time revalidation
    ↓
SpatialSystem.move(...) or interrupt
    ↓
remove active MovementAction
```

Movement Actions существуют только пока active; история completed/interrupted внутри Movement не хранится.

Первая duration model использует neutral grid transition length (`1`, `sqrt(2)`, `sqrt(3)` в fixed-point), делённую на `MovementRate`. Fractional timing сохраняется через deterministic per-object carry вместо per-step ceiling, а любой movement transition занимает минимум один simulation tick.

`MoveStepResult` и domain `MovementStartResult` используют уже существующий structured result floor. Unknown/stale trusted `ObjectId` остаётся programming/configuration error, а не normal domain rejection.

### Известные границы первого slice

- пока нет Occupancy или destination reservation: несколько objects теоретически могут выбрать одну cell;
- нет early movement cancellation: Action заканчивается только при scheduled completion;
- пока нет terrain/Shape-specific transition cost;
- нет Pathfinder или multi-step `MoveTo`;
- нет continuous/interpolated authoritative position между клетками.

Это explicit boundaries, а не случайное скрытое поведение.

## Timed process integration

Movement — первый production consumer общего timed-process pattern:

```text
domain system starts process
    ↓
domain-owned process state
    ↓
ProcessScheduler.scheduleAfter(delay, processId)
    ↓
BoundProcessScheduler binds one HandlerId
    ↓
Scheduler
    ↓
domain process processor resumes processId
```

Scheduler знает только **когда**, **какой handler** и **какой process id**. Domain владеет смыслом process.

`MovementActionId` не является `TaskHandle`. Один зарегистрированный Movement handler обслуживает все Movement Actions; будущие timed mechanics должны использовать тот же узкий binding pattern, а не central Scheduler switch или universal Action framework.

## Production simulation step

`SimulationStepper` теперь владеет первым production-определением одного simulation tick:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Один tick выполняет один Scheduler snapshot batch. Работа, поставленная handler-ом на тот же tick во время dispatch, не дренируется рекурсивно в этом batch. Movement никогда не schedule-ит zero-duration completion и не зависит от same-tick recursive execution.

Scenario и будущий presentation-код управляют этим production contract, а не определяют собственный ordering. Tests фиксируют, что `advanceTicks(n)` эквивалентен `n` отдельным вызовам production step.

## TransitionCost model — следующий этап

Следующий Movement milestone заменяет neutral grid length как единственный transition cost уже согласованной directed `TransitionCost` model.

Архитектурные правила:

```text
Navigation decides POSSIBILITY
TransitionCost decides PRICE
MovementRate converts PRICE to TIME
Pathfinder later consumes the SAME PRICE
```

Первая cost model должна учитывать обе клетки перехода `A -> B`, а не выбирать произвольный destination-only terrain value. Landscape definitions дают base surface cost; Shape даёт собственную traversal characteristic по тому же departure/arrival role law, который уже используется structural topology; grid direction добавляет cardinal/double-diagonal/triple-diagonal length.

Movement не должен содержать `instanceof RampShape` или растущий switch по concrete Shapes. Новый Shape владеет только своим local directed traversal contribution.

Actor-specific surface affinity — например болотное существо, предпочитающее грязь дороге, — намеренно deferred. Первая TransitionCost остаётся actor-independent; `MovementRate` меняет общую скорость, но не ranking маршрутов.

## Минимальная визуализация

Первая visual/debug view — обязательный этап после TransitionCost slice, но это ещё не проект финального renderer.

Её задача — сделать уже существующее spatial/navigation/movement поведение наблюдаемым человеком. Начальный scope должен быть небольшим:

```text
отрисовка одного Z-level
переключение видимого Z-level
terrain / ramp geometry
позиции объектов
клик или инспекция клетки
structural transition mask / базовая диагностика
наблюдение discrete cell-to-cell movement
```

Smooth movement interpolation в первой версии не нужен. Object может оставаться отображённым в source cell до commit Movement Action, поэтому более быстрые objects просто меняют клетку на более ранних simulation ticks.

Это development-инструмент. Финальная rendering architecture, art pipeline и полноценный Z-level UX остаются поздними задачами.

## Occupancy

Occupancy намеренно отделена от structural terrain topology. Navigation может считать две positions structurally adjacent, даже если destination временно занята другим object.

Точное occupancy/reservation representation остаётся deferred до первого реального multi-agent Movement scenario, который докажет необходимые semantics.

## Pathfinder

Pathfinding — обязательный более поздний milestone, но он идёт после Basic Movement, TransitionCost и Occupancy, потому что именно Movement формирует contracts, которые Pathfinder должен потреблять.

Pathfinding потребляет Navigation, а не определяет terrain topology, и использует тот же directed TransitionCost, что authoritative Movement. Его API должен формироваться из реальных требований Movement: нужен ли полный route или следующий step, как выражаются unreachable/partial paths и как участвует dynamic occupancy.

Первый Pathfinder также создаст representative workload для измерения Navigation/Geometry/Terrain lookup throughput и allocations.

Только после этого стоит решать, нужны ли topology caching, packed coordinate keys, chunk-local arrays, hierarchical search и другие low-level optimizations.

## Первый agent vertical slice

После появления Movement и Pathfinder первый agent slice сможет связать реальное object/controller intent с повторяющимся движением через structural Navigation.

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
actor-specific terrain/surface affinity
final occupancy/reservation representation
early cancelled MovementAction semantics
involuntary falling
climbing/jumping/swimming/flying overlays
```

Falling требует особой осторожности: empty space сейчас никогда не является valid structural edge. Если falling появится, это должен быть explicit involuntary mechanic/process, а не скрытая интерпретация missing terrain.

## Deferred Navigation decisions

```text
cache policy
cache invalidation lifecycle
diagnostic explanation API beyond real Movement/Pathfinder needs
hierarchical pathfinding
path cache
background pathfinding snapshot/revision model
```

Текущий primitive `int transitions(x,y,z)` contract остаётся намеренно мал и не расширяется без consumer evidence.

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

Прежний geometry-override lifecycle gap закрыт согласованной границей `LandscapeMutations`:

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
