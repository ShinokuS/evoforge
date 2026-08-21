package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V14-specific base-terrain composition that authors finite-world continent/island silhouettes
 * before the accepted V12 relief laws are synthesized.
 *
 * <p>Boundary policy owns only external-ocean safety and maximum land capacity. Silhouette
 * calibration and geometry are independently replaceable and own the actual geographic footprint.
 * The V12 elevation algorithm consumes that typed footprint but continues to own relief exactly as
 * before.</p>
 *
 * <p>The standard calibrators make continent geometry independent of semantic Land coverage: only
 * the final V12 {@code landCount} changes. {@link #prepare(WorldGenesis)} therefore retains the
 * accepted silhouette and its exact V12 land ranking across a Land-only retarget. Injected
 * calibrators remain safe: if any supposedly invariant operating value changes, materialization
 * falls back to a normal unprepared generation rather than reusing invalid prepared facts.</p>
 */
public final class V14OceanicBaseTerrainGenerator
        implements LandCoverageRetargetableElevationGenerator {
    private static final boolean PROFILE_MEMORY =
            Boolean.parseBoolean(System.getenv().getOrDefault("EVOFORGE_WORLDGEN_MEMORY_PROFILE", "false"));

    private final V12LandformCalibrator terrainCalibrator;
    private final V12LandformRecipe terrainRecipe;
    private final LandmassBoundaryCalibrator boundaryCalibrator;
    private final LandmassBoundaryRecipe boundaryRecipe;
    private final LandmassSilhouetteCalibrator silhouetteCalibrator;
    private final LandmassSilhouetteRecipe silhouetteRecipe;
    private final LandmassSilhouetteAlgorithm silhouetteAlgorithm;
    private final V12LandformElevationAlgorithm algorithm;

    public V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe) {
        this(
                terrainCalibrator,
                terrainRecipe,
                boundaryCalibrator,
                boundaryRecipe,
                LandmassSilhouetteCalibrator.standard(),
                LandmassSilhouetteRecipe.balanced(),
                LandmassSilhouetteAlgorithm.standard(),
                new V12LandformElevationAlgorithm());
    }

    V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe,
            V12LandformElevationAlgorithm algorithm) {
        this(
                terrainCalibrator,
                terrainRecipe,
                boundaryCalibrator,
                boundaryRecipe,
                LandmassSilhouetteCalibrator.standard(),
                LandmassSilhouetteRecipe.balanced(),
                LandmassSilhouetteAlgorithm.standard(),
                algorithm);
    }

    V14OceanicBaseTerrainGenerator(
            V12LandformCalibrator terrainCalibrator,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibrator boundaryCalibrator,
            LandmassBoundaryRecipe boundaryRecipe,
            LandmassSilhouetteCalibrator silhouetteCalibrator,
            LandmassSilhouetteRecipe silhouetteRecipe,
            LandmassSilhouetteAlgorithm silhouetteAlgorithm,
            V12LandformElevationAlgorithm algorithm) {
        if (terrainCalibrator == null
                || terrainRecipe == null
                || boundaryCalibrator == null
                || boundaryRecipe == null
                || silhouetteCalibrator == null
                || silhouetteRecipe == null
                || silhouetteAlgorithm == null
                || algorithm == null) {
            throw new IllegalArgumentException("V14 oceanic base-terrain dependencies must not be null");
        }
        this.terrainCalibrator = terrainCalibrator;
        this.terrainRecipe = terrainRecipe;
        this.boundaryCalibrator = boundaryCalibrator;
        this.boundaryRecipe = boundaryRecipe;
        this.silhouetteCalibrator = silhouetteCalibrator;
        this.silhouetteRecipe = silhouetteRecipe;
        this.silhouetteAlgorithm = silhouetteAlgorithm;
        this.algorithm = algorithm;
    }

    public static V14OceanicBaseTerrainGenerator standard() {
        return new V14OceanicBaseTerrainGenerator(
                V12LandformCalibrator.standard(),
                V12LandformRecipe.balanced(),
                LandmassBoundaryCalibrator.standard(),
                LandmassBoundaryRecipe.balanced());
    }

    @Override
    public PreparedLandCoverageElevation prepare(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        profileMemory("v14.prepare.before_inputs");
        PreparedInputs prepared = prepareInputs(genesis);
        profileMemory("v14.prepare.after_inputs");
        return targetGenesis -> materializePrepared(genesis, prepared, targetGenesis);
    }

    private ElevationField materializePrepared(
            WorldGenesis preparationGenesis,
            PreparedInputs prepared,
            WorldGenesis targetGenesis) {
        if (targetGenesis == null) throw new IllegalArgumentException("target genesis must not be null");
        if (!sameNonIntentIdentity(preparationGenesis, targetGenesis)) {
            return generateUnprepared(targetGenesis);
        }

        V12LandformCalibration targetTerrain = terrainCalibrator.calibrate(targetGenesis, terrainRecipe);
        if (targetTerrain == null) {
            throw new IllegalStateException("V14 terrain calibrator returned null");
        }
        LandmassBoundaryCalibration targetBoundary = boundaryCalibrator.calibrate(
                targetGenesis,
                targetTerrain,
                boundaryRecipe);
        LandmassSilhouetteCalibration targetSilhouetteCalibration = silhouetteCalibrator.calibrate(
                targetGenesis,
                targetTerrain,
                silhouetteRecipe);
        if (targetBoundary == null || targetSilhouetteCalibration == null) {
            throw new IllegalStateException("V14 landmass calibrator returned null");
        }

        if (!sameTerrainExceptLandCount(prepared.terrain(), targetTerrain)
                || !prepared.boundary().equals(targetBoundary)
                || !prepared.silhouetteCalibration().equals(targetSilhouetteCalibration)) {
            return generateUnprepared(targetGenesis);
        }
        profileMemory("v14.materialize.before_v12");
        ElevationField result = algorithm.generate(
                targetGenesis,
                targetTerrain,
                terrainRecipe,
                prepared.silhouette(),
                prepared.landRanking());
        profileMemory("v14.materialize.after_v12");
        return result;
    }

    private ElevationField generateUnprepared(WorldGenesis genesis) {
        PreparedInputs prepared = prepareInputs(genesis);
        return algorithm.generate(
                genesis,
                prepared.terrain(),
                terrainRecipe,
                prepared.silhouette(),
                prepared.landRanking());
    }

    private PreparedInputs prepareInputs(WorldGenesis genesis) {
        V12LandformCalibration terrain = terrainCalibrator.calibrate(genesis, terrainRecipe);
        if (terrain == null) {
            throw new IllegalStateException("V14 terrain calibrator returned null");
        }
        LandmassBoundaryCalibration boundary = boundaryCalibrator.calibrate(
                genesis,
                terrain,
                boundaryRecipe);
        LandmassSilhouetteCalibration silhouetteCalibration = silhouetteCalibrator.calibrate(
                genesis,
                terrain,
                silhouetteRecipe);
        if (boundary == null || silhouetteCalibration == null) {
            throw new IllegalStateException("V14 landmass calibrator returned null");
        }
        profileMemory("v14.prepare.before_silhouette");
        LandmassSilhouette silhouette = silhouetteAlgorithm.generate(
                genesis,
                boundary,
                silhouetteCalibration,
                silhouetteRecipe);
        if (silhouette == null) {
            throw new IllegalStateException("V14 landmass silhouette algorithm returned null");
        }
        profileMemory("v14.prepare.after_silhouette");
        V12LandformElevationAlgorithm.PreparedLandRanking landRanking = algorithm.prepareLandRanking(
                genesis,
                terrain,
                terrainRecipe,
                silhouette);
        profileMemory("v14.prepare.after_land_ranking");
        return new PreparedInputs(
                terrain,
                boundary,
                silhouetteCalibration,
                silhouette,
                landRanking);
    }

    private static boolean sameNonIntentIdentity(WorldGenesis first, WorldGenesis second) {
        return first.spec().equals(second.spec())
                && first.masterSeed() == second.masterSeed()
                && first.generationRevision().equals(second.generationRevision())
                && first.rngRevision().equals(second.rngRevision());
    }

    private static boolean sameTerrainExceptLandCount(
            V12LandformCalibration first,
            V12LandformCalibration second) {
        return first.width() == second.width()
                && first.height() == second.height()
                && first.area() == second.area()
                && first.coherentLandmassScale() == second.coherentLandmassScale()
                && first.fragmentedLandmassScale() == second.fragmentedLandmassScale()
                && first.fragmentationPpm() == second.fragmentationPpm()
                && first.landformSpacing() == second.landformSpacing()
                && first.upliftScale() == second.upliftScale()
                && first.ridgeScale() == second.ridgeScale()
                && first.rollingScale() == second.rollingScale()
                && first.rollingDetailScale() == second.rollingDetailScale()
                && first.reliefPpm() == second.reliefPpm()
                && first.localReliefPpm() == second.localReliefPpm()
                && first.ruggednessPpm() == second.ruggednessPpm()
                && first.maximumReadableStepSubunits() == second.maximumReadableStepSubunits();
    }

    private static void profileMemory(String stage) {
        if (!PROFILE_MEMORY) return;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf(
                "WORLDGEN_MEMORY stage=%s used_mib=%.2f committed_mib=%.2f max_mib=%.2f%n",
                stage,
                used / 1048576.0,
                runtime.totalMemory() / 1048576.0,
                runtime.maxMemory() / 1048576.0);
    }

    private record PreparedInputs(
            V12LandformCalibration terrain,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteCalibration silhouetteCalibration,
            LandmassSilhouette silhouette,
            V12LandformElevationAlgorithm.PreparedLandRanking landRanking) {
    }
}
