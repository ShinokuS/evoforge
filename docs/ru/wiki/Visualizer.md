# Z-level Visualizer и процедурный ландшафт

Visualizer EvoForge — **debug-наблюдатель за реальной симуляцией**, а не вторая модель мира и не владелец simulation state.

Его presentation contract — **полный top-down с горизонтальным Z-срезом, видимость которого выводится из геометрии мира**. Landscape art генерируется самим EvoForge при запуске вместо зависимости от внешнего tileset.

## Граница модулей

```text
simulation  pure Java, authoritative и headless
    ↑
core        libGDX presentation/debug visualizer
    ↑
lwjgl3      desktop launcher
```

`core` зависит от `simulation`. В simulation нет pixels, palettes, cutaway rules или camera visibility state.

`SimulationAssembly.start()` возвращает runtime, а visualizer получает только read-only capabilities:

```text
SimulationView
SimulationTime
SimulationStepper
```

`SimulationView` предоставляет `TerrainExtentLookup` — универсальный read-only факт `minZ/maxZ` о реально существующем terrain — и `TerrainRevisionLookup`, монотонную read-only версию состояния terrain. Это не presentation policy: extents позволяют узнать, где terrain действительно заканчивается, а revision позволяет derived read-cache доказать, что его исходное authoritative state не изменилось.

## Selected Z — горизонтальный срез

Z visualizer — **standing/navigation plane**. При текущем Shape support law terrain, поддерживающий standing position `(x,y,z)`, anchored в `(x,y,z-1)`.

Для каждого видимого XY `LandscapeSliceResolver` применяет строгий порядок:

```text
terrain на selected Z       -> SOLID_BODY
terrain на selected Z - 1   -> CURRENT_SURFACE
открытая колонка ниже       -> ближайший LOWER_SURFACE в debug-depth
иначе                       -> EMPTY
```

Поэтому гора не исчезает, когда selected plane опускается ниже её вершины. Terrain cells пересекают горизонтальный cut и становятся solid cut material.

Lower terrain виден только через действительно открытый volume. Более близкая solid/opaque cell закрывает обзор на всё, что находится глубже.

## Видимость зависит от геометрии, а не от абсолютного Z

У renderer нет правил вроде `Z < 0 значит cave` или `Z > 0 значит outside`.

Вместо этого отдельно определяется **что рисовать** и **в каком геометрическом контексте это находится**.

Resolved cell содержит presentation measurements:

```text
bodyDepth         сколько consecutive solid terrain находится над cut-body cell
dropDepth         расстояние вниз до видимой lower surface
ceilingDistance   высота открытого пространства до ближайшего opaque cover
coverDepth        толщина первого consecutive opaque cover
exposureDistance  расстояние через open volume до sky-connected exterior air
```

Эти величины независимы. Высокая cavern может иметь далёкий ceiling и одновременно находиться глубоко внутри горы. Глубокая шахта может иметь большой drop и при этом быть напрямую открыта небу.

### Асимметричный язык глубины

Обзор вниз с возвышенности не приравнивается к нахождению под толщей материала.

- **drop depth** постепенно угасает к цвету фона: ближайший lower terrain остаётся читаемым, а очень глубокие одиночные клетки перестают конкурировать с активным срезом;
- **cover/body depth** затемняются значительно сильнее;
- оба эффекта сохраняют исходную цветовую семью terrain вместо переключения на отдельный cave/cut hue;
- достаточно глубокие области могут почти сливаться с background и терять мелкую texture, не становясь другим simulation material.

Это presentation cue, а не simulation освещения.

## Exposure открытого объёма

`VisibilityVolumeLookup` — presentation-side геометрический контракт, который потребляет cutaway resolver:

```text
solid(x,y,z)
opaque(x,y,z)
min/max occupied Z
revision()
```

Текущий adapter `TerrainVisibilityVolume` трактует authoritative terrain как solid и opaque и прокидывает authoritative terrain revision. Будущий composite volume должен возвращать revision только если способен гарантировать, что версия учитывает всех contributors; отрицательное значение намеренно отключает повторное использование cache вместо риска stale visibility.

`LandscapeSliceResolver.Analysis` строит один bounded camera-local exposure field. Сначала источниками становятся air cells, которые действительно находятся выше верхнего opaque volume своей колонки, затем выполняется 6-neighbour flood/BFS только через open volume.

Получившееся кратчайшее расстояние через открытое пространство означает:

- exposed field имеет exposure distance `0`;
- cave mouth получает exposure сбоку без искусственной дыры в roof;
- клетки глубже в cave постепенно темнеют;
- sealed chamber не имеет пути к exterior и достигает насыщенного тёмного состояния;
- настоящий vertical opening открывает cavern под ним;
- высокие по вертикали cavern работают корректно, потому что `ceilingDistance` измеряется отдельно от horizontal exposure.

Текущий visual exposure horizon — `12` cells. После этого darkness saturates. Это **не** означает, что cave перестаёт существовать или simulation visibility ограничена 12 клетками; предел нужен только для bounded presentation flood и стабильного вида глубоких interiors.

