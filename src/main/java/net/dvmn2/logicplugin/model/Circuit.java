package net.dvmn2.logicplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One logic circuit: its cells, inputs, outputs, access list and simulation. Main thread only.
 *
 * <p>Invariants: every INPUT cell has exactly one {@link InputPoint}, every OUTPUT cell exactly one
 * {@link OutputPoint}, and the simulator always matches the current cells.
 */
public final class Circuit {

    /** Result of applying an edit to the cells. */
    public enum EditResult { SUCCESS, INVALID_DATA, LOCKED_CELL, FORBIDDEN_CELL }

    private final UUID id;
    private final String name;
    private final UUID owner;
    private final Placement placement;
    private final int width;
    private final int height;
    private final Set<UUID> trusted = new LinkedHashSet<>();
    private final Map<Integer, InputPoint> inputs = new TreeMap<>();
    private final Map<Integer, OutputPoint> outputs = new TreeMap<>();

    private byte[] cells;
    private int allowedMask;
    private int nextInputId = 1;
    private int nextOutputId = 1;
    private Simulator simulator;
    private InputPoint[] inputOrder = new InputPoint[0];
    private OutputPoint[] outputOrder = new OutputPoint[0];
    private boolean[] inputBuffer = new boolean[0];
    private boolean[] outputBuffer = new boolean[0];
    private @Nullable UUID editor;
    private boolean needsStep = true;
    private boolean dirty;
    private String lastActor = "";

