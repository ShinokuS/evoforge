package io.github.evoforge.simulation.world.liquid;

import io.github.evoforge.simulation.world.geometry.CellFace;
import io.github.evoforge.simulation.world.geometry.CellSpace;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Derives unconstrained deterministic hydraulic edge transfers from one authoritative snapshot. */
final class LiquidTransferPlanner {
    private final LiquidLookup liquids;
    private final GeometryLookup geometry;
    private final LiquidSurfaceRetentionLookup surfaceRetention;
    private final LiquidTransportLookup transport;

    LiquidTransferPlanner(
            LiquidLookup liquids,
            GeometryLookup geometry,
            LiquidSurfaceRetentionLookup surfaceRetention,
            LiquidTransportLookup transport) {
        if (liquids == null || geometry == null || surfaceRetention == null || transport == null) {
            throw new IllegalArgumentException("liquid transfer planner dependencies must not be null");
        }
        this.liquids = liquids;
        this.geometry = geometry;
        this.surfaceRetention = surfaceRetention;
        this.transport = transport;
    }

    List<LiquidTransfer> plan(List<LiquidCell> activeCells) {
        TreeSet<LiquidEdge> edges = new TreeSet<>();
        for (LiquidCell cell : activeCells) {
            for (CellFace face : CellFace.values()) {
                edges.add(LiquidEdge.of(cell, cell.offset(face)));
            }
        }

        List<LiquidTransfer> transfers = new ArrayList<>();
        for (LiquidEdge edge : edges) {
            LiquidTransfer transfer = plan(edge);
            if (transfer != null) transfers.add(transfer);
        }
        return transfers;
    }

    private LiquidTransfer plan(LiquidEdge edge) {
        LiquidCell first = edge.first();
        LiquidCell second = edge.second();
        Shape firstShape = shape(first);
        Shape secondShape = shape(second);

        CellFace firstToSecond = edge.faceFromFirst();
        long openingFloor = sharedOpeningFloor(
                first,
                firstShape,
                firstToSecond,
                second,
                secondShape);
        if (openingFloor == Long.MAX_VALUE) return null;

        int firstAmount = amount(first);
        int secondAmount = amount(second);
        LiquidTypeId firstType = type(first);
        LiquidTypeId secondType = type(second);

        if (firstType != null && secondType != null && !firstType.equals(secondType)) {
            return null;
        }

        long firstHead = hydraulicHead(first, firstShape, firstAmount);
        long secondHead = hydraulicHead(second, secondShape, secondAmount);
        if (firstHead == secondHead) return null;

        if (firstHead > secondHead) {
            if (firstType == null) return null;
            return planDirected(
                    first,
                    firstShape,
                    firstAmount,
                    firstHead,
                    second,
                    secondShape,
                    secondAmount,
                    openingFloor,
                    firstToSecond,
                    firstType);
        }

        if (secondType == null) return null;
        return planDirected(
                second,
                secondShape,
                secondAmount,
                secondHead,
                first,
                firstShape,
                firstAmount,
                openingFloor,
                firstToSecond.opposite(),
                secondType);
    }

    private LiquidTransfer planDirected(
            LiquidCell source,
            Shape sourceShape,
            int sourceAmount,
            long sourceHead,
            LiquidCell destination,
            Shape destinationShape,
            int destinationAmount,
            long openingFloor,
            CellFace direction,
            LiquidTypeId type) {
        if (sourceAmount == CellVolume.EMPTY || sourceHead <= openingFloor) return null;

        LiquidTypeId destinationType = type(destination);
        if (destinationType != null && !destinationType.equals(type)) return null;

        int destinationCapacity = CellSpace.capacity(destinationShape);
        int destinationFree = Math.max(
                CellVolume.EMPTY,
                destinationCapacity - destinationAmount);
        if (destinationFree == CellVolume.EMPTY) return null;

        int retainedBelowOpening = retainedBelowOpening(source, sourceShape, openingFloor);
        int availableAboveOpening = Math.max(
                CellVolume.EMPTY,
                sourceAmount - retainedBelowOpening);

        int retainedSurface = direction.dz() == 0
                ? CellVolume.requireValid(surfaceRetention.capacityAt(
                        source.x(), source.y(), source.z()))
                : CellVolume.EMPTY;
        int mobileAboveOpening = Math.max(
                CellVolume.EMPTY,
                availableAboveOpening - retainedSurface);

        int maximumTransfer = Math.min(mobileAboveOpening, destinationFree);
        if (maximumTransfer <= CellVolume.EMPTY) return null;

        int equilibriumTransfer = equilibriumTransfer(
                source,
                sourceShape,
                sourceAmount,
                destination,
                destinationShape,
                destinationAmount,
                maximumTransfer);
        int transfer = LiquidTransportMath.relaxedFlowAmount(
                equilibriumTransfer,
                transport.require(type));
        if (transfer <= CellVolume.EMPTY) return null;

        return new LiquidTransfer(source, destination, type, transfer);
    }

