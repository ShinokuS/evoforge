# Время и Scheduler

EvoForge избегает обязательного `update(dt)` у каждого object. Simulation time и activation управляются явной scheduling infrastructure, чтобы inactive entities не расходовали CPU только потому, что существуют.

## `SimulationClock`

`SimulationClock` — authoritative time value фундамента симуляции. Domain systems не должны выводить авторитетный порядок из wall-clock time или renderer frame timing.

Presentation может отображать/interpolate time, но authoritative mechanics используют simulation time.

## Ответственность Scheduler

Scheduler владеет ordering и activation timing, но не смыслом scheduled work.

```text
Scheduler answers: when and in what deterministic order?
Domain handler answers: what does this activation mean?
```

Так Scheduler не превращается в central enum/switch всех gameplay mechanics.

## Текущие типы

```text
SimulationClock
Scheduler
ScheduledTask
ScheduledHandler
HandlerId
HandlerRegistry
TaskHandle
```

Handlers регистрируются отдельно и referenced stable runtime handler ids внутри scheduled tasks.

## Event-driven activity

Предполагаемая модель:

```text
persistent objects may exist indefinitely
only active processes schedule work
scheduler activates due work
handler mutates the authoritative owner
optional event records the resulting fact
```

Для большого persistent world это лучше global scan каждого object каждый frame.

## Scheduler — не Action system

Scheduled task — infrastructure state. Будущий domain `Action` может использовать scheduling, но эти concepts не следует объединять.

Например, “cow walks to cell X” может быть domain action, фазы которого schedule future activation. Scheduler не должен знать, что task означает walking.

## Детерминированный порядок

Если несколько tasks due в одно simulation time, ordering должен быть explicit и stable. Authoritative outcomes не зависят от arbitrary heap/map iteration или thread timing.

Поэтому Scheduler tests — determinism infrastructure, а не просто container tests.

## Cancellation и handles

`TaskHandle` даёт identity scheduled work для cancel/tracking без зависимости от internal data structure Scheduler.

Handle — infrastructure identity activation, не `ObjectId` и не persistence key сам по себе.

## Handler registration

`HandlerRegistry` связывает handler ids с `ScheduledHandler`. Domain-specific code регистрирует handlers явно.

Pattern расширения:

```text
new timed mechanic
    -> new domain handler
    -> explicit registration
    -> existing Scheduler
```

а не изменение Scheduler switch.

## Wall clock и simulation clock

Renderer может работать на 30, 60, 144 или variable FPS. Эти частоты не определяют authoritative simulation semantics.

Pause, time acceleration, deterministic replay и headless scenarios требуют независимости simulation time от rendering cadence.

## Randomness

Generic RNG service пока нет: текущим mechanics не нужна authoritative randomness. При первом random consumer RNG state должен стать explicit/reproducible, а scheduled ordering — deterministic при одинаковом state/commands.

## Performance model

Scheduler помогает масштабу: CPU cost коррелирует с active processes, а не total persistent object count.

Это не означает, что каждая recurring process должна создавать множество tiny heap objects. Representation измеряется на реальном active-agent workload.

## Тестирование

Scheduler tests покрывают ordering, registration, task identity, cancellation, clock interaction и boundary/error behavior. С появлением domain actions integration tests должны доказывать deterministic authoritative results через обычные system boundaries.
