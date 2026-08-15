package io.github.evoforge.simulation.world.landscape.water;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Deterministic local redistribution of finite Water quantity.
 *
 * <p>The solver owns no liquid quantity. It reads one authoritative snapshot,
 * derives desired neighbor fluxes from hydraulic head and neutral Geometry,
 * bounds all fluxes deterministically, then commits the resulting deltas
 * simultaneously through {@link WaterSystem}.
 *
 * <p>Only cells explicitly activated by Water changes, previous flow or an external
 * Geometry coordinator are considered. A quiescent region therefore falls dormant
 * and costs no further solver work until something wakes it.
 */
public final class WaterFlowSystem {

    private static final int RELAXATION_DIVISOR = 2;

    private final WaterSystem water;
    private final GeometryLookup geometry;
    private final SurfaceWaterStorageLookup surfaceStorage;
    private final WaterFlowActivity activity;
    private final TreeMap<WaterCell, WaterFlowSample> lastFlow =
            new TreeMap<>();
    private final WaterFlowLookup flowLookup = (x, y, z) ->
            lastFlow.get(new WaterCell(x, y, z));

    public WaterFlowSystem(
            WaterSystem water,
            GeometryLookup geometry) {
        this(
                water,
                geometry,
                SurfaceWaterStorageLookup.NONE);
    }

