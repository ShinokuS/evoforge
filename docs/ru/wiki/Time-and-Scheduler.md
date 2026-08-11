# Время и Scheduler

EvoForge избегает обязательного `update(dt)` у каждого object. Simulation time и activation управляются явной scheduling infrastructure, чтобы inactive entities не расходовали CPU только потому, что существуют.

Первый production timed consumer — Movement. Его реализация сделала временную границу конкретной: domain systems планируют собственные process ids через узкую bound capability, а `SimulationStepper` владеет production-смыслом продвижения симуляции на один tick.

## Разделение ответственности

```text
SimulationClock
    -> authoritative current simulation tick

Scheduler
    -> когда scheduled work становится due
    -> deterministic ordering due work

HandlerRegistry
    -> HandlerId -> ScheduledHandler

ProcessScheduler
    -> narrow domain capability: scheduleAfter(delay, processId)

BoundProcessScheduler
    -> связывает один ProcessScheduler с одним HandlerId и SimulationTime

SimulationStepper
    -> production owner phase order одного tick

Domain action/process owner
    -> что означает processId и что происходит при пробуждении
```

Главный закон:

```text
Scheduler знает WHEN / HANDLER / PROCESS ID.
Domain знает WHAT THAT PROCESS MEANS.
```

Scheduler не должен превращаться в central switch gameplay mechanics.

## `SimulationClock` и `SimulationTime`

`SimulationClock` — authoritative mutable time value фундамента симуляции.

`SimulationTime` — его read-only capability:

```java
public interface SimulationTime {
    long tick();
}
```

Consumers, которым нужно только читать текущее simulation time, получают эту узкую capability, а не mutable clock control.

`BoundProcessScheduler` зависит от `SimulationTime` и не может продвигать world clock.

Domain systems не должны выводить authoritative ordering из wall-clock time или renderer frame timing. Presentation может отображать или ускорять simulation time, но authoritative mechanics используют simulation ticks.

## `Scheduler`

Scheduler владеет timed activation infrastructure. Концептуально каждая scheduled task содержит:

```text
when
HandlerId
processId
TaskHandle
stable ordering identity
```

Scheduler не интерпретирует `processId`. `processId = 17` может означать Movement action для одного handler и совершенно другой Crafting process для будущего другого handler.

Пары:

```text
HandlerId + processId
```

достаточно для routing activation без global process-type enum.

## `HandlerRegistry`

`HandlerRegistry` связывает runtime `HandlerId` с `ScheduledHandler` callbacks.

Одно семейство domain processes обычно регистрирует один handler:

```text
MovementActionProcessor::complete
    -> один HandlerId для всех MovementAction
```

Тысяча движущихся objects **не** требует тысячу handlers. Они создают тысячу scheduled tasks с разными process ids для одного Movement handler.

Будущие mechanics следуют тому же pattern:

```text
Movement     -> movement handler
Crafting     -> crafting handler
Growth       -> growth handler
Construction -> construction handler
```

При добавлении mechanic центральный Scheduler switch не меняется.

## `ScheduledHandler`

Infrastructure callback намеренно минимален:

```text
handle(processId)
```

Это adapter boundary, а не domain model.

Для Movement зарегистрированный callback интерпретирует `processId` как `MovementActionId`, загружает action из `MovementStateStore`, выполняет revalidation мира и либо коммитит Spatial, либо завершает interrupted action без перемещения.

## `ProcessScheduler`

Domain start systems обычно не должны получать raw `Scheduler + HandlerId + SimulationClock`.

Reusable narrow scheduling capability:

```java
ProcessScheduler.scheduleAfter(
        long delayTicks,
        long processId)
```

Она позволяет mechanic сказать:

```text
разбудить мой process 17 через 10 simulation ticks
```

не выдавая ей права:

```text
двигать clock
выбирать HandlerId другого domain
читать Scheduler storage
переопределять absolute time semantics
```

Эта abstraction появилась вместе с первым реальным timed consumer, а не была спроектирована speculative заранее.

## `BoundProcessScheduler`

`BoundProcessScheduler` — infrastructure adapter, реализующий narrow domain capability.

Он владеет references на:

```text
SimulationTime
Scheduler
один HandlerId
```

Когда domain вызывает:

```text
scheduleAfter(delay, processId)
```

adapter рассчитывает absolute due tick из authoritative simulation time и передаёт Scheduler уже bound handler.

Это intended general bridge для будущих timed mechanics.

## Domain process identity и `TaskHandle`

Scheduler task identity и domain process identity — разные concepts.

Для Movement:

```text
MovementActionId
    = domain identity active movement process

TaskHandle
    = infrastructure identity одной scheduled activation
```

Их нельзя объединять только потому, что оба могут быть представлены числом.

Future domain process может планировать несколько activations за свою жизнь. И наоборот, infrastructure cancellation может удалить task, не меняя semantic identity domain process.

Current Movement не поддерживает early cancellation, поэтому пока не хранит и не прокидывает `TaskHandle`.

## `SimulationStepper`

`SimulationStepper` — production owner текущей simulation-step semantics.

Текущий one-tick order:

```text
1. SimulationClock.advance()
2. Scheduler.dispatchDue(clock.tick())
```

Это важно: test fixtures и future GUI должны **вызывать** этот production contract, а не определять собственную семантику tick.

`ScenarioHarness.advance()` делегирует `SimulationStepper`. `advanceTicks(n)` — только цикл над той же production operation.

Следовательно:

```text
advanceTicks(10)
```

и:

```text
advance(); advance(); ... x10
```

обязаны давать одинаковый authoritative state.

## Same-tick dispatch semantics

Scheduler dispatch работает deterministic snapshot batch-ом работы, которая была due к моменту начала dispatch.

