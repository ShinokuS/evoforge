# Z-level Visualizer и процедурный ландшафт

Visualizer EvoForge — это **debug-наблюдатель за реальной симуляцией**, а не вторая модель мира и не владелец simulation state.

Текущий presentation contract — **полный top-down**. Landscape art генерируется самим EvoForge при запуске вместо зависимости от внешнего tileset.

## Граница модулей

```text
simulation  pure Java, authoritative и headless
    ↑
core        libGDX presentation/debug visualizer
    ↑
lwjgl3      desktop launcher
```

`core` зависит от `simulation`. Обратной зависимости `simulation` на libGDX, pixels, palettes, atlases или presentation code нет.

`SimulationAssembly` строит начальный мир, а `start()` возвращает `SimulationRuntime`. Visualizer получает только:

```text
SimulationView
SimulationTime
SimulationStepper
```

Он не получает `SpatialSystem`, `TerrainSystem`, `GeometrySystem`, `MovementSystem`, `Scheduler` или `SimulationClock`.

## Значение Z-plane

Z визуализатора — это **standing/navigation plane**, потому что именно в этих координатах живут Spatial objects и Navigation transitions.

При текущем supported-position law Shape terrain/Shape, поддерживающий standing position `(x,y,z)`, читается в:

```text
(x, y, z - 1)
```

Выбранный standing plane authoritative. Нижний слой — только presentation context:

```text
selected Z:
    objects на Z
    support terrain/Shape на Z - 1

lower context:
    objects не рисуются
    затемнённый support terrain ближайшего нижнего standing plane
    показывается только там, где у selected plane нет support terrain в этом XY
```

Положительный или отрицательный Z не трактуется автоматически как above-ground/underground.

## Контракт процедурной визуализации

Текущий landscape полностью генерируется внутри `core`:

```text
SimulationView
    ↓ terrain / Shape / XYZ
LandscapeRenderer
    ↓ neighbourhood + Z context
LandscapeTopology
    ↓ normalized cell topology
ProceduralLandscapePack
    ↓ generated TextureRegion
SpriteBatch
```

Главное разделение:

```text
simulation       что существует и как ведёт себя
presentation     как это состояние рисуется
```

Simulation definitions не содержат texture names, sprite ids или visual rules.

### Native pixel profile

Первый procedural profile EvoForge использует:

```text
logical world cell = 1 x 1 simulation unit
native visual cell = 16 x 16 source pixels
```

Значение 16 pixels относится только к presentation. Zoom может показывать одну world cell большим или меньшим количеством экранных pixels, не меняя simulation coordinates.

### Палитра

`EvoForgePalette` содержит одну ограниченную grass/earth palette. Сгенерированные tiles используют общие base, highlight, shadow, deep-tone и outline colors, поэтому независимо созданные элементы сохраняют единый визуальный язык.

Палитра намеренно небольшая. Новые материалы должны расширять общий visual language, а не вводить несвязанные цветовые схемы под каждый объект.

### In-memory atlas

`ProceduralLandscapePack` создаёт один `Pixmap` atlas во время старта visualizer, превращает его в одну libGDX `Texture`, а затем раздаёт `TextureRegion` из этой texture.

Source PNG, JSON visual descriptor, TexturePacker step и установка сторонних assets не требуются.

Вокруг каждого 16x16 region генерируется однопиксельный duplicate padding, а texture использует `Nearest` filtering, чтобы края pixel art оставались стабильными при zoom.

### Cell-aligned autotiling

Landscape остаётся выровненным по simulation grid.

Для каждой terrain cell `LandscapeRenderer` смотрит восемь соседей:

```text
NW  N  NE
 W  C   E
SW  S  SE
```

`LandscapeTopology` превращает neighbourhood в 8-bit mask. Для diagonals действует corner gating: diagonal учитывается только если присутствуют оба соседних cardinal направления.

Это позволяет выражать:

- outer edges;
- outer corners;
- inner corners;
- произвольные outlines плато

без смещения render-grid на половину клетки относительно simulation-grid.

Generator создаёт четыре детерминированных визуальных варианта для каждой topology. XYZ клетки стабильно выбирает variant, поэтому детали поверхности не мерцают между frames.

### Грани возвышенности

Отдельный `CliffShape` только ради графики не вводится.

Визуальные exposed earth/cliff edges генерируются из отсутствия same-level support terrain вокруг текущей поверхности. Это presentation-derived edge; authoritative vertical topology по-прежнему задают Terrain + Shape + Navigation.

North/west и south/east faces получают различную обработку light/shadow, чтобы высота читалась в полном top-down без pseudo-isometric displacement.

