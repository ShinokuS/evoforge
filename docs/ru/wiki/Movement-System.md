# Movement System

Movement — первая production gameplay-механика EvoForge с реальной длительностью во времени. Она превращает принятое внешнее намерение переместить объект в одну соседнюю structural-позицию в deterministic scheduled process, а перед фактическим перемещением повторно проверяет состояние мира и только затем коммитит authoritative изменение Spatial.

На этой странице подробно описан текущий реализованный контракт: ownership, command semantics, timing, интеграция со Scheduler, `MovementRate`, active actions, transition costs, traversal-факторы Shape, completion-time revalidation, известные ограничения и правила расширения.

## Ответственность в одной схеме

```text
Navigation решает, существует ли соседний structural edge.
Traversal cost определяет цену этого edge.
MovementRate переводит цену в simulation time.
Movement владеет timed action, который в момент завершения просит Spatial закоммитить новую позицию.
```

Movement **не** владеет terrain topology, identity объектов, storage координат, внутренностями Scheduler, pathfinding, occupancy, rendering или AI intent.

## Структура пакетов

Текущие production-типы разделены по ответственности:

```text
simulation/control/movement/
├── MoveStepCommand
├── MoveStepResult
└── MoveStepHandler

simulation/world/mechanics/movement/
├── MovementRate
├── MovementDefinitions
├── MovementDefinitionCompiler
├── MovementStartResult
├── MovementActionId
├── MovementAction
├── MovementStateStore
├── MovementSystem
└── MovementActionProcessor

simulation/world/mechanics/traversal/
├── SurfaceTraversalCost
├── LandscapeTraversalDefinitions
├── LandscapeTraversalDefinitionCompiler
├── TransitionCost
├── TransitionCostLookup
└── TransitionCostCalculator

simulation/time/
├── SimulationTime
├── SimulationClock
├── ProcessScheduler
├── BoundProcessScheduler
├── Scheduler
└── SimulationStepper
```

Разделение принципиально: Movement владеет семантикой движения, Traversal — actor-independent ценой edge, Time — моментом активации, Spatial — authoritative координатой объекта.

## Внешнее намерение: `MoveStepCommand`

Текущий Control vertical slice представляет ровно один запрос на соседний шаг:

```text
MoveStepCommand(objectId, destination XYZ)
```

Его смысл:

> начать timed-попытку переместить этот объект из его текущей authoritative позиции в указанную соседнюю позицию.

Это **не** означает «сразу переставить объект». Принятая команда запускает `MovementAction`; позиция остаётся прежней до завершения action.

Путь вызова:

```text
external controller
    ↓
MoveStepCommand
    ↓
SynchronousCommandGateway
    ↓
CommandDispatcher
    ↓
MoveStepHandler
    ↓
MovementSystem.startStep(...)
    ↓
MoveStepResult
```

Control остаётся тонким слоем. `MoveStepHandler` адаптирует external intent к domain API; он не строит путь, не считает duration и не мутирует Spatial напрямую.

## Structured result запуска

Movement использует общий result-floor проекта.

Обычные domain-impossibility представлены `MovementStartResult` / `MoveStepResult` с `accepted()` и namespaced `ResultCode`. Текущие причины отказа включают:

```text
movement capability отсутствует
object не размещён
object уже движется
destination не соседняя
structural transition недоступен
```

Unknown/stale id в доверенном runtime-path и сломанный bootstrap/configuration остаются programming/configuration error, а не обычным gameplay-result.

Принятый результат означает только:

```text
movement action успешно запущен
```

Он не означает, что destination уже записана в Spatial.

## Movement capability и `MovementRate`

Способность к обычному самостоятельному движению задаётся через definition aspect, а не полем внутри каждого `WorldObject`.

Object definition использует aspect `movement`:

```json
{
  "key": "core:walker",
  "aspects": {
    "movement": {
      "rate": 100
    }
  }
}
```

`MovementDefinitionCompiler` компилирует его в `MovementDefinitions`:

```text
ObjectDefinitionId -> MovementRate
```

`MovementRate` — положительное целое число в traversal-cost units на simulation tick.

Если `movement` aspect отсутствует, этот definition не имеет обычной self-propelled movement capability, используемой `MoveStepCommand`.

Текущая capability намеренно узкая. Swimming, flying, climbing, jumping, колёсное/гусеничное движение, stamina и surface affinity не прячутся внутри `MovementRate`.

## Проверки при старте

`MovementSystem.startStep` выполняет текущие проверки в следующем порядке:

