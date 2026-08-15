package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;

record LiquidCell(
        int x,
        int y,
        int z)
        implements Comparable<LiquidCell> {

    LiquidCell offset(CellFace face) {
        return new LiquidCell(
                x + face.dx(),
                y + face.dy(),
                z + face.dz());
    }

    @Override
    public int compareTo(LiquidCell other) {
        int xOrder = Integer.compare(x, other.x);
        if (xOrder != 0) return xOrder;
        int yOrder = Integer.compare(y, other.y);
        return yOrder != 0 ? yOrder : Integer.compare(z, other.z);
    }
}
