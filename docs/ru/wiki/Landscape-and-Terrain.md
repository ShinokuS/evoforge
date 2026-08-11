# Landscape и Terrain

Landscape представляет environmental content, адресуемый координатами мира. Terrain — текущий реализованный base landscape owner.

## Базовое представление

```text
XYZ -> LandscapeDefinitionId | absence
```

Terrain cell хранит landscape definition identity. Absence означает, что terrain по coordinate отсутствует.

## Absence — не definition

EvoForge намеренно не использует специальные content definitions:

```text
core:air
core:empty
core:open
```

для обычного отсутствия.

Если coordinate имеет `LandscapeDefinitionId`, там действительно существует landscape content.

Будущая loaded/unloaded distinction может потребовать richer read result, но это другое, чем “empty terrain material”.

## `TerrainSystem`

`TerrainSystem` — authoritative mutation owner terrain content. Consumers читают через `TerrainLookup`.

Storage делегируется границе `TerrainStorage`, чтобы chunking/packing позже менялись без изменения ordinary terrain consumers.

## `TerrainLookup`

Текущая семантика:

```text
LandscapeDefinitionId   terrain present
null                    terrain absent
```

`contains` выводится из `find`, а не является вторым source of state.

## `TerrainStorage`

`TerrainStorage` — implementation boundary, не domain promise о представлении terrain.

Текущий `SparseTerrainStorage` подходит для фундамента и tests. Region/chunk storage заменит его, когда появятся world-generation/persistence requirements.

## Landscape definitions

Terrain хранит typed `LandscapeDefinitionId`, скомпилированные из composition-driven landscape definitions.

Material identity и geometry разделены:

```text
LandscapeDefinitionId  -> что это за terrain content/material
Shape                  -> какая у него local geometry/topology
```

Разные landscape definitions могут иметь одинаковую geometry. Material может получить custom geometry override без изменения identity.

## Направление dependency Geometry

`GeometrySystem` читает `TerrainLookup`.

```text
TerrainSystem
    ↑ read boundary
GeometrySystem
```

Terrain не зависит от Geometry, поэтому base content storage не накапливает все mechanics поверх terrain.

Present terrain без override -> `FullShape.INSTANCE`; absent terrain -> no Shape.

## Видимость мутаций

Поскольку Geometry и Navigation сейчас не имеют persistent cache, terrain mutation видна на следующем query:

```text
Terrain mutation
    ↓
TerrainLookup
    ↓
GeometryLookup
    ↓
NavigationLookup
```

Будущие caches должны сохранить ту же semantic visibility через correct invalidation/revision handling.

## Landscape — не `WorldObject`

Миллионы terrain coordinates как `WorldObject` навязали бы object identity/lifetime overhead и загрязнили object spatial indexes.

Landscape остаётся отдельным domain, несмотря на общий XYZ.

## Будущие environmental mechanics

Water, temperature, weather, soil moisture, light, contamination и другое состояние обычно получают собственных specialized owners, а не поля universal terrain cell.

Физическое storage нескольких mechanics позже может быть co-located ради performance, но semantic ownership остаётся раздельным.

## Chunking и regions

Chunk dimensions пока не зафиксированы. Chunk/region concepts позже обслужат:

```text
spatial storage
world generation
loading/unloading
persistence boundaries
activation boundaries
cache locality
```

Их надо проектировать вместе, а не выбирать chunk size сейчас ради одной sparse map.

## Loaded и absent

Текущий `null` означает absent terrain. Streaming world может потребовать:

```text
PRESENT
ABSENT
UNLOADED / UNKNOWN
```

Distinction вводится явно: трактовать unloaded terrain как true empty space опасно для geometry/navigation.

## Тестирование

Terrain tests покрывают placement/removal, lookup semantics, storage behavior, definition ids и integration в World/Geometry/Navigation. Будущее region storage должно проходить те же semantic tests независимо от data structure.
