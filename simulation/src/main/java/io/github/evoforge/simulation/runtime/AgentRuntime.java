package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.agent.decision.AgentSystem;
import io.github.evoforge.simulation.world.agent.need.NeedSystem;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionSystem;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionSystem;
import io.github.evoforge.simulation.world.agent.search.AgentSearchSystem;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockSystem;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthSystem;

/** Runtime autonomous-agent capabilities shared with presentation. */
record AgentRuntime(
        VisionSystem vision,
        NeedSystem needs,
        NeedProgressionSystem needProgression,
        ConsumableStockSystem consumableStocks,
        GrowthSystem growth,
        AgentSystem agents,
        AgentSearchSystem searches) { }
