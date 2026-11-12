package net.dvmn2.logicplugin.api;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * API for other plugins. Obtain with:
 * <pre>
 * LogicApi api = Bukkit.getServicesManager().load(LogicApi.class);
 * </pre>
 * All methods MUST be called from the server main thread.
 */
public interface LogicApi {

    /** Outcome of {@link #setInput(String, int, boolean)}. */
    enum Result { SUCCESS, UNKNOWN_CIRCUIT, UNKNOWN_INPUT }

    /** Returns true if the player has the Logic client mod installed. */
    boolean hasMod(Player player);

    /** Returns the names of all circuits, sorted. */
    List<String> circuitNames();

    /**
     * Sets an input of a circuit.
     *
     * @param circuit circuit name, case-insensitive
     * @param inputId numeric id of the input (the {@code 1} of {@code i1})
     * @param value   the new value
     * @return the outcome
     */
    Result setInput(String circuit, int inputId, boolean value);

    /**
     * Reads the last known value of an output.
     *
     * @param circuit  circuit name, case-insensitive
     * @param outputId numeric id of the output (the {@code 1} of {@code o1})
     * @return true when the output is on; false when it is off or does not exist
     */
    boolean isOutputOn(String circuit, int outputId);
}
