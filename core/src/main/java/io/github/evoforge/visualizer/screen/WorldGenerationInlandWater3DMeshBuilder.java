package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.hydrology.InlandLakeTopology;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/** Builds bounded LOD-aware 3D water surfaces from generated inland-lake membership. */
final class WorldGenerationInlandWater3DMeshBuilder {
    private static final int CHUNK_INTERVALS = 96;
    private static final float WATER_R = 0.08f;
    private static final float WATER_G = 0.38f;
    private static final float WATER_B = 0.62f;
    private static final float WATER_A = 0.58f;

    private WorldGenerationInlandWater3DMeshBuilder() {
    }

    static Mesh[] build(
            InlandLakeTopology lakes,
            WorldBounds bounds,
            int sampleWidth,
            int sampleLength,
            float verticalExaggeration) {
        if (lakes == null || bounds == null) {
            throw new IllegalArgumentException("inland-water mesh inputs must not be null");
        }
        if (!bounds.equals(lakes.bounds())) {
            throw new IllegalArgumentException("inland-water topology must match preview bounds");
        }
        if (sampleWidth < 1 || sampleLength < 1 || verticalExaggeration <= 0f) {
            throw new IllegalArgumentException("invalid inland-water mesh sampling");
        }
        if (lakes.lakeCount() == 0 || sampleWidth < 2 || sampleLength < 2) {
            return new Mesh[0];
        }

        List<Mesh> meshes = new ArrayList<>();
        for (int startY = 0; startY < sampleLength - 1; startY += CHUNK_INTERVALS) {
            int endY = Math.min(sampleLength - 1, startY + CHUNK_INTERVALS);
            for (int startX = 0; startX < sampleWidth - 1; startX += CHUNK_INTERVALS) {
                int endX = Math.min(sampleWidth - 1, startX + CHUNK_INTERVALS);
                Mesh mesh = buildChunk(
                        lakes,
                        bounds,
                        sampleWidth,
                        sampleLength,
                        startX,
                        endX,
                        startY,
                        endY,
                        verticalExaggeration);
                if (mesh != null) meshes.add(mesh);
            }
        }
        return meshes.toArray(Mesh[]::new);
    }

    private static Mesh buildChunk(
            InlandLakeTopology lakes,
            WorldBounds bounds,
            int globalSampleWidth,
            int globalSampleLength,
            int startSampleX,
            int endSampleX,
            int startSampleY,
            int endSampleY,
            float verticalExaggeration) {
        int waterQuads = 0;
        for (int sampleY = startSampleY; sampleY < endSampleY; sampleY++) {
            int y0 = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY, globalSampleLength);
            int y1 = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY + 1, globalSampleLength);
            int centerY = midpoint(y0, y1);
            for (int sampleX = startSampleX; sampleX < endSampleX; sampleX++) {
                int x0 = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX, globalSampleWidth);
                int x1 = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX + 1, globalSampleWidth);
                if (lakes.isLakeAt(midpoint(x0, x1), centerY)) waterQuads++;
            }
        }
        if (waterQuads == 0) return null;

        int vertexCount = Math.multiplyExact(waterQuads, 4);
        float[] vertices = new float[Math.multiplyExact(vertexCount, 7)];
        short[] indices = new short[Math.multiplyExact(waterQuads, 6)];
        int vertexCursor = 0;
        int indexCursor = 0;
        int vertexBase = 0;

        for (int sampleY = startSampleY; sampleY < endSampleY; sampleY++) {
            int y0 = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY, globalSampleLength);
            int y1 = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY + 1, globalSampleLength);
            int centerY = midpoint(y0, y1);
            for (int sampleX = startSampleX; sampleX < endSampleX; sampleX++) {
                int x0 = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX, globalSampleWidth);
                int x1 = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX + 1, globalSampleWidth);
                int centerX = midpoint(x0, x1);
                if (!lakes.isLakeAt(centerX, centerY)) continue;

                float waterY = (float) lakes.surfaceElevationSubunitsAt(centerX, centerY)
                        / ElevationField.SUBUNITS_PER_CELL
                        * verticalExaggeration;
                vertexCursor = vertex(vertices, vertexCursor, x0, waterY, -y0);
                vertexCursor = vertex(vertices, vertexCursor, x1, waterY, -y0);
                vertexCursor = vertex(vertices, vertexCursor, x0, waterY, -y1);
                vertexCursor = vertex(vertices, vertexCursor, x1, waterY, -y1);

                indices[indexCursor++] = (short) vertexBase;
                indices[indexCursor++] = (short) (vertexBase + 1);
                indices[indexCursor++] = (short) (vertexBase + 2);
                indices[indexCursor++] = (short) (vertexBase + 1);
                indices[indexCursor++] = (short) (vertexBase + 3);
                indices[indexCursor++] = (short) (vertexBase + 2);
                vertexBase += 4;
            }
        }

        Mesh mesh = new Mesh(
                true,
                vertexCount,
                indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private static int vertex(float[] vertices, int cursor, float x, float y, float z) {
        vertices[cursor++] = x;
        vertices[cursor++] = y;
        vertices[cursor++] = z;
        vertices[cursor++] = WATER_R;
        vertices[cursor++] = WATER_G;
        vertices[cursor++] = WATER_B;
        vertices[cursor++] = WATER_A;
        return cursor;
    }

    private static int midpoint(int first, int second) {
        return Math.toIntExact((long) first + ((long) second - first) / 2L);
    }

    private static int sampleCoordinate(int min, int max, int sampleIndex, int sampleCount) {
        if (sampleCount <= 1) return min;
        long span = (long) max - min;
        return Math.toIntExact(min + span * sampleIndex / (sampleCount - 1L));
    }
}
