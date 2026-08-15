package io.github.evoforge.simulation.world.landscape.liquid;

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
 * Deterministic local redistribution of finite free-liquid quantity.
 *
 * <p>The solver owns no liquid state. It reads one authoritative snapshot,
 * derives local transfers from hydraulic head and Geometry, bounds them
 * deterministically and commits aggregate deltas through {@link LiquidSystem}.
 *
 * <p>The current content model is single-component per cell. Different liquid
 * types do not implicitly mix. An occupied unlike destination rejects transfer;
 * if several unlike liquids simultaneously target the same dry cell, every
 * contested inflow is suppressed for that step. This symmetric boundary is an
 * explicit placeholder for a future composition/mixing model, not a priority rule.
 */
public final class LiquidFlowSystem {

    private static final int RELAXATION_DIVISOR = 2;

    private final LiquidSystem liquids;
    private final GeometryLookup geometry;
    private final LiquidSurfaceRetentionLookup surfaceRetention;
    private final LiquidFlowActivity activity;
    private final TreeMap<LiquidCell, LiquidFlowSample> lastFlow = new TreeMap<>();
    private final LiquidFlowLookup flowLookup =
            (x, y, z) -> lastFlow.get(new LiquidCell(x, y, z));

    public LiquidFlowSystem(
            LiquidSystem liquids,
            GeometryLookup geometry) {
        this(liquids, geometry, LiquidSurfaceRetentionLookup.NONE);
    }

    public LiquidFlowSystem(
            LiquidSystem liquids,
            GeometryLookup geometry,
            LiquidSurfaceRetentionLookup surfaceRetention) {

        if (liquids == null || geometry == null || surfaceRetention == null) {
            throw new IllegalArgumentException(
                    "liquid flow dependencies must not be null");
        }
        this.liquids = liquids;
        this.geometry = geometry;
        this.surfaceRetention = surfaceRetention;
        this.activity = liquids.flowActivity();
    }

    public LiquidFlowLookup flowLookup() {
        return flowLookup;
    }

    /** Runs one local solver step and returns total volume crossing cell boundaries. */
    public long update() {
        lastFlow.clear();

        List<LiquidCell> activeCells = activity.drainSorted();
        if (activeCells.isEmpty()) return CellVolume.EMPTY;

        List<MutableTransfer> transfers = planTransfers(activeCells);
        if (transfers.isEmpty()) return CellVolume.EMPTY;

        limitOutgoing(transfers);
        rejectContestedIncomingTypes(transfers);
        limitIncoming(transfers);
        return commit(transfers);
    }

    /** Wakes the local hydraulic neighborhood after an external Geometry mutation. */
    public void activateAt(int x, int y, int z) {
        activity.activate(x, y, z);
    }

    public int activeCellCount() {
        return activity.size();
    }

    private List<MutableTransfer> planTransfers(List<LiquidCell> activeCells) {
        TreeSet<LiquidEdge> edges = new TreeSet<>();
        for (LiquidCell cell : activeCells) {
            for (CellFace face : CellFace.values()) {
                edges.add(LiquidEdge.of(cell, cell.offset(face)));
            }
        }

        List<MutableTransfer> transfers = new ArrayList<>();
        for (LiquidEdge edge : edges) {
            MutableTransfer transfer = plan(edge);
            if (transfer != null) transfers.add(transfer);
        }
        return transfers;
    }

