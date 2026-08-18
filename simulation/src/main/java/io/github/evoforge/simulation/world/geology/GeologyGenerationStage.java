package io.github.evoforge.simulation.world.geology;

import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.GenerationStageId;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Deterministic provisional geology: coherent jittered macro-provinces with coarse vertical strata.
 *
 * <p>This implementation exists to exercise the typed geology/profile/material pipeline and to give
 * prepared terrain a non-uniform bedrock identity. It is deliberately <em>not</em> the target
 * geology distribution model: adding more rock definitions here would only add more independently
 * sampled province/stratum choices. Final geology is expected to replace this algorithm behind the
 * same {@link GeologyGenerator}/{@link GeologyField} boundary with coherent formations, layers,
 * intrusions, lenses and deposits produced through the planned spatial-formation system.</p>
 *
 * <p>V1-V3 predate generated geology and intentionally reproduce the former uniform granite
 * bedrock identity. V4+ revisions currently share this provisional contract; newer revisions may
 * change other world-generation stages without silently changing geology.</p>
 */
public final class GeologyGenerationStage implements GeologyGenerator {
    public static final GenerationStageId STAGE_ID = GenerationStageId.of("world:geology");

    private static final GenerationPurposeId SITE_X = GenerationPurposeId.of("world:province-site-x");
    private static final GenerationPurposeId SITE_Y = GenerationPurposeId.of("world:province-site-y");
    private static final GenerationPurposeId PROVINCE_ID = GenerationPurposeId.of("world:province-id");
    private static final GenerationPurposeId STRATUM_UNIT = GenerationPurposeId.of("world:stratum-unit");
    private static final int PROVINCE_SCALE = 16;
    private static final int STRATUM_HEIGHT = 6;

    private final CompiledGeologyProfile profile;

    public GeologyGenerationStage() {
        this(GeologyProfiles.temperateCrust());
    }

    public GeologyGenerationStage(CompiledGeologyProfile profile) {
        if (profile == null) throw new IllegalArgumentException("geology profile must not be null");
        this.profile = profile;
    }

    @Override
    public GeologyField generate(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int depth = Math.toIntExact((long) bounds.maxZ() - bounds.minZ() + 1L);
        int area = Math.multiplyExact(width, height);
        int volume = Math.multiplyExact(area, depth);
        char[] unitOrdinals = new char[volume];
        long[] provinceIds = new long[area];

        GenerationRevision revision = genesis.generationRevision();
        if (GenerationRevision.V1.equals(revision)
                || GenerationRevision.V2.equals(revision)
                || GenerationRevision.V3.equals(revision)) {
            int granite = legacyGraniteOrdinal();
            java.util.Arrays.fill(unitOrdinals, (char) granite);
            return new DenseGeologyField(bounds, profile, unitOrdinals, provinceIds);
        }
        if (!usesGeneratedGeology(revision)) {
            throw new IllegalArgumentException(
                    "unsupported generation revision: " + revision.value());
        }

        GenerationRandom random = GenerationRandom.from(genesis);
        int column = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                Province province = provinceAt(random, worldX, worldY);
                provinceIds[column] = province.id();
                for (int localZ = 0; localZ < depth; localZ++) {
                    int worldZ = bounds.minZ() + localZ;
                    long stratum = Math.floorDiv((long) worldZ, STRATUM_HEIGHT);
                    long sample = random.sampleLong(
                            STAGE_ID,
                            STRATUM_UNIT,
                            province.latticeX(),
                            province.latticeY(),
                            stratum,
                            0L);
                    int ordinal = (int) Math.floorMod(sample, profile.units().size());
                    unitOrdinals[localZ * area + column] = (char) ordinal;
                }
                column++;
            }
        }
        return new DenseGeologyField(bounds, profile, unitOrdinals, provinceIds);
    }

    private static boolean usesGeneratedGeology(GenerationRevision revision) {
        return GenerationRevision.V4.equals(revision)
                || GenerationRevision.V5.equals(revision)
                || GenerationRevision.V6.equals(revision)
                || GenerationRevision.V7.equals(revision)
                || GenerationRevision.V8.equals(revision)
                || GenerationRevision.V9.equals(revision)
                || GenerationRevision.V10.equals(revision)
                || GenerationRevision.V11.equals(revision)
                || GenerationRevision.V12.equals(revision);
    }

    private int legacyGraniteOrdinal() {
        for (int index = 0; index < profile.units().size(); index++) {
            if (GeologyProfiles.GRANITE.equals(profile.units().get(index))) return index;
        }
        throw new IllegalArgumentException(
                "pre-v4 generation requires legacy geology unit: " + GeologyProfiles.GRANITE.value());
    }

    private static Province provinceAt(GenerationRandom random, int x, int y) {
        long cellX = Math.floorDiv((long) x, PROVINCE_SCALE);
        long cellY = Math.floorDiv((long) y, PROVINCE_SCALE);
        Province best = null;
        long bestDistance = Long.MAX_VALUE;

        for (long latticeY = cellY - 1L; latticeY <= cellY + 1L; latticeY++) {
            for (long latticeX = cellX - 1L; latticeX <= cellX + 1L; latticeX++) {
                long siteX = latticeX * PROVINCE_SCALE + Math.floorMod(
                        random.sampleLong(STAGE_ID, SITE_X, latticeX, latticeY, 0L, 0L),
                        PROVINCE_SCALE);
                long siteY = latticeY * PROVINCE_SCALE + Math.floorMod(
                        random.sampleLong(STAGE_ID, SITE_Y, latticeX, latticeY, 0L, 0L),
                        PROVINCE_SCALE);
                long dx = (long) x - siteX;
                long dy = (long) y - siteY;
                long distance = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy));
                long id = random.sampleLong(
                        STAGE_ID,
                        PROVINCE_ID,
                        latticeX,
                        latticeY,
                        0L,
                        0L);
                Province candidate = new Province(latticeX, latticeY, id);
                if (distance < bestDistance
                        || (distance == bestDistance && before(candidate, best))) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static boolean before(Province candidate, Province current) {
        if (current == null) return true;
        int x = Long.compare(candidate.latticeX(), current.latticeX());
        return x < 0 || (x == 0 && candidate.latticeY() < current.latticeY());
    }

    private record Province(long latticeX, long latticeY, long id) { }
}