## Будущие objects, roofs и terrain materials

Cutaway algorithm намеренно не содержит веток по именам `mountain`, `cave`, `wall` или `house`.

Будущая structure/object сможет участвовать в том же volume contract:

```text
terrain
future walls / roofs / large objects
        ↓
presentation volume adapter/composition
        ↓
VisibilityVolumeLookup
        ↓
неизменившийся cutaway resolver
```

Тест уже подставляет non-terrain solid/opaque contributor и проверяет, что он блокирует sight line на lower terrain, не становясь terrain.

Так первая реализация остаётся extensible без преждевременного simulation-wide Visibility subsystem. Если будущим gameplay consumers — actor line-of-sight, projectiles, lighting, smoke или glass — понадобится authoritative opacity/transmission, именно они должны обосновать такой simulation capability. Camera cutaway exposure — **не actor perception**.

Сегодня достаточно binary `solid/opaque`. Partial transmission для glass, water, smoke или foliage остаётся extension point, а не speculative API.

## Горы, пещеры и шахты

Mountains, cave rooms и shafts являются обычной геометрией этой модели.

Cave внутри горы строится из:

```text
solid terrain body вокруг
open cells для mouth/chamber
real lower terrain как floor
real terrain выше как roof/cover
```

На higher slice roof/body остаётся видимым; interior не подсвечивается магически через него. На уровне самой cave side mouth даёт exterior exposure, а chamber темнеет с реальным расстоянием через open air.

Cave под плоским terrain использует те же правила. Acceptance world содержит отдельную cavern с walls, flat cap и одним настоящим vertical opening. Объём под отверстием становится exposed потому, что геометрия реально открыта, а не потому, что renderer распознал специальный cave case.

Deep shaft аналогично состоит из действительно отсутствующих intermediate terrain cells и floor на несколько Z ниже.

## Процедурный visual language

```text
SimulationView
    ↓
TerrainVisibilityVolume
    ↓
LandscapeSliceResolver.Analysis
    ↓ role + depth/exposure context
LandscapeRenderer
    ├── surface/ramp -> ProceduralLandscapePack
    └── solid cut    -> ProceduralSliceArt
```

`LandscapeTopology` предоставляет normalized 8-neighbour masks и deterministic XYZ variants.

### Native profile

```text
logical simulation cell = 1 x 1
native procedural cell   = 16 x 16 pixels
```

Generated textures используют `Nearest` filtering и duplicate padding. Source PNG, JSON descriptor, TexturePacker step и установка third-party assets не нужны.

### Surface art

Surface pack владеет grass, edge/corner variation и всеми четырьмя cardinal Ramp orientations. Visual variants deterministic по XYZ, поэтому детали не мерцают между frames.

### Solid cut art

`ProceduralSliceArt` использует сдержанную затенённую palette, близкую к земле/растительности, а не отдельный грязно-чёрный visual language. `bodyDepth` постепенно убирает яркость и detail, при этом первые несколько cut layers остаются визуально связаны с поверхностью выше.

### Единая стилистика Ramp

У Ramp существует один procedural visual language.

Один и тот же generated Ramp region используется во всех случаях, когда Ramp видим, в том числе если его terrain cell пересекает нижний horizontal cut. Renderer меняет только environmental depth tint; отдельного ramp-cut sprite и upper descent marker больше нет.

Authoritative Ramp semantics полностью остаются в `RampShape` и Navigation.

## Периметр активного standing-Z

Текущая выбранная standing surface получает presentation-only perimeter cue, чтобы активный Z не приходилось определять только по оттенкам глубины.

Обводится только **внешняя cardinal-граница** `CURRENT_SURFACE`. Если две соседние клетки обе принадлежат активной поверхности, между ними линия не появляется, поэтому это не превращается в дополнительную сетку.

Cue состоит из светлого малонасыщенного rim со стороны активного слоя и узкой тёмной линии сразу снаружи. Толщина выводится из world-units-per-screen-pixel текущей камеры, поэтому contour остаётся примерно однопиксельным при разном zoom.

`LandscapeSliceResolver.isCurrentSurface(...)` владеет дешёвым structural query для этого overlay. Контур не запускает повторный exposure BFS и не добавляет нового simulation state или terrain type.

## Управление и inspector

```text
Space          run / pause
N              один simulation tick в pause
PageUp/Down    перемещение horizontal standing-Z cut
WASD           pan camera
mouse wheel    zoom
G              grid: off / subtle / debug
F2             Navigation transition overlay
F3             Ramp direction diagnostics
F4             lower visibility depth: 0 / 1 / 4 / 8
```

Click всегда адресует `(x,y,selectedZ)`. Видимый через shaft lower floor не меняет input Z автоматически.

В status HUD теперь также показывается текущий FPS. Cell inspector показывает slice role и геометрический context: body depth, drop depth, ceiling distance, cover depth и exposure distance там, где они применимы.

Objects пока остаются намеренно primitive current-Z debug markers. Их будущий art/occlusion должен потреблять тот же presentation context, а не заставлять terrain resolver знать object definitions.

