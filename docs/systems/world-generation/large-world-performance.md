# Large-world generation performance

## In plain language

EvoForge should be able to create a geographically detailed world without making the player wait an unreasonable amount of time or requiring an extreme amount of memory.

A `1,000 × 1,000` world contains one million horizontal cells. A `10,000 × 10,000` world contains **one hundred million cells**. This is not a ten-times-larger problem: every full-world array and every full-world pass becomes one hundred times larger than at `1,000 × 1,000`.

For example, a Java `long[]` with one value per `10,000 × 10,000` cell stores roughly:

```text
100,000,000 cells × 8 bytes ≈ 800 MB
```

Four such temporary arrays already approach 3.2 GB before array headers, the JVM, the final world, the visualizer, or any other stage is counted.

The performance work therefore has two equally important goals:

1. **do less work per cell**;
2. **move and retain fewer bytes per cell**.

The visual result and deterministic generation contracts remain authoritative. Performance work is not permission to lower terrain quality.

## Current status

The accepted V15 generation result is the quality baseline. The current performance milestone begins from squash merge `bf53773e01f01b6bd7c671923928e8647cf6d334`.

The target for this milestone is:

```text
minimum normal target: 10,000 × 10,000 = 100,000,000 cells
future direction: larger worlds without redesigning every algorithm again
```

The first audit shows that the current pipeline is not yet suitable for that target. Several algorithms are asymptotically or memory-bandwidth expensive even though they are acceptable at small preview sizes.

No wall-clock requirement is yet declared as an invariant. Hardware differs too much for ordinary CI to assert a useful absolute generation time. Instead we first remove known asymptotic and allocation pathologies, add a repeatable benchmark harness, and then establish measured budgets.

## Mental model

Performance is treated as an architectural property of the generation pipeline:

```text
semantic world intent
        ↓
calibrated generation policy
        ↓
coarse / sparse geographic structure
        ↓
streamed or tiled dense synthesis
        ↓
compact authoritative fields
        ↓
final ElevationField
```

The desired long-term shape is not:

```text
stage A → allocate several complete world arrays
stage B → copy complete world again
stage C → sort every cell
stage D → copy complete world again
```

At `10k × 10k`, memory traffic itself becomes a limiting resource. The Roofline performance model is useful here: low-arithmetic-intensity kernels are limited by data movement long before the CPU's arithmetic peak is reached. EvoForge therefore counts full-world passes and bytes moved, not only Big-O arithmetic.

## Scale arithmetic

Let:

```text
W = world width in cells
H = world height in cells
N = W × H
```

At the milestone target:

```text
W = 10,000
H = 10,000
N = 100,000,000
```

Approximate raw storage per one value per cell:

| Representation | Bytes/cell | 100 M cells |
|---|---:|---:|
| `boolean[]` payload | ~1 | ~100 MB |
| bit grid | 0.125 | ~12.5 MB |
| `short[]` / `char[]` | 2 | ~200 MB |
| `int[]` / `float[]` | 4 | ~400 MB |
| `long[]` / `double[]` | 8 | ~800 MB |

These figures exclude object/array headers and alignment, but they are sufficient to expose the large-world problem.

## Current pipeline cost audit

This audit records the implementation at the beginning of the performance milestone. It is deliberately specific so later refactors can show exactly what disappeared.

### V12 ordinary terrain

`V12LandformElevationAlgorithm` currently performs a global ranking by constructing one `long` rank key per cell and calling `Arrays.sort`.

Current complexity:

```text
memory: O(N) long rank array = 8N bytes
work:   O(N log N) comparison sort
```

The potential being ranked is only a 16-bit value. The exact ordering is:

```text
higher potential first
then lower linear cell index first
```

That means a comparison sort is unnecessary. A fixed histogram over the 65,536 possible potential values can compute the exact same rank in O(N + 65,536) work and a tiny fixed amount of auxiliary storage.

V12 also builds a full chamfer coast-distance field and then a second full `int[]` for mapped coastal interiority. The distance buffer can be converted to the final PPM values in-place after the transform.

### Deterministic generation randomness

`GenerationRandom.sampleLong(...)` is call-order independent and therefore already has the right semantic model for parallel generation. The hot implementation, however, repeatedly hashes the same stage and purpose strings for every sample.

Each sample currently performs:

```text
master seed mix
stage UTF-8 hash + mix
purpose UTF-8 hash + mix
x mix
y mix
z mix
ordinal mix
```

