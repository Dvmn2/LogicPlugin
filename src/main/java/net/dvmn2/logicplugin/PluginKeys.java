package net.dvmn2.logicplugin;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Factory of the {@code PersistentDataContainer} keys that mark every entity of the plugin.
 * Built from the plugin instance and passed through constructors.
 */
public final class PluginKeys {

    /** Role value of the clickable board entity. */
    public static final String ROLE_BOARD = "board";
    /** Role value of the board label display. */
    public static final String ROLE_BOARD_LABEL = "board-label";
    /** Role value of the clickable switch entity. */
    public static final String ROLE_SWITCH = "switch";
    /** Role value of the switch indicator display. */
    public static final String ROLE_SWITCH_DISPLAY = "switch-display";

    /** UUID string of the circuit the entity belongs to (the group id of paired entities). */
    public final NamespacedKey circuit;
    /** One of the {@code ROLE_*} values. */
    public final NamespacedKey role;
    /** Input id (integer) of switch entities. */
    public final NamespacedKey input;
    /** UUID string of the paired entity. */
    public final NamespacedKey partner;

    public PluginKeys(Plugin plugin) {
        this.circuit = new NamespacedKey(plugin, "circuit");
        this.role = new NamespacedKey(plugin, "role");
        this.input = new NamespacedKey(plugin, "input");
        this.partner = new NamespacedKey(plugin, "partner");
    }
}
