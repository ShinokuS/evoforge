# Z-level Visualizer и процедурный ландшафт

Visualizer EvoForge — это **debug-наблюдатель за реальной симуляцией**, а не вторая модель мира и не владелец simulation state.

Текущий presentation contract — **полный top-down с настоящим горизонтальным Z-срезом**. Landscape art генерируется самим EvoForge при запуске вместо зависимости от внешнего tileset.

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

## Selected Z — это горизонтальный срез

Z визуализатора — **standing/navigation plane**, потому что именно в этих координатах живут Spatial objects и Navigation transitions.

При текущем supported-position law terrain/Shape, поддерживающий standing position `(x,y,z)`, anchored в:

```text
(x, y, z - 1)
```

Renderer больше не трактует выбранный уровень как изолированный floor с прозрачным floor ниже. Для каждого видимого XY выполняется строгий порядок:

```text
1. terrain anchored на selected Z
      -> SOLID BODY, пересекающий текущий срез

2. иначе terrain anchored на selected Z - 1
      -> CURRENT SURFACE, поддерживающий этот standing plane

3. иначе поиск вниз по открытой колонке
      -> ближайший LOWER SURFACE в пределах заданной глубины

4. иначе
      -> EMPTY
```

Этот контракт реализован в `LandscapeSliceResolver` и тестируется headless независимо от libGDX.

### Почему solid body имеет приоритет

Верхнее плато не должно исчезать, когда пользователь переключается на один Z ниже.

```text
standing Z=1      grass top
                  ─────────
terrain Z=0       █████████  solid material
standing Z=0      горизонтальный срез через этот материал
terrain Z=-1      lower support
```

При selected `Z=1` terrain на `0` рисуется как текущая walkable surface.

При selected `Z=0` этот же terrain не становится ghost-floor сверху. Он занимает текущий spatial slice и рисуется как процедурный earth/rock cut.

Поэтому footprint горы естественно меняется при движении среза вверх/вниз.

## Открытые колонки и lower depth

Нижний контекст виден только через действительно открытую вертикальную колонку.

Если нет ни solid body на selected Z, ни support на selected Z-1, renderer ищет вниз ближайшую поверхность. Первая найденная поверхность рисуется с затемнением, зависящим от глубины.

Текущие debug-варианты:

```text
0   только текущий срез
1   один lower standing elevation
4   до четырёх lower standing elevations
```

`F4` циклически переключает эти значения. По умолчанию используется `4`, потому что lower surfaces появляются только в holes/open space и не засоряют обычный solid terrain.

Это позволяет читать:

- pits;
- shafts;
- deep cliffs;
- openings между уровнями;
- cave voids с floor ниже.

Для одного XY renderer не рисует сразу несколько нижних поверхностей. Показывается **ближайшая видимая lower surface**, потому что любой более близкий terrain закрывает sight line.

## В normal view нет полного ghost-above layer

Обычный режим намеренно **не накладывает целиком `Z+1` как прозрачный floor**.

Одновременное смешение двух похожих grass/floor textures ухудшает читаемость обоих уровней. Верхняя структура проявляется тем, что реально пересекает текущий горизонтальный срез:

```text
terrain body на selected Z -> видимый solid material
vertical connector          -> специальный presentation cue
upper floor вне среза        -> не рисуется
```

Позднее можно добавить отдельный construction/debug X-ray, но это будет самостоятельный режим, а не normal Z language.

## Горы и пещеры

Один и тот же slice contract покрывает и mountains, и underground.

Stacked mountain:

```text
terrain Z=3        ██ summit body
terrain Z=2      ██████
terrain Z=1    █████████
terrain Z=0  █████████████
```

при `PageUp/PageDown` выглядит как разные solid footprints одного массива.

Cave — это open region внутри body layer при сохранённом lower terrain как floor:

```text
selected Z=1

████████████████   terrain body на Z=1
██              ██
██    cave      ██   body на Z=1 отсутствует
██              ██   terrain на Z=0 становится floor
████████████████
```

Отдельные `CaveShape`, `MountainShape` или visual-only simulation state не нужны.

Будущий ceiling/covered indicator может оказаться полезным для interior gameplay, но текущий renderer намеренно не придумывает entity-height/roof semantics, которых ещё нет в simulation.

## Контракт процедурной визуализации

Generated presentation теперь разделена по смыслу:

```text
SimulationView
    ↓
LandscapeSliceResolver
    ↓ horizontal-cut role
LandscapeRenderer
    ├── surface role -> ProceduralLandscapePack
    └── cut-body role -> ProceduralSliceArt
```

`LandscapeTopology` предоставляет обеим веткам normalized 8-neighbour topology и детерминированный XYZ visual variant.

Simulation по-прежнему владеет только тем, что существует и как себя ведёт. Presentation владеет pixels, palette, shading и derived visual cues.

### Native pixel profile

```text
logical world cell = 1 x 1 simulation unit
native visual cell = 16 x 16 source pixels
```

16 pixels — исключительно presentation value. Zoom меняет screen size без изменения simulation coordinates.

### Палитра

`EvoForgePalette` задаёт общий grass/earth language для surfaces, cliffs и cut-body graphics. Ограниченные highlight/shadow/outline rules сохраняют единый стиль independently generated tiles.

### In-memory atlases

Generated art строится через libGDX `Pixmap` при старте visualizer и превращается в `TextureRegion` с `Nearest` filtering и однопиксельным duplicate padding.

Source PNG, JSON visual descriptor, TexturePacker step и сторонняя установка assets не нужны.

## Cell-aligned topology

Для terrain art renderer читает восемь соседей:

```text
NW  N  NE
 W  C   E
SW  S  SE
```

`LandscapeTopology` использует 8-bit mask с diagonal corner gating: diagonal учитывается только если присутствуют оба соседних cardinal направления.

Одна topology используется по-разному в зависимости от slice role:

- current/lower surfaces генерируют grass edges и exposed earth lips;
- solid-body cells генерируют earth/rock cut boundaries вокруг open space, включая cave walls;
- arbitrary plateau/cave outlines остаются точно aligned с simulation cells.

Четыре детерминированных XYZ-selected variants уменьшают заметное повторение без frame-to-frame randomness.

## Отображение Ramp между Z

Ramp semantics полностью принадлежат `RampShape`.

Procedural surface pack генерирует четыре независимо освещённые orientation:

```text
+X
-X
+Y
-Y
```

Horizontal-cut model даёт одному authoritative Ramp несколько presentation views без duplicate Shapes в simulation.

### Ramp на своём supported standing plane

Когда terrain Ramp находится на `selected Z - 1`, показывается обычный generated slope.

### Ramp body в lower cut

Когда terrain Ramp anchored непосредственно на `selected Z`, `ProceduralSliceArt` рисует directional ramp-cut внутри solid material. Так connector остаётся читаемым с нижнего среза, но не притворяется walkable surface на этом lower standing plane.

### Маркер спуска на upper landing

На supported standing plane верхний landing получает маленький presentation-only descent mouth, направленный обратно к Ramp.

Маркер **derived** из единственного authoritative `RampShape`. Это не второй Shape, object или Navigation edge.

`F3` по-прежнему может явно показывать direction arrows и для current surface ramps, и для ramp bodies, пересекающих выбранный cut.

## Rendering и debug UI

Terrain рисуется через `SpriteBatch`. `ShapeRenderer` остаётся для намеренно primitive/debug информации: objects, selection, grid и diagnostic overlays.

Управление:

```text
Space          run / pause
N              один simulation tick в pause
PageUp/Down    перемещение горизонтального standing-Z cut
WASD           pan camera
mouse wheel    zoom
G              grid: off / subtle / debug
F2             Navigation transition overlay
F3             Ramp/Shape direction overlay
F4             lower visibility depth: 0 / 1 / 4
```

Click всегда адресует `(x,y,selectedZ)`. Lower surface, видимая через shaft, не становится автоматически clicked Z.

Inspector показывает resolved slice role (`SOLID BODY`, `SURFACE`, `LOWER depth N`, `EMPTY`), source terrain Z, Shape и outgoing transitions.

## Z stress / acceptance world

`VisualizerDemoWorld` — deterministic presentation-owned setup через настоящий `SimulationAssembly`.

Сцена теперь специально нагружает Z language, а не показывает только одно плато:

- broad lower meadow;
- irregular base plateau на standing `Z=1`;
- четыре real cardinal base ramps;
- mountain body, stacked через несколько terrain Z;
- west-facing cave entrance и chamber, созданные отсутствием body cells;
- cave floor из lower terrain layer;
- high cliff без специального `CliffShape`;
- несколько higher local ramps, образующих длинный multi-Z ascent;
- summit до standing `Z=4`;
- deep open shaft с floor на несколько elevations ниже;
- slow/fast movers, сохраняющие authoritative пример Movement на 2/8 ticks.

Headless tests проверяют:

- все четыре base Ramp orientations;
- Ramp topology на последовательных mountain elevations;
- отсутствие cave body при сохранённом floor;
- gaps deep shaft и lower floor;
- timed Movement;
- slice priority и lower-depth clipping отдельно через `LandscapeSliceResolverTest`.

Manual acceptance нужно проводить, переключая одну и ту же XY-area через `Z=0..4`. Цель — понимать трёхмерную структуру без прозрачного полного upper floor.

## Почему поиск внешнего landscape tileset заморожен

Ранние внешние tileset experiments помогли выявить presentation boundary, но неоднократно задавали terrain grammar, не совпадающую с arbitrary Z topology EvoForge.

На текущем этапе topology поэтому сама генерирует canonical art. External или hand-authored art можно позднее подключить за presentation boundary без изменения simulation semantics.

## Отложенные visual decisions

Зафиксированы, но намеренно не реализованы:

- ceiling/roof/covered-state presentation после появления соответствующей simulation semantics;
- explicit adjacent-layer X-ray/build mode;
- дополнительные materials: dirt, stone, sand, snow, water;
- priority/layered transitions между несколькими materials;
- альтернативные procedural palettes/styles;
- крупные anchored sprites для trees, creatures, buildings и equipment;
- external/hand-authored visual packs за той же boundary;
- dual-grid / marching-squares selection для visual language, которому он реально понадобится;
- richer shadows/compositing;
- generated-atlas/export tooling;
- visual dirty caches/chunk render storage только после profiling.

Текущий renderer не утверждает, что любой будущий art style обязан использовать этот же autotiling. Он фиксирует первые доказанные presentation semantics для Z-world EvoForge.

## Performance boundary

Renderer обходит только camera-visible XY cells. Slice resolution и topology вычисляются on demand через существующие sparse read contracts.

Текущий maximum lower-depth search добавляет до четырёх terrain lookups только для open XY column, а не для каждой обычной solid cell. Caches, packed keys и chunk-local render state должны появляться только после реального profiling.

## Следующие simulation consumers

Visualizer остаётся observer при следующих milestones:

```text
Occupancy
    ↓
Pathfinder
    ↓
observable action outcome
    ↓
first agent / Cow vertical slice
```

Эти системы могут добавлять diagnostics, но presentation остаётся non-authoritative.