`stableStringHash` materializes UTF-8 bytes with `String.getBytes(StandardCharsets.UTF_8)`. V12 value-noise evaluation requests several lattice samples for each cell and does this across multiple noise fields. Re-hashing immutable semantic identifiers millions of times is pure overhead.

The first exact-preserving RNG optimization is a **bound sampler**:

```text
GenerationRandom
   + stage
   + purpose
        ↓ bind once
BoundGenerationSampler
        ↓
sample(x, y, z, ordinal)
```

The bound sampler precomputes exactly the state that is invariant for that semantic stream. The remaining coordinate/ordinal mix sequence stays unchanged, so generated values remain bit-identical.

A later, stronger optimization can cache deterministic value-noise lattice samples themselves. Value noise reads randomness only at lattice vertices; neighbouring world cells repeatedly ask for the same vertices. Precomputing a small lattice turns repeated hashing into array reads while preserving the exact interpolation rule.

### Landmass silhouette

`RegularizedGraphLandmassSilhouetteAlgorithm` correctly operates on a sparse irregular control graph for its macro geometry, but final dense materialization currently has a second global sort when positive coast support exceeds the maximum selectable land capacity.

It also keeps dense `double[]` score state and constructs `LandmassSilhouette` from dense `int[] potentialPpm` plus `boolean[] supported`; the silhouette constructor defensively clones both arrays.

Large-world work will separate two concepts that are currently conflated:

```text
geometric support       = where the coast field can represent land
selection capacity      = maximum number of cells V12 may select as land
```

Because the current final potential is monotonic with coast score, selection capacity can potentially be represented explicitly and the full positive-score sort can disappear. This change must first prove identical selected membership for all supported land intents, including deterministic ties.

Dense support masks should eventually be bit-packed. Fresh algorithm-owned arrays should use explicit ownership transfer rather than an immediate defensive clone.

### Dense elevation storage

`DenseElevationField` currently clones the supplied `long[]` in its constructor. Generation algorithms normally allocate a fresh result and immediately hand it to `DenseElevationField`, so the safety copy duplicates a complete authoritative field even though no other owner exists.

At 100 M cells, one such clone moves another ~800 MB.

The planned contract is explicit rather than unsafe:

```text
copyOf(...) / public constructor
    external or shared caller → defensive copy

takeOwnership(...)
    package-internal fresh generation buffer → no copy
```

A later representation optimization may use an `int`-backed elevation field where calibrated world bounds prove that every subunit value fits exactly in signed 32-bit storage. With one million subunits per Z cell, signed `int` still covers roughly ±2,147 Z cells. This is an optimization opportunity, not a new global assumption: overflow-proof validation is mandatory and `long` remains the semantic API.

### Mountains

`MountainMorphologyAlgorithm` is mainly O(N), and its sparse mountain-source sorting is not proportional to every cell. Its main large-world issue is memory traffic:

```text
base heights  long[N]
land mask     boolean[N]
uplift         long[N]
result         long[N]
```

The stage can later use an owned result buffer, a bit grid for land membership, and a narrower exact uplift representation when calibrated bounds allow it.

### Ocean bathymetry

`BathymetryMorphologyAlgorithm` is currently one of the strongest 10k blockers.

Its pipeline materializes numerous complete fields for shoreline distance, coastal character, box-window mass/support, local relief, propagation order and intermediate horizontal/vertical fields. It also sorts water cells by encoded shoreline distance.

The desired replacement principles are:

1. distance values are bounded integers, so distance ordering should use counting/bucket organization rather than comparison sorting;
2. large fixed-radius box filters should use streaming/separable sliding windows where only one/few outputs are required, instead of several simultaneous 2D integral images;
3. scratch buffers with non-overlapping lifetimes should be reused explicitly;
4. connected-component work must use primitive storage, not boxed `Integer` queues;
5. data used by both ordinary and deep bathymetry should be carried through a narrow typed context rather than recomputed as another full-world field.

`DeepBathymetryStructureAlgorithm` currently copies/retains more complete `long[]` fields even though deep structures are sparse. It should operate per component/ROI where possible.

### Inland lakes

`TerrainLowlandInlandLakeDomainAlgorithm` currently combines several large dense fields:

```text
source elevation
coast distance
broad smoothed elevation
eligibility
eligible-height collection
morphology distances
component labels
final lake mask
```

Important problems:

- eligible heights are globally `Arrays.sort`ed only to obtain a quantile;
- broad box smoothing uses full integral-image storage;
- component traversal uses `ArrayDeque<Integer>`, which boxes cell indices;
- several `int[N]` distance/label arrays have non-overlapping lifetimes but are allocated independently.

