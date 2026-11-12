package net.dvmn2.logicplugin;

import net.dvmn2.logicplugin.api.LogicOutputTriggerEvent;
import net.dvmn2.logicplugin.gui.EditorNetwork;
import net.dvmn2.logicplugin.model.CellType;
import net.dvmn2.logicplugin.model.Circuit;
import net.dvmn2.logicplugin.model.EditMode;
import net.dvmn2.logicplugin.model.InputPoint;
import net.dvmn2.logicplugin.model.OutputPoint;
import net.dvmn2.logicplugin.model.Placement;
import net.dvmn2.logicplugin.model.SwitchRef;
import net.dvmn2.logicplugin.storage.CircuitCodec;
import net.dvmn2.logicplugin.storage.CircuitStore;
import net.dvmn2.logicplugin.storage.dto.CircuitDto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Runtime state of the plugin: circuits, editor sessions, simulation, entities and persistence.
 * Main thread only.
 */
public final class LogicManager {

    /** Outcome of {@link #create}. */
    public enum CreateResult { SUCCESS, INVALID_NAME, NAME_TAKEN, INVALID_SIZE, LIMIT_REACHED, WORLD_MISSING }

    /** Outcome of {@link #spawnSwitch}. */
    public enum SwitchResult { SUCCESS, UNKNOWN_INPUT, WORLD_MISSING }

    /** Outcome of output command changes. */
    public enum CommandResult { SUCCESS, UNKNOWN_OUTPUT, INVALID_COMMAND, LIMIT_REACHED, BAD_INDEX }

    /** Outcome of {@link #openEditor}. */
    public enum OpenResult { SUCCESS, BUSY, NEED_MOD }

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long TICKS_PER_SECOND = 20L;
    private static final float BOARD_SIZE = 1.0f;
    private static final float SWITCH_SIZE = 0.6f;
    private static final double LABEL_OFFSET = 1.2;
    private static final double SWITCH_LABEL_OFFSET = 0.8;
    private static final int CHUNK_SHIFT = 4;

    /** An open editor: who edits what, in which mode, and what the client last saw. */
    private static final class Session {
        private final Circuit circuit;
        private final EditMode mode;
        private final long openedNanos;
        private byte[] lastLit;

        private Session(Circuit circuit, EditMode mode, long openedNanos) {
            this.circuit = circuit;
            this.mode = mode;
            this.openedNanos = openedNanos;
        }
    }

    private final LogicPlugin plugin;
    private final Supplier<LogicSettings> settings;
    private final CircuitStore store;
    private final PluginKeys keys;
    private final EditorNetwork network;
    private final Map<UUID, Circuit> byId = new LinkedHashMap<>();
    private final Map<String, Circuit> byName = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private @Nullable BukkitTask simulationTask;
    private @Nullable BukkitTask saveTask;

