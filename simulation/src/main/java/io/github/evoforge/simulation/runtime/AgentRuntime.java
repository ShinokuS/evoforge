package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.agents.decision.AgentSystem;
import io.github.evoforge.simulation.agents.need.NeedSystem;
import io.github.evoforge.simulation.agents.need.progression.NeedProgressionSystem;
import io.github.evoforge.simulation.agents.perception.vision.VisionSystem;
import io.github.evoforge.simulation.agents.search.AgentSearchSystem;
import io.github.evoforge.simulation.world.object.stock.ConsumableStockSystem;
import io.github.evoforge.simulation.world.object.stock.growth.GrowthSystem;

/** Runtime autonomous-agent capabilities shared with presentation. */
record AgentRuntime(
        VisionSystem vision,
        NeedSystem needs,
        NeedProgressionSystem needProgression,
        ConsumableStockSystem consumableStocks,
        GrowthSystem growth,
        AgentSystem agents,
        AgentSearchSystem searches) { }
