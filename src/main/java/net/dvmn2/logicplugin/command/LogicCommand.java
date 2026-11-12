package net.dvmn2.logicplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.dvmn2.logicplugin.Lang;
import net.dvmn2.logicplugin.LogicManager;
import net.dvmn2.logicplugin.LogicPlugin;
import net.dvmn2.logicplugin.LogicSettings;
import net.dvmn2.logicplugin.Permissions;
import net.dvmn2.logicplugin.model.CellType;
import net.dvmn2.logicplugin.model.Cells;
import net.dvmn2.logicplugin.model.Circuit;
import net.dvmn2.logicplugin.model.InputPoint;
import net.dvmn2.logicplugin.model.OutputPoint;
import net.dvmn2.logicplugin.model.Placement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The {@code /logic} command.
 *
 * <pre>
 * /logic create &lt;name&gt; &lt;width&gt; &lt;height&gt;                  logic.create
 * /logic delete &lt;circuit&gt;                                 owner or logic.admin
 * /logic list
 * /logic info &lt;circuit&gt;
 * /logic allow &lt;circuit&gt; &lt;type&gt; &lt;true|false&gt;              owner or logic.admin
 * /logic trust &lt;circuit&gt; add|remove &lt;player&gt;             owner or logic.admin
 * /logic input &lt;circuit&gt; &lt;input&gt; true|false|toggle        owner, trusted or logic.admin
 * /logic spawn-input &lt;circuit&gt; &lt;input&gt;                    owner or logic.admin
 * /logic output &lt;circuit&gt; &lt;output&gt; command add &lt;command&gt;   logic.command and owner or logic.admin
 * /logic output &lt;circuit&gt; &lt;output&gt; command remove &lt;number&gt;
 * /logic output &lt;circuit&gt; &lt;output&gt; command list
 * /logic reload                                         logic.reload
 * </pre>
 */
public final class LogicCommand {

    private static final String ARG_NAME = "name";
    private static final String ARG_WIDTH = "width";
    private static final String ARG_HEIGHT = "height";
    private static final String ARG_CIRCUIT = "circuit";
    private static final String ARG_TYPE = "type";
    private static final String ARG_ALLOWED = "allowed";
    private static final String ARG_PLAYER = "player";
    private static final String ARG_INPUT = "input";
    private static final String ARG_OUTPUT = "output";
    private static final String ARG_COMMAND = "command";
    private static final String ARG_NUMBER = "number";
    private static final char INPUT_PREFIX = 'i';
    private static final char OUTPUT_PREFIX = 'o';

    /** What the sender must be allowed to do with a circuit. */
    private enum Access { EDIT, MANAGE }

    private final LogicPlugin plugin;
    private final LogicManager manager;
    private final Supplier<LogicSettings> settings;