    private int equilibriumTransfer(
            LiquidCell source,
            Shape sourceShape,
            int sourceAmount,
            LiquidCell destination,
            Shape destinationShape,
            int destinationAmount,
            int maximumTransfer) {
        long differenceAtMaximum = headDifferenceAfterTransfer(
                source,
                sourceShape,
                sourceAmount,
                destination,
                destinationShape,
                destinationAmount,
                maximumTransfer);
        if (differenceAtMaximum > 0L) return maximumTransfer;

        int low = 1;
        int high = maximumTransfer;
        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            long difference = headDifferenceAfterTransfer(
                    source,
                    sourceShape,
                    sourceAmount,
                    destination,
                    destinationShape,
                    destinationAmount,
                    middle);
            if (difference <= 0L) high = middle;
            else low = middle + 1;
        }

        int crossing = low;
        int before = crossing - 1;
        long crossingDifference = Math.abs(headDifferenceAfterTransfer(
                source,
                sourceShape,
                sourceAmount,
                destination,
                destinationShape,
                destinationAmount,
                crossing));
        long beforeDifference = Math.abs(headDifferenceAfterTransfer(
                source,
                sourceShape,
                sourceAmount,
                destination,
                destinationShape,
                destinationAmount,
                before));
        return beforeDifference <= crossingDifference ? before : crossing;
    }

    private long headDifferenceAfterTransfer(
            LiquidCell source,
            Shape sourceShape,
            int sourceAmount,
            LiquidCell destination,
            Shape destinationShape,
            int destinationAmount,
            int transfer) {
        return hydraulicHead(source, sourceShape, sourceAmount - transfer)
                - hydraulicHead(
                        destination,
                        destinationShape,
                        destinationAmount + transfer);
    }

    private long sharedOpeningFloor(
            LiquidCell first,
            Shape firstShape,
            CellFace firstToSecond,
            LiquidCell second,
            Shape secondShape) {
        int firstFloor = CellSpace.boundaryOpeningFloor(firstShape, firstToSecond);
        int secondFloor = CellSpace.boundaryOpeningFloor(
                secondShape,
                firstToSecond.opposite());
        if (firstFloor == CellSpace.CLOSED || secondFloor == CellSpace.CLOSED) {
            return Long.MAX_VALUE;
        }
        return Math.max(
                globalHeight(first, firstFloor),
                globalHeight(second, secondFloor));
    }

    private int retainedBelowOpening(
            LiquidCell source,
            Shape sourceShape,
            long openingFloor) {
        long localHeight = openingFloor - baseHeight(source);
        if (localHeight <= CellSpace.EMPTY_HEIGHT) return CellVolume.EMPTY;
        if (localHeight >= CellSpace.FULL_HEIGHT) return CellSpace.capacity(sourceShape);
        return CellSpace.freeVolumeBelow(sourceShape, (int) localHeight);
    }

    private static long hydraulicHead(LiquidCell cell, Shape shape, int amount) {
        int capacity = CellSpace.capacity(shape);
        long base = baseHeight(cell);
        if (amount > capacity) {
            return base + CellSpace.FULL_HEIGHT + (amount - capacity);
        }
        return base + CellSpace.surfaceHeight(shape, amount);
    }

    private Shape shape(LiquidCell cell) {
        return geometry.find(cell.x(), cell.y(), cell.z());
    }

    private int amount(LiquidCell cell) {
        return CellVolume.requireValid(liquids.amount(cell.x(), cell.y(), cell.z()));
    }

    private LiquidTypeId type(LiquidCell cell) {
        return liquids.typeAt(cell.x(), cell.y(), cell.z());
    }

    private static long globalHeight(LiquidCell cell, int localHeight) {
        return baseHeight(cell) + localHeight;
    }

    private static long baseHeight(LiquidCell cell) {
        return (long) cell.z() * CellSpace.FULL_HEIGHT;
    }

    private record LiquidEdge(LiquidCell first, LiquidCell second)
            implements Comparable<LiquidEdge> {
        private static LiquidEdge of(LiquidCell first, LiquidCell second) {
            return first.compareTo(second) <= 0
                    ? new LiquidEdge(first, second)
                    : new LiquidEdge(second, first);
        }

        private CellFace faceFromFirst() {
            return CellFace.fromDelta(
                    second.x() - first.x(),
                    second.y() - first.y(),
                    second.z() - first.z());
        }

        @Override
        public int compareTo(LiquidEdge other) {
            int firstOrder = first.compareTo(other.first);
            return firstOrder != 0 ? firstOrder : second.compareTo(other.second);
        }
    }
}
