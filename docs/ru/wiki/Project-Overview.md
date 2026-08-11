# Обзор проекта

## Что такое EvoForge

EvoForge — проект детерминированной эмерджентной симуляции. Архитектура рассчитана на мир, где множество независимых механик взаимодействуют без необходимости при каждой новой функции менять центральный тип объекта, гигантскую структуру клетки или универсальный update-loop.

Целевой масштаб — примерно миллион persistent objects, более ста тысяч positioned objects и порядка десяти тысяч одновременно активных agents. Это архитектурные ориентиры масштаба, а не обещания производительности. Они нужны, чтобы сразу отбрасывать дизайны с обязательными global scans или per-tick работой, пропорциональной всем persistent entities.

## Что проект оптимизирует

Главные цели — семантическая стабильность, детерминизм, расширяемость и измеряемая производительность. Проект предпочитает узкие интерфейсы и явных владельцев центральным изменяемым реестрам. Оптимизация начинается с устранения лишней работы, затем используются locality и indexes, а специализированные data-oriented структуры появляются только для доказанных hot paths.

Подсистема считается удачной, если новая механика может пользоваться её публичным контрактом, не зная внутреннее представление storage и конкретные implementation types.

## Чем EvoForge намеренно не является

EvoForge не строится как:

- чистый ECS, где каждое свойство обязательно помещается в component tables;
- универсальный `WorldCell` со всеми environmental mechanics;
- система, где каждый объект получает `update(dt)` каждый frame;
- универсальный physics engine, диктующий abstractions всему gameplay;
- центральный type switch, знающий каждый object, terrain type, Shape или Command;
- command bus, через который обязана проходить каждая внутренняя мутация;
- framework, заранее реализующий speculative infrastructure без реального consumer.

Выборочный data-oriented design допустим и ожидается в измеренных hot paths, но это техника реализации, а не доменная модель.

## Технологическая база

Текущая база — Java 21, presentation-модули libGDX 1.14.x и чистый Java-модуль `simulation`. `simulation` не зависит от libGDX и тестируется headless.

```text
simulation/  authoritative domain and simulation code
core/        libGDX application / presentation layer
lwjgl3/      desktop launcher
assets/      data definitions and presentation assets
```

Авторитетное состояние не должно переезжать в `core` или `lwjgl3` ради удобства.

## Высокоуровневая модель исполнения

EvoForge сочетает несколько архитектурных идей, а не следует одному именованному pattern:

```text
OO domain model
+ immutable composition-driven definitions
+ specialized mutable state owners
+ scheduler/event-driven execution
+ external-intent Command boundary
+ narrow coordinated domain write capabilities
+ deterministic authoritative mutation
+ indexed spatial/world queries
+ selective DOD after profiling
```

Объекты — реальные доменные объекты со стабильной идентичностью, но изменяемые механики не накапливаются внутри `WorldObject`. Definitions описывают неизменяемую композицию. Systems владеют авторитетными runtime-свойствами. Scheduler управляет временем работы, но не доменной семантикой.

Внешнее намерение Player/AI/script/scenario сходится к Control Commands. Внутренние simulation processes не обязаны снова проходить через Command и могут напрямую использовать явно выданные узкие domain APIs.

## Декомпозиция мира

Текущий мир намеренно разделён на object и landscape domains.

```text
WORLD
├── Objects
│   ├── identity / existence
│   ├── definitions
│   └── object positions
│
└── Landscape
    ├── terrain material/content
    ├── coordinated LandscapeMutations boundary
    └── mechanics layered over terrain
```

Оба домена используют одно integer XYZ address space, но не общего владельца storage. Terrain не превращается в `WorldObject` только потому, что занимает координаты.

## Детерминизм

При одинаковом авторитетном initial state, sequence submitted commands и authoritative random state симуляция должна выдавать одинаковый поддерживаемый результат. Поэтому поведение не может зависеть от неопределённого порядка `HashMap`, неконтролируемой случайности, thread timing или прямой мутации мира worker threads.

Текущая synchronous Control delivery выполняет каждую submitted command немедленно, поэтому последующие вызовы видят мутации предыдущих. Будущая queued delivery должна отдельно определить deterministic ordering и semantics видимости.

Cross-platform bit-identical floating-point semantics пока не обещаются. Более строгий numeric contract появится только при реальной потребности механики.

## Философия расширения

Проект различает новый контент и новую механику.

Если существующие mechanics уже выражают новый тип object/landscape, добавляются definition data. Если требуется действительно новое runtime behavior, вводится специализированная механика со своим definition compiler/state owner/system и тестами. Нельзя расширять центральный `WorldObject`, `TerrainSystem` или registry просто потому, что туда удобно добавить поле.

То же относится к geometry: новый Shape — новая реализация `Shape`. Navigation не должна получать `instanceof RampShape` или switch по известным shape types.

Для внешнего intent новая Command добавляет typed command/result/handler в подходящий Control use-case. `CommandDispatcher` не получает центральный domain switch. Внутренние mechanics не должны изобретать Commands только ради вызова другой системы.

## Текущее состояние

Завершённый фундамент:

```text
Object identity and repository
Definition loading and aspect compilation
Simulation clock and scheduler
Discrete XYZ object positioning and spatial index
Landscape definitions and terrain storage
Coordinated LandscapeMutations lifecycle boundary
Geometry abstraction and Shape contract
TransitionMask / TransitionPorts / TransitionComposition
FullShape
Cardinal RampShape
Directed structural Navigation resolver
Control Backbone core and synchronous delivery
PlaceTerrainCommand vertical slice
```

Следующий крупный consumer — Scenario Harness. Далее появятся basic movement, occupancy, pathfinding и первый agent vertical slice.

См. [Control Backbone](Control-Backbone.md) для command model и [Дорожную карту и отложенные решения](Roadmap-and-Deferred-Decisions.md) для оставшихся намеренно открытых решений.
