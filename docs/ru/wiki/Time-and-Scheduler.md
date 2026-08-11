# Время и Scheduler

EvoForge избегает обязательного `update(dt)` у каждого object. Simulation time и activation управляются явной scheduling infrastructure, чтобы inactive entities не расходовали CPU только потому, что существуют.

## `SimulationClock` и `SimulationTime`

`SimulationClock` — authoritative mutable time value фундамента симуляции. Domain systems не должны выводить авторитетный порядок из wall-clock time или renderer frame timing.

`SimulationTime` — его read-only capability, которая раскрывает только `tick()`. Infrastructure, которой нужно читать текущее simulation time, но не двигать его, должна зависеть от этой более узкой границы.

Presentation может отображать simulation time по-разному, но authoritative mechanics используют simulation ticks.

## Ответственность Scheduler

Scheduler владеет ordering и activation timing, но не смыслом scheduled work.

```text
Scheduler answers: when and in what deterministic order?
Domain process owner answers: what does this activation mean?
```

Так Scheduler не превращается в central enum/switch всех gameplay mechanics.

## Текущие типы

```text
SimulationTime
SimulationClock
SimulationStepper
Scheduler
ScheduledTask
ScheduledHandler
HandlerId
HandlerRegistry
TaskHandle
ProcessScheduler
BoundProcessScheduler
```

Handlers регистрируются отдельно и referenced runtime handler ids внутри scheduled tasks.

## Production simulation step

`SimulationStepper` теперь владеет первым production-определением одного шага симуляции:

```text
advance SimulationClock на один tick
    ↓
Scheduler.dispatchDue(currentTick) один раз
```

Scenario fixture и будущие presentation layers могут вызывать этот production API, но не определяют собственную семантику tick.

Scheduler dispatch-ит snapshot batch работы, которая была due к началу dispatch. Работа, поставленная handler-ом на тот же tick, поэтому не дренируется рекурсивно в том же batch. Timed Movement гарантирует минимальную длительность один tick и не зависит от same-tick recursive execution.

## Event-driven activity

Предполагаемая модель:

```text
persistent objects may exist indefinitely
only active processes schedule work
scheduler activates due work
handler resumes the authoritative domain process
optional diagnostics/events may record resulting facts later
```

Для большого persistent world это лучше global scan каждого object каждый frame.

## Scheduler — не Action system

Scheduled task — infrastructure state. Domain Action или Process может использовать scheduling, но эти concepts не объединяются.

Timed Movement — первый concrete пример:

```text
MovementAction
    = domain state одного active adjacent transition

ScheduledTask
    = wake Movement на simulation tick с processId
```

`MovementActionId` и `TaskHandle` намеренно являются разными identities.

## Bound process scheduling

Domain mechanics не должны получать raw `Scheduler + HandlerId` только ради scheduling собственного continuation.

`ProcessScheduler` — узкий domain-facing capability:

```text
scheduleAfter(delayTicks, processId)
```

`BoundProcessScheduler` привязывает эту capability к одному зарегистрированному `HandlerId` и переводит relative delay в absolute simulation time.

Pattern расширения:

```text
new timed mechanic
    ↓
mechanic-owned process store / processor
    ↓
register processor callback once
    ↓
HandlerId
    ↓
BoundProcessScheduler
    ↓
mechanic receives only ProcessScheduler
```

Один handler обслуживает все active processes одной mechanic. Scheduler различает их через `processId`; отдельный handler на каждый Action не создаётся.

## Детерминированный порядок

Если несколько tasks due в одно simulation time, ordering должен быть explicit и stable. Authoritative outcomes не зависят от arbitrary heap/map iteration или thread timing.

Scheduler упорядочивает задачи по scheduled time, затем по task handle. Поэтому Scheduler tests — determinism infrastructure, а не просто container tests.

## Cancellation и handles

`TaskHandle` даёт identity scheduled work для cancel/tracking без зависимости от internal data structure Scheduler.

Handle — infrastructure identity activation, не `ObjectId` и не domain Action identity.

Первый Timed Movement slice намеренно не имеет early cancellation API и поэтому не раскрывает `TaskHandle` через `ProcessScheduler`. Movement Actions удаляются, когда выполняется их scheduled completion. Когда early cancellation станет реальным requirement, stale-wakeup против scheduler-cancellation semantics должны быть выбраны явно.

## Handler registration

`HandlerRegistry` связывает handler ids с `ScheduledHandler`. Domain-specific process processors регистрируются явно, обычно через узкий method reference вроде `movementActions::complete`.

Pattern расширения — не изменение Scheduler switch при появлении новой mechanic.

Scheduler остаётся неизменным при добавлении Movement, Crafting, Growth и других timed mechanics.

## Wall clock и simulation clock

Renderer может работать на 30, 60, 144 или variable FPS. Эти частоты не определяют authoritative simulation semantics.

Будущие pause и time acceleration должны менять то, насколько быстро real time двигает simulation steps, а не смысл и ordering самих simulation ticks.

Headless tests уже фиксируют тот же принцип: advance десяти ticks одним helper-вызовом эквивалентен десяти отдельным вызовам production stepper.

## Randomness

Generic RNG service пока нет: текущим mechanics не нужна authoritative randomness. При первом random consumer RNG state должен стать explicit/reproducible, а scheduled ordering — deterministic при одинаковом state/commands.

## Performance model

Scheduler помогает масштабу: CPU cost коррелирует с active processes, а не total persistent object count.

Это не означает, что каждая recurring process должна создавать множество tiny heap objects. Representation нужно измерять на реальном active-agent workload.

## Тестирование

Scheduler tests покрывают ordering, registration, task identity, cancellation, clock interaction и boundary/error behavior.

Timed Movement добавляет первые production integration tests для:

- delayed authoritative mutation;
- разных process durations из-за разных movement rates;
- completion-time revalidation;
- deterministic fractional timing carry;
- эквивалентности batched и individual tick advancement.
