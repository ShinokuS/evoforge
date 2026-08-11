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
9. [Стратегия тестирования](Testing-Strategy.md)
10. [Процесс разработки](Development-Workflow.md)
11. [Дорожная карта и отложенные решения](Roadmap-and-Deferred-Decisions.md)

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
│   └── SpatialSystem           ObjectId -> XYZ
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
```

Центральное правило дизайна — владение: у каждого изменяемого авторитетного факта один владелец. Общие координаты не означают общего хранилища, а удобство запроса не является основанием переносить доменную ответственность в query-layer.

Commands переносят внешнее намерение в симуляцию. Внутренние процессы не обязаны превращать каждую мутацию в Command и могут использовать явно выданные узкие domain write-capabilities.

## Geometry и Navigation в одной схеме

Terrain `Shape` привязан к terrain-coordinate и вносит вклад в локальную топологию. Navigation композиционно объединяет эти вклады, не зная конкретных типов Shape.

```text
FullShape        RampShape        FullShape
    █                /                █
    █               /                 █

lower position  <-> ramp position <-> upper position
```

Структурный edge существует только тогда, когда его разрешает общая алгебра:

```text
resolved = departures & arrivals & ~blocks
```

Сам edge всегда ведёт к одному из 26 непосредственных XYZ-соседей. Resolver может читать supporting geometry ниже source, потому что Shape, подтверждающий destination surface, может быть anchored ниже этой destination.

## Стабильные уровни документации

В проекте намеренно три уровня:

- `docs/ARCHITECTURE.md` — краткий архитектурный контракт: стабильные границы, инварианты и отложенные решения.
- `docs/TECHNICAL_REFERENCE.md` — текущая реализация, пакеты, алгоритмы, тесты и известные технические пробелы.
- эта Wiki — объяснительный слой: примеры, схемы, reasoning, инструкции по расширению и walkthrough подсистем.

Русские переводы находятся в `docs/ru/`, английские оригиналы остаются каноническими. Подробнее — [Сопровождение документации](Wiki-Maintenance.md).

## Текущая фаза проекта

Реализованный фундамент теперь включает definitions, object identity, scheduling, дискретное object spatial state, landscape terrain, geometry, structural transition algebra, `FullShape`, cardinal `RampShape`, направленную локальную Navigation и первый vertical slice Control Backbone со structured command results и `PlaceTerrainCommand`.

Следующий крупный шаг — Scenario Harness; далее идут basic movement, occupancy, pathfinding и первый agent vertical slice.

Проект намеренно не строит системы до появления потребителя. Queued command batching semantics, caches, богатые movement costs, actor capability overlays, falling, chunk layouts и advanced pathfinding остаются deferred до появления реальной нагрузки.

## Навигация по документации

Полная карта доступна в sidebar. [Глоссарий](Glossary.md) определяет проектные термины: *authoritative owner*, *terrain anchor*, *standing position*, *departure*, *arrival*, *block* и *structural edge*.
