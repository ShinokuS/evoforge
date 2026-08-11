# Добавление механики

Это руководство описывает предпочтительный процесс добавления действительно нового runtime behavior в EvoForge.

## Первый вопрос: content или mechanic?

До добавления Java-кода определите, могут ли существующие mechanics уже выразить нужное поведение.

Если да — добавляйте только definition data.

Если нет — это новая mechanic, которой обычно нужен собственный semantic owner.

Примеры:

```text
new terrain material with another traversal.cost
    -> content using existing traversal mechanic

new animal definition with movement.rate
    -> content using existing Movement capability

new actor-specific swimming policy
    -> new mechanic/capability interaction
```

Такое разделение не позволяет каждому новому content type раздувать runtime type system.

## Определить owned state

Сначала явно запишите, каким authoritative mutable property владеет mechanic.

Примеры:

```text
Health      ObjectId -> health state
Inventory   ObjectId -> inventory state
Disease     ObjectId -> disease state
Water       world position -> water state
Movement    ObjectId -> active movement + timing carry
```

Если этот ответ пересекается с существующим owner, остановитесь и сначала разрешите ownership. Две системы не должны одновременно считать себя authoritative owner одного и того же mutable semantic fact.

## Отделить immutable definition data

Если mechanic нужна конфигурация, общая для всех экземпляров одной definition, добавьте composition-driven definition aspect и compiler.

Например:

```text
movement aspect
    -> ObjectDefinitionId -> MovementRate

traversal aspect
    -> LandscapeDefinitionId -> SurfaceTraversalCost
```

Не помещайте mutable per-instance state в definitions.

Полезная проверка:

```text
same value for every instance of the definition
    -> definition data may be appropriate

changes independently for one runtime instance/process
    -> runtime state owned by a mechanic
```

## Предпочитать narrow read contracts

Другие systems должны зависеть от минимально необходимого semantic read interface, а не от concrete implementation механики.

Примеры:

```text
TransformLookup
TerrainLookup
GeometryLookup
NavigationLookup
TransitionCostLookup
SimulationTime
ObjectLookup
```

Read contract должен описывать то, что нужно consumers, а не раскрывать internal collections или storage classes.

## Явно определить writes

Если mutation пересекает системные границы, задайте narrow write capability или coordinated operation. Не раскрывайте широкое mutable state только ради удобной интеграции.

Когда одна logical mutation должна изменить нескольких owners, координируйте их сверху, а не вводите circular dependencies.

Примеры:

```text
LandscapeMutations
    -> coordinates Terrain + Geometry lifecycle

SpatialSystem.move
    -> authoritative object position mutation + indexes
```

## Определить: mechanic immediate или timed

Некоторые domain operations завершаются в момент initiating call. Другие только запускают process, который завершится позже в simulation time.

Не скрывайте это различие за Command delivery.

```text
synchronous Command delivery
    !=
necessarily synchronous domain completion
```

Текущие примеры:

```text
PlaceTerrainCommand
    -> accepted operation mutates landscape before submit returns

MoveStepCommand
    -> accepted operation starts MovementAction
    -> Spatial changes only after scheduled completion
```

Если mechanic timed, её process state должен быть явно представлен в domain.

## Scheduler integration для timed mechanics

Timed mechanic не должна помещать domain meaning внутрь Scheduler и не должна создавать отдельный handler на каждый process instance.

Текущий reusable pattern:

```text
DomainStartSystem
    -> creates domain-owned process/action state
    -> calls ProcessScheduler.scheduleAfter(delay, processId)

BoundProcessScheduler
    -> knows SimulationTime + Scheduler + one HandlerId

Scheduler
    -> stores when / handler / opaque processId

DomainProcessProcessor
    -> registered once for the process family
    -> reloads domain state by processId
    -> revalidates and applies domain effects
```

Закон:

```text
Scheduler knows WHEN / HANDLER / PROCESS ID.
Domain knows WHAT THE PROCESS MEANS.
```

Текущий Movement — reference implementation:

```text
MovementSystem
    -> MovementActionStore/MovementStateStore state
    -> ProcessScheduler

MovementActionProcessor::complete
    -> one registered ScheduledHandler for all MovementActions
```

Не создавайте:

```text
one ScheduledHandler per object
global Scheduler switch over process types
universal ActionSystem only because multiple mechanics use time
raw HandlerId authority inside every domain system
```

Domain process identity и Scheduler `TaskHandle` identity остаются разными понятиями.

## Использовать production simulation stepping

