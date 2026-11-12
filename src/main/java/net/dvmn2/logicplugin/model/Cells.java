package net.dvmn2.logicplugin.model;

import org.jetbrains.annotations.Nullable;

/**
 * Packing of one cell into a single byte, shared by the file and wire formats.
 *
 * <pre>
 * bits 0..3  type code ({@link CellType#code()})
 * bits 4..5  direction: 0 = up, 1 = right, 2 = down, 3 = left
 * bit  6     locked (the editor may not change the cell)
 * bit  7     reserved, MUST be zero
 * </pre>
 */
public final class Cells {

    /** Hard limit of the circuit side, in cells; keeps one packet under the plugin message limit. */
    public static final int MAX_DIMENSION = 128;
    /** Hard limit of the cell count of one circuit. */
    public static final int MAX_CELLS = MAX_DIMENSION * MAX_DIMENSION;
    /** Number of directions. */
    public static final int DIRECTIONS = 4;

    private static final int TYPE_MASK = 0x0F;
    private static final int DIRECTION_SHIFT = 4;
    private static final int DIRECTION_MASK = 0x03;
    private static final int LOCKED_BIT = 0x40;
    private static final int RESERVED_BIT = 0x80;

    private Cells() {
    }

    public static byte pack(CellType type, int direction, boolean locked) {
        int value = type.code() | ((direction & DIRECTION_MASK) << DIRECTION_SHIFT);
        return (byte) (locked ? value | LOCKED_BIT : value);
    }

    /** Returns true when the byte has a known type and a zero reserved bit. */
    public static boolean isValid(byte cell) {
        return (cell & RESERVED_BIT) == 0 && CellType.fromCode(cell & TYPE_MASK) != null;
    }

    /** Returns the type of a valid cell, or {@code null} for an invalid one. */
    public static @Nullable CellType type(byte cell) {
        return CellType.fromCode(cell & TYPE_MASK);
    }

    public static int direction(byte cell) {
        return (cell >> DIRECTION_SHIFT) & DIRECTION_MASK;
    }

    public static boolean isLocked(byte cell) {
        return (cell & LOCKED_BIT) != 0;
    }

    public static byte withLocked(byte cell, boolean locked) {
        return (byte) (locked ? cell | LOCKED_BIT : cell & ~LOCKED_BIT);
    }
}
