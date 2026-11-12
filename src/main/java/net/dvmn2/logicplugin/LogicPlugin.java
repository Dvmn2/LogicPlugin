package net.dvmn2.logicplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.logicplugin.api.LogicApi;
import net.dvmn2.logicplugin.command.LogicCommand;
import net.dvmn2.logicplugin.gui.EditorNetwork;
import net.dvmn2.logicplugin.storage.CircuitStore;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Composition root: builds the objects, registers them and stops them. */
public final class LogicPlugin extends JavaPlugin {

    private volatile LogicSettings settings;
    private CircuitStore store;
    private EditorNetwork network;
    private LogicManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        store = new CircuitStore(getDataFolder().toPath(), getLogger());
        network = new EditorNetwork(this);
        PluginKeys keys = new PluginKeys(this);
        manager = new LogicManager(this, () -> settings, store, keys, network);
        manager.start();

        getServer().getPluginManager().registerEvents(new LogicListener(manager), this);

        LogicCommand command = new LogicCommand(this, manager, () -> settings);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Logic circuits", List.of()));

        getServer().getServicesManager().register(LogicApi.class, new LogicApiImpl(manager, network),
                this, ServicePriority.Normal);

        getLogger().info("Enabled with " + manager.circuitCount() + " circuit(s).");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (manager != null) {
            manager.shutdown();
        } else if (store != null) {
            store.close();
        }
    }

    /** Rereads {@code config.yml} and applies it without restarting the plugin. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
        if (manager != null) {
            manager.onSettingsReloaded();
        }
    }

    private void reloadSettings() {
        settings = LogicSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
