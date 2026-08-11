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

Концептуально:

```json
{
  "key": "core:example",
  "aspects": {
    "physical": {
      "mass": 1.0
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

Это поддерживает deterministic cross-definition references без зависимости семантики от source order.

## Typed ids

Домены оборачивают generic/runtime ids в typed ids:

```text
ObjectDefinitionId
LandscapeDefinitionId
```

Так обычные Java APIs не смешивают разные definition domains.

Runtime typed id оптимизирован для быстрых references в systems. Stable string key остаётся идентичностью, переживающей save/load и content packs.

## Object definitions

Object bootstrap находится под:

```text
world/object/definition/
```

`ObjectFactory` использует compiled object catalog для создания `WorldObject` с валидным `ObjectDefinitionId`.

Object definitions не владеют mutable instance state: position, health, inventory, process progress и т.п. Этим владеют runtime mechanics.

## Landscape definitions

Landscape bootstrap отделён от object definitions:

```text
world/landscape/definition/
```

Terrain cell хранит `LandscapeDefinitionId`, а не `WorldObject` identity.

Так landscape content переиспользует composition-driven infrastructure без object lifetime semantics.

## Mechanic-specific compiled data

Physical mechanic демонстрирует intended pattern:

```text
source aspect
    ↓
PhysicalDefinitionCompiler
    ↓
PhysicalDefinitions
```

Новая mechanic с per-definition configuration обычно добавляет собственный compiler и compiled definition store вместо полей в central definition class.

## Детерминированная загрузка

Definition loading не должен зависеть от filesystem iteration order или unordered-map traversal, когда порядок влияет на результат. Explicit registration и stable identity resolution поддерживают deterministic startup.

Если в будущем появится ordering definition packs/overrides, он должен стать явным контрактом.

## Добавление контента

Если существующие mechanics уже выражают новый content type, достаточно data:

```text
existing mechanics + new definition JSON
    -> new content
```

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

## Persistence rule

Нельзя сериализовать runtime numeric definition id как единственную durable identity. Сохраняйте stable key и разрешайте runtime id заново при load под текущим catalog.