```text
object существует
    ↓
его definition имеет MovementRate
    ↓
object размещён в Spatial
    ↓
у object нет активного MovementAction
    ↓
destination — одна из 26 непосредственных соседних позиций
    ↓
Navigation содержит этот directed structural transition
    ↓
рассчитывается TransitionCost
    ↓
cost переводится в duration через MovementRate + carry
    ↓
создаётся active MovementAction
    ↓
планируется completion
```

Delta destination должна удовлетворять:

```text
dx, dy, dz ∈ [-1, 1]
не (0, 0, 0)
```

Movement не дублирует правила Full/Ramp/Shape. Если Navigation не отдаёт edge, Movement отклоняет старт.

## Семантика позиции во время движения

Movement authoritative, но дискретный.

Для action:

```text
A -> B
```

с completion на tick 15:

```text
tick 0..14  Spatial position = A
tick 15     completion revalidation
tick 15     если всё ещё valid: Spatial position становится B
```

Authoritative дробной координаты между A и B нет.

Первая debug-визуализация должна наблюдать именно эти discrete commits. Smooth interpolation не является частью текущего simulation contract и не нужна для корректности.

## `MovementAction`

`MovementAction` — runtime-state одного принятого соседнего шага.

Он хранит только данные, необходимые completion:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

Он намеренно не хранит generic action-status enum, history, renderer progress, wall-clock timestamps или path.

Наличие action в `MovementStateStore` означает, что он active. Удаление означает, что он больше не active.

## `MovementActionId`

`MovementActionId` — domain identity процесса движения.

ID монотонный и не переиспользуется. Это исключает ситуацию, когда stale scheduled activation случайно попадёт в более поздний MovementAction с тем же числом.

Он не равен Scheduler `TaskHandle`:

```text
MovementActionId = identity domain movement-state
TaskHandle        = identity scheduler infrastructure work
```

Текущий Movement slice не прокидывает `TaskHandle`, потому что early cancellation пока не является поддерживаемой операцией.

## `MovementStateStore`

`MovementStateStore` владеет runtime-state Movement, а не историей.

Концептуально он содержит:

```text
на ObjectId:
    fractional timing carry
    active MovementActionId | none

на MovementActionId:
    active MovementAction
```

Это обеспечивает два важных инварианта:

```text
у одного объекта не может быть двух одновременных ordinary MovementAction
fractional timing precision сохраняется между отдельными соседними шагами
```

Completed/interrupted actions удаляются, а не остаются навсегда. Будущая diagnostics/replay/history должна жить отдельно, а не превращать active-state store в бесконечный журнал.

## Fractional timing carry

Пошаговый `ceil` систематически искажает скорость быстрых объектов и диагональных переходов. Поэтому EvoForge переносит целочисленный остаток между шагами.

Для transition cost `cost`, actor rate `rate` и прошлого остатка `carry`:

```text
total = cost + carry
ticks = floor(total / rate)
carry = total mod rate
```

Реализация перестроена так, чтобы не складывать произвольные `long` напрямую с риском overflow, но математически эквивалентна этой формуле для valid state.

Duration всегда минимум один simulation tick:

```text
duration >= 1 tick
```

Это определяет текущую time resolution и запрещает бесконечные zero-duration цепочки внутри одного tick.

Пример для `cost = 1000`, `rate = 300`:

```text
step 1: 3 ticks, carry 100
step 2: 3 ticks, carry 200
step 3: 4 ticks, carry   0
```

Три клетки занимают 10 ticks, то есть долгосрочная скорость сохраняется, вместо того чтобы каждый шаг отдельно округлялся до четырёх ticks.

Carry принадлежит per-object Movement state, а не одному `MovementAction`, потому что должен переживать `A -> B`, затем `B -> C` и последующие шаги.

## Граница со Scheduler

Movement не знает `HandlerId`, не регистрирует произвольные scheduler handlers и не вычисляет абсолютный completion tick.

Он получает узкую capability:

```java
ProcessScheduler.scheduleAfter(delayTicks, processId)
```

Для Movement `processId` — числовое значение `MovementActionId`.

`BoundProcessScheduler` связывает эту capability с:

```text
SimulationTime
Scheduler
одним HandlerId
```

Поэтому Movement может сказать:

```text
разбудить movement action 17 через 10 ticks
```

но не может запланировать чужой domain handler или изменить simulation clock.

Это общий паттерн для будущих timed mechanics:

```text
Domain start system
    ↓ scheduleAfter(delay, processId)
BoundProcessScheduler
    ↓
Scheduler
    ↓ when due
один зарегистрированный domain ScheduledHandler
    ↓
Domain action/process owner продолжает processId
```