    public WaterFlowSystem(
            WaterSystem water,
            GeometryLookup geometry,
            SurfaceWaterStorageLookup surfaceStorage) {

        if (water == null) {
            throw new IllegalArgumentException(
                    "water must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }
        if (surfaceStorage == null) {
            throw new IllegalArgumentException(
                    "surfaceStorage must not be null");
        }

        this.water = water;
        this.geometry = geometry;
        this.surfaceStorage = surfaceStorage;
        activity = water.flowActivity();
    }

    /** Actual sparse transfer state from the latest evaluated solver step. */
    public WaterFlowLookup flowLookup() {
        return flowLookup;
    }

    /**
     * Redistributes one deterministic local flow step and returns the total volume
     * that crossed cell boundaries during this update.
     */
    public long update() {
        lastFlow.clear();

        List<WaterCell> activeCells =
                activity.drainSorted();
        if (activeCells.isEmpty()) {
            return CellVolume.EMPTY;
        }

        List<MutableTransfer> transfers =
                planTransfers(activeCells);
        if (transfers.isEmpty()) {
            return CellVolume.EMPTY;
        }

        limitOutgoing(transfers);
        limitIncoming(transfers);
        return commit(transfers);
    }

    /**
     * Wakes the local hydraulic neighborhood after an external Geometry mutation.
     *
     * <p>The future landscape coordinator should call this for a cell whose physical
     * Shape changed. Water additions/removals and successful flow wake themselves.
     */
    public void activateAt(
            int x,
            int y,
            int z) {

        activity.activate(x, y, z);
    }

    /** Read-only diagnostic count of cells waiting for the next local flow update. */
    public int activeCellCount() {
        return activity.size();
    }

    private List<MutableTransfer> planTransfers(
            List<WaterCell> activeCells) {

        TreeSet<WaterEdge> edges = new TreeSet<>();
        for (WaterCell cell : activeCells) {
            for (CellFace face : CellFace.values()) {
                edges.add(WaterEdge.of(
                        cell,
                        cell.offset(face)));
            }
        }

        List<MutableTransfer> transfers =
                new ArrayList<>();

        for (WaterEdge edge : edges) {
            MutableTransfer transfer =
                    plan(edge);
            if (transfer != null) {
                transfers.add(transfer);
            }
        }

        return transfers;
    }

    private MutableTransfer plan(
            WaterEdge edge) {

        WaterCell first = edge.first();
        WaterCell second = edge.second();
        Shape firstShape = shape(first);
        Shape secondShape = shape(second);

        CellFace firstToSecond = edge.faceFromFirst();
        long openingFloor = sharedOpeningFloor(
                first,
                firstShape,
                firstToSecond,
                second,
                secondShape);
        if (openingFloor == Long.MAX_VALUE) {
            return null;
        }

        int firstAmount = amount(first);
        int secondAmount = amount(second);

        long firstHead = hydraulicHead(
                first,
                firstShape,
                firstAmount);
        long secondHead = hydraulicHead(
                second,
                secondShape,
                secondAmount);

        if (firstHead == secondHead) {
            return null;
        }

        if (firstHead > secondHead) {
            return planDirected(
                    first,
                    firstShape,
                    firstAmount,
                    firstHead,
                    second,
                    secondShape,
                    secondAmount,
                    openingFloor,
                    firstToSecond);
        }

        return planDirected(
                second,
                secondShape,
                secondAmount,
                secondHead,
                first,
                firstShape,
                firstAmount,
                openingFloor,
                firstToSecond.opposite());
    }

    private MutableTransfer planDirected(
            WaterCell source,
            Shape sourceShape,
            int sourceAmount,
            long sourceHead,
            WaterCell destination,
            Shape destinationShape,
            int destinationAmount,
            long openingFloor,
            CellFace direction) {

        if (sourceAmount == CellVolume.EMPTY
                || sourceHead <= openingFloor) {
            return null;
        }

        int destinationCapacity =
                CellSpace.capacity(destinationShape);
        int destinationFree = Math.max(
                CellVolume.EMPTY,
                destinationCapacity - destinationAmount);
        if (destinationFree == CellVolume.EMPTY) {
            return null;
        }

        int retainedBelowOpening =
                retainedBelowOpening(
                        source,
                        sourceShape,
                        openingFloor);
        int availableAboveOpening = Math.max(
                CellVolume.EMPTY,
                sourceAmount - retainedBelowOpening);

        int retainedSurface = direction.dz() == 0
                ? CellVolume.requireValid(
                        surfaceStorage.capacityAtWaterCell(
                                source.x(),
                                source.y(),
                                source.z()))
                : CellVolume.EMPTY;
        int mobileAboveOpening = Math.max(
                CellVolume.EMPTY,
                availableAboveOpening - retainedSurface);

        int maximumTransfer = Math.min(
                mobileAboveOpening,
                destinationFree);
        if (maximumTransfer <= CellVolume.EMPTY) {
            return null;
        }

        int equilibriumTransfer =
                equilibriumTransfer(
                        source,
                        sourceShape,
                        sourceAmount,
                        destination,
                        destinationShape,
                        destinationAmount,
                        maximumTransfer);

        int relaxedTransfer =
                equilibriumTransfer / RELAXATION_DIVISOR;
        if (relaxedTransfer <= CellVolume.EMPTY) {
            return null;
        }

        return new MutableTransfer(
                source,
                destination,
                relaxedTransfer);
    }

    private int equilibriumTransfer(
            WaterCell source,
            Shape sourceShape,
            int sourceAmount,
            WaterCell destination,
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

        if (differenceAtMaximum > 0L) {
            return maximumTransfer;
        }

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

            if (difference <= 0L) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }

        int crossing = low;
        int before = crossing - 1;

        long crossingDifference = Math.abs(
                headDifferenceAfterTransfer(
                        source,
                        sourceShape,
                        sourceAmount,
                        destination,
                        destinationShape,
                        destinationAmount,
                        crossing));
        long beforeDifference = Math.abs(
                headDifferenceAfterTransfer(
                        source,
                        sourceShape,
                        sourceAmount,
                        destination,
                        destinationShape,
                        destinationAmount,
                        before));

        return beforeDifference <= crossingDifference
                ? before
                : crossing;
    }

    private long headDifferenceAfterTransfer(
            WaterCell source,
            Shape sourceShape,
            int sourceAmount,
            WaterCell destination,
            Shape destinationShape,
            int destinationAmount,
            int transfer) {

        long sourceHead = hydraulicHead(
                source,
                sourceShape,
                sourceAmount - transfer);
        long destinationHead = hydraulicHead(
                destination,
                destinationShape,
                destinationAmount + transfer);

        return sourceHead - destinationHead;
    }

    private void limitOutgoing(
            List<MutableTransfer> transfers) {

        Map<WaterCell, List<MutableTransfer>> outgoing =
                new TreeMap<>();

        for (MutableTransfer transfer : transfers) {
            outgoing.computeIfAbsent(
                    transfer.source,
                    ignored -> new ArrayList<>())
                    .add(transfer);
        }

        for (Map.Entry<WaterCell, List<MutableTransfer>> entry
                : outgoing.entrySet()) {

            List<MutableTransfer> group = entry.getValue();
            group.sort(Comparator.comparing(
                    transfer -> transfer.destination));

            int sourceAmount = amount(entry.getKey());
            int relaxedSourceLimit =
                    sourceAmount / RELAXATION_DIVISOR;
            proportionallyLimit(
                    group,
                    relaxedSourceLimit);
        }
    }

    private void limitIncoming(
            List<MutableTransfer> transfers) {

        Map<WaterCell, List<MutableTransfer>> incoming =
                new TreeMap<>();

        for (MutableTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) {
                continue;
            }

            incoming.computeIfAbsent(
                    transfer.destination,
                    ignored -> new ArrayList<>())
                    .add(transfer);
        }

        for (Map.Entry<WaterCell, List<MutableTransfer>> entry
                : incoming.entrySet()) {

            WaterCell destination = entry.getKey();
            List<MutableTransfer> group = entry.getValue();
            group.sort(Comparator.comparing(
                    transfer -> transfer.source));

            int freeCapacity = Math.max(
                    CellVolume.EMPTY,
                    CellSpace.capacity(shape(destination))
                            - amount(destination));

            proportionallyLimit(
                    group,
                    freeCapacity);
        }
    }