    public LogicManager(LogicPlugin plugin, Supplier<LogicSettings> settings, CircuitStore store,
            PluginKeys keys, EditorNetwork network) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.keys = keys;
        this.network = network;
    }

    // ---------- Lifecycle ----------

    /** Loads circuits, removes orphaned entities, opens the channels and starts the periodic tasks. */
    public void start() {
        for (CircuitDto dto : store.loadAll()) {
            Circuit circuit = CircuitCodec.fromDto(dto, plugin.getLogger());
            if (circuit == null) {
                continue;
            }
            if (byId.containsKey(circuit.id()) || byName.containsKey(circuit.name())) {
                plugin.getLogger().warning("Duplicate circuit " + circuit.name() + " skipped");
                continue;
            }
            register(circuit);
        }
        sweepOrphans();
        network.start(this::onSaveRequest);
        scheduleTasks();
    }

    /** Applies new settings: restarts the periodic tasks with the new periods. */
    public void onSettingsReloaded() {
        scheduleTasks();
    }

    /** Stops the tasks, closes editors and writes everything; blocks until the writes finish. */
    public void shutdown() {
        cancelTasks();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                network.sendClose(player, entry.getValue().circuit.id());
            }
            entry.getValue().circuit.setEditor(null);
        }
        sessions.clear();
        network.shutdown();
        for (Circuit circuit : byId.values()) {
            store.saveAsync(CircuitCodec.toDto(circuit));
        }
        store.close();
    }

    public int circuitCount() {
        return byId.size();
    }

    private void scheduleTasks() {
        cancelTasks();
        long interval = settings.get().tickIntervalTicks();
        simulationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        long savePeriod = settings.get().saveIntervalSeconds() * TICKS_PER_SECOND;
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveDirty, savePeriod, savePeriod);
    }

    private void cancelTasks() {
        if (simulationTask != null) {
            simulationTask.cancel();
            simulationTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    private void register(Circuit circuit) {
        byId.put(circuit.id(), circuit);
        byName.put(circuit.name(), circuit);
    }

    // ---------- Queries and access ----------

    public @Nullable Circuit find(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    private @Nullable Circuit findById(String raw) {
        try {
            return byId.get(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            // A foreign or damaged PDC value: treat as unknown.
            return null;
        }
    }

    /** Returns true for the owner and for holders of {@code logic.admin}. */
    public boolean canManage(CommandSender sender, Circuit circuit) {
        return sender.hasPermission(Permissions.ADMIN)
                || sender instanceof Player player && circuit.isOwner(player.getUniqueId());
    }

    /** Returns true for everyone who may edit the circuit and drive its inputs by command. */
    public boolean canEdit(CommandSender sender, Circuit circuit) {
        return canManage(sender, circuit)
                || sender instanceof Player player && circuit.isTrusted(player.getUniqueId());
    }

    /** Returns the circuits the sender may edit, sorted by name. */
    public List<Circuit> accessible(CommandSender sender) {
        List<Circuit> result = new ArrayList<>();
        for (Circuit circuit : byId.values()) {
            if (canEdit(sender, circuit)) {
                result.add(circuit);
            }
        }
        result.sort(Comparator.comparing(Circuit::name));
        return result;
    }

    /** Returns all circuit names, sorted. */
    public List<String> allNames() {
        List<String> names = new ArrayList<>(byName.keySet());
        names.sort(Comparator.naturalOrder());
        return names;
    }

    // ---------- Create and delete ----------

    /** Creates a circuit and its board at the block of {@code location}. */
    public CreateResult create(Player owner, String rawName, int width, int height, Location location) {
        LogicSettings current = settings.get();
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!NAME_PATTERN.matcher(name).matches()) {
            return CreateResult.INVALID_NAME;
        }
        if (width < 1 || width > current.maxWidthCells() || height < 1 || height > current.maxHeightCells()) {
            return CreateResult.INVALID_SIZE;
        }
        if (byName.containsKey(name)) {
            return CreateResult.NAME_TAKEN;
        }
        boolean unlimited = owner.hasPermission(Permissions.ADMIN);
        if (!unlimited && countOwned(owner.getUniqueId()) >= current.maxCircuitsPerPlayer()) {
            return CreateResult.LIMIT_REACHED;
        }
        World world = location.getWorld();
        if (world == null) {
            return CreateResult.WORLD_MISSING;
        }
        UUID id = UUID.randomUUID();
        Location base = blockCenter(location);
        Interaction board = world.spawn(base, Interaction.class, entity -> {
            entity.setInteractionWidth(BOARD_SIZE);
            entity.setInteractionHeight(BOARD_SIZE);
            entity.setResponsive(true);
            entity.setPersistent(true);
            mark(entity, id, PluginKeys.ROLE_BOARD);
        });
        TextDisplay label = world.spawn(base.clone().add(0, LABEL_OFFSET, 0), TextDisplay.class, entity -> {
            entity.text(Lang.get(Lang.Key.BOARD_LABEL, null, Placeholder.unparsed("name", name)));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
            mark(entity, id, PluginKeys.ROLE_BOARD_LABEL);
        });
        pair(board, label);
        Placement placement = new Placement(world.getUID(), base.getX(), base.getY(), base.getZ(),
                board.getUniqueId(), label.getUniqueId());
        Circuit circuit = new Circuit(id, name, owner.getUniqueId(), placement, width, height,
                new byte[width * height], CellType.defaultAllowedMask());
        circuit.markDirty();
        register(circuit);
        return CreateResult.SUCCESS;
    }

    /** Deletes a circuit with all of its entities and its file. */
    public void delete(Circuit circuit) {
        UUID editor = circuit.editor();
        if (editor != null) {
            Player player = Bukkit.getPlayer(editor);
            if (player != null) {
                network.sendClose(player, circuit.id());
                player.sendMessage(Lang.get(Lang.Key.EDIT_CLOSED, player));
            }
            sessions.remove(editor);
            circuit.setEditor(null);
        }
        despawn(circuit.placement().boardEntity());
        despawn(circuit.placement().labelEntity());
        for (InputPoint point : circuit.inputs().values()) {
            despawnSwitches(point);
        }
        byId.remove(circuit.id());
        byName.remove(circuit.name());
        store.deleteAsync(circuit.id());
    }

    private int countOwned(UUID player) {
        int count = 0;
        for (Circuit circuit : byId.values()) {
            if (circuit.isOwner(player)) {
                count++;
            }
        }
        return count;
    }

    // ---------- Inputs and switches ----------

    /** Sets an input and refreshes its switch indicators; returns true when the value changed. */
    public boolean setInput(Circuit circuit, InputPoint point, boolean value, String actor) {
        boolean changed = circuit.setInput(point, value, actor);
        if (changed) {
            refreshSwitches(point);
        }
        return changed;
    }

    /** Creates a physical switch for an input at the block of {@code location}. */
    public SwitchResult spawnSwitch(Circuit circuit, int inputId, Location location) {
        InputPoint point = circuit.inputs().get(inputId);
        if (point == null) {
            return SwitchResult.UNKNOWN_INPUT;
        }
        World world = location.getWorld();
        if (world == null) {
            return SwitchResult.WORLD_MISSING;
        }
        Location base = blockCenter(location);
        Interaction clickable = world.spawn(base, Interaction.class, entity -> {
            entity.setInteractionWidth(SWITCH_SIZE);
            entity.setInteractionHeight(SWITCH_SIZE);
            entity.setResponsive(true);
            entity.setPersistent(true);
            mark(entity, circuit.id(), PluginKeys.ROLE_SWITCH);
            entity.getPersistentDataContainer().set(keys.input, PersistentDataType.INTEGER, inputId);
        });
        TextDisplay indicator = world.spawn(base.clone().add(0, SWITCH_LABEL_OFFSET, 0), TextDisplay.class, entity -> {
            entity.text(switchText(point));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
            mark(entity, circuit.id(), PluginKeys.ROLE_SWITCH_DISPLAY);
            entity.getPersistentDataContainer().set(keys.input, PersistentDataType.INTEGER, inputId);
        });
        pair(clickable, indicator);
        point.addSwitch(new SwitchRef(clickable.getUniqueId(), indicator.getUniqueId()));
        circuit.markDirty();
        return SwitchResult.SUCCESS;
    }

    private Component switchText(InputPoint point) {
        return Lang.get(point.state() ? Lang.Key.SWITCH_ON : Lang.Key.SWITCH_OFF, null,
                Placeholder.unparsed("input", "i" + point.id()));
    }

    private void refreshSwitches(InputPoint point) {
        for (SwitchRef ref : point.switches()) {
            if (Bukkit.getEntity(ref.display()) instanceof TextDisplay display) {
                display.text(switchText(point));
            }
        }
    }

    private void despawnSwitches(InputPoint point) {
        for (SwitchRef ref : point.switches()) {
            despawn(ref.interaction());
            despawn(ref.display());
        }
    }

    // ---------- Output commands ----------

    /** Adds a command to an output. A leading slash is dropped. */
    public CommandResult addCommand(Circuit circuit, int outputId, String raw) {
        OutputPoint point = circuit.outputs().get(outputId);
        if (point == null) {
            return CommandResult.UNKNOWN_OUTPUT;
        }
        String command = raw.strip();
        if (command.startsWith("/")) {
            command = command.substring(1).strip();
        }
        LogicSettings current = settings.get();
        if (command.isEmpty() || command.length() > current.maxCommandLengthChars()
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            return CommandResult.INVALID_COMMAND;
        }
        if (point.commands().size() >= current.maxCommandsPerOutput()) {
            return CommandResult.LIMIT_REACHED;
        }
        point.addCommand(command);
        circuit.markDirty();
        return CommandResult.SUCCESS;
    }

    /** Removes the command with a one-based number from an output. */
    public CommandResult removeCommand(Circuit circuit, int outputId, int number) {
        OutputPoint point = circuit.outputs().get(outputId);
        if (point == null) {
            return CommandResult.UNKNOWN_OUTPUT;
        }
        if (!point.removeCommand(number - 1)) {
            return CommandResult.BAD_INDEX;
        }
        circuit.markDirty();
        return CommandResult.SUCCESS;
    }

    // ---------- Interaction ----------

    /**
     * Handles a right click on an entity of the plugin.
     *
     * @return true when the entity belongs to the plugin and the click was consumed
     */
    public boolean handleInteraction(Player player, Interaction entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String role = data.get(keys.role, PersistentDataType.STRING);
        String circuitId = data.get(keys.circuit, PersistentDataType.STRING);
        if (role == null || circuitId == null) {
            return false;
        }
        Circuit circuit = findById(circuitId);
        if (circuit == null || !circuit.ownsEntity(entity.getUniqueId())) {
            entity.remove();
            return true;
        }
        switch (role) {
            case PluginKeys.ROLE_BOARD -> openBoard(player, circuit);
            case PluginKeys.ROLE_SWITCH -> toggleSwitch(player, circuit, data);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void openBoard(Player player, Circuit circuit) {
        if (!player.hasPermission(Permissions.USE) || !canEdit(player, circuit)) {
            player.sendMessage(Lang.get(Lang.Key.NO_ACCESS, player));
            return;
        }
        EditMode mode = player.isSneaking() && canManage(player, circuit) ? EditMode.SETUP : EditMode.EDIT;
        switch (openEditor(player, circuit, mode)) {
            case BUSY -> player.sendMessage(Lang.get(Lang.Key.EDITOR_BUSY, player));
            case NEED_MOD -> player.sendMessage(Lang.get(Lang.Key.NEED_MOD, player));
            case SUCCESS -> player.sendMessage(Lang.get(
                    mode == EditMode.SETUP ? Lang.Key.SETUP_OPENED : Lang.Key.EDITOR_OPENED, player,
                    Placeholder.unparsed("name", circuit.name())));
        }
    }

    private void toggleSwitch(Player player, Circuit circuit, PersistentDataContainer data) {
        if (!player.hasPermission(Permissions.USE)) {
            player.sendMessage(Lang.get(Lang.Key.NO_ACCESS, player));
            return;
        }
        Integer inputId = data.get(keys.input, PersistentDataType.INTEGER);
        InputPoint point = inputId == null ? null : circuit.inputs().get(inputId);
        if (point == null) {
            player.sendMessage(Lang.get(Lang.Key.UNKNOWN_INPUT, player,
                    Placeholder.unparsed("input", inputId == null ? "?" : "i" + inputId)));
            return;
        }
        setInput(circuit, point, !point.state(), player.getName());
    }

    // ---------- Editor sessions ----------

    /** Opens the client editor for a player if the circuit is free and the client has the mod. */
    public OpenResult openEditor(Player player, Circuit circuit, EditMode mode) {
        UUID editor = circuit.editor();
        if (editor != null && !editor.equals(player.getUniqueId())) {
            return OpenResult.BUSY;
        }
        if (!network.hasMod(player)) {
            return OpenResult.NEED_MOD;
        }
        releaseSession(player.getUniqueId());
        Session session = new Session(circuit, mode, System.nanoTime());
        sessions.put(player.getUniqueId(), session);
        circuit.setEditor(player.getUniqueId());
        network.sendOpen(player, circuit, mode);
        session.lastLit = circuit.litBits();
        network.sendSignals(player, circuit.id(), session.lastLit);
        return OpenResult.SUCCESS;
    }

    /** Releases the editor lock of a player who left. */
    public void onQuit(Player player) {
        releaseSession(player.getUniqueId());
    }

    private void releaseSession(UUID player) {
        Session session = sessions.remove(player);
        if (session != null) {
            session.circuit.setEditor(null);
        }
    }

    private void onSaveRequest(EditorNetwork.SaveRequest request) {
        Player player = request.player();
        if (!player.isOnline()) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.circuit.id().equals(request.circuit())) {
            return;
        }
        releaseSession(player.getUniqueId());
        Circuit circuit = session.circuit;
        if (request.cancel()) {
            player.sendMessage(Lang.get(Lang.Key.EDIT_CANCELLED, player));
            return;
        }
        boolean allowed = session.mode == EditMode.SETUP ? canManage(player, circuit) : canEdit(player, circuit);
        if (!allowed || !byId.containsKey(circuit.id())) {
            player.sendMessage(Lang.get(Lang.Key.NO_ACCESS, player));
            return;
        }
        if (request.width() != circuit.width() || request.height() != circuit.height()) {
            player.sendMessage(Lang.get(Lang.Key.EDIT_REJECTED_INVALID, player));
            return;
        }
        Map<Integer, InputPoint> before = Map.copyOf(circuit.inputs());
        switch (circuit.applyEdit(request.cells(), session.mode)) {
            case SUCCESS -> {
                for (InputPoint old : before.values()) {
                    if (!circuit.inputs().containsKey(old.id())) {
                        despawnSwitches(old);
                    }
                }
                player.sendMessage(Lang.get(Lang.Key.EDIT_SAVED, player,
                        Placeholder.unparsed("name", circuit.name())));
            }
            case INVALID_DATA -> player.sendMessage(Lang.get(Lang.Key.EDIT_REJECTED_INVALID, player));
            case LOCKED_CELL -> player.sendMessage(Lang.get(Lang.Key.EDIT_REJECTED_LOCKED, player));
            case FORBIDDEN_CELL -> player.sendMessage(Lang.get(Lang.Key.EDIT_REJECTED_FORBIDDEN, player));
        }
    }

    private void expireSessions(long now) {
        if (sessions.isEmpty()) {
            return;
        }
        long timeout = settings.get().editorTimeoutSeconds() * NANOS_PER_SECOND;
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(sessions.entrySet())) {
            if (now - entry.getValue().openedNanos < timeout) {
                continue;
            }
            releaseSession(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                network.sendClose(player, entry.getValue().circuit.id());
                player.sendMessage(Lang.get(Lang.Key.EDIT_TIMEOUT, player));
            }
        }
    }

    // ---------- Simulation ----------

    private void tick() {
        expireSessions(System.nanoTime());
        List<Circuit> due = null;
        for (Circuit circuit : byId.values()) {
            if (circuit.needsStep() && isLoaded(circuit)) {
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(circuit);
            }
        }
        if (due == null) {
            return;
        }
        for (Circuit circuit : due) {
            List<OutputPoint> risen = circuit.step();
            sendSignals(circuit);
            for (OutputPoint output : risen) {
                trigger(circuit, output);
            }
        }
    }

    private boolean isLoaded(Circuit circuit) {
        Placement placement = circuit.placement();
        World world = Bukkit.getWorld(placement.world());
        return world != null && world.isChunkLoaded(
                (int) Math.floor(placement.x()) >> CHUNK_SHIFT, (int) Math.floor(placement.z()) >> CHUNK_SHIFT);
    }

    private void sendSignals(Circuit circuit) {
        UUID editor = circuit.editor();
        if (editor == null) {
            return;
        }
        Session session = sessions.get(editor);
        Player player = Bukkit.getPlayer(editor);
        if (session == null || player == null) {
            return;
        }
        byte[] bits = circuit.litBits();
        if (!Arrays.equals(bits, session.lastLit)) {
            session.lastLit = bits;
            network.sendSignals(player, circuit.id(), bits);
        }
    }

    private void trigger(Circuit circuit, OutputPoint output) {
        if (output.commands().isEmpty()) {
            return;
        }
        LogicOutputTriggerEvent event = new LogicOutputTriggerEvent(circuit.name(), output.id(), output.commands());
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        for (String template : output.commands()) {
            String command = template
                    .replace("<player>", circuit.lastActor())
                    .replace("<circuit>", circuit.name())
                    .replace("<output>", "o" + output.id());
            try {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            } catch (CommandException exception) {
                plugin.getLogger().warning("Command of " + circuit.name() + " o" + output.id()
                        + " failed: " + exception.getMessage());
            }
        }
    }

    // ---------- Persistence ----------

    private void saveDirty() {
        for (Circuit circuit : byId.values()) {
            if (circuit.isDirty()) {
                circuit.clearDirty();
                store.saveAsync(CircuitCodec.toDto(circuit));
            }
        }
    }

    // ---------- Entities ----------

    /** Removes marked entities of loaded chunks that no circuit owns. */
    private void sweepOrphans() {
        List<Entity> orphans = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isOrphan(entity)) {
                    orphans.add(entity);
                }
            }
        }
        orphans.forEach(Entity::remove);
    }

    /** Removes orphaned entities of a freshly loaded chunk and refreshes switch indicators. */
    public void onEntitiesLoad(Collection<Entity> entities) {
        List<Entity> orphans = null;
        for (Entity entity : entities) {
            if (isOrphan(entity)) {
                if (orphans == null) {
                    orphans = new ArrayList<>();
                }
                orphans.add(entity);
            } else {
                refreshIndicator(entity);
            }
        }
        if (orphans != null) {
            List<Entity> toRemove = orphans;
            // Removing inside the load event is avoided on purpose; the next tick is safe.
            plugin.getServer().getScheduler().runTask(plugin, () -> toRemove.forEach(Entity::remove));
        }
    }

    private boolean isOrphan(Entity entity) {
        if (!(entity instanceof Interaction) && !(entity instanceof TextDisplay)) {
            return false;
        }
        String circuitId = entity.getPersistentDataContainer().get(keys.circuit, PersistentDataType.STRING);
        if (circuitId == null) {
            return false;
        }
        Circuit circuit = findById(circuitId);
        return circuit == null || !circuit.ownsEntity(entity.getUniqueId());
    }

    private void refreshIndicator(Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return;
        }
        PersistentDataContainer data = display.getPersistentDataContainer();
        if (!PluginKeys.ROLE_SWITCH_DISPLAY.equals(data.get(keys.role, PersistentDataType.STRING))) {
            return;
        }
        String circuitId = data.get(keys.circuit, PersistentDataType.STRING);
        Integer inputId = data.get(keys.input, PersistentDataType.INTEGER);
        Circuit circuit = circuitId == null ? null : findById(circuitId);
        InputPoint point = circuit == null || inputId == null ? null : circuit.inputs().get(inputId);
        if (point != null) {
            display.text(switchText(point));
        }
    }

    private void mark(Entity entity, UUID circuit, String role) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(keys.circuit, PersistentDataType.STRING, circuit.toString());
        data.set(keys.role, PersistentDataType.STRING, role);
    }

    private void pair(Entity first, Entity second) {
        first.getPersistentDataContainer().set(keys.partner, PersistentDataType.STRING,
                second.getUniqueId().toString());
        second.getPersistentDataContainer().set(keys.partner, PersistentDataType.STRING,
                first.getUniqueId().toString());
    }

    private void despawn(UUID entity) {
        Entity found = Bukkit.getEntity(entity);
        if (found != null) {
            found.remove();
        }
    }

    private static Location blockCenter(Location location) {
        return new Location(location.getWorld(), Math.floor(location.getX()) + 0.5,
                Math.floor(location.getY()), Math.floor(location.getZ()) + 0.5);
    }

    /** Returns the set of cell types a circuit currently allows, in enum order. */
    public static Set<CellType> allowedTypes(Circuit circuit) {
        Set<CellType> result = new LinkedHashSet<>();
        for (CellType type : CellType.values()) {
            if (type.isPlaceable() && (circuit.allowedMask() & type.bit()) != 0) {
                result.add(type);
            }
        }
        return result;
    }
}
