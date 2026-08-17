package io.github.evoforge.simulation.world.geology;

import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.GenerationStageId;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Deterministic first geology model: coherent jittered macro-provinces with vertical strata.
 *
 * <p>V1-V3 predate generated geology and intentionally reproduce the former uniform granite
 * bedrock identity. V4+ generate multiple content-defined units without material-specific rules.</p>
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
        if (!GenerationRevision.V4.equals(revision)
                && !GenerationRevision.V5.equals(revision)
                && !GenerationRevision.V6.equals(revision)
                && !GenerationRevision.V7.equals(revision)) {
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

    private int legacyGraniteOrdinal() {
        GeologyUnitKey granite = GeologyProfiles.GRANITE;
        for (int ordinal = 0; ordinal < profile.units().size(); ordinal++) {
            if (profile.units().get(ordinal).key().equals(granite)) {
                return ordinal;
            }
        }
        throw new IllegalStateException("geology profile does not define legacy granite unit");
    }

    private static Province provinceAt(GenerationRandom random, int x, int y) {
        long cellX = Math.floorDiv((long) x, PROVINCE_SCALE);
        long cellY = Math.floorDiv((long) y, PROVINCE_SCALE);
        Province closest = null;
        long closestDistance = Long.MAX_VALUE;

        for (long offsetY = -1L; offsetY <= 1L; offsetY++) {
            for (long offsetX = -1L; offsetX <= 1L; offsetX++) {
                long latticeX = cellX + offsetX;
                long latticeY = cellY + offsetY;
                Province candidate = provinceSite(random, latticeX, latticeY);
                long dx = (long) x - candidate.siteX();
                long dy = (long) y - candidate.siteY();
                long distance = dx * dx + dy * dy;
                if (closest == null
                        || distance < closestDistance
                        || (distance == closestDistance && candidate.id() < closest.id())) {
                    closest = candidate;
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }

    private static Province provinceSite(
            GenerationRandom random,
            long latticeX,
            long latticeY) {
        int jitterX = (int) Math.floorMod(
                random.sampleLong(STAGE_ID, SITE_X, latticeX, latticeY, 0L, 0L),
                PROVINCE_SCALE);
        int jitterY = (int) Math.floorMod(
                random.sampleLong(STAGE_ID, SITE_Y, latticeX, latticeY, 0L, 0L),
                PROVINCE_SCALE);
        long siteX = Math.addExact(Math.multiplyExact(latticeX, PROVINCE_SCALE), jitterX);
        long siteY = Math.addExact(Math.multiplyExact(latticeY, PROVINCE_SCALE), jitterY);
        long id = random.sampleLong(STAGE_ID, PROVINCE_ID, latticeX, latticeY, 0L, 0L);
        return new Province(latticeX, latticeY, siteX, siteY, id);
    }

    private record Province(
            long latticeX,
            long latticeY,
            long siteX,
            long siteY,
            long id) { }
}