Planned improvements:

- deterministic nth-element/selection or bounded histograms instead of full sort where exact domains allow it;
- streaming separable box means with row/ring buffers;
- primitive integer queues or run-length connected components;
- scratch-buffer reuse;
- component/ROI representation for sparse accepted lakes.

`DistanceProfileInlandLakeBathymetryAlgorithm` already contains an important large-world idea: shoreline distance is computed per inland-water component inside a padded bounding box rather than by another full-world distance transform. That pattern should be retained and generalized where it preserves semantics.

### V15 land compensation

`V15InlandLakeBaseTerrainGenerator` has a predictive fast path, but can still synthesize the expensive continental base twice when actual lake area differs materially from the reservation.

At large scale a second full V12 synthesis is too costly to be a routine correction mechanism.

The preferred future direction is to separate reusable **land-selection basis** from expensive detailed relief synthesis so coverage compensation can change the threshold/selection cheaply without recreating every noise and morphology field. This requires careful ownership and memory design; it will not be implemented as a quick patch.

## Why global comparison sorting is especially harmful

A global comparison sort has two costs at large scale:

```text
O(N log N) comparisons
+
O(N) extra key storage
```

When the key domain is bounded, EvoForge should prefer bounded-domain algorithms.

For V12 16-bit potential, define:

```text
count[p]       = number of supported cells with potential p
higher[p]      = sum of count[q] for every q > p
seen[p]        = number of potential-p cells already visited in linear cell order
```

Then the exact old rank of a cell with potential `p` is:

```text
rank(cell) = higher[p] + seen[p]
```

Scanning cells in increasing linear index makes `seen[p]` reproduce the old tie-break exactly.

This changes:

```text
O(N log N) + 8N-byte rank keys
```

into:

```text
O(N + 65,536) + fixed small histograms
```

without changing the generated world.

## Large-window filtering

The current code uses summed-area/integral tables in several places. A summed-area table makes an arbitrary rectangular sum O(1), which is valuable when many different windows are queried from the same image. The cost is another complete 2D table.

World generation often needs a different workload:

```text
one fixed radius
for every cell
once
```

For that workload, a separable sliding window can compute horizontal sums and then vertical sums in O(N) while keeping only a bounded row ring/window of intermediate state. Runtime stays independent of the window radius while peak memory can be much lower.

For min/max morphology with large rectangular or octagonal kernels, the van Herk family is especially relevant: the published algorithm requires a constant number of comparisons per pixel independent of kernel size. It is a candidate only where it matches the exact morphology contract; it is not automatically substituted for chamfer-distance morphology.

## Distance transforms

Current lake/coast logic often uses deterministic 8-neighbour chamfer distance because its metric is already part of accepted geometry.

Where a future algorithm actually requires exact squared-Euclidean distance, Felzenszwalb & Huttenlocher provide a separable linear-time distance-transform/min-convolution construction. This avoids treating Euclidean distance as inherently expensive.

Performance work must not silently replace an accepted chamfer metric with Euclidean distance merely because an algorithm is faster or academically attractive. Distance metric is visible generation semantics.

## Connected components

Boxed flood-fill is not acceptable at 100 M cells.

The progression is:

```text
small exact refactor:
ArrayDeque<Integer>
        ↓
primitive int queue

large-world structural version:
row runs / tile-local labels
        ↓
deterministic union of touching runs/tile borders
        ↓
component summaries
```

Run-length connected-component labeling is attractive because accepted lake/deep-water domains contain long coherent spans and because it avoids one Java object per queued cell. A tile implementation must merge borders in a deterministic fixed order so worker count never changes component identity or downstream tie-breaking.

## Deterministic parallel execution

EvoForge's addressed generation RNG is intentionally not a mutable random stream. That makes cell/tile computation naturally parallel once algorithms no longer depend on traversal order for hidden state.

The parallel contract will be:

```text
same genesis
same generation revision
same RNG revision
same result bits
regardless of configured worker count
```

Parallel stages should use fixed deterministic tiles. Global summaries use tile-local results followed by a deterministic reduction order.

Examples:

- histogram ranking: local histograms → ordered sum → fixed global prefix;
- filtering: independent stripes/tiles with explicit halo or separable synchronization;
- connected components: tile-local labels → ordered border union;
- dense synthesis: disjoint output ranges.