Один handler обслуживает семейство процессов, а не создаётся на каждый action. Также нет одного глобального switch по всем типам gameplay process.

## Production simulation step

`SimulationStepper` — production owner текущего one-tick phase ordering.

Текущий шаг:

```text
1. увеличить SimulationClock на один tick
2. dispatch Scheduler batch, due на новом tick
```

Scenario tests и будущий presentation layer вызывают этот production contract, но не определяют свой отдельный смысл simulation tick.

`ScenarioHarness.advanceTicks(10)` — это просто десять вызовов того же production step. Группировка вызовов не меняет authoritative semantics.

Wall-clock FPS вне этого контракта. Большее число simulation ticks за реальную секунду меняет скорость наблюдения мира, но не deterministic результат одинаковой последовательности ticks.

## Completion-time revalidation

Scheduled completion не коммитит destination вслепую.

`MovementActionProcessor` повторно загружает active action и проверяет:

```text
object всё ещё alive
у object всё ещё есть transform
object всё ещё находится в записанном source
Navigation всё ещё содержит source -> destination
```

Только после этого вызывается:

```text
SpatialSystem.move(objectId, destination)
```

После успешного completion или interruption active action удаляется.

Так сохраняется ownership Spatial: Movement решает, что движение разрешено, а Spatial выполняет authoritative изменение позиции и обновляет свои indexes.

## Семантика sleeping action

Между start и scheduled completion action намеренно спит.

Если мир изменился на промежуточном tick, текущий Movement slice не просыпается мгновенно. Изменение обнаруживается при completion revalidation.

Пример:

```text
tick 100  movement A -> B started
tick 110  support B удалён
tick 115  completion просыпается
tick 115  Navigation больше не содержит A -> B
tick 115  action удаляется; object остаётся в A
```

Это сознательный tradeoff первого timed slice, а не случайный недочёт. Future event-driven invalidation может будить затронутые процессы раньше, если реальный consumer оправдает сложность.

## Общая модель TransitionCost

Timed Movement foundation первоначально использовал только длину grid direction. Теперь реализована явная actor-independent модель `TransitionCost`, доступная через узкую read capability.

Для directed соседнего перехода `A -> B`:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost(A -> B)
    = lengthFactor(d)
      * average(localA, localB)
```

В fixed-point factors масштабируются на 1000, а реализация выполняет объединённый расчёт integer arithmetic с одним deterministic half-up rounding на финальной границе TransitionCost.

Модель намеренно учитывает **обе** поддерживающие клетки, а не только destination.

Для пути:

```text
A -> B -> C
```

при neutral Shape factors и cardinal length:

```text
cost(A->B) = A/2 + B/2
cost(B->C) = B/2 + C/2
```

То есть внутренняя cell B суммарно вносит одну полную surface cost через два соседних transition.

## Surface traversal cost

Базовая цена поверхности принадлежит landscape definition, а не `Shape` и не actor.

Landscape definition использует aspect `traversal`:

```json
{
  "key": "core:granite",
  "aspects": {
    "traversal": {
      "cost": 1000
    }
  }
}
```

`LandscapeTraversalDefinitionCompiler` создаёт:

```text
LandscapeDefinitionId -> SurfaceTraversalCost
```

`1000` — текущий neutral baseline. Future content может задавать меньшие или большие положительные значения без изменения Movement или calculator.

Если terrain участвует в valid Movement transition, но traversal-definition отсутствует, это broken definition/bootstrap configuration, а не normal gameplay rejection.

## Grid transition length

Длина направления независима от материала terrain и Shape traversal characteristic.

`GridTransitionLength` использует fixed-point:

```text
cardinal / одна изменённая ось          = 1000   ≈ 1
double diagonal / две изменённые оси   = 1414   ≈ √2
triple diagonal / три изменённые оси   = 1732   ≈ √3
```

Поэтому diagonal movement не получает ту же длительность, что более короткий cardinal transition, только потому что оба являются immediate neighbors.

Length term принадлежит самому transition. Он не хранится в terrain definition и не является свойством concrete Shape.

## Traversal factors Shape

`Shape` теперь кроме topology предоставляет две directed traversal-характеристики:

```text
departureTraversalFactor(...)
arrivalTraversalFactor(...)
```

Это intrinsic geometry contributions, а не actor-specific movement policy.

Шкала:

```text
ShapeTraversalFactor.NONE    = 0
ShapeTraversalFactor.NEUTRAL = 1000
```

Положительный factor масштабирует локальный surface contribution. Например гипотетический `1250` означает `1.25x` local traversal price для данного role/direction.

### Тот же role law, что у topology

Traversal factors используют тот же ownership-закон, что Shape ports.

Для `A -> B` с direction `d`:

```text
source support Shape:
    queried как departure owner
    relative source = (0,0,1)
    direction = d