### Ramp art

Ramp semantics по-прежнему полностью принадлежат `RampShape`.

Procedural pack генерирует **четыре отдельные orientation Ramp**:

```text
+X
-X
+Y
-Y
```

Они не получаются простым поворотом одного готового sprite. Geometry симметрична, но lighting остаётся фиксированным в world space. Поэтому highlight/shadow не поворачиваются вместе с ramp.

В normal view направление подъёма должно читаться из самой сгенерированной terrain graphics. `F3` остаётся опциональным диагностическим overlay направления Shape.

## Rendering и debug UI

Terrain мира рисуется через `SpriteBatch`. `ShapeRenderer` остаётся для намеренно primitive/debug информации: текущих object markers, selection и diagnostic arrows.

Управление:

```text
Space          run / pause
N              один simulation tick в pause
PageUp/Down    selected standing Z
WASD           pan camera
mouse wheel    zoom
G              grid: off / subtle / debug
F2             Navigation transition overlay
F3             Shape direction overlay
F4             lower-Z context
```

HUD рисуется в непрозрачных screen-space panels вместо голого текста поверх карты. Cell inspector показывает XYZ, наличие support, Shape и количество исходящих transitions. Object inspector показывает id, definition id и authoritative XYZ.

## Demo / acceptance world

`VisualizerDemoWorld` — presentation-owned deterministic setup content, построенный через `SimulationAssembly`.

Текущая сцена содержит:

- широкую meadow на standing `Z=0`;
- irregular plateau на standing `Z=1` для проверки обычных edges и inner/outer corner cases;
- по одному настоящему Ramp **с каждой из четырёх сторон плато**;
- небольшую terrace на standing `Z=2`;
- ещё один настоящий Ramp между `Z=1` и `Z=2`;
- slow и fast mover на основном плато.

Четыре first-level Ramp — это acceptance coverage всех cardinal orientations, а не декоративные копии. Headless tests проверяют их реальные Navigation transitions.

Вторая terrace доказывает, что presentation не захардкожен под единственный перепад высоты.

Оба mover на tick zero начинают одинаковый по стоимости adjacent `MoveStep`. Fast mover завершает его через 2 ticks, slow mover — через 8 ticks. Тем самым presentation наблюдает authoritative timed Movement, а не анимирует собственное состояние.

## Почему поиск внешнего landscape tileset заморожен

Эксперименты с Minifantasy и Beast Pixels помогли выявить нужную presentation boundary, но также показали ключевую проблему: внешний pack легко может не содержать visual primitive для реальной arbitrary Z topology EvoForge или использовать другую terrain grammar.

Поэтому на текущем этапе разработки landscape visuals используют canonical procedural pack. Это убирает asset licensing/setup friction и позволяет topology определять graphics, а не наоборот.

Presentation boundary всё равно позволяет позднее подключить external или hand-authored art. Procedural generation — текущий canonical development visual language, а не новая simulation dependency.

## Отложенные visual decisions

Следующие идеи намеренно зафиксированы, но **не реализуются**, пока не появится реальный consumer:

- дополнительные procedural materials: dirt, stone, sand, snow, water;
- priority/layered transitions между несколькими terrain materials;
- альтернативные procedural palettes/styles;
- крупные anchored sprites для trees, animals, buildings и equipment;
- external или hand-authored visual packs за той же presentation boundary;
- dual-grid / marching-squares selector для будущего pack, которому он действительно понадобится;
- cached/dirty visual tiles или chunk presentation storage после измеренного renderer cost;
- несколько lower Z layers и более сильные z-fog/cutaway modes;
- tooling для export generated atlas и отдельного визуального анализа;
- более богатые shadow/compositing passes;
- procedural characters/objects только при появлении первого реального gameplay consumer.

Хранение этих решений в документации вместо преждевременной реализации сохраняет extension knowledge без dormant infrastructure.

## Performance boundary

Renderer по-прежнему обходит только camera-visible XY range и не сканирует весь object repository.

Terrain/Geometry reads остаются на текущем sparse-storage path. Autotile topology пока вычисляется для видимых клеток, а не кешируется. Любые dirty caches, chunk render storage или packed-coordinate optimization должны появиться только после JFR/representative world measurements.

## Следующие simulation consumers

Visualizer теперь должен быть достаточно устойчивым наблюдателем для следующих milestones:

```text
Occupancy
    ↓
Pathfinder
    ↓
observable action outcome
    ↓
first agent / Cow vertical slice
```

Эти системы могут добавлять debug overlays, но каждая остаётся authoritative в своём simulation subsystem. Presentation остаётся observer.