Parallel prefix/scan is a standard building block for this family of compaction, offsets and deterministic prefix work. EvoForge can initially use scalar ordered prefixes where 65k histograms are small, then parallelize only when profiling proves it useful.

A dedicated bounded CPU worker pool is preferable to `parallelStream()` because generation needs explicit scheduling, deterministic partition boundaries, lifecycle ownership and later progress reporting for the UI.

## SIMD / Vector API

JDK 21 contains the Vector API as the **sixth incubator**. It can express portable vector computations that HotSpot maps to SIMD instructions.

It is deliberately **not Phase A**.

Reason:

```text
removing an O(N log N) sort
or avoiding an 800 MB copy
≫
hand-vectorizing an already memory-bound loop
```

After the pipeline is O(N), compact and tiled, profiling may identify stable arithmetic kernels worth vectorizing. Because the API is incubating in JDK 21, EvoForge should not make core architecture depend on it unless measured benefit justifies the module/tooling cost.

## GC is not the primary solution

JDK 21 includes Generational ZGC and other strong collectors. GC tuning can improve pauses, but it cannot make an algorithm that simultaneously allocates several multi-hundred-megabyte scratch arrays efficient.

The priority order is:

```text
algorithmic complexity
→ representation size
→ number of full-world passes
→ allocation lifetime/reuse
→ deterministic parallelism
→ optional SIMD / JVM tuning
```

## Performance architecture

### Phase A — exact-result cleanup

No intentional generated-value change.

1. remove needless defensive copies through explicit ownership-transfer factories;
2. replace V12 global 16-bit potential sort with exact histogram ranking;
3. map coast distance to interiority in-place;
4. replace boxed cell queues with primitive queues;
5. bind generation RNG stage/purpose state once per semantic stream;
6. add repeatable benchmark/profiling entry points.

Each transformation gets an equivalence regression where practical.

### Phase B — memory and bounded-domain redesign

Still preserve accepted generation semantics, but allow internal representations to change substantially.

Candidates:

- bit grids for boolean membership;
- compact/adaptive integer elevation storage;
- explicit reusable scratch workspace;
- remove landmass positive-score global sort via separate selection-capacity contract;
- replace full-array quantile sorts with selection/histogram methods;
- streamed separable broad filters;
- bucketed bounded-distance ordering;
- component/ROI storage rather than world-sized label arrays.

### Phase C — deterministic tiled parallelism

Only after hidden global ordering has been removed.

- fixed tiles/stripes;
- bounded world-generation worker pool;
- deterministic tile-local summaries and ordered reductions;
- worker-count equivalence tests;
- stage timing/progress instrumentation.

This phase is deliberately compatible with the later UI requirement to keep the game responsive while generation runs and to report named generation stages.

### Phase D — beyond one-machine dense-memory comfort

For worlds substantially larger than 10k, even one dense 64-bit height field becomes expensive. The pipeline should therefore be capable of evolving to chunked storage and bounded working sets.

Likely requirements:

- chunk/tile-backed immutable generated fields;
- halo-aware local transforms;
- sparse macro structures separate from dense final materialization;
- deterministic border reconciliation;
- optional persisted/intermediate chunks if generation exceeds comfortable RAM;
- preview LOD that never requires materializing a second complete rendering copy.

Phase D is not a license to prematurely chunk every small algorithm. The milestone first measures how far compact O(N) in-memory generation can go.

## Benchmark methodology

Performance claims must be measured separately from correctness CI.

A benchmark run records at minimum:

```text
revision / commit
JDK version
CPU model / available processors
maximum JVM heap
world dimensions
seed and semantic generation intent
per-stage elapsed time
whole generation elapsed time
peak/representative memory evidence when available
output checksum / deterministic identity
```

Recommended scale ladder:

```text
300 × 300       fast development sanity
1,000 × 1,000   current pain-point baseline
2,000 × 2,000   scaling check
5,000 × 5,000   large intermediate target
10,000 × 10,000 milestone target
```

Do not assert absolute milliseconds in ordinary CI. CI should assert algorithmic contracts such as exact output equivalence, single-pass behavior, no forbidden global sort in a known path where a structural test is sensible, and deterministic equality across worker counts.

## Invariants