Task, поставленная handler-ом на current tick во время этого dispatch, не дренируется рекурсивно бесконечно в том же batch.

Current Movement дополнительно гарантирует:

```text
movement duration >= 1 tick
```

поэтому Movement action не планирует собственное completion на тот же tick, в котором стартовал.

Если future mechanic действительно потребует repeated same-tick activation, это должно стать осознанным изменением phase policy по evidence реального consumer, а не зависимостью от случайного recursive draining.

## Пример Movement

Пусть current tick = 100, а actor запускает Movement action длительностью 15 ticks.

```text
tick 100
MovementSystem создаёт MovementAction #42
MovementSystem вызывает movement ProcessScheduler.scheduleAfter(15, 42)

BoundProcessScheduler
    читает current SimulationTime = 100
    ставит Scheduler task на tick 115
    использует уже bound Movement HandlerId

... никакого per-tick Movement polling ...

tick 115
SimulationStepper продвигает clock
Scheduler dispatch-ит due Movement task
MovementActionProcessor.complete(42)
    -> загружает action
    -> revalidate source/object/Navigation
    -> SpatialSystem.move(...) если переход всё ещё valid
```

На ticks 101..114 action не потребляет обязательный CPU только потому, что существует.

## Event-driven activity

Intended simulation model:

```text
persistent objects may exist indefinitely
only active processes schedule work
scheduler activates due work
domain handler resumes the authoritative domain process
optional diagnostics/events may record resulting facts later
```

Для большого persistent world это лучше global scan каждого object каждый frame.

Movement теперь является первым concrete proof этой модели, а не только будущим примером.

## Scheduler — не Action system

Scheduled task — infrastructure state. Domain `Action` — domain runtime state.

Current Movement показывает разницу:

```text
MovementAction
    владеет object/source/destination semantics
    живёт в MovementStateStore

ScheduledTask
    владеет activation time/routing
    живёт в Scheduler
```

Scheduler не знает, что task означает walking.

Эту границу нужно сохранять для будущих Crafting, Growth, Combat или Construction processes вместо преждевременного generic all-purpose `ActionSystem`.

## Детерминированный порядок

Если несколько tasks due в одно simulation time, ordering должен быть explicit и stable. Authoritative outcomes не зависят от arbitrary heap/map iteration или thread timing.

Current Scheduler упорядочивает задачи по scheduled time, затем по task handle. Scheduler tests поэтому являются determinism infrastructure, а не просто container tests.

Future multi-agent workload сделает ordering effects ещё заметнее, особенно после появления Occupancy и конкуренции за destinations/resources. Ordering contract должен оставаться stable и observable.

## Cancellation и stale activation

`TaskHandle` даёт infrastructure identity scheduled work для cancellation/tracking.

Current Movement намеренно не реализует early cancellation. Обычно action остаётся active до scheduled completion, которое его будит и удаляет.

Когда появится реальный cancellation consumer — например death, stun, forced displacement или replacement process — дизайн должен выбрать:

```text
удалять Scheduler task eagerly через TaskHandle
или
удалять domain process и допускать поздний stale wake-up, который ничего не найдёт
```

Решение должно исходить из semantics и измеренного stale-task volume, а не из предположений заранее.

## Wall clock и simulation clock

Renderer может работать на 30, 60, 144 или variable FPS. Эти частоты не определяют authoritative simulation semantics.

Аналогично game-speed control вроде 1x/2x/5x должен концептуально менять число simulation ticks, продвигаемых за real second, а не менять `MovementRate` или `TransitionCost` только ради ускорения presentation.

При одинаковом initial state и одинаковой последовательности simulation ticks/commands authoritative state не должен зависеть от presentation FPS.

Future pause, time acceleration, deterministic replay, headless scenario execution и testing опираются на это разделение.

## Randomness

Generic RNG service пока нет, потому что current timed Movement и TransitionCost deterministic и не требуют authoritative randomness.

Когда появится первый random mechanic, RNG state должен быть explicit/reproducible, а scheduled ordering — deterministic при одинаковом state и commands.

## Performance model

Scheduler помогает large-world scale: CPU cost коррелирует с active processes, а не total persistent object count.

Movement сейчас планирует одно completion activation на active adjacent step вместо per-tick polling каждого mover.

Это не означает, что любая future recurring process должна создавать множество tiny heap objects. Allocation/storage representation нужно измерять на representative active-agent workload.

## Тестирование

Time/Scheduler coverage теперь включает:

```text
clock advancement
handler registration
deterministic task ordering
task identity/cancellation infrastructure
BoundProcessScheduler relative scheduling
SimulationStepper phase order
movement scheduled completion
batched-versus-individual tick advancement equivalence
completion-time world revalidation
```

Ключевое integration rule: domain action tests используют настоящий production stepping path, а не вызывают scheduled handlers вручную в произвольный момент.

## Стабильные правила

```text
1. SimulationClock — authoritative simulation time.
2. Presentation wall time/FPS не определяет authoritative mechanics.
3. Scheduler владеет when/order, но не domain meaning.
4. HandlerRegistry routing-ит process families без global gameplay switch.
5. ProcessScheduler — normal narrow scheduling capability domain start system.
6. BoundProcessScheduler привязывает domain family к одному HandlerId.
7. Domain process id и TaskHandle остаются разными identities.
8. SimulationStepper владеет production tick phase order.
9. Test fixtures вызывают SimulationStepper, а не придумывают собственную time semantics.
10. Domain process хранит свой runtime state вне Scheduler.
```

## Связанная документация

- [Movement System](Movement-System.md) — первый production timed consumer и интеграция TransitionCost.
- [Control Backbone](Control-Backbone.md) — external start intent.
- [Стратегия тестирования](Testing-Strategy.md) — deterministic integration tests.
