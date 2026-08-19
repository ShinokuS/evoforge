# References and Algorithm Sources

EvoForge documentation distinguishes between **external algorithm/model sources** and **EvoForge-specific design**. A citation means that a paper/specification informs an implemented algorithm, a mathematical building block, or the design direction described by the linked system page. It does **not** mean EvoForge reproduces that source verbatim.

When an algorithm is original to EvoForge, its System page says so explicitly and points to production code/tests as the authoritative source instead of attaching a misleading academic citation.

## Pathfinding

### Hart, Nilsson & Raphael — A*

Peter E. Hart, Nils J. Nilsson, Bertram Raphael. **“A Formal Basis for the Heuristic Determination of Minimum Cost Paths.”** *IEEE Transactions on Systems Science and Cybernetics*, 4(2), 100–107, 1968. DOI: [10.1109/TSSC.1968.300136](https://doi.org/10.1109/TSSC.1968.300136).

Used as the algorithmic lineage for EvoForge's deterministic resumable A* pathfinder. EvoForge adds its own 3D traversal contracts, deterministic tie-breaking, resumable execution and reachability/hierarchy preflight.

### Dijkstra — shortest paths

E. W. Dijkstra. **“A note on two problems in connexion with graphs.”** *Numerische Mathematik* 1, 269–271, 1959. DOI: [10.1007/BF01386390](https://doi.org/10.1007/BF01386390).

Background for shortest-path reasoning and the `h = 0` relationship between uniform-cost search and A*-style search. EvoForge does not use a separate Dijkstra runtime pathfinder.

## Procedural terrain and geomorphology

### Génevaux et al. — hydrology-oriented procedural terrain

Jean-David Génevaux, Éric Galin, Éric Guérin, Adrien Peytavie, Bedrich Benes. **“Terrain Generation Using Procedural Models Based on Hydrology.”** *ACM Transactions on Graphics* 32(4), Article 143, 2013. DOI: [10.1145/2461912.2461996](https://doi.org/10.1145/2461912.2461996).

Important conceptual reference for feature-oriented terrain generation, hierarchical drainage structure, watersheds and terrain carving controlled by high-level parameters. It informs the direction of the dry-hydrography stages; current V12 is not an implementation of this paper.

### Cordonnier et al. — uplift + fluvial erosion

Guillaume Cordonnier, Jean Braun, Marie-Paule Cani, Bedrich Benes, Éric Galin, Adrien Peytavie, Éric Guérin. **“Large Scale Terrain Generation from Tectonic Uplift and Fluvial Erosion.”** *Computer Graphics Forum* 35(2), 165–175, 2016. DOI: [10.1111/cgf.12820](https://doi.org/10.1111/cgf.12820).

Important conceptual reference for separating large-scale uplift from hydraulic erosion and for reconstructing terrain from hydrologically meaningful feature structure. The paper uses the stream-power erosion relationship; EvoForge Stage 0/V12 does not claim to implement that erosion model. It is a research basis for later mountain/hydrography work.

## Soil hydraulics

### Saxton & Rawls — soil water characteristics

K. E. Saxton, W. J. Rawls. **“Soil Water Characteristic Estimates by Texture and Organic Matter for Hydrologic Solutions.”** *Soil Science Society of America Journal* 70, 1569–1578, 2006. DOI: [10.2136/sssaj2005.0117](https://doi.org/10.2136/sssaj2005.0117).

The production `SaxtonRawls2006SoilHydraulicCalibrator` is explicitly based on this model family. The surrounding EvoForge semantic-definition and local Soil-formation layers are project-specific composition around that calibration.

## Deterministic generation randomness

### Steele, Lea & Flood — SplitMix / splittable PRNG mixing lineage

Guy L. Steele Jr., Doug Lea, Christine H. Flood. **“Fast splittable pseudorandom number generators.”** OOPSLA 2014, 453–472. DOI: [10.1145/2660193.2660195](https://doi.org/10.1145/2660193.2660195).

EvoForge's `GenerationRandom` is not a mutable SplitMix stream, but its 64-bit avalanche/finalizer constants follow the SplitMix-style `mix64` lineage. EvoForge wraps that mixer in a call-order-independent addressable sampler keyed by master seed, semantic stage/purpose, coordinates and ordinal.

### Fowler–Noll–Vo (FNV-1a)

The stable UTF-8 hash used for generation stage/purpose identifiers follows 64-bit **FNV-1a**. A current specification is the IETF FNV Internet-Draft, *The FNV Non-Cryptographic Hash Algorithm*: [datatracker.ietf.org/doc/draft-eastlake-fnv/](https://datatracker.ietf.org/doc/draft-eastlake-fnv/).

FNV is used only as a stable non-cryptographic identifier hash inside deterministic generation sampling; it is not used for security.

## Internal algorithms with no claimed external model

The following current mechanics are intentionally documented as EvoForge-specific deterministic models rather than being presented as implementations of a published physical solver:

- V12 ocean-first land ranking, feature-kernel hills/depressions, multi-scale relief blending, coast gating and bounded slope relaxation;
- local finite-volume free-liquid redistribution and dormancy/frontier scheduling;
- surface-retention reserve used to prevent perpetual one-cell thin-film runoff;
- simulation scheduling/process ownership conventions;
- multi-owner movement/occupancy semantics and completion-time revalidation;
- deterministic agent utility/intention lifecycle;
- generated-world preparation/materialization/bootstrap ownership boundaries.

For these systems, the production code, headless tests and the corresponding System page are the primary source.

## Source-use rule

When adding or changing a model:

1. state whether the implementation is **directly based on**, **inspired by**, or **independent of** an external source;
2. cite the original/primary source when practical;
3. write the actual equations used by EvoForge, not only the source's equations;
4. explain any simplifications or differences;
5. add the reference here if it is reusable across multiple System pages.

This prevents documentation from implying scientific fidelity that the implementation does not actually provide.
