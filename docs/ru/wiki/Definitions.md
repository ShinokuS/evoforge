# Definitions

EvoForge отделяет неизменяемое описание контента от изменяемого runtime state. Definitions отвечают на вопрос, *как настроен тип контента*; systems владеют тем, *что конкретные runtime instances сейчас делают или хранят*.

## Стабильная source identity

Source definitions используют ключи вроде:

```text
namespace:name
```

Этот стабильный текстовый key — persistence-facing identity. Runtime integer-backed typed ids пересобираются при bootstrap и не считаются durable save identity.

## Generic definition infrastructure

Пакет `simulation.definition` содержит:

```text
DefinitionId
DefinitionCatalog
DefinitionRegistry
DefinitionAspectCompiler
DefinitionCompilerRegistry
DefinitionFileReader
DefinitionDirectoryLoader
DefinitionLoader
```

Generic layer управляет loading/order/registration без знания object-specific или landscape-specific semantics.

## Composition-driven source data

Definitions собираются из aspects вместо универсальной schema со всеми полями механик.

Пример:

```json
{
  "key": "core:example",
  "aspects": {
    "physical": {
      "mass": 1.0
    },
    "movement": {
      "rate": 100
    }
  }
}
```

Механика регистрирует compiler своего aspect. Definition loader не нужен switch со всеми будущими mechanics.

## Явная регистрация compilers

`DefinitionCompilerRegistry` отображает aspect names в `DefinitionAspectCompiler`. Регистрация выполняется явно при bootstrap.

Для фундаментальной simulation composition намеренно не используются reflection/service discovery: startup dependencies остаются видимыми и детерминированно тестируемыми.

## Идея двух проходов identity/compilation

Stable keys и compiled mechanic data — разные concerns. Сначала definitions могут установить identity/catalog membership, затем mechanic compilers разрешают aspect data относительно известных definitions.

Это поддерживает deterministic cross-definition references без зависимости semantics от source order.

## Typed ids

Домены оборачивают generic/runtime ids в typed ids:

```text
ObjectDefinitionId
LandscapeDefinitionId
```

Так ordinary Java APIs не смешивают разные definition domains.

Runtime typed id оптимизирован для быстрых references в systems. Stable string key остаётся identity, переживающей save/load и content packs.

## Object definitions

Object bootstrap находится под:

```text
world/object/definition/
```

`ObjectFactory` использует compiled object catalog для создания `WorldObject` с валидным `ObjectDefinitionId`.

Object definitions не владеют mutable instance state: position, health, inventory, process progress и т.п. Этим владеют runtime mechanics.

### Aspect `movement`

Ordinary self-propelled movement capability задаётся через definition:

```json
{
  "key": "core:walker",
  "aspects": {
    "movement": {
      "rate": 100
    }
  }
}
```

Compilation path:

```text
movement aspect
    ↓
MovementDefinitionCompiler
    ↓
MovementDefinitions
    ↓
ObjectDefinitionId -> MovementRate
```

`movement.rate` — положительное integer в traversal-cost units на simulation tick.

Если aspect отсутствует, definition не имеет текущей ordinary Movement capability. Это явная composition semantics, а не implicit default speed.

`MovementRate` — immutable definition data. Fractional timing carry и active `MovementAction` являются mutable per-object runtime state и поэтому находятся в `MovementStateStore`, а не в definitions.

## Landscape definitions

Landscape bootstrap отделён от object definitions:

```text
world/landscape/definition/
```

Terrain cell хранит `LandscapeDefinitionId`, а не `WorldObject` identity.

Так landscape content переиспользует composition-driven infrastructure без object lifetime semantics.

### Aspect `traversal`

Landscape material может задавать actor-independent базовую traversal price:

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

Compilation path:

```text
traversal aspect
    ↓
LandscapeTraversalDefinitionCompiler
    ↓
LandscapeTraversalDefinitions
    ↓
LandscapeDefinitionId -> SurfaceTraversalCost
```

`traversal.cost` — положительное integer. `1000` — текущий neutral baseline TransitionCost model.

Значение описывает intrinsic surface contribution данного landscape material. Оно не кодирует actor-specific affinity и не решает, существует ли structural edge.

Если valid Movement edge опирается на terrain без compiled traversal cost, это broken configuration, а не обычный gameplay rejection. EvoForge не подставляет silent fallback price, потому что это скрывало бы ошибки content/bootstrap и меняло deterministic timing.

## Текущие mechanic-specific compiled data

В проекте теперь несколько примеров intended pattern:

```text
physical aspect
    ↓
PhysicalDefinitionCompiler
    ↓
PhysicalDefinitions

movement aspect
    ↓
MovementDefinitionCompiler
    ↓
MovementDefinitions

traversal aspect
    ↓
LandscapeTraversalDefinitionCompiler
    ↓
LandscapeTraversalDefinitions
```

Новая mechanic с per-definition configuration обычно добавляет собственный compiler и compiled definition store вместо полей в central definition class.

Definition domain и mechanic domain не обязаны совпадать: `movement` привязан к `ObjectDefinitionId`, а `traversal` — к `LandscapeDefinitionId`, потому что configured fact принадлежит terrain material.

## Definition data против runtime data

Полезная граница:

```text
Одинаково ли значение для всех instances данного definition?
    -> вероятно definition data.

Может ли значение независимо измениться у одного runtime object/cell/process?
    -> runtime state конкретной системы.
```

Для current Movement:

```text
MovementRate             -> object definition data
SurfaceTraversalCost     -> landscape definition data
MovementAction           -> runtime state
per-object timing carry  -> runtime state
Spatial XYZ              -> runtime state owned by Spatial
```

## Детерминированная загрузка

Definition loading не должен зависеть от filesystem iteration order или unordered-map traversal, когда порядок влияет на результат. Explicit registration и stable identity resolution поддерживают deterministic startup.

Если в будущем появится ordering definition packs/overrides, он должен стать явным контрактом.

Compilers, владеющие mutable compiled stores, финализируют их в `finish()`. Current Movement и Landscape Traversal compilers freeze свои stores, поэтому runtime consumers видят immutable configuration после bootstrap.

## Добавление контента

Если существующие mechanics уже выражают новый content type, достаточно data:

```text
existing mechanics + new definition JSON
    -> new content
```

Например новый ordinary terrain material может выбрать другое `traversal.cost` без изменений `MovementSystem` или `TransitionCostCalculator`.

Необходимость менять Java для каждого обычного content definition — сигнал чрезмерной централизации mechanic/configuration boundaries.

## Добавление definition-backed механики

Обычно требуется:

```text
source aspect format
DefinitionAspectCompiler
mechanic-owned compiled definition store
explicit bootstrap registration
unit/loading tests
runtime consumer
```

Не добавляйте aspect без consumer только ради будущего configuration space.

Runtime consumer должен зависеть от compiled store или narrow read boundary, а не повторно parse source JSON во время simulation.

## Persistence rule

Нельзя сериализовать runtime numeric definition id как единственную durable identity. Сохраняйте stable key и разрешайте runtime id заново при load под текущим catalog.

## Связанная документация

- [Movement System](Movement-System.md) — участие `movement.rate` и `traversal.cost` в timed Movement.
- [Landscape и Terrain](Landscape-and-Terrain.md) — authoritative terrain state.
- [Модель объектов](Object-Model.md) — object identity и definition-backed creation.