destination support Shape:
    queried как arrival owner
    relative source = (0,0,1) - d
    direction = d
```

Source Shape не считает цену за destination Shape. Destination Shape не читает source terrain. Каждый owner вносит только свою local geometric characteristic.

Default implementation Shape выводит наличие factor из собственных `transitionPorts`. Если Shape не владеет данным transition role, возвращается `ShapeTraversalFactor.NONE`; если владеет — neutral factor, если concrete Shape специально не переопределил его.

Таким образом cost-contract изоморфен departure/arrival topology-contract и не требует центрального `instanceof RampShape` или `switch(shapeType)`.

### Текущие production Shapes

`FullShape` и текущий cardinal `RampShape` используют neutral default factor для тех structural roles, которые они предоставляют.

Это сделано сознательно. Ramp topology уже меняет реальный grid direction, включая Z там, где это требуется, а `GridTransitionLength` учитывает соответствующее геометрическое расстояние. Дополнительный произвольный uphill/downhill multiplier сейчас не придумывается.

Если будущая Shape действительно требует intrinsic geometry penalty, она сможет локально override factor без изменения `TransitionCostCalculator`. Actor-specific различия вроде «колёсному объекту тяжело по лестнице» относятся к отдельному будущему interaction capability и не должны кодироваться универсальным Shape factor прямо сейчас.

## Почему TransitionCost пока actor-independent

Текущая формула не получает `ObjectId`, `MovementRate`, species, locomotion mode или другие actor capability.

Следовательно разные actors сейчас одинаково ранжируют intrinsic edge costs; `MovementRate` меняет только время прохождения одной и той же цены.

Например лошадь с rate вдвое выше человека проходит одинаковый cost примерно вдвое быстрее, с учётом one-tick resolution и deterministic carry.

Текущая модель намеренно не обещает, что swamp creature предпочитает mud, а человек road. Для этого потребуется actor/surface interaction, который будет проектироваться при появлении реального consumer.

## Navigation против TransitionCost

Порядок принципиален:

```text
Navigation: существует ли structural A -> B?
TransitionCost: какова intrinsic цена уже valid edge?
Movement: сколько времени конкретный actor тратит на его выполнение?
```

`TransitionCostCalculator` не создаёт и не разрешает edges. `MovementSystem` сначала спрашивает Navigation и только после существования directed edge вычисляет cost.

Так cost configuration не превращается скрытно в topology.

Future Pathfinder должен использовать **те же** transition-cost semantics, а не свою отдельную таблицу цен. Поэтому `TransitionCostLookup` оформлен как narrow reusable read boundary.

## Fixed-point arithmetic

Authoritative cost/timing arithmetic использует integers.

Текущие масштабы:

```text
surface neutral cost     = 1000 units
Shape factor neutral     = 1000
Grid length scale        = 1000
```

Conceptual formula собирается в один положительный integer numerator с фиксированным denominator, после чего один раз применяется half-up rounding для получения `TransitionCost.units`.

Это исключает `double` из authoritative cost calculation и не накапливает повторные intermediate rounding на каждом компоненте.

После этого Movement отдельно применяет per-object carry при переводе transition-cost units в ticks.

## Граница ошибок

Normal world-state rejection остаётся structured. Broken invariants/configuration — exceptional.

Normal rejection:

```text
already moving
not adjacent
Navigation transition unavailable
movement capability absent
```

Exceptional state:

```text
trusted ObjectId неизвестен
valid structural edge пришёл к support terrain без traversal definition
cost calculation не нашёл source/destination support Shape в текущем single-standing-position contract
integer configuration вышла за поддерживаемую арифметику
```

Явная ошибка лучше silent fallback price, который незаметно меняет deterministic simulation.

## Известные ограничения

### Occupancy и reservation

Movement пока не резервирует destination и не обращается к domain Occupancy system.

Два actors могут одновременно начать движение к одной structurally valid destination. Multi-agent conflict — известная ответственность будущего Occupancy milestone, а не повод внедрять занятость в structural Navigation.

### Early cancellation

Public `cancel movement` пока нет, и Movement не хранит Scheduler `TaskHandle`.

Обычно action удаляется, когда срабатывает его scheduled completion. Forced interruption вроде death, stun или forced displacement определит cancellation semantics, когда появится реальный consumer.

### Немедленная реакция на mutation мира

Active Movement не подписан на terrain/geometry mutation events. Он делает revalidation при scheduled completion.

### Actor-specific surface affinity

Surface/locomotion interaction deferred. Current TransitionCost actor-independent.

### Pathfinder / `MoveTo`

Текущий command выполняет только один adjacent step. Route planner и long-lived `MoveTo` process пока отсутствуют.

Future route executor должен использовать те же one-edge Movement semantics и revalidate каждый edge, а не телепортировать object по заранее рассчитанному path.

## Интеграция со Scenario fixture

Test-only Scenario fixture собирает настоящий production Movement path.

Arrange phase умеет:

```text
регистрировать landscape definitions с traversal costs
регистрировать object definitions
задавать MovementRate
размещать terrain
устанавливать Shapes
создавать и размещать objects
```

После `start()` harness может:

```text
submit MoveStepCommand
advance один production tick
advance N production ticks
читать object transforms
читать terrain / geometry / navigation
```

Running harness по-прежнему не раскрывает raw authoritative mutators только ради удобства тестов.

Scenario coverage включает:

```text
позиция остаётся source до completion
разные MovementRate завершают одинаковый edge в разные ticks
diagonal length меняет duration
fractional carry deterministic между шагами
второй active movement отклоняется
отсутствие movement capability отклоняется
invalid structural transition отклоняется
completion revalidation прерывает stale edge
surface traversal cost меняет duration
Shape traversal factor меняет duration
advanceTicks(N) эквивалентен N вызовам advance()
```

## Расширение terrain costs

Чтобы добавить новый landscape material с другой intrinsic traversal cost:

```text
1. задать traversal.cost в definition
2. загрузить через LandscapeTraversalDefinitionCompiler
3. добавить content/integration tests ожидаемого относительного cost
```

Не нужно менять `MovementSystem`, Navigation или `TransitionCostCalculator` для каждого terrain material.

## Расширение Shape traversal characteristics

Если новая Shape имеет intrinsic geometry multiplier, который уже не выражается grid direction length:

```text
1. реализовать обычные transitionPorts / transitionBlocks
2. override только нужный departure/arrival traversal factor
3. сохранить тот же local relative-coordinate role law
4. добавить role-contract и transition-cost tests
```

Нельзя добавлять:

```text
if (shape instanceof NewShape)
switch (shapeType)
central map всех concrete Shape
```

в Movement или calculator.

## Future Pathfinder contract

Когда появится Pathfinder, концептуально он должен получать:

```text
Navigation -> candidate structural outgoing edges
TransitionCostLookup -> price каждого valid edge
```

Pathfinder выбирает route. Movement остаётся ответственным за фактическое выполнение и revalidation каждого edge.

Так planner и execution используют одну цену, но Pathfinder не становится authoritative movement mutator.

## Стабильные инварианты

Текущая Movement architecture должна сохранять следующие правила:

```text
1. MoveStepCommand запускает action, а не мгновенно меняет Spatial.
2. На object может быть максимум один ordinary MovementAction.
3. Spatial остаётся единственным authoritative owner object XYZ.
4. Navigation остаётся structural и actor-independent.
5. TransitionCost считается только для уже valid adjacent structural edge.
6. Surface cost принадлежит LandscapeDefinitionId data.
7. Shape вносит только свою departure/arrival traversal characteristic.
8. Central cost logic не ветвится по concrete Shape type.
9. MovementRate переводит cost во время, а не определяет цену edge.
10. Fractional timing carry хранится per object между шагами.
11. Любое movement длится минимум один simulation tick.
12. Scheduler знает when/handler/process id, но не movement semantics.
13. Movement не знает HandlerId и absolute completion tick.
14. Completion выполняет revalidation до Spatial commit.
15. Completed/interrupted active actions удаляются из Movement state.
16. Wall-clock/render FPS не определяет authoritative timing движения.
17. Future Pathfinder обязан переиспользовать те же transition-cost semantics.
```

## Связанная документация

- [Navigation](Navigation.md) — structural adjacency.
- [Контракт Shape](Shape-Contract.md) — local role law и ownership traversal factors.
- [Spatial System](Spatial-System.md) — authoritative storage object position.
- [Время и Scheduler](Time-and-Scheduler.md) — event-driven activation и production stepping.
- [Control Backbone](Control-Backbone.md) — external intent и structured command results.
- [Definitions](Definitions.md) — composition-driven object/landscape aspects.
- [Дорожная карта и отложенные решения](Roadmap-and-Deferred-Decisions.md) — Occupancy, Pathfinder, visualizer и дальнейшая работа по Movement.
