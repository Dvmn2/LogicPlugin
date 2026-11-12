package net.dvmn2.logicplugin;

import net.dvmn2.logicplugin.model.Cells;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * @param language              message language: ru, en or auto
 * @param maxWidthCells         maximum circuit width, cells
 * @param maxHeightCells        maximum circuit height, cells
 * @param tickIntervalTicks     simulation step period, ticks
 * @param saveIntervalSeconds   period of writing modified circuits, seconds
 * @param maxCircuitsPerPlayer  circuits one player may own
 * @param maxCommandsPerOutput  commands one output may run
 * @param maxCommandLengthChars length of one output command, characters
 * @param editorTimeoutSeconds  lifetime of an editor session, seconds
 */
public record LogicSettings(String language, int maxWidthCells, int maxHeightCells, int tickIntervalTicks,
        int saveIntervalSeconds, int maxCircuitsPerPlayer, int maxCommandsPerOutput,
        int maxCommandLengthChars, int editorTimeoutSeconds) {

    private static final String ROOT = "settings.";
    private static final int DEFAULT_MAX_WIDTH = 64;
    private static final int DEFAULT_MAX_HEIGHT = 64;
    private static final int DEFAULT_TICK_INTERVAL = 4;
    private static final int DEFAULT_SAVE_INTERVAL = 30;
    private static final int DEFAULT_MAX_CIRCUITS = 5;
    private static final int DEFAULT_MAX_COMMANDS = 8;
    private static final int DEFAULT_MAX_COMMAND_LENGTH = 256;
    private static final int DEFAULT_EDITOR_TIMEOUT = 900;

    /** Reads and validates the configuration; invalid values fall back to defaults with a warning. */
    public static LogicSettings parse(ConfigurationSection config, Logger log) {
        return new LogicSettings(
                config.getString(ROOT + "language", "auto"),
                readInt(config, log, "max-width-cells", DEFAULT_MAX_WIDTH, 1, Cells.MAX_DIMENSION),
                readInt(config, log, "max-height-cells", DEFAULT_MAX_HEIGHT, 1, Cells.MAX_DIMENSION),
                readInt(config, log, "tick-interval-ticks", DEFAULT_TICK_INTERVAL, 1, 100),
                readInt(config, log, "save-interval-seconds", DEFAULT_SAVE_INTERVAL, 5, 3600),
                readInt(config, log, "max-circuits-per-player", DEFAULT_MAX_CIRCUITS, 1, 1000),
                readInt(config, log, "max-commands-per-output", DEFAULT_MAX_COMMANDS, 1, 64),
                readInt(config, log, "max-command-length-chars", DEFAULT_MAX_COMMAND_LENGTH, 1, 1024),
                readInt(config, log, "editor-timeout-seconds", DEFAULT_EDITOR_TIMEOUT, 30, 86400));
    }

    private static int readInt(ConfigurationSection config, Logger log, String key, int def, int min, int max) {
        int value = config.getInt(ROOT + key, def);
        if (value < min || value > max) {
            log.warning(ROOT + key + " must be in " + min + ".." + max + ", got " + value + "; using " + def);
            return def;
        }
        return value;
    }
}
