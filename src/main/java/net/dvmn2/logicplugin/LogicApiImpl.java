package net.dvmn2.logicplugin;

import net.dvmn2.logicplugin.api.LogicApi;
import net.dvmn2.logicplugin.gui.EditorNetwork;
import net.dvmn2.logicplugin.model.Circuit;
import net.dvmn2.logicplugin.model.InputPoint;
import net.dvmn2.logicplugin.model.OutputPoint;
import org.bukkit.entity.Player;

import java.util.List;

/** Implementation of {@link LogicApi}; delegates to the manager. Main thread only. */
public final class LogicApiImpl implements LogicApi {

    private static final String API_ACTOR = "";

    private final LogicManager manager;
    private final EditorNetwork network;

    public LogicApiImpl(LogicManager manager, EditorNetwork network) {
        this.manager = manager;
        this.network = network;
    }

    @Override
    public boolean hasMod(Player player) {
        return network.hasMod(player);
    }

    @Override
    public List<String> circuitNames() {
        return manager.allNames();
    }

    @Override
    public Result setInput(String circuit, int inputId, boolean value) {
        Circuit found = manager.find(circuit);
        if (found == null) {
            return Result.UNKNOWN_CIRCUIT;
        }
        InputPoint point = found.inputs().get(inputId);
        if (point == null) {
            return Result.UNKNOWN_INPUT;
        }
        manager.setInput(found, point, value, API_ACTOR);
        return Result.SUCCESS;
    }

    @Override
    public boolean isOutputOn(String circuit, int outputId) {
        Circuit found = manager.find(circuit);
        OutputPoint point = found == null ? null : found.outputs().get(outputId);
        return point != null && point.last();
    }
}
