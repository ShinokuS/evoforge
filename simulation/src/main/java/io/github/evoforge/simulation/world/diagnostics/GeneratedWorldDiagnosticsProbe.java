package io.github.evoforge.simulation.world.diagnostics;

import java.util.HashSet;
import java.util.Set;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.geology.GeologyField;
import io.github.evoforge.simulation.world.geology.GeologyUnitKey;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Performs an explicit deterministic audit of a generated world and its runtime state. */
public final class GeneratedWorldDiagnosticsProbe {

    public GeneratedWorldDiagnostics snapshot(WorldAtlas atlas, SimulationRuntime runtime) {
        if (atlas == null || runtime == null) {
            throw new IllegalArgumentException(
                    "generated world diagnostic dependencies must not be null");
        }

        WorldBounds bounds = atlas.genesis().spec().bounds();
        ElevationField elevation = atlas.elevation();
        GeologyField geology = atlas.geology();
        DrainageField drainage = atlas.drainage();
        SurfaceHydrologyField surfaceHydrology = atlas.surfaceHydrology();
        SimulationView view = runtime.view();

        if (!bounds.equals(elevation.bounds())
                || !bounds.equals(geology.bounds())
                || !bounds.equals(drainage.bounds())
                || !bounds.equals(surfaceHydrology.bounds())) {
            throw new IllegalStateException("Atlas diagnostic layers must share world bounds");
        }

        int minimumSurfaceZ = Integer.MAX_VALUE;
        int maximumSurfaceZ = Integer.MIN_VALUE;
        long surfaceMismatches = 0L;
        long maximumContributingArea = 0L;
        long generatedInitialWaterVolume = 0L;
        int generatedInitialWaterColumns = 0;
        int generatedShorelineColumns = 0;
        Set<Long> geologyProvinces = new HashSet<>();
        Set<GeologyUnitKey> geologyUnits = new HashSet<>();
        Set<Long> terminalBasins = new HashSet<>();

        for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
            int worldX = (int) x;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                int surfaceZ = elevation.elevationAt(worldX, worldY);
                minimumSurfaceZ = Math.min(minimumSurfaceZ, surfaceZ);
                maximumSurfaceZ = Math.max(maximumSurfaceZ, surfaceZ);

                if (!view.terrainSurfaces().hasColumn(worldX, worldY)
                        || view.terrainSurfaces().topZ(worldX, worldY) != surfaceZ) {
                    surfaceMismatches = Math.addExact(surfaceMismatches, 1L);
                }

                geologyProvinces.add(geology.provinceIdAt(worldX, worldY));
                terminalBasins.add(pack(
                        drainage.terminalXAt(worldX, worldY),
                        drainage.terminalYAt(worldX, worldY)));
                maximumContributingArea = Math.max(
                        maximumContributingArea,
                        drainage.contributingAreaAt(worldX, worldY));

                int generatedWater = surfaceHydrology.initialWaterVolumeAt(worldX, worldY);
                generatedInitialWaterVolume = Math.addExact(
                        generatedInitialWaterVolume,
                        generatedWater);
                if (generatedWater > 0) generatedInitialWaterColumns++;
                if (surfaceHydrology.isShoreline(worldX, worldY)) generatedShorelineColumns++;
            }
        }

        long terrainCells = 0L;
        long freeWaterVolume = 0L;
        long retainedWaterVolume = 0L;
        long wetWaterCells = 0L;
        long wetSoilCells = 0L;
        int wetWaterColumns = 0;
        int wetSoilColumns = 0;
        long maximumFreeWaterColumnVolume = 0L;
        long maximumRetainedWaterColumnVolume = 0L;
        int maximumWetWaterCellsPerColumn = 0;

        for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
            int worldX = (int) x;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                long columnFreeWater = 0L;
                long columnRetainedWater = 0L;
                int columnWetWaterCells = 0;
                int columnWetSoilCells = 0;

                for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                    int worldZ = (int) z;
                    geologyUnits.add(geology.unitAt(worldX, worldY, worldZ));
                    if (view.terrain().contains(worldX, worldY, worldZ)) {
                        terrainCells = Math.addExact(terrainCells, 1L);
                    }

                    int freeWater = view.water().amount(worldX, worldY, worldZ);
                    int retainedWater = view.soilLiquids().amountOf(
                            WaterSystem.TYPE,
                            worldX,
                            worldY,
                            worldZ);

                    freeWaterVolume = Math.addExact(freeWaterVolume, freeWater);
                    retainedWaterVolume = Math.addExact(retainedWaterVolume, retainedWater);
                    columnFreeWater = Math.addExact(columnFreeWater, freeWater);
                    columnRetainedWater = Math.addExact(columnRetainedWater, retainedWater);
                    if (freeWater > 0) {
                        wetWaterCells = Math.addExact(wetWaterCells, 1L);
                        columnWetWaterCells++;
                    }
                    if (retainedWater > 0) {
                        wetSoilCells = Math.addExact(wetSoilCells, 1L);
                        columnWetSoilCells++;
                    }
                }

                if (columnWetWaterCells > 0) wetWaterColumns++;
                if (columnWetSoilCells > 0) wetSoilColumns++;
                maximumFreeWaterColumnVolume = Math.max(
                        maximumFreeWaterColumnVolume,
                        columnFreeWater);
                maximumRetainedWaterColumnVolume = Math.max(
                        maximumRetainedWaterColumnVolume,
                        columnRetainedWater);
                maximumWetWaterCellsPerColumn = Math.max(
                        maximumWetWaterCellsPerColumn,
                        columnWetWaterCells);
            }
        }

        return new GeneratedWorldDiagnostics(
                runtime.time().tick(),
                atlas.genesis().masterSeed(),
                atlas.genesis().generationRevision(),
                atlas.genesis().rngRevision(),
                bounds,
                terrainCells,
                view.terrainSurfaces().columnCount(),
                surfaceMismatches,
                minimumSurfaceZ,
                maximumSurfaceZ,
                geologyProvinces.size(),
                geologyUnits.size(),
                terminalBasins.size(),
                maximumContributingArea,
                generatedInitialWaterVolume,
                generatedInitialWaterColumns,
                generatedShorelineColumns,
                freeWaterVolume,
                retainedWaterVolume,
                wetWaterCells,
                wetSoilCells,
                wetWaterColumns,
                wetSoilColumns,
                maximumFreeWaterColumnVolume,
                maximumRetainedWaterColumnVolume,
                maximumWetWaterCellsPerColumn);
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffff_ffffL);
    }
}
