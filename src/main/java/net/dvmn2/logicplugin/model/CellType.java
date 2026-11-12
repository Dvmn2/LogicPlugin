package net.dvmn2.logicplugin.model;

import org.jetbrains.annotations.Nullable;

/**
 * Kind of a circuit cell. The numeric code is part of the file and wire formats and MUST NOT change.
 *
 * <p>The type is immutable and safe to use from any thread.
 */
public enum CellType {
    EMPTY(0),
    WIRE(1),
    INPUT(2),
    OUTPUT(3),
    AND(4),
    OR(5),
    NOT(6),
    XOR(7),
    NAND(8),
    NOR(9),
    XNOR(10),
    SPLITTER(11),
    BRIDGE(12);

    private static final CellType[] BY_CODE = values();

    private final int code;

    CellType(int code) {
        this.code = code;
    }

    /** Returns the mask with every placeable type set; the default for a new circuit. */
    public static int defaultAllowedMask() {
        int mask = 0;
        for (CellType type : BY_CODE) {
            if (type.isPlaceable()) {
                mask |= type.bit();
            }
        }
        return mask;
    }

    /** Returns the type for a code, or {@code null} when the code is unknown. */
    public static @Nullable CellType fromCode(int code) {
        return code >= 0 && code < BY_CODE.length ? BY_CODE[code] : null;
    }

    public int code() {
        return code;
    }

    /** Returns the bit of this type inside an allowed-types mask. */
    public int bit() {
        return 1 << code;
    }

    public boolean isGate() {
        return this == AND || this == OR || this == NOT || this == XOR
                || this == NAND || this == NOR || this == XNOR;
    }

    /** Returns true for types an editor may place when the creator allowed them. */
    public boolean isPlaceable() {
        return this != EMPTY && this != INPUT && this != OUTPUT;
    }
}
