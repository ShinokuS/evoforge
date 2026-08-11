# Wiki EvoForge

EvoForge — проект детерминированной эмерджентной симуляции на Java 21. Авторитетная симуляция находится в чистом Java-модуле `simulation`; libGDX используется только в presentation- и launcher-слоях.

Эта Wiki — подробное техническое руководство по проекту. Она объясняет не только существующие классы, но и причины границ между подсистемами, стабильные инварианты, намеренно отложенные решения и правила добавления новых механик без расшатывания существующей архитектуры.

> Русская версия предназначена для чтения. Каноническими источниками семантики остаются английские документы в `docs/`; при расхождении английская версия имеет приоритет.

## С чего начать

Если вы впервые знакомитесь с проектом, рекомендуемый порядок:

1. [Обзор проекта](Project-Overview.md)
2. [Архитектурные принципы](Architecture-Principles.md)
3. [Структура проекта](Project-Structure.md)
4. [Модель мира](World-Model.md)
5. [Контракт Shape](Shape-Contract.md)
6. [Алгебра переходов](Transition-Algebra.md)
7. [Navigation](Navigation.md)
8. [Control Backbone](Control-Backbone.md)
9. [Movement System](Movement-System.md)
10. [Время и Scheduler](Time-and-Scheduler.md)
11. [Стратегия тестирования](Testing-Strategy.md)
12. [Процесс разработки](Development-Workflow.md)
13. [Дорожная карта и отложенные решения](Roadmap-and-Deferred-Decisions.md)

## Текущая архитектура в одном взгляде

```text
External intent
    ↓
Control Backbone
    ↓
authoritative domain APIs

WORLD
├── Objects
│   ├── ObjectRepository        identity / existence
│   ├── ObjectFactory           definition-backed creation
│   ├── SpatialSystem           authoritative ObjectId -> XYZ
│   └── MovementSystem          timed adjacent execution
│            │
│            ├── NavigationLookup        structural permission
│            ├── TransitionCostLookup    intrinsic edge price
│            ├── MovementRate            actor rate
│            └── ProcessScheduler        delayed completion
│
└── Landscape
    ├── LandscapeMutations      coordinated write boundary
    └── TerrainSystem           XYZ -> LandscapeDefinitionId | absence
             │
             ▼
        GeometrySystem          terrain presence -> Shape
             │
             ▼
        NavigationSystem        Shape contributions -> structural edges

TIME
├── SimulationClock
├── Scheduler
├── BoundProcessScheduler
└── SimulationStepper
```

Центральное правило дизайна — владение: у каждого изменяемого авторитетного факта один владелец. Общие координаты не означают общего хранилища, а удобство запроса не является основанием переносить доменную ответственность в query-layer.

Commands переносят внешнее намерение в симуляцию. Внутренние процессы не обязаны превращать каждую мутацию в Command и могут использовать явно выданные узкие domain write-capabilities.

## Geometry, Navigation, cost и Movement

Terrain `Shape` привязан к terrain-coordinate и вносит вклад в локальную topology. Navigation композиционно объединяет эти вклады, не зная concrete Shape types.

```text
FullShape        RampShape        FullShape
    █                /                █
    █               /                 █

lower position  <-> ramp position <-> upper position
```

Structural edge существует только тогда, когда его разрешает общая algebra:

```text
resolved = departures & arrivals & ~blocks
```

Сам edge всегда ведёт к одному из 26 непосредственных XYZ-соседей. Resolver может читать supporting geometry ниже source, потому что Shape, подтверждающий destination surface, может быть anchored ниже этой destination.

После того как Navigation подтвердил directed edge, `TransitionCostCalculator` вычисляет его цену из двух supporting landscape cells, directed traversal factors обоих Shape и grid direction length. Затем Movement переводит cost во время через definition-backed `MovementRate`, сохраняя дробную точность через per-object carry.

Принятое движение остаётся дискретным и timed:

```text
MoveStepCommand
    -> MovementAction starts
    -> source position остаётся authoritative
    -> Scheduler позже будит completion
    -> transition повторно проверяется
    -> SpatialSystem.move коммитит destination
```

Полный контракт и формулы — в [Movement System](Movement-System.md).

## Стабильные уровни документации

В проекте намеренно три уровня:

- `docs/ARCHITECTURE.md` — краткий архитектурный контракт: стабильные границы, инварианты и отложенные решения.
- `docs/TECHNICAL_REFERENCE.md` — текущая реализация, пакеты, алгоритмы, тесты и известные технические пробелы.
- эта Wiki — объяснительный слой: примеры, схемы, reasoning, инструкции по расширению и walkthrough подсистем.

Русские переводы находятся в `docs/ru/`, английские оригиналы остаются каноническими. Подробнее — [Сопровождение документации](Wiki-Maintenance.md).

## Текущая фаза проекта

Реализованный фундамент теперь включает definitions, object identity, scheduling, production simulation stepping, дискретное object spatial state, landscape terrain, geometry, structural transition algebra, `FullShape`, cardinal `RampShape`, directed local Navigation, Control Backbone, deterministic Scenario fixture, timed adjacent Movement и actor-independent TransitionCost model.

Movement теперь реальный consumer Scheduler: принятый `MoveStepCommand` создаёт active action, ждёт deterministic число simulation ticks, повторно валидирует edge на completion и только после этого меняет Spatial.

Transition-cost layer теперь включает:

```text
landscape traversal.cost
source departure Shape factor
destination arrival Shape factor
cardinal / double-diagonal / triple-diagonal grid length
fixed-point deterministic arithmetic
```

Следующий обязательный gameplay milestone — минимальная Z-level debug-визуализация, затем Occupancy, Pathfinder, первый agent vertical slice и world generation.

Проект по-прежнему избегает speculative systems. Actor-specific surface affinity, early movement cancellation, reactive wake-up при landscape mutation, полная Occupancy semantics, `MoveTo`, детали Pathfinder, финальная renderer architecture, chunks/regions и generation algorithms остаются deferred до своих реальных milestones.

## Навигация по документации

Полная карта доступна в sidebar. [Глоссарий](Glossary.md) определяет проектные термины: *authoritative owner*, *terrain anchor*, *standing position*, *departure*, *arrival*, *block*, *structural edge* и связанные понятия симуляции.
