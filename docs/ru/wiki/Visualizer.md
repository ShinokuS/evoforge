# Минимальный Z-level Visualizer

Первый визуализатор EvoForge — это **debug-наблюдатель за реальной симуляцией**, а не вторая модель мира и не игровой UI.

Его задача — сделать уже реализованные системы видимыми до появления Pathfinder, Occupancy и первого agent vertical slice.

## Граница модулей

```text
simulation  pure Java, authoritative и headless
    ↑
core        libGDX presentation/debug visualizer
    ↑
lwjgl3      desktop launcher
```

`core` зависит от `simulation`. Обратной зависимости `simulation` на libGDX или presentation code нет.

Production composition root — `SimulationAssembly`. Это setup-фаза для регистрации definitions и построения начального terrain, Shapes и objects. `start()` закрывает setup mutation и возвращает `SimulationRuntime`.

Запущенный runtime раскрывает command submission, `SimulationTime`, `SimulationStepper` и read-only `SimulationView`. View содержит только observation-capabilities: Terrain, Geometry, Object, Transform, Navigation и lookup объектов по клетке.

Visualizer не получает `SpatialSystem`, `TerrainSystem`, `GeometrySystem`, `MovementSystem`, `Scheduler` или `SimulationClock`.

## Значение Z-plane

Z визуализатора — это **standing/navigation plane**, потому что именно в этих координатах находятся Spatial objects и Navigation transitions.

При текущем supported-position law Shape terrain/Shape, поддерживающий standing position `(x,y,z)`, читается в:

```text
(x, y, z - 1)
```

Поэтому выбранный standing plane `Z` рисуется так:

```text
current plane:
    objects на Z
    support terrain/Shape на Z - 1

lower context plane:
    objects не рисуются
    затемнённый support terrain/Shape на Z - 2
```

Нижний слой — только presentation context. Он не меняет visibility, Navigation или simulation truth.

`RampShape` рисуется на standing plane, который он поддерживает, и получает явную стрелку направления подъёма. Presentation может знать concrete Shape types ради диагностики; Navigation остаётся generic и не получает `instanceof RampShape`.

## Время

Rendering FPS и simulation ticks независимы.

Visualizer владеет небольшим fixed-step accumulator. Только он переводит real frame time в запросы production ticks:

```text
real frame delta
    ↓
VisualizerTimeController
    ↓
SimulationStepper.advance()
```

`SimulationStepper` остаётся единственной production operation для продвижения simulation на один tick.

Первая версия поддерживает:

- pause;
- `N` — ровно один tick в pause;
- `Space` — run/pause на одной debug-скорости.

Приложение стартует на pause, поэтому tick-zero state и уже запущенные timed Movement actions можно изучить до их завершения.

## Rendering и инспекция

Текущая версия намеренно использует primitive debug graphics и не делает sprite-art частью simulation contract.

Реализовано:

- orthographic camera;
- `WASD` pan;
- zoom колесом мыши;
- выбор standing Z через `PageUp` / `PageDown`;
- выбранный plane плюс затемнённый нижний;
- различение default/full terrain и Ramp terrain;
- стрелка направления Ramp;
- objects через `CellObjectLookup` только в видимых клетках;
- выбор клетки левой кнопкой мыши;
- live overlay исходящих structural transitions из `NavigationLookup`;
- выбор object, если он находится в нажатой клетке;
- HUD с tick, standing Z и time mode;
- object inspector с `ObjectId`, definition id и authoritative XYZ.

Цвета transition overlay различают flat, upward и downward edges. Overlay показывает тот же primitive mask, который сейчас использует Movement.

## Demo world

`VisualizerDemoWorld` — presentation-owned deterministic setup content. Он не является частью simulation semantics и не является test-only Scenario Harness.

Он строит:

- flat platform на standing `Z=0`;
- настоящий positive-Y Ramp sample между standing `Z=-1` и `Z=0`;
- slow и fast mover на flat platform.

Оба mover на tick zero начинают одинаковый по стоимости adjacent `MoveStep`. Fast mover завершает его через 2 ticks, slow mover — через 8 ticks. Тем самым Visualizer показывает authoritative timed Movement, а не самостоятельно двигает sprites.

Headless tests проверяют Ramp transitions и эту разницу timing без создания libGDX window.

## Performance boundary

Renderer обходит только видимый камерой XY-range и не сканирует весь object repository. Terrain/Geometry reads намеренно остаются на текущем sparse-storage path, чтобы visualizer стал representative workload для последующего profiling.

Storage/chunk optimization не вводится только потому, что предполагаются lookup allocations. Allocation rate, GC и frame cost нужно измерить JFR на живом visualizer до изменения sparse-coordinate representation.

## Намеренно не входит

В первой версии нет:

- movement interpolation;
- Scene2D game UI;
- path overlay;
- transition-cost heatmap;
- Occupancy display;
- action-completion inspector;
- roofs/cutaway logic;
- presentation-driven world mutation;
- semantic assumption, что положительный/отрицательный Z означает above/below ground.

Внешний tileset, например DawnLike, позже может заменить primitive presentation без изменения runtime/view boundary.

## Следующие consumers

Visualizer должен оставаться полезным при следующих simulation milestones:

```text
Occupancy
    ↓
Pathfinder
    ↓
first agent / Cow vertical slice
```

Эти системы могут добавлять debug overlays, но должны использовать свои authoritative read contracts, а не превращать Visualizer в owner simulation state.
