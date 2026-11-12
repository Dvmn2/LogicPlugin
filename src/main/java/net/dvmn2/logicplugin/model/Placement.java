package net.dvmn2.logicplugin.model;

import java.util.UUID;

/**
 * Where a circuit lives in the world.
 *
 * @param world       world UUID
 * @param x           x of the board entity
 * @param y           y of the board entity
 * @param z           z of the board entity
 * @param boardEntity UUID of the clickable board {@code Interaction}
 * @param labelEntity UUID of the label {@code TextDisplay}
 */
public record Placement(UUID world, double x, double y, double z, UUID boardEntity, UUID labelEntity) {
}
