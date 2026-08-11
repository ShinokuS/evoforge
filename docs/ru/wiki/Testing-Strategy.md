# Стратегия тестирования

EvoForge относится к тестам как к executable architecture. Подсистема не считается завершённой только потому, что работают example scenarios: tests должны кодировать laws, не позволяющие future mechanics незаметно нарушать established boundaries.

## Цели

```text
local correctness
architectural invariants
cross-system integration
regression protection
reference/property validation
boundary safety
deterministic time/process semantics
```

Один test style не заменяет остальные.

## Unit tests

Unit tests проверяют abstraction изолированно.

Current examples:

```text
ObjectId slot/generation
DefinitionId validation
MovementDefinitionCompiler
LandscapeTraversalDefinitionCompiler
TransitionMask mapping
GridTransitionLength values
TransitionPorts packing
TransitionComposition algebra
FullShape topology
RampShape orientation symmetry
TerrainStorage
BoundProcessScheduler relative scheduling
SimulationStepper phase order
```

Broken local contract должен быть понятен без построения whole World.

## Integration tests

Integration tests проверяют ownership boundaries и composition:

```text
Definition -> compiled catalog -> ObjectFactory
Terrain -> Geometry
Geometry -> Navigation
Navigation-valid edge -> TransitionCost
TransitionCost + MovementRate -> scheduled duration
Movement completion -> SpatialSystem.move
terrain mutation during Movement -> completion revalidation
SpatialSystem -> object spatial indexes
```

Особенно ценны boundaries, где две systems должны оставаться ignorant concrete implementation друг друга.

## Scenario vertical slices

Test-only Scenario fixture использует real production paths, но не становится вторым runtime.

Arrange выполняется через controlled setup; после `start()` tests submit-ят production Commands, advance-ят production `SimulationStepper` и читают read-only state.

Current Movement scenarios доказывают:

```text
MoveStepCommand accepted -> action starts, position unchanged
position changes exactly on completion tick
different MovementRate -> different completion time
diagonal length changes duration
fractional carry persists across steps
second movement while active rejected
missing movement capability rejected
invalid structural edge rejected
surface traversal cost changes authoritative duration
Shape traversal factor changes authoritative duration
advanceTicks(N) == N calls to advance()
```

Scenario Harness не должен раскрывать raw runtime mutators ради удобства одного test. Если after-start mutation нужна для focused boundary test, используется отдельный integration fixture с real domain capability.

## Architectural invariant tests

Reusable laws:

```text
stale ObjectId never resolves to reused object
terrain absence never exposes default Full geometry
solid terrain cells are not normal navigation positions
Shape topology roles obey standing-position contract
Shape traversal factor ownership matches topology roles
Navigation never emits center/out-of-neighborhood direction
composition independent of Shape processing order
missing endpoint support removes edge
at most one ordinary MovementAction active per object
Movement never commits Spatial before scheduled completion
```

Цель — ловить class of bugs, а не один исторический case.

## TransitionCost tests

Cost model тестируется по независимым contributions и вместе:

```text
surface A=1000, B=1600 -> cardinal 1300
neutral double diagonal -> 1414
source departure + destination arrival applied independently
reverse directed edge may have another cost
missing traversal definition -> loud configuration error
non-adjacent input rejected
```

Custom test Shape доказывает extensibility без concrete-type branch в production calculator.

Production Shape role sweep проверяет, что `FullShape` и все cardinal `RampShape` дают NEUTRAL factor ровно там, где соответствующий port-role существует, и NONE в остальных местах.

Это фиксирует единую coordinate/role semantics topology и cost.

## Movement timing tests

Per-step `ceil` не используется. Tests проверяют persistent carry на rate, который не делит cost нацело.

Пример:

```text
cost = 1000
rate = 300
steps = 3, 3, 4 ticks
```

Simulation time discrete, поэтому individual step не обязан иметь fractional duration. Contract — deterministic long-run precision + minimum one tick.

## Completion revalidation

Dedicated integration test:

```text
start A -> B
remove support making edge valid
advance until due tick
MovementActionProcessor revalidates
Navigation no longer exposes edge
Spatial remains A
action removed
```

Это доказывает current semantics: нет immediate reactive wake-up, но и нет stale blind commit.

## Ramp hardening

```text
lower Full <-> Ramp <-> upper Full
missing upper -> no upper edge
missing lower -> no lower ascent/descent
consecutive ramps
side entry blocked
Ramp body non-navigable
occupied destination blocks ascent
```

Reverse traversal проверяется отдельно.

Current Ramp traversal factor neutral; elevation price приходит из `GridTransitionLength`. Future intrinsic ramp factor потребует отдельных directed factor tests.

## Reference resolver

`NavigationReferencePropertyTest` сравнивает production resolver с intentionally simpler independent implementation на deterministic randomized mutations.

Samples:

```text
FullShape
all Ramp orientations
synthetic table-driven Shapes
random departure/arrival/block masks
near and distant mutations
```

Fixed seed делает failures reproducible.

## Determinism

Randomized tests используют explicit seeds/stable ordering.

Для timed mechanics:

```text
same initial state
+ same commands
+ same simulation ticks
= same authoritative result
```

Caller batching (`advanceTicks` vs repeated `advance`) не меняет semantics. Presentation FPS отсутствует из authoritative tests.

## Negative-space tests

Нужно проверять то, чего не должно происходить:

```text
no free Full-to-Full vertical step
no unsupported-void edge
no Ramp side entry
no walk through solid terrain
no false edge after endpoint removal
no coordinate-wrap neighbor
no immediate Spatial move after accepted MoveStepCommand
no second active ordinary movement
no silent fallback traversal cost
no central Shape branch required for custom factor
```

## Boundary tests

Coordinates — `int`; local arithmetic у integer extremes защищён от wrap.

TransitionCost validates adjacency до support lookup, а fixed-point multiplication использует checked arithmetic: unsupported overflow fails loudly.

## Test-first hardening workflow

```text
1. выразить expected semantics минимальным failing test;
2. запустить на current production code;
3. подтвердить actual failure mechanism;
4. внести smallest production change;
5. прогнать nearby regression tests;
6. перед merge прогнать full simulation suite.
```

## Что не делать permanent invariant слишком рано

Current one-standing-position Shape model достаточно strong для tests, Navigation read window и TransitionCost support owners, но не должна навечно запрещать future geometry.

Current actor-independent TransitionCost также не означает, что все future actors обязаны одинаково оценивать поверхности.

Различаем:

```text
FIXED semantic invariant
CURRENT production-model contract
implementation detail
```

## Запуск

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Для focused development используйте `--tests`, затем возвращайтесь к full suite перед final review.
