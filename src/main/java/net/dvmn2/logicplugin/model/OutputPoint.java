package net.dvmn2.logicplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime state of one circuit output and the commands it runs. Main thread only.
 *
 * <p>The id is stable for the life of the output cell and is shown to players as {@code o<id>}.
 */
public final class OutputPoint {

    private final int id;
    private final int cell;
    private final List<String> commands = new ArrayList<>();
    private boolean last;

    public OutputPoint(int id, int cell) {
        this.id = id;
        this.cell = cell;
    }

    public int id() {
        return id;
    }

    /** Index of the output cell inside the circuit. */
    public int cell() {
        return cell;
    }

    /** Value seen on the previous step; used to detect the rising edge. */
    public boolean last() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public List<String> commands() {
        return Collections.unmodifiableList(commands);
    }

    public void addCommand(String command) {
        commands.add(command);
    }

    /** Removes the command at a zero-based index; returns false when the index is out of range. */
    public boolean removeCommand(int index) {
        if (index < 0 || index >= commands.size()) {
            return false;
        }
        commands.remove(index);
        return true;
    }
}
