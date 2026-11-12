package net.dvmn2.logicplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Localisation of every player-visible text. The only holder of static mutable state in the plugin
 * (the current language, volatile).
 */
public final class Lang {

    /** Message keys. */
    public enum Key {
        ONLY_PLAYERS, RELOADED, UNKNOWN_CIRCUIT, NO_ACCESS, INVALID_NAME, NAME_TAKEN, INVALID_SIZE,
        LIMIT_REACHED, WORLD_MISSING, CIRCUIT_CREATED, CIRCUIT_DELETED, LIST_HEADER, LIST_ENTRY, LIST_EMPTY,
        INFO_HEADER, INFO_OWNER, INFO_LOCATION, INFO_INPUTS, INFO_OUTPUTS, INFO_ALLOWED, INFO_NONE,
        UNKNOWN_CELL_TYPE, ALLOW_CHANGED, TRUST_ADDED, TRUST_REMOVED, TRUST_UNCHANGED, TRUST_OWNER,
        PLAYER_NOT_FOUND, UNKNOWN_INPUT, UNKNOWN_OUTPUT, INPUT_ON, INPUT_OFF, SWITCH_SPAWNED,
        COMMAND_ADDED, COMMAND_REMOVED, COMMAND_LIST_HEADER, COMMAND_LIST_ENTRY, COMMAND_LIST_EMPTY,
        COMMAND_INVALID, COMMAND_LIMIT, BAD_INDEX, EDITOR_BUSY, NEED_MOD, EDITOR_OPENED, SETUP_OPENED,
        EDIT_SAVED, EDIT_CANCELLED, EDIT_REJECTED_INVALID, EDIT_REJECTED_LOCKED, EDIT_REJECTED_FORBIDDEN,
        EDIT_TIMEOUT, EDIT_CLOSED, SWITCH_ON, SWITCH_OFF, BOARD_LABEL, STATE_ON, STATE_OFF
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.ONLY_PLAYERS,
                "<red>Эту команду могут использовать только игроки.",
                "<red>This command can only be used by players.");
        put(Key.RELOADED,
                "<green>Конфигурация перезагружена.",
                "<green>Configuration reloaded.");
        put(Key.UNKNOWN_CIRCUIT,
                "<red>Схема <name> не найдена.",
                "<red>Circuit <name> was not found.");
        put(Key.NO_ACCESS,
                "<red>У вас нет доступа к этой схеме.",
                "<red>You have no access to this circuit.");
        put(Key.INVALID_NAME,
                "<red>Имя схемы: 1-32 символа, только a-z, 0-9, _ и -.",
                "<red>Circuit name: 1-32 characters, only a-z, 0-9, _ and -.");
        put(Key.NAME_TAKEN,
                "<red>Схема <name> уже существует.",
                "<red>Circuit <name> already exists.");
        put(Key.INVALID_SIZE,
                "<red>Размер должен быть от 1 до <max_width> по ширине и от 1 до <max_height> по высоте.",
                "<red>Size must be 1 to <max_width> wide and 1 to <max_height> high.");
        put(Key.LIMIT_REACHED,
                "<red>Вы достигли лимита схем: <max>.",
                "<red>You reached the circuit limit: <max>.");
        put(Key.WORLD_MISSING,
                "<red>Мир схемы не загружен.",
                "<red>The circuit world is not loaded.");
        put(Key.CIRCUIT_CREATED,
                "<green>Схема <name> (<width>x<height>) создана. Нажмите ПКМ с Shift по доске, чтобы настроить её.",
                "<green>Circuit <name> (<width>x<height>) created. Sneak and right-click the board to set it up.");
        put(Key.CIRCUIT_DELETED,
                "<green>Схема <name> удалена.",
                "<green>Circuit <name> deleted.");
        put(Key.LIST_HEADER,
                "<gold>Схемы (<count>):",
                "<gold>Circuits (<count>):");
        put(Key.LIST_ENTRY,
                "<gray>- <yellow><name></yellow> <gray>(<width>x<height>), входов: <inputs>, выходов: <outputs>",
                "<gray>- <yellow><name></yellow> <gray>(<width>x<height>), inputs: <inputs>, outputs: <outputs>");
        put(Key.LIST_EMPTY,
                "<gray>Доступных схем нет.",
                "<gray>No accessible circuits.");
        put(Key.INFO_HEADER,
                "<gold>Схема <name> (<width>x<height>)",
                "<gold>Circuit <name> (<width>x<height>)");
        put(Key.INFO_OWNER,
                "<gray>Владелец: <yellow><owner></yellow><gray>, доверенных: <trusted>",
                "<gray>Owner: <yellow><owner></yellow><gray>, trusted: <trusted>");
        put(Key.INFO_LOCATION,
                "<gray>Положение: <world> <x> <y> <z>",
                "<gray>Location: <world> <x> <y> <z>");
        put(Key.INFO_INPUTS,
                "<gray>Входы: <list>",
                "<gray>Inputs: <list>");
        put(Key.INFO_OUTPUTS,
                "<gray>Выходы: <list>",
                "<gray>Outputs: <list>");
        put(Key.INFO_ALLOWED,
                "<gray>Разрешённые элементы: <list>",
                "<gray>Allowed cells: <list>");
        put(Key.INFO_NONE,
                "нет",
                "none");
        put(Key.UNKNOWN_CELL_TYPE,
                "<red>Неизвестный тип клетки: <type>.",
                "<red>Unknown cell type: <type>.");
        put(Key.ALLOW_CHANGED,
                "<green>Элемент <type> теперь <state> для схемы <name>.",
                "<green>Cell <type> is now <state> for circuit <name>.");
        put(Key.TRUST_ADDED,
                "<green><player> добавлен в доверенные схемы <name>.",
                "<green><player> was added to the trusted players of <name>.");
        put(Key.TRUST_REMOVED,
                "<green><player> удалён из доверенных схемы <name>.",
                "<green><player> was removed from the trusted players of <name>.");
        put(Key.TRUST_UNCHANGED,
                "<gray>Список доверенных не изменился.",
                "<gray>The trusted list did not change.");
        put(Key.TRUST_OWNER,
                "<red>Владелец и так имеет доступ.",
                "<red>The owner already has access.");
        put(Key.PLAYER_NOT_FOUND,
                "<red>Игрок не найден.",
                "<red>Player not found.");
        put(Key.UNKNOWN_INPUT,
                "<red>Вход <input> не найден.",
                "<red>Input <input> was not found.");
        put(Key.UNKNOWN_OUTPUT,
                "<red>Выход <output> не найден.",
                "<red>Output <output> was not found.");
        put(Key.INPUT_ON,
                "<green>Вход <input> схемы <name> включён.",
                "<green>Input <input> of <name> is on.");
        put(Key.INPUT_OFF,
                "<gray>Вход <input> схемы <name> выключен.",
                "<gray>Input <input> of <name> is off.");
        put(Key.SWITCH_SPAWNED,
                "<green>Переключатель входа <input> схемы <name> создан.",
                "<green>Switch for input <input> of <name> created.");
        put(Key.COMMAND_ADDED,
                "<green>Команда добавлена к выходу <output>.",
                "<green>Command added to output <output>.");
        put(Key.COMMAND_REMOVED,
                "<green>Команда удалена с выхода <output>.",
                "<green>Command removed from output <output>.");
        put(Key.COMMAND_LIST_HEADER,
                "<gold>Команды выхода <output>:",
                "<gold>Commands of output <output>:");
        put(Key.COMMAND_LIST_ENTRY,
                "<gray><index>. <white><command>",
                "<gray><index>. <white><command>");
        put(Key.COMMAND_LIST_EMPTY,
                "<gray>У выхода нет команд.",
                "<gray>The output has no commands.");
        put(Key.COMMAND_INVALID,
                "<red>Команда пуста или длиннее <max> символов.",
                "<red>The command is empty or longer than <max> characters.");
        put(Key.COMMAND_LIMIT,
                "<red>У выхода не может быть больше <max> команд.",
                "<red>An output cannot have more than <max> commands.");
        put(Key.BAD_INDEX,
                "<red>Нет команды с таким номером.",
                "<red>There is no command with that number.");
        put(Key.EDITOR_BUSY,
                "<red>Схему уже редактирует другой игрок.",
                "<red>Another player is editing this circuit.");
        put(Key.NEED_MOD,
                "<red>Для редактирования нужен клиентский мод Logic.",
                "<red>Editing requires the Logic client mod.");
        put(Key.EDITOR_OPENED,
                "<gray>Редактор схемы <name> открыт.",
                "<gray>Editor of circuit <name> opened.");
        put(Key.SETUP_OPENED,
                "<gray>Настройка схемы <name> открыта.",
                "<gray>Setup of circuit <name> opened.");
        put(Key.EDIT_SAVED,
                "<green>Схема <name> сохранена.",
                "<green>Circuit <name> saved.");
        put(Key.EDIT_CANCELLED,
                "<gray>Редактирование отменено.",
                "<gray>Editing cancelled.");
        put(Key.EDIT_REJECTED_INVALID,
                "<red>Изменения отклонены: некорректные данные.",
                "<red>Changes rejected: invalid data.");
        put(Key.EDIT_REJECTED_LOCKED,
                "<red>Изменения отклонены: изменена заблокированная клетка.",
                "<red>Changes rejected: a locked cell was modified.");
        put(Key.EDIT_REJECTED_FORBIDDEN,
                "<red>Изменения отклонены: использован запрещённый элемент.",
                "<red>Changes rejected: a forbidden cell was used.");
        put(Key.EDIT_TIMEOUT,
                "<red>Время редактирования истекло.",
                "<red>The editing time ran out.");
        put(Key.EDIT_CLOSED,
                "<red>Редактор закрыт сервером.",
                "<red>The editor was closed by the server.");
        put(Key.SWITCH_ON,
                "<green>● <input>",
                "<green>● <input>");
        put(Key.SWITCH_OFF,
                "<red>○ <input>",
                "<red>○ <input>");
        put(Key.BOARD_LABEL,
                "<gold>⚙ <name>",
                "<gold>⚙ <name>");
        put(Key.STATE_ON,
                "разрешён",
                "allowed");
        put(Key.STATE_OFF,
                "запрещён",
                "forbidden");
        verify();
    }

    private Lang() {
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Renders a message.
     *
     * @param key       message key
     * @param sender    recipient that selects the language under {@code auto}; {@code null} means the console
     * @param resolvers placeholder resolvers; player data MUST go through them
     */
    public static Component get(Key key, @Nullable CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    private static Map<Key, String> table(@Nullable CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
