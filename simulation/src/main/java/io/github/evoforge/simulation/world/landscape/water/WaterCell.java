package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;

record WaterCell(
        int x,
        int y,
        int z)
        implements Comparable<WaterCell> {

    WaterCell offset(
            CellFace face) {

        return new WaterCell(
                x + face.dx(),
                y + face.dy(),
                z + face.dz());
    }

    @Override
    public int compareTo(
            WaterCell other) {

        int xOrder = Integer.compare(x, other.x);
        if (xOrder != 0) {
            return xOrder;
        }

        int yOrder = Integer.compare(y, other.y);
        if (yOrder != 0) {
            return yOrder;
        }

        return Integer.compare(z, other.z);
    }
}
