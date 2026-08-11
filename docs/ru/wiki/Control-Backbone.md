# Control Backbone

Control Backbone — единая граница, через которую внешнее намерение попадает в авторитетную логику симуляции.

Этот слой намеренно остаётся маленьким. Он не является EventBus, внутренним RPC, заменой Scheduler и не требует представлять каждую мутацию мира в виде Command.

## Основной принцип

Command пересекает **границу внешнего намерения**.

Типичные источники:

- ввод игрока;
- AI-контроллеры;
- скрипты и сценарии;
- сетевые адаптеры;
- debug/admin-инструменты.

После принятия намерения продолжающиеся внутренние процессы и внутренние производители состояния работают напрямую через узкие domain API авторитетных систем.

```text
Player / AI / Script / Network
            |
            v
          Command
            |
            v
SynchronousCommandGateway
            |
            v
     CommandDispatcher
            |
            v
          Handler
            |
            v
       domain write API
```

World generation, erosion, уже запущенный процесс mining или другая внутренняя механика не обязаны создавать Commands только ради вызова другой авторитетной системы.

Это не даёт Control превратиться в шину сообщений, где каждая мутация скрыта за `ApplySomethingCommand`.

## Модель результатов

Все результаты операций имеют минимальный нейтральный контракт:

```java
public interface OperationResult {
    boolean accepted();
    ResultCode code();
}
```

`ResultCode` имеет namespace, например:

```text
terrain:placed
terrain:position_occupied
movement:blocked
```

Глобального enum со всеми возможными причинами отказа нет.

`CommandResult` расширяет `OperationResult`, поэтому generic Control может зафиксировать, была ли команда принята и какой namespaced result code получен, не зная доменной семантики.

Конкретные домены при этом могут иметь более богатые типизированные результаты с дополнительными данными.

## Отказ или исключение

Граница фиксирована:

```text
конфликт из-за текущего состояния мира
    -> structured result

некорректный программный/configuration input
    -> exception
```

Примеры нормального structured rejection:

- terrain-позиция уже занята;
- требуемый terrain отсутствует;
- будущий movement transition заблокирован;
- будущая попытка строительства не имеет опоры.

Примеры ошибок программы или конфигурации:

- null command или dependency;
- handler для типа команды не зарегистрирован;
- повторная регистрация handler;
- handler вернул null;
- вызывающий код передал неизвестный runtime `LandscapeDefinitionId`.

## Ожидания внутренних producers

Один и тот же domain result может быть нормальным отказом для одного клиента и нарушением инварианта для другого.

Команда размещения игрока может штатно получить `terrain:position_occupied`. Детерминированный world generator, напротив, может требовать, чтобы генерируемая позиция была свободна.

Внутренние producers выражают такое ожидание абстрактно:

```java
OperationResults.requireAccepted(
        landscape.placeTerrain(...));
```

Они не сравнивают результат с конкретной success-константой вроде `result == PLACED`, если различие конкретных вариантов действительно не является частью их логики.

`requireAccepted` не меняет контракт domain operation. Он лишь говорит, что данный вызывающий считает любой rejection неожиданным.

## Generic Command core

Generic-часть находится в:

```text
simulation/control/core/
```

Текущие типы:

- `Command<R extends CommandResult>` — маркер неизменяемого intent;
- `CommandResult` — минимальный наблюдаемый результат;
- `CommandHandler<C,R>` — типизированная граница выполнения;
- `CommandDispatcher` — регистрация и dispatch по точному runtime-классу.

Dispatcher сам хранит небольшую карту регистраций. Отдельный registry не вводится, пока для него не появится реальная необходимость.

### Правило точного типа

Для одного concrete command class существует один handler.

```text
PlaceTerrainCommand.class -> PlaceTerrainHandler
```

Dispatcher не ищет handler по superclass или интерфейсам.

Отсутствующая или дублирующая регистрация — bootstrap/programming error, а не domain rejection.

## Семантика синхронной доставки