    private static void proportionallyLimit(
            List<MutableTransfer> transfers,
            int limit) {

        long total = 0L;
        for (MutableTransfer transfer : transfers) {
            total += transfer.amount;
        }

        if (total <= limit) {
            return;
        }
        if (limit <= CellVolume.EMPTY) {
            for (MutableTransfer transfer : transfers) {
                transfer.amount = CellVolume.EMPTY;
            }
            return;
        }

        int assigned = 0;
        for (MutableTransfer transfer : transfers) {
            int reduced = (int) (((long) transfer.amount * limit)
                    / total);
            transfer.amount = reduced;
            assigned += reduced;
        }

        int remainder = limit - assigned;
        for (MutableTransfer transfer : transfers) {
            if (remainder == 0) {
                break;
            }
            transfer.amount++;
            remainder--;
        }
    }

    private long commit(
            List<MutableTransfer> transfers) {

        Map<WaterCell, Integer> deltas =
                new TreeMap<>();
        long moved = 0L;

        for (MutableTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) {
                continue;
            }

            deltas.merge(
                    transfer.source,
                    -transfer.amount,
                    Integer::sum);
            deltas.merge(
                    transfer.destination,
                    transfer.amount,
                    Integer::sum);
            moved = Math.addExact(
                    moved,
                    transfer.amount);
            recordFlow(transfer);
        }

        if (moved == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        long conservation = 0L;
        for (int delta : deltas.values()) {
            conservation += delta;
        }
        if (conservation != 0L) {
            throw new IllegalStateException(
                    "water flow plan does not conserve volume: "
                            + conservation);
        }

        for (Map.Entry<WaterCell, Integer> entry
                : deltas.entrySet()) {

            WaterCell cell = entry.getKey();
            int nextAmount = Math.addExact(
                    amount(cell),
                    entry.getValue());
            CellVolume.requireValid(nextAmount);

            if (entry.getValue() > 0) {
                int capacity =
                        CellSpace.capacity(shape(cell));
                if (nextAmount > capacity) {
                    throw new IllegalStateException(
                            "water flow exceeded destination capacity at "
                                    + cell
                                    + ": "
                                    + nextAmount
                                    + " > "
                                    + capacity);
                }
            }

            water.replaceFromFlow(
                    cell,
                    nextAmount);
        }

        for (MutableTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) {
                continue;
            }
            activity.activate(transfer.source);
            activity.activate(transfer.destination);
        }

