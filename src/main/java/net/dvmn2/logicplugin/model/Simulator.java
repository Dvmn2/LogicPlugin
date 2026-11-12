package net.dvmn2.logicplugin.model;

import java.util.Arrays;

/**
 * Discrete simulation of one circuit. Pure logic with no Bukkit dependency; used from the main thread only.
 *
 * <p>Rules:
 * <ul>
 *   <li>Every cell with a direction emits only towards its front, except splitters and bridges.</li>
 *   <li>Wires, splitters and bridges transmit instantly. A signal travels by flooding from true sources,
 *       so a loop of wires cannot hold a value.</li>
 *   <li>A gate reads the signals of its connected neighbours (all sides but its front) and shows the result
 *       on the next step, so every gate adds a delay of one step.</li>
 *   <li>A gate with no connected input is false. NOT is true when none of its inputs is true.</li>
 * </ul>
 */
public final class Simulator {

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};
    private static final int BITS_PER_BYTE = 8;

    private final int width;
    private final int height;
    private final int size;
    private final byte[] cells;
    private final int[] inputCells;
    private final int[] outputCells;
    private final int[] gateCells;
    private final int[] gateInputCount;
    private final boolean[] gateOut;
    private final boolean[] nextGate;
    private final boolean[] emit;
    private final boolean[] outputFeed;
    private final int[] queue;
    private final byte[] lit;

    /** Builds a simulator for a snapshot of the cells; the array is copied. */
    public Simulator(int width, int height, byte[] cells) {
        this.width = width;
        this.height = height;
        this.size = width * height;
        this.cells = cells.clone();
        this.gateOut = new boolean[size];
        this.emit = new boolean[size * Cells.DIRECTIONS];
        this.outputFeed = new boolean[size];
        this.queue = new int[size * Cells.DIRECTIONS];
        this.lit = new byte[(size + BITS_PER_BYTE - 1) / BITS_PER_BYTE];

        int inputs = 0;
        int outputs = 0;
        int gates = 0;
        for (byte cell : this.cells) {
            CellType type = Cells.type(cell);
            if (type == CellType.INPUT) {
                inputs++;
            } else if (type == CellType.OUTPUT) {
                outputs++;
            } else if (type != null && type.isGate()) {
                gates++;
            }
        }
        this.inputCells = new int[inputs];
        this.outputCells = new int[outputs];
        this.gateCells = new int[gates];
        this.gateInputCount = new int[gates];
        this.nextGate = new boolean[gates];

        int in = 0;
        int out = 0;
        int gate = 0;
        for (int i = 0; i < size; i++) {
            CellType type = Cells.type(this.cells[i]);
            if (type == CellType.INPUT) {
                inputCells[in++] = i;
            } else if (type == CellType.OUTPUT) {
                outputCells[out++] = i;
            } else if (type != null && type.isGate()) {
                gateCells[gate++] = i;
            }
        }
        // Structural pass: every source is treated as true to learn which neighbours are wired to a gate.
        flood(true, new boolean[0]);
        for (int k = 0; k < gateCells.length; k++) {
            gateInputCount[k] = countFeeders(gateCells[k]);
        }
    }

    /** Cell indices of the inputs in ascending order; the caller supplies values in the same order. */
    public int[] inputCells() {
        return inputCells.clone();
    }

    /** Cell indices of the outputs in ascending order. */
    public int[] outputCells() {
        return outputCells.clone();
    }

    /**
     * Runs one step.
     *
     * @param inputValues  values of the inputs, aligned with {@link #inputCells()}
     * @param outputValues receives the values of the outputs, aligned with {@link #outputCells()}
     * @return true when any gate changed its value, so another step may change the outputs
     */
    public boolean step(boolean[] inputValues, boolean[] outputValues) {
        flood(false, inputValues);
        for (int k = 0; k < gateCells.length; k++) {
            int cell = gateCells[k];
            nextGate[k] = evaluate(Cells.type(cells[cell]), countFeeders(cell), gateInputCount[k]);
        }
        updateLit();
        for (int k = 0; k < outputCells.length; k++) {
            outputValues[k] = outputFeed[outputCells[k]];
        }
        boolean changed = false;
        for (int k = 0; k < gateCells.length; k++) {
            int cell = gateCells[k];
            if (gateOut[cell] != nextGate[k]) {
                gateOut[cell] = nextGate[k];
                changed = true;
            }
        }
        return changed;
    }

    /** Returns a copy of the lit bitmask: bit {@code i} is set when cell {@code i} carries a true signal. */
    public byte[] litBits() {
        return lit.clone();
    }

    /** Exports gate values as a bitmask by cell index, for persistence. */
    public byte[] exportGates() {
        byte[] bits = new byte[lit.length];
        for (int cell : gateCells) {
            if (gateOut[cell]) {
                bits[cell / BITS_PER_BYTE] |= (byte) (1 << (cell % BITS_PER_BYTE));
            }
        }
        return bits;
    }

    /** Restores gate values exported by {@link #exportGates()}; a bitmask of the wrong length is ignored. */
    public void importGates(byte[] bits) {
        if (bits.length != lit.length) {
            return;
        }
        for (int cell : gateCells) {
            gateOut[cell] = (bits[cell / BITS_PER_BYTE] & (1 << (cell % BITS_PER_BYTE))) != 0;
        }
    }

    private void flood(boolean structural, boolean[] inputValues) {
        Arrays.fill(emit, false);
        Arrays.fill(outputFeed, false);
        int tail = 0;
        for (int k = 0; k < inputCells.length; k++) {
            if (structural || inputValues[k]) {
                tail = emitTo(inputCells[k], Cells.direction(cells[inputCells[k]]), tail);
            }
        }
        for (int cell : gateCells) {
            if (structural || gateOut[cell]) {
                tail = emitTo(cell, Cells.direction(cells[cell]), tail);
            }
        }
        int head = 0;
        while (head < tail) {
            int packed = queue[head++];
            int from = packed / Cells.DIRECTIONS;
            int direction = packed % Cells.DIRECTIONS;
            int target = neighbour(from, direction);
            if (target >= 0) {
                tail = receive(target, direction, tail);
            }
        }
    }

    private int receive(int target, int direction, int tail) {
        CellType type = Cells.type(cells[target]);
        if (type == null) {
            return tail;
        }
        int front = Cells.direction(cells[target]);
        switch (type) {
            case WIRE -> {
                return emitTo(target, front, tail);
            }
            case SPLITTER -> {
                if (direction != front) {
                    return tail;
                }
                int next = emitTo(target, front, tail);
                next = emitTo(target, (front + 1) % Cells.DIRECTIONS, next);
                return emitTo(target, (front + 3) % Cells.DIRECTIONS, next);
            }
            case BRIDGE -> {
                return emitTo(target, direction, tail);
            }
            case OUTPUT -> {
                outputFeed[target] = true;
                return tail;
            }
            default -> {
                return tail;
            }
        }
    }

    private int emitTo(int cell, int direction, int tail) {
        int index = cell * Cells.DIRECTIONS + direction;
        if (emit[index]) {
            return tail;
        }
        emit[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private int neighbour(int cell, int direction) {
        int x = cell % width + DX[direction];
        int y = cell / width + DY[direction];
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return -1;
        }
        return y * width + x;
    }

    /** Counts the neighbours on the three non-front sides that emit towards the gate in the current flood. */
    private int countFeeders(int gate) {
        int front = Cells.direction(cells[gate]);
        int count = 0;
        for (int side = 0; side < Cells.DIRECTIONS; side++) {
            if (side == front) {
                continue;
            }
            int neighbour = neighbour(gate, side);
            if (neighbour >= 0 && emit[neighbour * Cells.DIRECTIONS + (side + 2) % Cells.DIRECTIONS]) {
                count++;
            }
        }
        return count;
    }

    private void updateLit() {
        Arrays.fill(lit, (byte) 0);
        for (int cell = 0; cell < size; cell++) {
            int base = cell * Cells.DIRECTIONS;
            boolean on = outputFeed[cell] || emit[base] || emit[base + 1] || emit[base + 2] || emit[base + 3];
            if (on) {
                lit[cell / BITS_PER_BYTE] |= (byte) (1 << (cell % BITS_PER_BYTE));
            }
        }
    }

    private static boolean evaluate(CellType type, int high, int total) {
        if (total == 0 || type == null) {
            return false;
        }
        return switch (type) {
            case AND -> high == total;
            case OR -> high > 0;
            case NOT, NOR -> high == 0;
            case NAND -> high != total;
            case XOR -> (high & 1) == 1;
            case XNOR -> (high & 1) == 0;
            default -> false;
        };
    }
}
