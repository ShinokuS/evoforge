# Geometry System

Geometry — mechanic поверх наличия terrain. Она владеет local Shape overrides и предоставляет `Shape`, представляющий terrain geometry по coordinate.

## Почему Geometry отделена от Terrain

Terrain отвечает:

> Какой landscape content существует в этой XYZ?

Geometry отвечает:

> Какую local structural geometry предоставляет существующий terrain?

Это разные semantics. Granite, soil, wood и metal могут быть full blocks. Один material может использовать ramp или другой Shape через geometry override.

Разделение не даёт structural topology просочиться в material definitions или `TerrainSystem` storage.

## Public read contract

```java
public interface GeometryLookup {
    Shape find(int x, int y, int z);
}
```

Текущая семантика:

```text
terrain absent
    -> null

terrain present, no custom override
    -> FullShape.INSTANCE

terrain present, custom override
    -> custom Shape
```

Consumers не обязаны отдельно спрашивать Terrain только ради факта geometry existence.

## `GeometrySystem`

`GeometrySystem` зависит только от `TerrainLookup` и владеет `GeometryState` для sparse non-default overrides.

Главная ответственность — разрешить три случая выше, оставляя default Full geometry implicit.

## `GeometryState`

Хранятся только non-default Shape overrides.

Мир из ordinary Full terrain не требует explicit Shape reference на каждую cell в geometry layer.

```text
Terrain present + no GeometryState entry
    => FullShape.INSTANCE
```

Custom `RampShape` хранится только там, где default заменён.

## Default geometry — семантика

`FullShape.INSTANCE` как default для present terrain — часть current geometry semantics, а не cache optimization. Present terrain cell является solid Full block, если geometry не overridden.

Definition-driven default Shapes в будущем потребуют explicit contract change.

## Shape context-free

Geometry возвращает `Shape`, но сам Shape не получает `TerrainLookup`, `GeometryLookup`, World context, соседние Shapes или identity moving object.

Navigation вычисляет topology по source coordinates относительно Shape anchor. `TransitionCost` позже спрашивает source-support и destination-support Shape только об их собственных local directed traversal factors после того, как Navigation уже подтвердил edge.

```text
Geometry chooses the Shape instance
Shape declares local topology + intrinsic traversal geometry
Navigation composes topology
TransitionCost combines local Shape factors with landscape surface cost
```

Так Shape implementations не выполняют hidden world scans, а central code не изучает concrete Shape types.

## Shared immutable Shapes

Current production Shapes:

```text
FullShape.INSTANCE
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

Они immutable, зависят только от relative coordinates/orientation и переиспользуются на любом числе anchors.

## Traversal factors Shape

Shape contract теперь кроме topology содержит actor-independent directed traversal characteristics:

```text
departureTraversalFactor(...)
arrivalTraversalFactor(...)
```

Они используют тот же departure/arrival ownership и relative-coordinate law, что `transitionPorts`.

Current fixed-point values:

```text
ShapeTraversalFactor.NONE    = 0
ShapeTraversalFactor.NEUTRAL = 1000
```

Default implementation возвращает `NEUTRAL` только если собственные ports Shape действительно предоставляют requested role; иначе — `NONE`.

Current `FullShape` и cardinal `RampShape` поэтому не требуют movement-specific switch и используют neutral factor для всех owned roles. Future Shape с реальным intrinsic geometry penalty может override только свой local factor без изменений `NavigationSystem` или `TransitionCostCalculator`.

Actor-specific policy вроде wheels vs stairs не является intrinsic Shape geometry и намеренно не кодируется здесь.

Полный role law и formula cost описаны в [Контракте Shape](Shape-Contract.md) и [Movement System](Movement-System.md).

## Solid-cell blocking

`SolidCellBlocking` содержит common obstruction behavior для Full и Ramp terrain bodies. Helper вычисляет local block masks без world context.

Он существует потому, что несколько real Shapes имеют одинаковую solid-volume semantics. Это не requirement к любому future Shape быть solid cube.

## Geometry -> Navigation

Navigation зависит от `GeometryLookup`, а не `TerrainLookup` или internals `GeometrySystem`.

```text
TerrainLookup
    ↓
GeometrySystem / GeometryLookup
    ↓
NavigationSystem
```

Поэтому Navigation не знает mapping terrain identity -> default/custom geometry.

Navigation также отделена от traversal price: она разрешает structural edges, а `TransitionCostCalculator` читает source/destination support Shapes только после существования edge.

## Mutation и lifecycle

Изменение Shape override меняет topology на следующем Navigation query. Persistent Navigation cache пока нет.

Custom override можно установить только там, где terrain существует по current validation.

Lifetime terrain и lifetime geometry override координируются выше обоих low-level owners через `LandscapeSystem` / `LandscapeMutations`:

```text
placeTerrain
    -> successful placement clears stale geometry override
    -> present terrain resolves to default FullShape

replaceTerrain
    -> successful replacement preserves current override

removeTerrain
    -> successful removal clears geometry override
```

Это закрывает прежний stale-override lifecycle gap без reverse dependency `TerrainSystem -> GeometrySystem`.

Non-default Shape принадлежит lifetime текущей terrain cell и не воскресает после remove/re-place в той же coordinate.

## Extension boundary

Новый Shape, compatible с current contract, обычно требует:

```text
new Shape implementation
+ topology tests
+ role-contract tests
+ traversal-factor tests if non-neutral
```

Он не должен требовать concrete-type changes в `GeometrySystem`, `NavigationSystem` или `TransitionCostCalculator`.

Если future Shape перестанет fit current one-supported-position model, Shape contract, Navigation read envelope и TransitionCost support-owner lookup пересматриваются вместе, а не патчатся concrete-type exception.

См. [Контракт Shape](Shape-Contract.md), [Добавление Shape](Adding-a-Shape.md) и [Movement System](Movement-System.md).