        return moved;
    }

    private void recordFlow(
            MutableTransfer transfer) {

        WaterFlowSample sample = new WaterFlowSample(
                transfer.destination.x() - transfer.source.x(),
                transfer.destination.y() - transfer.source.y(),
                transfer.destination.z() - transfer.source.z(),
                transfer.amount);
        recordDominant(transfer.source, sample);
        recordDominant(transfer.destination, sample);
    }

    private void recordDominant(
            WaterCell cell,
            WaterFlowSample candidate) {

        WaterFlowSample existing = lastFlow.get(cell);
        if (existing == null
                || candidate.amount() > existing.amount()) {
            lastFlow.put(cell, candidate);
        }
    }

    private long sharedOpeningFloor(
            WaterCell first,
            Shape firstShape,
            CellFace firstToSecond,
            WaterCell second,
            Shape secondShape) {

        int firstFloor = CellSpace.boundaryOpeningFloor(
                firstShape,
                firstToSecond);
        int secondFloor = CellSpace.boundaryOpeningFloor(
                secondShape,
                firstToSecond.opposite());

        if (firstFloor == CellSpace.CLOSED
                || secondFloor == CellSpace.CLOSED) {
            return Long.MAX_VALUE;
        }

        return Math.max(
                globalHeight(first, firstFloor),
                globalHeight(second, secondFloor));
    }

    private int retainedBelowOpening(
            WaterCell source,
            Shape sourceShape,
            long openingFloor) {

        long localHeight = openingFloor
                - baseHeight(source);

        if (localHeight <= CellSpace.EMPTY_HEIGHT) {
            return CellVolume.EMPTY;
        }
        if (localHeight >= CellSpace.FULL_HEIGHT) {
            return CellSpace.capacity(sourceShape);
        }

        return CellSpace.freeVolumeBelow(
                sourceShape,
                (int) localHeight);
    }

    private long hydraulicHead(
            WaterCell cell,
            Shape shape,
            int amount) {

        int capacity = CellSpace.capacity(shape);
        long base = baseHeight(cell);

        if (amount > capacity) {
            return base
                    + CellSpace.FULL_HEIGHT
                    + (amount - capacity);
        }

        return base
                + CellSpace.surfaceHeight(
                        shape,
                        amount);
    }

    private Shape shape(
            WaterCell cell) {

        return geometry.find(
                cell.x(),
                cell.y(),
                cell.z());
    }

    private int amount(
            WaterCell cell) {

        return CellVolume.requireValid(
                water.lookup().amount(
                        cell.x(),
                        cell.y(),
                        cell.z()));
    }

    private static long globalHeight(
            WaterCell cell,
            int localHeight) {

        return baseHeight(cell)
                + localHeight;
    }

    private static long baseHeight(
            WaterCell cell) {

        return (long) cell.z()
                * CellSpace.FULL_HEIGHT;
    }

    private static final class MutableTransfer {
        private final WaterCell source;
        private final WaterCell destination;
        private int amount;

        private MutableTransfer(
                WaterCell source,
                WaterCell destination,
                int amount) {

            this.source = source;
            this.destination = destination;
            this.amount = amount;
        }
    }

    private record WaterEdge(
            WaterCell first,
            WaterCell second)
            implements Comparable<WaterEdge> {

        private static WaterEdge of(
                WaterCell first,
                WaterCell second) {

            return first.compareTo(second) <= 0
                    ? new WaterEdge(first, second)
                    : new WaterEdge(second, first);
        }

        private CellFace faceFromFirst() {
            return CellFace.fromDelta(
                    second.x() - first.x(),
                    second.y() - first.y(),
                    second.z() - first.z());
        }

        @Override
        public int compareTo(
                WaterEdge other) {

            int firstOrder = first.compareTo(other.first);
            if (firstOrder != 0) {
                return firstOrder;
            }
            return second.compareTo(other.second);
        }
    }
}