    public LogicCommand(LogicPlugin plugin, LogicManager manager, Supplier<LogicSettings> settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("logic")
                .requires(src -> src.getSender().hasPermission(Permissions.USE))
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission(Permissions.CREATE))
                        .then(Commands.argument(ARG_NAME, StringArgumentType.word())
                                .then(Commands.argument(ARG_WIDTH, IntegerArgumentType.integer(1, Cells.MAX_DIMENSION))
                                        .then(Commands.argument(ARG_HEIGHT,
                                                        IntegerArgumentType.integer(1, Cells.MAX_DIMENSION))
                                                .executes(this::create)))))
                .then(Commands.literal("delete")
                        .then(circuitArgument().executes(this::delete)))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("info")
                        .then(circuitArgument().executes(this::info)))
                .then(Commands.literal("allow")
                        .then(circuitArgument()
                                .then(Commands.argument(ARG_TYPE, StringArgumentType.word())
                                        .suggests(this::suggestTypes)
                                        .then(Commands.argument(ARG_ALLOWED, BoolArgumentType.bool())
                                                .executes(this::allow)))))
                .then(Commands.literal("trust")
                        .then(circuitArgument()
                                .then(Commands.literal("add")
                                        .then(Commands.argument(ARG_PLAYER, ArgumentTypes.player())
                                                .executes(ctx -> trust(ctx, true))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument(ARG_PLAYER, ArgumentTypes.player())
                                                .executes(ctx -> trust(ctx, false))))))
                .then(Commands.literal("input")
                        .then(circuitArgument()
                                .then(Commands.argument(ARG_INPUT, StringArgumentType.word())
                                        .suggests(this::suggestInputs)
                                        .then(Commands.literal("true").executes(ctx -> input(ctx, Boolean.TRUE)))
                                        .then(Commands.literal("false").executes(ctx -> input(ctx, Boolean.FALSE)))
                                        .then(Commands.literal("toggle").executes(ctx -> input(ctx, null))))))
                .then(Commands.literal("spawn-input")
                        .then(circuitArgument()
                                .then(Commands.argument(ARG_INPUT, StringArgumentType.word())
                                        .suggests(this::suggestInputs)
                                        .executes(this::spawnInput))))
                .then(Commands.literal("output")
                        .requires(src -> src.getSender().hasPermission(Permissions.COMMAND))
                        .then(circuitArgument()
                                .then(Commands.argument(ARG_OUTPUT, StringArgumentType.word())
                                        .suggests(this::suggestOutputs)
                                        .then(Commands.literal("command")
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument(ARG_COMMAND,
                                                                        StringArgumentType.greedyString())
                                                                .executes(this::commandAdd)))
                                                .then(Commands.literal("remove")
                                                        .then(Commands.argument(ARG_NUMBER,
                                                                        IntegerArgumentType.integer(1))
                                                                .executes(this::commandRemove)))
                                                .then(Commands.literal("list").executes(this::commandList))))))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(Permissions.RELOAD))
                        .executes(this::reload))
                .build();
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> circuitArgument() {
        return Commands.argument(ARG_CIRCUIT, StringArgumentType.word()).suggests(this::suggestCircuits);
    }

    // ---------- Executors ----------

    private int create(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            sender.sendMessage(Lang.get(Lang.Key.ONLY_PLAYERS, sender));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, ARG_NAME);
        int width = IntegerArgumentType.getInteger(ctx, ARG_WIDTH);
        int height = IntegerArgumentType.getInteger(ctx, ARG_HEIGHT);
        LogicSettings current = settings.get();
        switch (manager.create(player, name, width, height, player.getLocation())) {
            case SUCCESS -> {
                sender.sendMessage(Lang.get(Lang.Key.CIRCUIT_CREATED, sender,
                        Placeholder.unparsed("name", name.toLowerCase(Locale.ROOT)),
                        Placeholder.unparsed("width", String.valueOf(width)),
                        Placeholder.unparsed("height", String.valueOf(height))));
                return Command.SINGLE_SUCCESS;
            }
            case INVALID_NAME -> sender.sendMessage(Lang.get(Lang.Key.INVALID_NAME, sender));
            case NAME_TAKEN -> sender.sendMessage(Lang.get(Lang.Key.NAME_TAKEN, sender,
                    Placeholder.unparsed("name", name)));
            case INVALID_SIZE -> sender.sendMessage(Lang.get(Lang.Key.INVALID_SIZE, sender,
                    Placeholder.unparsed("max_width", String.valueOf(current.maxWidthCells())),
                    Placeholder.unparsed("max_height", String.valueOf(current.maxHeightCells()))));
            case LIMIT_REACHED -> sender.sendMessage(Lang.get(Lang.Key.LIMIT_REACHED, sender,
                    Placeholder.unparsed("max", String.valueOf(current.maxCircuitsPerPlayer()))));
            case WORLD_MISSING -> sender.sendMessage(Lang.get(Lang.Key.WORLD_MISSING, sender));
        }
        return 0;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        manager.delete(circuit);
        sender.sendMessage(Lang.get(Lang.Key.CIRCUIT_DELETED, sender, Placeholder.unparsed("name", circuit.name())));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Circuit> circuits = manager.accessible(sender);
        if (circuits.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.LIST_EMPTY, sender));
            return 0;
        }
        sender.sendMessage(Lang.get(Lang.Key.LIST_HEADER, sender,
                Placeholder.unparsed("count", String.valueOf(circuits.size()))));
        for (Circuit circuit : circuits) {
            sender.sendMessage(Lang.get(Lang.Key.LIST_ENTRY, sender,
                    Placeholder.unparsed("name", circuit.name()),
                    Placeholder.unparsed("width", String.valueOf(circuit.width())),
                    Placeholder.unparsed("height", String.valueOf(circuit.height())),
                    Placeholder.unparsed("inputs", String.valueOf(circuit.inputs().size())),
                    Placeholder.unparsed("outputs", String.valueOf(circuit.outputs().size()))));
        }
        return circuits.size();
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Circuit circuit = circuitFor(ctx, Access.EDIT);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Lang.get(Lang.Key.INFO_HEADER, sender,
                Placeholder.unparsed("name", circuit.name()),
                Placeholder.unparsed("width", String.valueOf(circuit.width())),
                Placeholder.unparsed("height", String.valueOf(circuit.height()))));
        String owner = Bukkit.getOfflinePlayer(circuit.owner()).getName();
        sender.sendMessage(Lang.get(Lang.Key.INFO_OWNER, sender,
                Placeholder.unparsed("owner", owner == null ? circuit.owner().toString() : owner),
                Placeholder.unparsed("trusted", String.valueOf(circuit.trusted().size()))));
        Placement placement = circuit.placement();
        World world = Bukkit.getWorld(placement.world());
        sender.sendMessage(Lang.get(Lang.Key.INFO_LOCATION, sender,
                Placeholder.unparsed("world", world == null ? "?" : world.getName()),
                Placeholder.unparsed("x", String.valueOf((int) Math.floor(placement.x()))),
                Placeholder.unparsed("y", String.valueOf((int) Math.floor(placement.y()))),
                Placeholder.unparsed("z", String.valueOf((int) Math.floor(placement.z())))));
        List<String> inputs = new ArrayList<>();
        for (InputPoint point : circuit.inputs().values()) {
            inputs.add("i" + point.id() + " " + coordinates(circuit, point.cell()));
        }
        List<String> outputs = new ArrayList<>();
        for (OutputPoint point : circuit.outputs().values()) {
            outputs.add("o" + point.id() + " " + coordinates(circuit, point.cell())
                    + " [" + point.commands().size() + "]");
        }
        List<String> allowed = new ArrayList<>();
        for (CellType type : LogicManager.allowedTypes(circuit)) {
            allowed.add(type.name().toLowerCase(Locale.ROOT));
        }
        sender.sendMessage(Lang.get(Lang.Key.INFO_INPUTS, sender, listResolver(sender, inputs)));
        sender.sendMessage(Lang.get(Lang.Key.INFO_OUTPUTS, sender, listResolver(sender, outputs)));
        sender.sendMessage(Lang.get(Lang.Key.INFO_ALLOWED, sender, listResolver(sender, allowed)));
        return Command.SINGLE_SUCCESS;
    }

    private net.kyori.adventure.text.minimessage.tag.resolver.TagResolver listResolver(
            CommandSender sender, List<String> items) {
        Component value = items.isEmpty()
                ? Lang.get(Lang.Key.INFO_NONE, sender)
                : Component.text(String.join(", ", items));
        return Placeholder.component("list", value);
    }

    private int allow(CommandContext<CommandSourceStack> ctx) {
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        String rawType = StringArgumentType.getString(ctx, ARG_TYPE);
        CellType type = parseType(rawType);
        if (type == null) {
            sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_CELL_TYPE, sender, Placeholder.unparsed("type", rawType)));
            return 0;
        }
        boolean allowed = BoolArgumentType.getBool(ctx, ARG_ALLOWED);
        circuit.setAllowed(type, allowed);
        sender.sendMessage(Lang.get(Lang.Key.ALLOW_CHANGED, sender,
                Placeholder.unparsed("type", type.name().toLowerCase(Locale.ROOT)),
                Placeholder.component("state",
                        Lang.get(allowed ? Lang.Key.STATE_ON : Lang.Key.STATE_OFF, sender)),
                Placeholder.unparsed("name", circuit.name())));
        return Command.SINGLE_SUCCESS;
    }

    private int trust(CommandContext<CommandSourceStack> ctx, boolean add) {
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        Player target = resolvePlayer(ctx);
        if (target == null) {
            sender.sendMessage(Lang.get(Lang.Key.PLAYER_NOT_FOUND, sender));
            return 0;
        }
        if (circuit.isOwner(target.getUniqueId())) {
            sender.sendMessage(Lang.get(Lang.Key.TRUST_OWNER, sender));
            return 0;
        }
        boolean changed = add ? circuit.addTrusted(target.getUniqueId()) : circuit.removeTrusted(target.getUniqueId());
        if (!changed) {
            sender.sendMessage(Lang.get(Lang.Key.TRUST_UNCHANGED, sender));
            return 0;
        }
        sender.sendMessage(Lang.get(add ? Lang.Key.TRUST_ADDED : Lang.Key.TRUST_REMOVED, sender,
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("name", circuit.name())));
        return Command.SINGLE_SUCCESS;
    }

    private int input(CommandContext<CommandSourceStack> ctx, @Nullable Boolean value) {
        Circuit circuit = circuitFor(ctx, Access.EDIT);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        String raw = StringArgumentType.getString(ctx, ARG_INPUT);
        InputPoint point = circuit.inputs().get(parseId(raw, INPUT_PREFIX));
        if (point == null) {
            sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_INPUT, sender, Placeholder.unparsed("input", raw)));
            return 0;
        }
        boolean target = value == null ? !point.state() : value;
        manager.setInput(circuit, point, target, sender instanceof Player player ? player.getName() : "");
        sender.sendMessage(Lang.get(target ? Lang.Key.INPUT_ON : Lang.Key.INPUT_OFF, sender,
                Placeholder.unparsed("input", "i" + point.id()),
                Placeholder.unparsed("name", circuit.name())));
        return Command.SINGLE_SUCCESS;
    }

    private int spawnInput(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            sender.sendMessage(Lang.get(Lang.Key.ONLY_PLAYERS, sender));
            return 0;
        }
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, ARG_INPUT);
        switch (manager.spawnSwitch(circuit, parseId(raw, INPUT_PREFIX), player.getLocation())) {
            case SUCCESS -> {
                sender.sendMessage(Lang.get(Lang.Key.SWITCH_SPAWNED, sender,
                        Placeholder.unparsed("input", raw.toLowerCase(Locale.ROOT)),
                        Placeholder.unparsed("name", circuit.name())));
                return Command.SINGLE_SUCCESS;
            }
            case UNKNOWN_INPUT -> sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_INPUT, sender,
                    Placeholder.unparsed("input", raw)));
            case WORLD_MISSING -> sender.sendMessage(Lang.get(Lang.Key.WORLD_MISSING, sender));
        }
        return 0;
    }

    private int commandAdd(CommandContext<CommandSourceStack> ctx) {
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        String raw = StringArgumentType.getString(ctx, ARG_OUTPUT);
        LogicSettings current = settings.get();
        switch (manager.addCommand(circuit, parseId(raw, OUTPUT_PREFIX),
                StringArgumentType.getString(ctx, ARG_COMMAND))) {
            case SUCCESS -> {
                sender.sendMessage(Lang.get(Lang.Key.COMMAND_ADDED, sender, Placeholder.unparsed("output", raw)));
                return Command.SINGLE_SUCCESS;
            }
            case UNKNOWN_OUTPUT -> sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_OUTPUT, sender,
                    Placeholder.unparsed("output", raw)));
            case INVALID_COMMAND -> sender.sendMessage(Lang.get(Lang.Key.COMMAND_INVALID, sender,
                    Placeholder.unparsed("max", String.valueOf(current.maxCommandLengthChars()))));
            case LIMIT_REACHED -> sender.sendMessage(Lang.get(Lang.Key.COMMAND_LIMIT, sender,
                    Placeholder.unparsed("max", String.valueOf(current.maxCommandsPerOutput()))));
            case BAD_INDEX -> sender.sendMessage(Lang.get(Lang.Key.BAD_INDEX, sender));
        }
        return 0;
    }

    private int commandRemove(CommandContext<CommandSourceStack> ctx) {
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        String raw = StringArgumentType.getString(ctx, ARG_OUTPUT);
        switch (manager.removeCommand(circuit, parseId(raw, OUTPUT_PREFIX),
                IntegerArgumentType.getInteger(ctx, ARG_NUMBER))) {
            case SUCCESS -> {
                sender.sendMessage(Lang.get(Lang.Key.COMMAND_REMOVED, sender, Placeholder.unparsed("output", raw)));
                return Command.SINGLE_SUCCESS;
            }
            case UNKNOWN_OUTPUT -> sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_OUTPUT, sender,
                    Placeholder.unparsed("output", raw)));
            case BAD_INDEX -> sender.sendMessage(Lang.get(Lang.Key.BAD_INDEX, sender));
            default -> sender.sendMessage(Lang.get(Lang.Key.BAD_INDEX, sender));
        }
        return 0;
    }

    private int commandList(CommandContext<CommandSourceStack> ctx) {
        Circuit circuit = circuitFor(ctx, Access.MANAGE);
        if (circuit == null) {
            return 0;
        }
        CommandSender sender = ctx.getSource().getSender();
        String raw = StringArgumentType.getString(ctx, ARG_OUTPUT);
        OutputPoint point = circuit.outputs().get(parseId(raw, OUTPUT_PREFIX));
        if (point == null) {
            sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_OUTPUT, sender, Placeholder.unparsed("output", raw)));
            return 0;
        }
        if (point.commands().isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.COMMAND_LIST_EMPTY, sender));
            return 0;
        }
        sender.sendMessage(Lang.get(Lang.Key.COMMAND_LIST_HEADER, sender, Placeholder.unparsed("output", raw)));
        int index = 1;
        for (String command : point.commands()) {
            sender.sendMessage(Lang.get(Lang.Key.COMMAND_LIST_ENTRY, sender,
                    Placeholder.unparsed("index", String.valueOf(index++)),
                    Placeholder.unparsed("command", command)));
        }
        return point.commands().size();
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadAll();
        sender.sendMessage(Lang.get(Lang.Key.RELOADED, sender));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Helpers ----------

    /** Finds the circuit named in the command and checks access; reports the failure to the sender. */
    private @Nullable Circuit circuitFor(CommandContext<CommandSourceStack> ctx, Access access) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, ARG_CIRCUIT);
        Circuit circuit = manager.find(name);
        if (circuit == null) {
            sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_CIRCUIT, sender, Placeholder.unparsed("name", name)));
            return null;
        }
        boolean allowed = access == Access.MANAGE
                ? manager.canManage(sender, circuit)
                : manager.canEdit(sender, circuit);
        if (!allowed) {
            sender.sendMessage(Lang.get(Lang.Key.NO_ACCESS, sender));
            return null;
        }
        return circuit;
    }

    private static @Nullable Player resolvePlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            List<Player> players = ctx.getArgument(ARG_PLAYER, PlayerSelectorArgumentResolver.class)
                    .resolve(ctx.getSource());
            return players.isEmpty() ? null : players.get(0);
        } catch (CommandSyntaxException exception) {
            return null;
        }
    }

    /** Formats a cell index as {@code (x,y)}, zero-based, the way the editor shows it. */
    private static String coordinates(Circuit circuit, int cell) {
        return "(" + cell % circuit.width() + "," + cell / circuit.width() + ")";
    }

    private static @Nullable CellType parseType(String raw) {
        for (CellType type : CellType.values()) {
            if (type.isPlaceable() && type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }

    /** Parses ids like {@code i3}; returns -1 for anything else, which no point ever has. */
    private static int parseId(String raw, char prefix) {
        String text = raw.toLowerCase(Locale.ROOT);
        if (text.length() < 2 || text.charAt(0) != prefix) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private @Nullable Circuit suggestionCircuit(CommandContext<CommandSourceStack> ctx) {
        return manager.find(StringArgumentType.getString(ctx, ARG_CIRCUIT));
    }

    private CompletableFuture<Suggestions> suggestCircuits(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (Circuit circuit : manager.accessible(ctx.getSource().getSender())) {
            if (circuit.name().startsWith(prefix)) {
                builder.suggest(circuit.name());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTypes(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (CellType type : CellType.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            if (type.isPlaceable() && name.startsWith(prefix)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestInputs(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        Circuit circuit = suggestionCircuit(ctx);
        String prefix = builder.getRemainingLowerCase();
        if (circuit != null) {
            for (InputPoint point : circuit.inputs().values()) {
                String id = "i" + point.id();
                if (id.startsWith(prefix)) {
                    builder.suggest(id);
                }
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOutputs(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        Circuit circuit = suggestionCircuit(ctx);
        String prefix = builder.getRemainingLowerCase();
        if (circuit != null) {
            for (OutputPoint point : circuit.outputs().values()) {
                String id = "o" + point.id();
                if (id.startsWith(prefix)) {
                    builder.suggest(id);
                }
            }
        }
        return builder.buildFuture();
    }
}
