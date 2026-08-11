# Landscape и Terrain

Landscape представляет environmental content, адресуемый координатами мира. Terrain — current implemented base landscape owner.

## Базовое представление

```text
XYZ -> LandscapeDefinitionId | absence
```

Terrain cell хранит landscape definition identity. Absence означает, что terrain по coordinate отсутствует.

## Absence — не definition

EvoForge намеренно не использует специальные content definitions `core:air`, `core:empty` или `core:open` для ordinary absence.

Если coordinate имеет `LandscapeDefinitionId`, там действительно существует landscape content.

Future loaded/unloaded distinction может потребовать richer read result, но это не то же самое, что вводить «empty terrain material».

## `TerrainSystem`

`TerrainSystem` — authoritative owner terrain storage и terrain-specific mutation invariants. Consumers читают через `TerrainLookup`.

Storage делегируется `TerrainStorage`, чтобы chunking/packing позже менялись без изменения ordinary consumers.

Mutation methods возвращают structured results:

```text
place occupied -> POSITION_OCCUPIED
replace absent -> TERRAIN_ABSENT
remove absent  -> TERRAIN_ABSENT
```

Это normal world-state conflicts. Invalid/null definition ids остаются programming/configuration errors.

## `LandscapeMutations`

Terrain и Geometry — separate authoritative owners, но lifetime terrain cell имеет geometry consequences. Public coordinated write capability — `LandscapeMutations`, current implementation — `LandscapeSystem`.

```text
external Command handler ─┐
world generation ─────────┤
erosion / internal Action ┤
                          v
                 LandscapeMutations
                    /           \
             TerrainSystem   GeometrySystem
```

Так external commands и internal producers получают одинаковую lifecycle semantics без forced internal Commands.

Current policy:

```text
placeTerrain
    -> create only when empty
    -> clear stale geometry override
    -> default geometry FullShape

replaceTerrain
    -> change existing definition
    -> preserve geometry override

removeTerrain
    -> remove terrain
    -> clear geometry override
```

Custom Shape принадлежит lifetime terrain cell и не переживает remove/re-place в том же XYZ.

## Structured results

Terrain results реализуют `OperationResult`:

```text
accepted
namespaced ResultCode
```

Примеры:

```text
terrain:placed
terrain:position_occupied
terrain:terrain_absent
```

Internal producer, для которого rejection нарушает его собственный invariant, использует:

```java
OperationResults.requireAccepted(
        landscape.placeTerrain(...));
```

## `TerrainLookup` и storage

Current lookup:

```text
LandscapeDefinitionId   terrain present
null                    terrain absent
```

`TerrainStorage` — implementation boundary. Current `SparseTerrainStorage` подходит для foundation/tests; future chunk/region storage может заменить его без изменения semantic contract.

## Landscape definitions

Terrain cell хранит только `LandscapeDefinitionId`. Material identity, geometry и mechanic-specific material data разделены:

```text
LandscapeDefinitionId
    -> content/material identity

Shape
    -> local geometry/topology + intrinsic local traversal factor

LandscapeTraversalDefinitions
    -> actor-independent SurfaceTraversalCost material
```

Одинаковая Shape может использоваться разными materials, а geometry override не меняет material identity.

### Aspect `traversal`

Current Movement pricing использует landscape aspect:

```json
{
  "key": "core:granite",
  "aspects": {
    "traversal": {
      "cost": 1000
    }
  }
}
```

Compilation:

```text
LandscapeDefinitionId -> SurfaceTraversalCost
```

`traversal.cost` — positive integer; `1000` — current neutral baseline.

Cost не дублируется per-cell: terrain cell уже ссылается на definition, а `TransitionCostCalculator` читает compiled traversal data.

Это actor-independent intrinsic surface price. Он не создаёт structural edge и не кодирует species/locomotion affinity.

Для valid directed Movement edge cost-model объединяет обе supporting terrain cells, departure/arrival factors их Shapes и grid direction length. Missing traversal data для support definition — broken configuration, а не silent fallback.

Подробнее: [Definitions](Definitions.md) и [Movement System](Movement-System.md).

## Dependency Geometry

`GeometrySystem` читает `TerrainLookup`:

```text
TerrainSystem
    ↑ read boundary
GeometrySystem
```

Terrain не зависит от Geometry. Cross-owner lifecycle coordination выполняет `LandscapeSystem` сверху.

Present terrain без override -> `FullShape.INSTANCE`; absent terrain -> no Shape.

## Landscape, Navigation и Movement

Current chain:

```text
TerrainLookup
    ↓
GeometryLookup
    ↓
NavigationLookup
        structural edge exists?

TerrainLookup + GeometryLookup
    ↓
TransitionCostLookup
        intrinsic price of already-valid edge

Movement
    ↓
MovementRate + timing carry
    ↓
Scheduler
    ↓
completion revalidation
```

Navigation намеренно не знает material cost. Low traversal cost не способен создать missing topology.

## Видимость mutations

Поскольку Geometry/Navigation current implementation не скрывают state за persistent cache, landscape mutation видна на next query.

Sleeping Movement Action при mutation не просыпается immediately; `MovementActionProcessor` увидит changed Navigation на scheduled completion и не выполнит stale Spatial commit.

Future caches/events должны сохранить semantic correctness, даже если recomputation станет другой.

## Landscape — не `WorldObject`

Terrain coordinates не превращаются в миллионы `WorldObject`: это навязало бы object identity/lifetime overhead и загрязнило Spatial indexes.

Landscape остаётся separate domain при общем XYZ address space.

## Future environmental mechanics

Water, temperature, weather, soil moisture, light, contamination и другие environment properties обычно получают specialized owners, а не поля universal terrain cell.

Physical storage позже может быть co-located ради performance, но semantic ownership остаётся отдельным.

## Chunking / regions / loaded state

Chunk dimensions пока не fixed. Chunk/region concepts позже будут связаны со storage, generation, loading, persistence, activation и cache locality и должны проектироваться вместе.

Current `null` означает absent terrain. Streaming world может потребовать:

```text
PRESENT
ABSENT
UNLOADED / UNKNOWN
```

Нельзя автоматически трактовать unloaded как empty space, иначе Geometry/Navigation/Movement могут получать false semantics.

## Тестирование

Terrain/Landscape tests покрывают structured mutations, lookup/storage, definition ids, geometry lifecycle и World/Geometry/Navigation integration.

Movement/Traversal tests дополнительно проверяют, что `traversal.cost` supporting landscape definitions реально меняет authoritative movement duration, а removal support terrain во время timed action предотвращает stale completion commit.

Future region storage обязано проходить те же semantic tests независимо от internal data structure.
