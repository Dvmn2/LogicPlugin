package net.dvmn2.logicplugin;

import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Bukkit events of the plugin: board and switch clicks, editor lock release, orphan cleanup. */
public final class LogicListener implements Listener {

    private final LogicManager manager;

    public LogicListener(LogicManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // The At-variant is fired for the same click; handling both would act twice.
        if (event instanceof PlayerInteractAtEntityEvent || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Interaction entity)) {
            return;
        }
        if (manager.handleInteraction(event.getPlayer(), entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        manager.onEntitiesLoad(event.getEntities());
    }
}
