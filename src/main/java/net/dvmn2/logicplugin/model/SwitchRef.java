package net.dvmn2.logicplugin.model;

import java.util.UUID;

/**
 * A physical switch of an input: the clickable {@code Interaction} and its {@code TextDisplay} indicator.
 *
 * @param interaction UUID of the clickable entity
 * @param display     UUID of the indicator entity
 */
public record SwitchRef(UUID interaction, UUID display) {
}