Tests timed mechanics и presentation должны продвигать время через `SimulationStepper`, а не вручную увеличивать `SimulationClock` и вызывать handlers в произвольном порядке.

Текущий production step:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Так tick ordering остаётся единым production contract для scenarios и будущего GUI.

Если новая mechanic действительно требует отдельной simulation phase, это основание явно пересмотреть `SimulationStepper`, а не вводить private per-system time semantics.

## Определить structured results

Если operation может быть отклонена из-за обычного world state, возвращайте structured result, реализующий нейтральный `OperationResult` floor.

```text
accepted
ResultCode
optional typed domain data
```

Примеры:

```text
terrain:position_occupied
movement:already_moving
movement:transition_unavailable
```

Exceptions оставляйте для invalid programming/bootstrap/configuration state и нарушенных internal invariants.

## Определить, как external intent попадает в mechanic

Если Player, AI, scripts, network adapters, scenarios или debug tools должны запрашивать operation, добавьте Control use-case:

```text
immutable Command
CommandResult
one typed handler
```

Handler должен оставаться тонким adapter к domain API.

Timed process **не** должен продолжаться через отправку internal Commands на каждую scheduled phase. После acceptance его domain processor продолжает работу напрямую через domain APIs.

## Не смешивать diagnostics с hot reads

Hot read contract должен оставаться primitive и дешёвым. Diagnostic explanations при необходимости получают отдельный cold path для debugging/visualization.

Не заставляйте каждый high-frequency query создавать strings/collections только потому, что tooling когда-нибудь может захотеть подробное объяснение.

## Продумать invalidation и revalidation

Timed или cached mechanics должны иметь ясный ответ на изменения мира после старта работы.

Текущий Movement намеренно использует:

```text
start-time validation
    -> sleep until completion
    -> completion-time revalidation
```

Он пока не просыпается немедленно при terrain mutation.

Новая mechanic должна документировать, какой policy она использует:

```text
revalidates only when scheduled
subscribes/reacts to relevant mutation events
reserves state to prevent conflicts
or uses another explicit policy
```

Не оставляйте timing invalidation случайным implementation behavior.

## Сначала добавить focused tests

Сначала тестируйте local laws, а не стройте огромный scenario.

В зависимости от mechanic полезны уровни:

```text
definition compiler tests
state-owner unit tests
result/rejection tests
scheduler binding tests
integration with authoritative owner
scenario vertical slice
negative-space tests
boundary arithmetic tests
reference/property tests
```

Timed mechanics дополнительно должны доказывать:

```text
no final mutation before due tick
correct completion tick
caller tick batching does not alter result
intermediate world mutation is handled according to documented policy
```

## Производительность — после semantics

Зафиксируйте предполагаемый workload, но не заменяйте ясную реализацию сразу на packed arrays, off-heap buffers, custom heaps или multi-threaded infrastructure.

Проверяйте по порядку:

```text
Can unnecessary work be removed?
Can the query be localized/indexed?
Can derived work be reused?
Are allocations visible in a representative profile?
Does data representation need specialization?
Does parallelism still matter after that?
```

Текущий Movement следует этому правилу: он schedules одно completion на active adjacent step вместо per-tick polling каждого mover, а specialization state storage оставляет до появления representative agent workloads.

## Обновлять документацию по уровням

Настоящая mechanic обычно затрагивает несколько уровней документации:

```text
ARCHITECTURE.md
    -> only stable semantic contracts/invariants

TECHNICAL_REFERENCE.md
    -> concrete classes, current algorithms, tests, known gaps

Wiki subsystem page
    -> detailed reasoning, examples, formulas, extension rules

Project Structure / Overview / Roadmap
    -> when package/phase/current capabilities changed

EN/RU counterparts + i18n source hashes
```

Текущий Movement показывает нужную глубину: его подробная Wiki-страница описывает Command start semantics, Action state, timing carry, Scheduler binding, revalidation, TransitionCost, Shape roles и известные deferred responsibilities.

## Checklist готовности

Новая mechanic готова, когда:

```text
single authoritative owner identified
immutable definition data separated from mutable runtime state
narrow read/write contracts defined
immediate vs timed semantics explicit
timed process state owned by domain when applicable
Scheduler binding uses narrow process scheduling when applicable
normal rejection vs exception boundary explicit
external Command added only when external intent needs it
revalidation/invalidation policy explicit
headless unit/integration/scenario tests added
negative cases covered
performance risks noted without speculative infrastructure
architecture/reference/Wiki docs synchronized EN/RU
full simulation suite and docs checks green
```
