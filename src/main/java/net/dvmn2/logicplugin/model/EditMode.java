package net.dvmn2.logicplugin.model;

/** Editor mode. The ordinal is part of the wire format. */
public enum EditMode {
    /** Ordinary editing: locked cells are frozen, only allowed cell types may be placed. */
    EDIT,
    /** Creator mode: inputs, outputs and locked cells may be placed. */
    SETUP
}
