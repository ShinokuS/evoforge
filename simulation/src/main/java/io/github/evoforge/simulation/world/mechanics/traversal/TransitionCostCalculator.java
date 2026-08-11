package io.github.evoforge.simulation.world.mechanics.traversal;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GridTransitionLength;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.ShapeTraversalFactor;

public final class TransitionCostCalculator
        implements TransitionCostLookup {

    private static final int STANDING_OFFSET_Z = 1;

    private final TerrainLookup terrain;
    private final GeometryLookup geometry;
    private final LandscapeTraversalDefinitions definitions;

    public TransitionCostCalculator(
            TerrainLookup terrain,
            GeometryLookup geometry,
            LandscapeTraversalDefinitions definitions) {

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.terrain = terrain;
        this.geometry = geometry;
        this.definitions = definitions;
    }

    @Override
    public TransitionCost cost(
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ) {

        long dxLong = (long) toX - fromX;
        long dyLong = (long) toY - fromY;
        long dzLong = (long) toZ - fromZ;

        if (dxLong < -1 || dxLong > 1
                || dyLong < -1 || dyLong > 1
                || dzLong < -1 || dzLong > 1
                || dxLong == 0 && dyLong == 0 && dzLong == 0) {
            throw new IllegalArgumentException(
                    "transition must connect immediate neighboring positions");
        }

        int dx = (int) dxLong;
        int dy = (int) dyLong;
        int dz = (int) dzLong;

        int sourceAnchorZ = supportAnchorZ(fromZ);
        int destinationAnchorZ = supportAnchorZ(toZ);

        LandscapeDefinitionId sourceDefinition = requireTerrain(
                fromX,
                fromY,
                sourceAnchorZ,
                "source");
        LandscapeDefinitionId destinationDefinition = requireTerrain(
                toX,
                toY,
                destinationAnchorZ,
                "destination");

        Shape sourceShape = requireShape(
                fromX,
                fromY,
                sourceAnchorZ,
                "source");
        Shape destinationShape = requireShape(
                toX,
                toY,
                destinationAnchorZ,
                "destination");

        int sourceFactor = sourceShape.departureTraversalFactor(
                0,
                0,
                STANDING_OFFSET_Z,
                dx,
                dy,
                dz);

        int destinationFactor = destinationShape.arrivalTraversalFactor(
                -dx,
                -dy,
                STANDING_OFFSET_Z - dz,
                dx,
                dy,
                dz);

        if (sourceFactor <= ShapeTraversalFactor.NONE) {
            throw new IllegalStateException(
                    "source Shape does not own traversal departure");
        }
        if (destinationFactor <= ShapeTraversalFactor.NONE) {
            throw new IllegalStateException(
                    "destination Shape does not own traversal arrival");
        }

        long sourceWeighted = Math.multiplyExact(
                definitions.cost(sourceDefinition).units(),
                sourceFactor);
        long destinationWeighted = Math.multiplyExact(
                definitions.cost(destinationDefinition).units(),
                destinationFactor);

        long localSum = Math.addExact(
                sourceWeighted,
                destinationWeighted);

        long numerator = Math.multiplyExact(
                localSum,
                GridTransitionLength.units(
                        dx,
                        dy,
                        dz));

        long denominator = Math.multiplyExact(
                2L * ShapeTraversalFactor.SCALE,
                GridTransitionLength.SCALE);

        long units = divideRoundHalfUp(
                numerator,
                denominator);

        return TransitionCost.of(
                Math.max(1L, units));
    }

    private LandscapeDefinitionId requireTerrain(
            int x,
            int y,
            int z,
            String role) {

        LandscapeDefinitionId definition = terrain.find(
                x,
                y,
                z);

        if (definition == null) {
            throw new IllegalStateException(
                    role + " traversal support terrain is missing");
        }

        return definition;
    }

    private Shape requireShape(
            int x,
            int y,
            int z,
            String role) {

        Shape shape = geometry.find(
                x,
                y,
                z);

        if (shape == null) {
            throw new IllegalStateException(
                    role + " traversal support Shape is missing");
        }

        return shape;
    }

    private static int supportAnchorZ(
            int standingZ) {

        try {
            return Math.subtractExact(
                    standingZ,
                    STANDING_OFFSET_Z);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "standing position has no representable support anchor",
                    exception);
        }
    }

    private static long divideRoundHalfUp(
            long numerator,
            long denominator) {

        long quotient = numerator / denominator;
        long remainder = numerator % denominator;

        if (remainder * 2 >= denominator) {
            return Math.addExact(
                    quotient,
                    1L);
        }

        return quotient;
    }
}