## Z / cave torture world

`VisualizerDemoWorld` намеренно объединяет ситуации, которые нагружают контракт:

- broad lower meadow;
- irregular base plateau на standing `Z=1`;
- четыре настоящих cardinal base Ramps;
- stacked mountain и последовательные local Ramps до standing `Z=4`;
- mountain cave с side mouth, chamber, real floor и real roof;
- отдельная cavern под flat cap;
- настоящий vertical opening в эту cavern;
- high cliff без graphics-only `CliffShape`;
- deep open shaft с floor на несколько elevations ниже;
- slow/fast movers с authoritative Movement на 8/2 ticks.

Headless tests покрывают terrain extent/revision lifecycle, horizontal-cut priority, дешёвый current-surface query для perimeter, body/drop/cover/ceiling measurements, exposure-distance от side mouth, sealed chambers, tall caverns, future non-terrain occluders, Ramp topology и Movement timing.

Manual acceptance следует проводить на нескольких relevant Z и проверять, что структура мира остаётся понятной без полного прозрачного upper floor.

## Performance boundary

Производительность рассматривается как development invariant, а не как уборка только перед релизом: сначала добавляем дешёвую telemetry, затем оптимизируем доказанный hot-path без изменения simulation semantics.

Первый profiling-pass visualizer показал две конкретные стоимости:

- camera-local 3D exposure BFS перестраивался каждый frame и представлял каждую посещённую клетку через `HashMap<Position, Integer>` и queue-объекты;
- sparse coordinate reads создавали временный key-record при каждом terrain, geometry-override и cell-object lookup.

Текущая реализация поэтому:

- хранит exposure distance в плотном primitive `int[]` volume и использует `int[]` BFS queue, устраняя per-node объекты BFS;
- держит один padded `LandscapeSliceResolver.Analysis` вокруг видимого диапазона камеры и повторно использует его, пока камера остаётся внутри этого окна;
- инвалидирует analysis при изменении selected Z, lower-depth policy или versioned visibility volume;
- использует монотонный authoritative terrain revision для текущего terrain-backed volume;
- использует reusable non-stored coordinate probes в read-hot sparse-map lookups, поэтому чтение больше не создаёт coordinate key каждый раз;
- оставляет active-Z perimeter на прямых membership checks и никогда не перестраивает exposure analysis ради outline.

`LandscapeRenderer` примерно раз в секунду печатает строку `VisualizerPerf` со значениями:

```text
fps
число видимых клеток
landscape average / maximum CPU time
analysis average / maximum CPU time
analysis cache hit / miss count
```

Эта telemetry намеренно лёгкая и остаётся доступной по мере развития visualizer. При regression сначала следует локализовать проблему этими числами, а уже затем вводить chunks, dirty regions или более широкие caches.

### Pixel-stable движение камеры

Процедурный landscape использует `Nearest` filtering, поэтому continuously moving orthographic camera не должна передавать в renderer произвольные sub-screen-pixel смещения. На большом zoom-out это меняет фазу nearest-sampling сразу для всей картинки и визуально может выглядеть как stutter даже при стабильном frame time.

`ZLevelVisualizer` поэтому разделяет две позиции камеры:

- непрерывный logical pan target, который WASD обновляет frame-time-independent движением;
- render position, которая снапится так, чтобы край viewport двигался целыми screen-pixel шагами для текущего zoom и размера окна.

Logical target не квантуется, поэтому input не накапливает rounding error. Pixel alignment применяется только к presentation. `CameraPixelSnap` содержит чистую тестируемую математику и не влияет на simulation coordinates, selection semantics или visibility caches.

Terrain extent поддерживается инкрементально внутри `TerrainSystem`; visualizer не придумывает arbitrary maximum Z.

Текущий adapter всё ещё проходит occupied terrain Z extent при перестроении exposure field, чтобы найти top opaque cell конкретной колонки. Если representative deep/sparse world покажет значимое время именно в `analysis max`, оптимизация должна появиться **за существующей volume boundary** — например per-column top query или другая измеренная representation. Семантика видимости ради representation optimization не меняется.

## Отложенные presentation decisions

Намеренно остаются deferred:

- explicit adjacent-layer X-ray/build mode;
- real roofs/structures/large objects как contributors volume после появления этих consumers;
- partial optical transmission для glass/water/smoke/foliage;
- authoritative actor LOS и lighting systems;
- дополнительные procedural materials и material transitions;
- альтернативные visual styles и hand-authored packs;
- крупные sprites для creatures, trees, equipment и buildings;
- richer shadows/compositing;
- generated-atlas export tooling;
- dirty/chunk visual caches до profiling evidence.

## Следующие simulation consumers

Visualizer остаётся non-authoritative observer, пока simulation движется к:

```text
Occupancy
    ↓
Pathfinder
    ↓
observable action outcome
    ↓
first agent / Cow vertical slice
```

Эти системы могут получать diagnostics, но renderer visibility никогда не определяет simulation truth.