    private MutableTransfer plan(LiquidEdge edge) {
        LiquidCell first = edge.first();
        LiquidCell second = edge.second();
        Shape firstShape = shape(first);
        Shape secondShape = shape(second);

        CellFace firstToSecond = edge.faceFromFirst();
        long openingFloor = sharedOpeningFloor(
                first, firstShape, firstToSecond, second, secondShape);
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
                    first, firstShape, firstAmount, firstHead,
                    second, secondShape, secondAmount,
                    openingFloor, firstToSecond, firstType);
        }

        if (secondType == null) return null;
        return planDirected(
                second, secondShape, secondAmount, secondHead,
                first, firstShape, firstAmount,
                openingFloor, firstToSecond.opposite(), secondType);
    }

    private MutableTransfer planDirected(
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
                CellVolume.EMPTY, destinationCapacity - destinationAmount);
        if (destinationFree == CellVolume.EMPTY) return null;

        int retainedBelowOpening = retainedBelowOpening(source, sourceShape, openingFloor);
        int availableAboveOpening = Math.max(
                CellVolume.EMPTY, sourceAmount - retainedBelowOpening);

        int retainedSurface = direction.dz() == 0
                ? CellVolume.requireValid(surfaceRetention.capacityAt(
                        type, source.x(), source.y(), source.z()))
                : CellVolume.EMPTY;
        int mobileAboveOpening = Math.max(
                CellVolume.EMPTY, availableAboveOpening - retainedSurface);

        int maximumTransfer = Math.min(mobileAboveOpening, destinationFree);
        if (maximumTransfer <= CellVolume.EMPTY) return null;

        int equilibriumTransfer = equilibriumTransfer(
                source, sourceShape, sourceAmount,
                destination, destinationShape, destinationAmount,
                maximumTransfer);
        int relaxedTransfer = equilibriumTransfer / RELAXATION_DIVISOR;
        if (relaxedTransfer <= CellVolume.EMPTY) return null;

        return new MutableTransfer(source, destination, type, relaxedTransfer);
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
                source, sourceShape, sourceAmount,
                destination, destinationShape, destinationAmount,
                maximumTransfer);
        if (differenceAtMaximum > 0L) return maximumTransfer;

        int low = 1;
        int high = maximumTransfer;
        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            long difference = headDifferenceAfterTransfer(
                    source, sourceShape, sourceAmount,
                    destination, destinationShape, destinationAmount,
                    middle);
            if (difference <= 0L) high = middle;
            else low = middle + 1;
        }

        int crossing = low;
        int before = crossing - 1;
        long crossingDifference = Math.abs(headDifferenceAfterTransfer(
                source, sourceShape, sourceAmount,
                destination, destinationShape, destinationAmount,
                crossing));
        long beforeDifference = Math.abs(headDifferenceAfterTransfer(
                source, sourceShape, sourceAmount,
                destination, destinationShape, destinationAmount,
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

    private void limitOutgoing(List<MutableTransfer> transfers) {
        Map<LiquidCell, List<MutableTransfer>> outgoing = new TreeMap<>();
        for (MutableTransfer transfer : transfers) {
            outgoing.computeIfAbsent(
                    transfer.source, ignored -> new ArrayList<>()).add(transfer);
        }

        for (Map.Entry<LiquidCell, List<MutableTransfer>> entry : outgoing.entrySet()) {
            LiquidCell source = entry.getKey();
            List<MutableTransfer> group = entry.getValue();
            group.sort(Comparator.comparing(transfer -> transfer.destination));

            int sourceAmount = amount(source);
            proportionallyLimit(group, sourceAmount / RELAXATION_DIVISOR);

            int verticalOut = CellVolume.EMPTY;
            List<MutableTransfer> horizontal = new ArrayList<>();
            for (MutableTransfer transfer : group) {
                if (transfer.amount <= CellVolume.EMPTY) continue;
                if (transfer.destination.z() != source.z()) {
                    verticalOut = Math.addExact(verticalOut, transfer.amount);
                } else {
                    horizontal.add(transfer);
                }
            }

            if (!horizontal.isEmpty()) {
                LiquidTypeId sourceType = type(source);
                int retainedSurface = CellVolume.requireValid(
                        surfaceRetention.capacityAt(
                                sourceType, source.x(), source.y(), source.z()));
                int horizontalLimit = Math.max(
                        CellVolume.EMPTY,
                        sourceAmount - verticalOut - retainedSurface);
                proportionallyLimit(horizontal, horizontalLimit);
            }
        }
    }

    /**
     * Prevents deterministic ordering from becoming an accidental mixing rule.
     * A dry cell simultaneously targeted by more than one liquid type accepts none.
     */
    private void rejectContestedIncomingTypes(List<MutableTransfer> transfers) {
        Map<LiquidCell, List<MutableTransfer>> incoming = incomingGroups(transfers);
        for (Map.Entry<LiquidCell, List<MutableTransfer>> entry : incoming.entrySet()) {
            if (type(entry.getKey()) != null) continue;

            LiquidTypeId candidate = null;
            boolean contested = false;
            for (MutableTransfer transfer : entry.getValue()) {
                if (transfer.amount <= CellVolume.EMPTY) continue;
                if (candidate == null) candidate = transfer.type;
                else if (!candidate.equals(transfer.type)) {
                    contested = true;
                    break;
                }
            }
            if (contested) {
                for (MutableTransfer transfer : entry.getValue()) {
                    transfer.amount = CellVolume.EMPTY;
                }
            }
        }
    }

    private void limitIncoming(List<MutableTransfer> transfers) {
        Map<LiquidCell, List<MutableTransfer>> incoming = incomingGroups(transfers);
        for (Map.Entry<LiquidCell, List<MutableTransfer>> entry : incoming.entrySet()) {
            LiquidCell destination = entry.getKey();
            List<MutableTransfer> group = entry.getValue();
            group.sort(Comparator.comparing(transfer -> transfer.source));

            int freeCapacity = Math.max(
                    CellVolume.EMPTY,
                    CellSpace.capacity(shape(destination)) - amount(destination));
            proportionallyLimit(group, freeCapacity);
        }
    }

    private static Map<LiquidCell, List<MutableTransfer>> incomingGroups(
            List<MutableTransfer> transfers) {
        Map<LiquidCell, List<MutableTransfer>> incoming = new TreeMap<>();
        for (MutableTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) continue;
            incoming.computeIfAbsent(
                    transfer.destination, ignored -> new ArrayList<>()).add(transfer);
        }
        return incoming;
    }

    private static void proportionallyLimit(
            List<MutableTransfer> transfers,
            int limit) {

        long total = 0L;
        for (MutableTransfer transfer : transfers) total += transfer.amount;
        if (total <= limit) return;
        if (limit <= CellVolume.EMPTY) {
            for (MutableTransfer transfer : transfers) transfer.amount = CellVolume.EMPTY;
            return;
        }

        int assigned = 0;
        for (MutableTransfer transfer : transfers) {
            int reduced = (int) (((long) transfer.amount * limit) / total);
            transfer.amount = reduced;
            assigned += reduced;
        }

        int remainder = limit - assigned;
        for (MutableTransfer transfer : transfers) {
            if (remainder == 0) break;
            transfer.amount++;
            remainder--;
        }
    }

    private long commit(List<MutableTransfer> transfers) {
        Map<LiquidCell, Integer> deltas = new TreeMap<>();
        Map<LiquidCell, LiquidTypeId> incomingTypes = new TreeMap<>();
        long moved = 0L;

        for (MutableTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) continue;

            deltas.merge(transfer.source, -transfer.amount, Integer::sum);
            deltas.merge(transfer.destination, transfer.amount, Integer::sum);
            LiquidTypeId previous = incomingTypes.putIfAbsent(
                    transfer.destination, transfer.type);
            if (previous != null && !previous.equals(transfer.type)) {
                throw new IllegalStateException(
                        "contested liquid destination escaped planning: "
                                + transfer.destination);
            }
            moved = Math.addExact(moved, transfer.amount);
            recordFlow(transfer);
        }

        if (moved == CellVolume.EMPTY) return CellVolume.EMPTY;

        long conservation = 0L;
        for (int delta : deltas.values()) conservation += delta;
        if (conservation != 0L) {
            throw new IllegalStateException(
                    "liquid flow plan does not conserve volume: " + conservation);
        }

        for (Map.Entry<LiquidCell, Integer> entry : deltas.entrySet()) {
            LiquidCell cell = entry.getKey();
            int delta = entry.getValue();
            int nextAmount = Math.addExact(amount(cell), delta);
            CellVolume.requireValid(nextAmount);

            LiquidTypeId nextType = delta > 0
                    ? incomingTypes.get(cell)
                    : type(cell);
            if (nextAmount > CellVolume.EMPTY && nextType == null) {
                throw new IllegalStateException(
                        "positive liquid cell has no type after flow: " + cell);
            }

            if (delta > 0) {
                LiquidTypeId resident = type(cell);
                if (resident != null && !resident.equals(nextType)) {
                    throw new IllegalStateException(
                            "liquid flow attempted implicit mixing at " + cell);
                }
                int capacity = CellSpace.capacity(shape(cell));
                if (nextAmount > capacity) {
                    throw new IllegalStateException(
                            "liquid flow exceeded destination capacity at "
                                    + cell + ": " + nextAmount + " > " + capacity);
                }
            }

            liquids.replaceFromFlow(cell, nextType, nextAmount);
        }

        for (MutableTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) continue;
            activity.activate(transfer.source);
            activity.activate(transfer.destination);
        }
        return moved;
    }

    private void recordFlow(MutableTransfer transfer) {
        LiquidFlowSample sample = new LiquidFlowSample(
                transfer.type,
                transfer.destination.x() - transfer.source.x(),
                transfer.destination.y() - transfer.source.y(),
                transfer.destination.z() - transfer.source.z(),
                transfer.amount);
        recordDominant(transfer.source, sample);
        recordDominant(transfer.destination, sample);
    }

    private void recordDominant(LiquidCell cell, LiquidFlowSample candidate) {
        LiquidFlowSample existing = lastFlow.get(cell);
        if (existing == null
                || candidate.amount() > existing.amount()
                || (candidate.amount() == existing.amount()
                        && candidate.type().compareTo(existing.type()) < 0)) {
            lastFlow.put(cell, candidate);
        }
    }

    private long sharedOpeningFloor(
            LiquidCell first,
            Shape firstShape,
            CellFace firstToSecond,
            LiquidCell second,
            Shape secondShape) {

        int firstFloor = CellSpace.boundaryOpeningFloor(firstShape, firstToSecond);
        int secondFloor = CellSpace.boundaryOpeningFloor(
                secondShape, firstToSecond.opposite());
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

    private long hydraulicHead(
            LiquidCell cell,
            Shape shape,
            int amount) {

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
        return CellVolume.requireValid(
                liquids.lookup().amount(cell.x(), cell.y(), cell.z()));
    }

    private LiquidTypeId type(LiquidCell cell) {
        return liquids.lookup().typeAt(cell.x(), cell.y(), cell.z());
    }

    private static long globalHeight(LiquidCell cell, int localHeight) {
        return baseHeight(cell) + localHeight;
    }

    private static long baseHeight(LiquidCell cell) {
        return (long) cell.z() * CellSpace.FULL_HEIGHT;
    }

    private static final class MutableTransfer {
        private final LiquidCell source;
        private final LiquidCell destination;
        private final LiquidTypeId type;
        private int amount;

        private MutableTransfer(
                LiquidCell source,
                LiquidCell destination,
                LiquidTypeId type,
                int amount) {
            this.source = source;
            this.destination = destination;
            this.type = type;
            this.amount = amount;
        }
    }

    private record LiquidEdge(
            LiquidCell first,
            LiquidCell second)
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
