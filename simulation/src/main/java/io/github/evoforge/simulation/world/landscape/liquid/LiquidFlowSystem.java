package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic local redistribution of finite free-liquid quantity.
 *
 * <p>The system owns no liquid state. It orchestrates a snapshot-derived hydraulic transfer plan,
 * deterministic transfer bounds and one authoritative aggregate commit through {@link LiquidSystem}.
 * Planning and limiting are pure with respect to authoritative liquid state; conservation checks,
 * mutation, neighborhood reactivation and latest-step diagnostics remain here at the mutation
 * boundary.</p>
 *
 * <p>Kinematic viscosity affects mobility exactly once at the planned edge transfer. The current
 * content model remains single-component per free cell: unlike liquids do not implicitly mix and
 * contested unlike inflow into one dry destination is suppressed symmetrically.</p>
 */
public final class LiquidFlowSystem {
    private final LiquidSystem liquids;
    private final GeometryLookup geometry;
    private final LiquidFlowActivity activity;
    private final LiquidTransferPlanner planner;
    private final LiquidTransferLimiter limiter;
    private final TreeMap<LiquidCell, LiquidFlowSample> lastFlow = new TreeMap<>();
    private final LiquidFlowLookup flowLookup =
            (x, y, z) -> lastFlow.get(new LiquidCell(x, y, z));

    public LiquidFlowSystem(
            LiquidSystem liquids,
            GeometryLookup geometry,
            LiquidTransportLookup transport) {
        this(liquids, geometry, LiquidSurfaceRetentionLookup.NONE, transport);
    }

    public LiquidFlowSystem(
            LiquidSystem liquids,
            GeometryLookup geometry,
            LiquidSurfaceRetentionLookup surfaceRetention,
            LiquidTransportLookup transport) {
        if (liquids == null
                || geometry == null
                || surfaceRetention == null
                || transport == null) {
            throw new IllegalArgumentException("liquid flow dependencies must not be null");
        }
        this.liquids = liquids;
        this.geometry = geometry;
        activity = liquids.flowActivity();
        planner = new LiquidTransferPlanner(
                liquids.lookup(),
                geometry,
                surfaceRetention,
                transport);
        limiter = new LiquidTransferLimiter(
                liquids.lookup(),
                geometry,
                surfaceRetention);
    }

    public LiquidFlowLookup flowLookup() {
        return flowLookup;
    }

    /** Runs one local solver step and returns total volume crossing cell boundaries. */
    public long update() {
        lastFlow.clear();

        List<LiquidCell> activeCells = activity.drainSorted();
        if (activeCells.isEmpty()) return CellVolume.EMPTY;

        List<LiquidTransfer> transfers = planner.plan(activeCells);
        if (transfers.isEmpty()) return CellVolume.EMPTY;

        limiter.limit(transfers);
        return commit(transfers);
    }

    /** Wakes the local hydraulic neighborhood after an external Geometry mutation. */
    public void activateAt(int x, int y, int z) {
        activity.activate(x, y, z);
    }

    public int activeCellCount() {
        return activity.size();
    }

    private long commit(List<LiquidTransfer> transfers) {
        Map<LiquidCell, Integer> deltas = new TreeMap<>();
        Map<LiquidCell, LiquidTypeId> incomingTypes = new TreeMap<>();
        Map<LiquidCell, FlowAccumulator> netFlow = new TreeMap<>();
        long moved = 0L;

        for (LiquidTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) continue;

            deltas.merge(transfer.source, -transfer.amount, Integer::sum);
            deltas.merge(transfer.destination, transfer.amount, Integer::sum);
            LiquidTypeId previous = incomingTypes.putIfAbsent(
                    transfer.destination,
                    transfer.type);
            if (previous != null && !previous.equals(transfer.type)) {
                throw new IllegalStateException(
                        "contested liquid destination escaped planning: " + transfer.destination);
            }
            moved = Math.addExact(moved, transfer.amount);
            accumulateNetFlow(netFlow, transfer);
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

        publishNetFlow(netFlow);
        for (LiquidTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) continue;
            activity.activate(transfer.source);
            activity.activate(transfer.destination);
        }
        return moved;
    }

    private static void accumulateNetFlow(
            Map<LiquidCell, FlowAccumulator> netFlow,
            LiquidTransfer transfer) {
        int dx = transfer.destination.x() - transfer.source.x();
        int dy = transfer.destination.y() - transfer.source.y();
        int dz = transfer.destination.z() - transfer.source.z();
        netFlow.computeIfAbsent(transfer.source, ignored -> new FlowAccumulator())
                .add(transfer.type, dx, dy, dz, transfer.amount);
        netFlow.computeIfAbsent(transfer.destination, ignored -> new FlowAccumulator())
                .add(transfer.type, dx, dy, dz, transfer.amount);
    }

    private void publishNetFlow(Map<LiquidCell, FlowAccumulator> netFlow) {
        for (Map.Entry<LiquidCell, FlowAccumulator> entry : netFlow.entrySet()) {
            LiquidFlowSample sample = entry.getValue().sample();
            if (sample != null) lastFlow.put(entry.getKey(), sample);
        }
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

    private static final class FlowAccumulator {
        private LiquidTypeId type;
        private long xFlux;
        private long yFlux;
        private long zFlux;

        private void add(
                LiquidTypeId type,
                int dx,
                int dy,
                int dz,
                int amount) {
            if (this.type == null) this.type = type;
            else if (!this.type.equals(type)) {
                throw new IllegalStateException(
                        "one liquid cell accumulated multiple flow identities");
            }
            xFlux = Math.addExact(xFlux, Math.multiplyExact((long) dx, amount));
            yFlux = Math.addExact(yFlux, Math.multiplyExact((long) dy, amount));
            zFlux = Math.addExact(zFlux, Math.multiplyExact((long) dz, amount));
        }

        private LiquidFlowSample sample() {
            long absX = Math.abs(xFlux);
            long absY = Math.abs(yFlux);
            long absZ = Math.abs(zFlux);
            if (absX == 0L && absY == 0L && absZ == 0L) return null;

            int dx = 0;
            int dy = 0;
            int dz = 0;
            long magnitude;
            if (absZ >= absX && absZ >= absY) {
                dz = Long.signum(zFlux);
                magnitude = absZ;
            } else if (absX >= absY) {
                dx = Long.signum(xFlux);
                magnitude = absX;
            } else {
                dy = Long.signum(yFlux);
                magnitude = absY;
            }
            int boundedMagnitude = (int) Math.min((long) CellVolume.FULL, magnitude);
            return new LiquidFlowSample(type, dx, dy, dz, boundedMagnitude);
        }
    }
}
