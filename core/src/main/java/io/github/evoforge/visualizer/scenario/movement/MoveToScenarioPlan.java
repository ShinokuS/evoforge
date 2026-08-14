package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.world.pathfinding.PathRoute;

record MoveToScenarioPlan(
        int goalX,
        int goalY,
        int goalZ,
        PathRoute route) { }
