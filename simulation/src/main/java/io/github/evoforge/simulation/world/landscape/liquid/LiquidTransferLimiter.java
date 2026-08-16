package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Applies deterministic conservation/stability/capacity bounds to planned liquid transfers. */
final class LiquidTransferLimiter {
    private final LiquidLookup liquids;
    private final GeometryLookup geometry;
    private final LiquidSurfaceRetentionLookup surfaceRetention;

    LiquidTransferLimiter(
            LiquidLookup liquids,
            GeometryLookup geometry,
            LiquidSurfaceRetentionLookup surfaceRetention) {
        if (liquids == null || geometry == null || surfaceRetention == null) {
            throw new IllegalArgumentException("liquid transfer limiter dependencies must not be null");
        }
        this.liquids = liquids;
        this.geometry = geometry;
        this.surfaceRetention = surfaceRetention;
    }

    void limit(List<LiquidTransfer> transfers) {
        limitOutgoing(transfers);
        rejectContestedIncomingTypes(transfers);
        limitIncoming(transfers);
    }

    private void limitOutgoing(List<LiquidTransfer> transfers) {
        Map<LiquidCell, List<LiquidTransfer>> outgoing = new TreeMap<>();
        for (LiquidTransfer transfer : transfers) {
            outgoing.computeIfAbsent(
                    transfer.source,
                    ignored -> new ArrayList<>()).add(transfer);
        }

        for (Map.Entry<LiquidCell, List<LiquidTransfer>> entry : outgoing.entrySet()) {
            LiquidCell source = entry.getKey();
            List<LiquidTransfer> group = entry.getValue();
            group.sort(Comparator.comparing(transfer -> transfer.destination));

            int sourceAmount = amount(source);
            proportionallyLimit(group, sourceAmount / 2);

            int verticalOut = CellVolume.EMPTY;
            List<LiquidTransfer> horizontal = new ArrayList<>();
            for (LiquidTransfer transfer : group) {
                if (transfer.amount <= CellVolume.EMPTY) continue;
                if (transfer.destination.z() != source.z()) {
                    verticalOut = Math.addExact(verticalOut, transfer.amount);
                } else {
                    horizontal.add(transfer);
                }
            }

            if (!horizontal.isEmpty()) {
                int retainedSurface = CellVolume.requireValid(surfaceRetention.capacityAt(
                        source.x(), source.y(), source.z()));
                int horizontalLimit = Math.max(
                        CellVolume.EMPTY,
                        sourceAmount - verticalOut - retainedSurface);
                proportionallyLimit(horizontal, horizontalLimit);
            }
        }
    }

    /** A dry cell simultaneously targeted by more than one liquid type accepts none. */
    private void rejectContestedIncomingTypes(List<LiquidTransfer> transfers) {
        Map<LiquidCell, List<LiquidTransfer>> incoming = incomingGroups(transfers);
        for (Map.Entry<LiquidCell, List<LiquidTransfer>> entry : incoming.entrySet()) {
            if (type(entry.getKey()) != null) continue;

            LiquidTypeId candidate = null;
            boolean contested = false;
            for (LiquidTransfer transfer : entry.getValue()) {
                if (transfer.amount <= CellVolume.EMPTY) continue;
                if (candidate == null) candidate = transfer.type;
                else if (!candidate.equals(transfer.type)) {
                    contested = true;
                    break;
                }
            }
            if (contested) {
                for (LiquidTransfer transfer : entry.getValue()) {
                    transfer.amount = CellVolume.EMPTY;
                }
            }
        }
    }

    private void limitIncoming(List<LiquidTransfer> transfers) {
        Map<LiquidCell, List<LiquidTransfer>> incoming = incomingGroups(transfers);
        for (Map.Entry<LiquidCell, List<LiquidTransfer>> entry : incoming.entrySet()) {
            LiquidCell destination = entry.getKey();
            List<LiquidTransfer> group = entry.getValue();
            group.sort(Comparator.comparing(transfer -> transfer.source));

            int freeCapacity = Math.max(
                    CellVolume.EMPTY,
                    CellSpace.capacity(geometry.find(
                            destination.x(), destination.y(), destination.z()))
                            - amount(destination));
            proportionallyLimit(group, freeCapacity);
        }
    }

    private static Map<LiquidCell, List<LiquidTransfer>> incomingGroups(
            List<LiquidTransfer> transfers) {
        Map<LiquidCell, List<LiquidTransfer>> incoming = new TreeMap<>();
        for (LiquidTransfer transfer : transfers) {
            if (transfer.amount <= CellVolume.EMPTY) continue;
            incoming.computeIfAbsent(
                    transfer.destination,
                    ignored -> new ArrayList<>()).add(transfer);
        }
        return incoming;
    }

    private static void proportionallyLimit(List<LiquidTransfer> transfers, int limit) {
        long total = 0L;
        for (LiquidTransfer transfer : transfers) total += transfer.amount;
        if (total <= limit) return;
        if (limit <= CellVolume.EMPTY) {
            for (LiquidTransfer transfer : transfers) transfer.amount = CellVolume.EMPTY;
            return;
        }

        int assigned = 0;
        for (LiquidTransfer transfer : transfers) {
            int reduced = (int) (((long) transfer.amount * limit) / total);
            transfer.amount = reduced;
            assigned += reduced;
        }

        int remainder = limit - assigned;
        for (LiquidTransfer transfer : transfers) {
            if (remainder == 0) break;
            transfer.amount++;
            remainder--;
        }
    }

    private int amount(LiquidCell cell) {
        return CellVolume.requireValid(liquids.amount(cell.x(), cell.y(), cell.z()));
    }

    private LiquidTypeId type(LiquidCell cell) {
        return liquids.typeAt(cell.x(), cell.y(), cell.z());
    }
}
