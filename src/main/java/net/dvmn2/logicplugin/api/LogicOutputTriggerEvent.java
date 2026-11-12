package net.dvmn2.logicplugin.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Called on the main thread when an output goes from false to true, before its commands run.
 * Cancelling the event skips the commands of this trigger.
 */
public final class LogicOutputTriggerEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String circuit;
    private final int output;
    private final List<String> commands;
    private boolean cancelled;

    public LogicOutputTriggerEvent(String circuit, int output, List<String> commands) {
        this.circuit = circuit;
        this.output = output;
        this.commands = List.copyOf(commands);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** Name of the circuit. */
    public String getCircuit() {
        return circuit;
    }

    /** Numeric id of the output (the {@code 1} of {@code o1}). */
    public int getOutput() {
        return output;
    }

    /** Unmodifiable copy of the commands about to run. */
    public List<String> getCommands() {
        return commands;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