    public Circuit(UUID id, String name, UUID owner, Placement placement, int width, int height,
            byte[] cells, int allowedMask) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.placement = placement;
        this.width = width;
        this.height = height;
        this.cells = cells.clone();
        this.allowedMask = allowedMask;
        reconcile();
    }

    // ---------- Identity ----------

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID owner() {
        return owner;
    }

    public Placement placement() {
        return placement;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // ---------- Cells ----------

    /** Returns a copy of the packed cells, row by row. */
    public byte[] cells() {
        return cells.clone();
    }

    public int allowedMask() {
        return allowedMask;
    }

    public void setAllowed(CellType type, boolean allowed) {
        allowedMask = allowed ? allowedMask | type.bit() : allowedMask & ~type.bit();
        dirty = true;
    }

    /**
     * Applies an edited copy of the cells.
     *
     * @param incoming packed cells of exactly {@code width * height} bytes
     * @param mode     the mode the editor was opened in
     * @return the outcome; the circuit is unchanged unless the result is {@link EditResult#SUCCESS}
     */
    public EditResult applyEdit(byte[] incoming, EditMode mode) {
        if (incoming.length != cells.length) {
            return EditResult.INVALID_DATA;
        }
        byte[] result = new byte[incoming.length];
        for (int i = 0; i < incoming.length; i++) {
            EditResult cellResult = checkCell(cells[i], incoming[i], mode);
            if (cellResult != EditResult.SUCCESS) {
                return cellResult;
            }
            result[i] = normalise(incoming[i], mode);
        }
        cells = result;
        reconcile();
        dirty = true;
        return EditResult.SUCCESS;
    }

    private EditResult checkCell(byte old, byte now, EditMode mode) {
        if (!Cells.isValid(now)) {
            return EditResult.INVALID_DATA;
        }
        if (mode == EditMode.SETUP) {
            return EditResult.SUCCESS;
        }
        if (Cells.isLocked(old)) {
            return now == old ? EditResult.SUCCESS : EditResult.LOCKED_CELL;
        }
        CellType type = Cells.type(now);
        if (Cells.isLocked(now) || type == CellType.INPUT || type == CellType.OUTPUT) {
            return EditResult.FORBIDDEN_CELL;
        }
        if (type != CellType.EMPTY && (allowedMask & type.bit()) == 0) {
            return EditResult.FORBIDDEN_CELL;
        }
        return EditResult.SUCCESS;
    }

    private static byte normalise(byte cell, EditMode mode) {
        CellType type = Cells.type(cell);
        if (mode == EditMode.SETUP && (type == CellType.INPUT || type == CellType.OUTPUT)) {
            return Cells.withLocked(cell, true);
        }
        return cell;
    }

    // ---------- Access ----------

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public boolean isTrusted(UUID player) {
        return trusted.contains(player);
    }

    public Set<UUID> trusted() {
        return Collections.unmodifiableSet(trusted);
    }

    /** Adds a trusted player; returns false when already present. */
    public boolean addTrusted(UUID player) {
        boolean changed = trusted.add(player);
        dirty |= changed;
        return changed;
    }

    /** Removes a trusted player; returns false when absent. */
    public boolean removeTrusted(UUID player) {
        boolean changed = trusted.remove(player);
        dirty |= changed;
        return changed;
    }

    public @Nullable UUID editor() {
        return editor;
    }

    public void setEditor(@Nullable UUID editor) {
        this.editor = editor;
    }

    // ---------- Inputs and outputs ----------

    public Map<Integer, InputPoint> inputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public Map<Integer, OutputPoint> outputs() {
        return Collections.unmodifiableMap(outputs);
    }

    public int nextInputId() {
        return nextInputId;
    }

    public int nextOutputId() {
        return nextOutputId;
    }

    /**
     * Restores persisted points and counters. Points that no longer match a cell are dropped and cells
     * without a point get a fresh one.
     */
    public void restore(Collection<InputPoint> savedInputs, Collection<OutputPoint> savedOutputs,
            int savedNextInputId, int savedNextOutputId, byte[] gateBits, Collection<UUID> savedTrusted) {
        int maxInput = 0;
        for (InputPoint point : savedInputs) {
            inputs.put(point.id(), point);
            maxInput = Math.max(maxInput, point.id());
        }
        int maxOutput = 0;
        for (OutputPoint point : savedOutputs) {
            outputs.put(point.id(), point);
            maxOutput = Math.max(maxOutput, point.id());
        }
        nextInputId = Math.max(Math.max(savedNextInputId, maxInput + 1), 1);
        nextOutputId = Math.max(Math.max(savedNextOutputId, maxOutput + 1), 1);
        trusted.addAll(savedTrusted);
        reconcile();
        simulator.importGates(gateBits);
    }

    /** Sets an input; returns true when the value changed. */
    public boolean setInput(InputPoint point, boolean value, String actor) {
        if (point.state() == value) {
            return false;
        }
        point.setState(value);
        needsStep = true;
        dirty = true;
        lastActor = actor;
        return true;
    }

    /** Name of the player who last changed an input, or an empty string. */
    public String lastActor() {
        return lastActor;
    }

    // ---------- Simulation ----------

    public boolean needsStep() {
        return needsStep;
    }

    /** Runs one simulation step and returns the outputs that went from false to true. */
    public List<OutputPoint> step() {
        for (int k = 0; k < inputOrder.length; k++) {
            inputBuffer[k] = inputOrder[k].state();
        }
        boolean gatesChanged = simulator.step(inputBuffer, outputBuffer);
        List<OutputPoint> risen = List.of();
        for (int k = 0; k < outputOrder.length; k++) {
            OutputPoint point = outputOrder[k];
            boolean value = outputBuffer[k];
            if (value == point.last()) {
                continue;
            }
            point.setLast(value);
            dirty = true;
            if (value) {
                if (risen.isEmpty()) {
                    risen = new ArrayList<>();
                }
                risen.add(point);
            }
        }
        needsStep = gatesChanged;
        return risen;
    }

    /** Returns the lit bitmask of the last step; bit {@code i} is cell {@code i}. */
    public byte[] litBits() {
        return simulator.litBits();
    }

    /** Returns the persisted form of the gate values. */
    public byte[] gateBits() {
        return simulator.exportGates();
    }

    // ---------- Persistence flags ----------

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    // ---------- Entities ----------

    /** Returns true when the entity belongs to this circuit's registry. */
    public boolean ownsEntity(UUID entity) {
        if (entity.equals(placement.boardEntity()) || entity.equals(placement.labelEntity())) {
            return true;
        }
        for (InputPoint point : inputs.values()) {
            for (SwitchRef ref : point.switches()) {
                if (entity.equals(ref.interaction()) || entity.equals(ref.display())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reconcile() {
        Map<Integer, InputPoint> inputsByCell = new HashMap<>();
        for (InputPoint point : inputs.values()) {
            inputsByCell.put(point.cell(), point);
        }
        Map<Integer, OutputPoint> outputsByCell = new HashMap<>();
        for (OutputPoint point : outputs.values()) {
            outputsByCell.put(point.cell(), point);
        }
        inputs.clear();
        outputs.clear();
        for (int i = 0; i < cells.length; i++) {
            CellType type = Cells.type(cells[i]);
            if (type == CellType.INPUT) {
                InputPoint point = inputsByCell.get(i);
                if (point == null) {
                    point = new InputPoint(nextInputId++, i);
                }
                inputs.put(point.id(), point);
            } else if (type == CellType.OUTPUT) {
                OutputPoint point = outputsByCell.get(i);
                if (point == null) {
                    point = new OutputPoint(nextOutputId++, i);
                }
                outputs.put(point.id(), point);
            }
        }
        simulator = new Simulator(width, height, cells);
        inputOrder = inputs.values().stream()
                .sorted(Comparator.comparingInt(InputPoint::cell))
                .toArray(InputPoint[]::new);
        outputOrder = outputs.values().stream()
                .sorted(Comparator.comparingInt(OutputPoint::cell))
                .toArray(OutputPoint[]::new);
        inputBuffer = new boolean[inputOrder.length];
        outputBuffer = new boolean[outputOrder.length];
        needsStep = true;
    }
}