Первая реализация доставки:

```text
simulation/control/sync/SynchronousCommandGateway
```

`submit(command)` немедленно выполняет dispatch и handler. Авторитетные мутации, выполненные handler, видимы до возврата из `submit`.

Для детерминированного вызывающего это означает:

```text
command A
    -> mutation A становится видимой
command B
    -> видит состояние после A
```

Текущий детерминированный порядок равен детерминированному порядку вызовов.

Будущие queued/asynchronous gateways смогут переиспользовать те же Command, Handler и Dispatcher. Но они обязаны отдельно определить порядок flush очереди и семантику видимости состояния; смена delivery policy не считается автоматически сохраняющей within-tick semantics.

## Закон зависимостей

Generic Control маршрутизирует команды, но не знает world domains.

Направление зависимостей является исполняемым архитектурным правилом:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Конкретные use-case adapters в `simulation/control/<use-case>/` могут импортировать узкие domain API, которые они оркестрируют.

Например:

```text
control/terrain/PlaceTerrainHandler
        -> LandscapeMutations
```

Обратная зависимость запрещена.

Это проверяет `ControlDependencyContractTest`.

## Организация команд

Command surface остаётся обозримой под одним архитектурным корнем:

```text
simulation/control/
├── core/
├── sync/
├── terrain/
├── movement/       # в будущем
├── construction/   # в будущем
└── ...
```

Concrete commands группируются по **намерению/use-case**, а не обязательно по авторитетной системе, которую они изменяют.

Будущий `BuildStructureCommand` относится к construction, даже если его handler координирует Inventory, Objects, Spatial и Landscape.

## Граница мутаций Landscape

Terrain state и Geometry state — отдельные авторитетные области, но некоторые lifecycle-операции landscape должны поддерживать их согласованность.

Публичная согласованная write-capability:

```text
LandscapeMutations
```

Текущая политика lifecycle terrain:

```text
placeTerrain
    новая terrain cell
    -> старый orphan geometry override очищается
    -> geometry по умолчанию = FullShape

replaceTerrain
    definition существующей terrain cell меняется
    -> geometry override сохраняется

removeTerrain
    terrain cell исчезает
    -> geometry override очищается
```

`TerrainSystem` остаётся владельцем terrain storage и terrain-инвариантов и не зависит от Geometry.

`LandscapeSystem` координирует `TerrainSystem` и `GeometrySystem`, поэтому любой клиент `LandscapeMutations` получает одинаковую lifecycle-семантику: Command handler, generator, erosion mechanic или другой внутренний producer.

## Первый вертикальный срез

Первая concrete command — `PlaceTerrainCommand`.

```text
PlaceTerrainCommand
        |
        v
PlaceTerrainHandler
        |
        v
LandscapeMutations.placeTerrain
        |
        v
TerrainSystem + Geometry lifecycle
        |
        v
PlaceTerrainResult
```

Ожидаемое поведение:

```text
первая установка в пустую позицию
    -> ACCEPTED / terrain:placed

повторная установка в ту же позицию
    -> REJECTED / terrain:position_occupied
    -> исходный terrain не изменён
```

Этот slice проверяет маршрутизацию команд, structured rejection, немедленную синхронную видимость и авторитетное владение domain-state без введения Movement, связи со Scheduler, EventBus или долгоживущих Actions.

## Чек-лист новой команды

При добавлении новой команды:

1. убедиться, что она действительно пересекает external intent boundary;
2. создать immutable command в подходящем `control/<use-case>/`;
3. определить типизированный `CommandResult` с наблюдаемыми `accepted` и namespaced `code`;
4. реализовать один typed handler через узкие domain API;
5. зарегистрировать ровно один handler для concrete command class;
6. протестировать accepted и rejected world-state paths;
7. некорректные programming/configuration inputs оставлять исключениями;
8. не обучать `CommandDispatcher` новому domain type;
9. при изменении стабильного контракта обновлять architecture/reference документацию.