1. Same authoritative inputs and generation revisions produce exactly deterministic output unless a deliberate new generation revision explicitly changes semantics.
2. Performance refactors do not lower accepted visual quality.
3. No hidden mutable global RNG stream is introduced for speed.
4. Worker count must not change authoritative output.
5. Dense full-world arrays must have an explicit semantic reason and lifetime; scratch arrays are not accumulated casually.
6. A bounded-domain key should not use an O(N log N) comparison sort without documented justification.
7. Boxed per-cell graph/flood-fill nodes are forbidden in large dense generation paths.
8. Presentation/preview optimization does not become authoritative generation logic.
9. A fallback that repeats the complete expensive world synthesis is considered a scalability risk and must be measured/documented.
10. Performance improvements are benchmarked before claims are made.

## Current limitations / deliberately absent

- No final wall-clock target is declared yet; baseline measurement is part of this milestone.
- GPU compute is not currently part of the plan. CPU algorithms, memory layout and deterministic parallelism have substantial unclaimed headroom first.
- The Vector API is not a required dependency.
- Chunked/out-of-core generation is a future scalability layer, not yet the standard representation.
- Drainage/rivers remain deferred; this milestone optimizes the accepted V15 dry terrain + standing-water baseline before adding more global analyses.

## Code and tests

Primary generation hot paths currently live under:

`simulation/src/main/java/io/github/evoforge/simulation/world/atlas/`

Key audit entry points:

- `V12LandformElevationAlgorithm`
- `RegularizedGraphLandmassSilhouetteAlgorithm`
- `MountainMorphologyAlgorithm`
- `BathymetryMorphologyAlgorithm`
- `DeepBathymetryStructureAlgorithm`
- `TerrainLowlandInlandLakeDomainAlgorithm`
- `DistanceProfileInlandLakeBathymetryAlgorithm`
- `V15InlandLakeBaseTerrainGenerator`
- `GenerationRandom`
- `DenseElevationField`

Performance-specific tests and benchmark entry points are added during this milestone rather than treating timing assertions as ordinary unit tests.

## Sources

### Direct/algorithm lineage relevant to candidate optimizations

- Pedro F. Felzenszwalb, Daniel P. Huttenlocher. **“Distance Transforms of Sampled Functions.”** *Theory of Computing* 8(19), 415–428, 2012. DOI: 10.4086/toc.2012.v008a019. Linear-time separable distance/min-convolution algorithms; relevant when exact Euclidean distance is actually required. https://theoryofcomputing.org/articles/v008a019/
- Marcel van Herk. **“A fast algorithm for local minimum and maximum filters on rectangular and octagonal kernels.”** *Pattern Recognition Letters* 13(7), 517–521, 1992. DOI: 10.1016/0167-8655(92)90069-C. Constant-comparison large-kernel morphology candidate. https://doi.org/10.1016/0167-8655(92)90069-C
- Guy E. Blelloch. **“Prefix Sums and Their Applications.”** CMU-CS-90-190, 1990 / chapter version 1991. Parallel scan lineage for deterministic histogram prefix, compaction and offsets. https://www.cs.cmu.edu/afs/cs.cmu.edu/project/scandal/public/papers/CMU-CS-90-190.html
- John K. Salmon, Mark A. Moraes, Ron O. Dror, David E. Shaw. **“Parallel Random Numbers: As Easy as 1, 2, 3.”** SC11, 2011. Counter/key-addressed parallel randomness is conceptual lineage for stateless deterministic parallel generation; EvoForge's exact mixer/address schema remains project-specific. https://www.deshawresearch.com/resources.html
- Franklin C. Crow. **“Summed-Area Tables for Texture Mapping.”** SIGGRAPH 1984. Historical lineage for the integral-image/summed-area technique currently used in several broad filters. EvoForge may replace SATs with streaming windows when the workload does not need arbitrary box queries.

### Performance-model / platform sources

- Samuel Williams, Andrew Waterman, David Patterson. **“Roofline: An Insightful Visual Performance Model for Multicore Architectures.”** *Communications of the ACM* 52(4), 65–76, 2009. Used as conceptual guidance for treating memory traffic as a first-class limit after asymptotic problems are removed. https://amcr.lbl.gov/departments/computer-science-department/ppan/roofline-performance-model/ppan-roofline-publications/
- OpenJDK **JDK 21** / JEP 448, Vector API (Sixth Incubator). Platform capability reference only; not yet an implemented EvoForge dependency. https://openjdk.org/projects/jdk/21/

### Internal EvoForge design

The concrete phase ordering, buffer-ownership rules, compact-field strategy, deterministic tile/reduction contract, benchmark ladder and decision to preserve accepted V15 visuals are EvoForge-specific engineering decisions. Production code, tests and benchmark evidence are authoritative for those choices.
