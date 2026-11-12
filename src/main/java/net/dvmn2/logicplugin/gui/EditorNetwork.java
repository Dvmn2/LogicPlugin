package net.dvmn2.logicplugin.gui;

import net.dvmn2.logicplugin.model.Cells;
import net.dvmn2.logicplugin.model.Circuit;
import net.dvmn2.logicplugin.model.EditMode;
import net.dvmn2.logicplugin.util.Wire;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin-message transport of the client-side circuit editor.
 *
 * <p>Every payload starts with {@code VarInt protocol}. UUIDs are two big-endian longs, strings are
 * VarInt-prefixed UTF-8, arrays are VarInt-prefixed bytes. A cell is one byte, see
 * {@link net.dvmn2.logicplugin.model.Cells}.
 *
 * <pre>
 * dvmn2:lg_open     server to client
 *   VarInt protocol, UUID circuit, String name, VarInt mode (0 = EDIT, 1 = SETUP),
 *   VarInt width, VarInt height, VarInt allowedMask, byte[] cells
 * dvmn2:lg_signals  server to client
 *   VarInt protocol, UUID circuit, byte[] litBits (bit i of the array = cell i is lit)
 * dvmn2:lg_close    server to client
 *   VarInt protocol, UUID circuit
 * dvmn2:lg_save     client to server
 *   VarInt protocol, UUID circuit, VarInt action (0 = SAVE, 1 = CANCEL),
 *   VarInt width, VarInt height, byte[] cells (empty for CANCEL)
 * </pre>
 *
 * <p>Incoming messages may arrive off the main thread, so they are decoded here and handed to the
 * receiver on the main thread. Sending MUST happen on the main thread.
 */
public final class EditorNetwork implements PluginMessageListener {

    /** Wire protocol version. */
    public static final int PROTOCOL = 1;
    public static final String OPEN = "dvmn2:lg_open";
    public static final String SIGNALS = "dvmn2:lg_signals";
    public static final String CLOSE = "dvmn2:lg_close";
    public static final String SAVE = "dvmn2:lg_save";

    private static final int ACTION_CANCEL = 1;

    /**
     * A decoded request from the editor.
     *
     * @param player  the sender
     * @param circuit circuit the editor was opened for
     * @param cancel  true when the player closed the editor without saving
     * @param width   width of the edited field, cells
     * @param height  height of the edited field, cells
     * @param cells   edited cells; empty for a cancel
     */
    public record SaveRequest(Player player, UUID circuit, boolean cancel, int width, int height, byte[] cells) {
    }

    private final Plugin plugin;
    private final Logger logger;
    private volatile Consumer<SaveRequest> receiver = request -> { };

    public EditorNetwork(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Registers the channels; {@code handler} is called on the main thread. */
    public void start(Consumer<SaveRequest> handler) {
        this.receiver = handler;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, OPEN);
        messenger.registerOutgoingPluginChannel(plugin, SIGNALS);
        messenger.registerOutgoingPluginChannel(plugin, CLOSE);
        messenger.registerIncomingPluginChannel(plugin, SAVE, this);
    }

    /** Unregisters every channel of the plugin. */
    public void shutdown() {
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin);
        messenger.unregisterIncomingPluginChannel(plugin);
    }

    /** Returns true if the client announced the editor channel, which means the mod is installed. */
    public boolean hasMod(Player player) {
        return player.getListeningPluginChannels().contains(OPEN);
    }

    /** Sends the whole circuit to the editor. */
    public void sendOpen(Player player, Circuit circuit, EditMode mode) {
        send(player, OPEN, write(out -> {
            Wire.writeUuid(out, circuit.id());
            Wire.writeString(out, circuit.name());
            Wire.writeVarInt(out, mode.ordinal());
            Wire.writeVarInt(out, circuit.width());
            Wire.writeVarInt(out, circuit.height());
            Wire.writeVarInt(out, circuit.allowedMask());
            Wire.writeBytes(out, circuit.cells());
        }));
    }

    /** Sends the lit bitmask of a circuit. */
    public void sendSignals(Player player, UUID circuit, byte[] bits) {
        send(player, SIGNALS, write(out -> {
            Wire.writeUuid(out, circuit);
            Wire.writeBytes(out, bits);
        }));
    }

    /** Tells the editor to close without sending anything back. */
    public void sendClose(Player player, UUID circuit) {
        send(player, CLOSE, write(out -> Wire.writeUuid(out, circuit)));
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!SAVE.equals(channel)) {
            return;
        }
        SaveRequest request;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (Wire.readVarInt(in) != PROTOCOL) {
                return;
            }
            UUID circuit = Wire.readUuid(in);
            boolean cancel = Wire.readVarInt(in) == ACTION_CANCEL;
            int width = Wire.readVarInt(in);
            int height = Wire.readVarInt(in);
            byte[] cells = Wire.readBytes(in, Cells.MAX_CELLS);
            request = new SaveRequest(player, circuit, cancel, width, height, cells);
        } catch (IOException exception) {
            logger.log(Level.FINE, "Malformed editor message from " + player.getName(), exception);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> receiver.accept(request));
    }

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(Body body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            Wire.writeVarInt(out, PROTOCOL);
            body.write(out);
        } catch (IOException exception) {
            // Writing to memory cannot fail.
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    private void send(Player player, String channel, byte[] data) {
        try {
            player.sendPluginMessage(plugin, channel, data);
        } catch (IllegalArgumentException exception) {
            logger.log(Level.FINE, "Cannot send " + channel + " to " + player.getName(), exception);
        }
    }
}
