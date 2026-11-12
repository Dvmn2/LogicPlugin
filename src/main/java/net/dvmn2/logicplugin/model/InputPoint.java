package net.dvmn2.logicplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime state of one circuit input. Main thread only.
 *
 * <p>The id is stable for the life of the input cell and is shown to players as {@code i<id>}.
 */
public final class InputPoint {

    private final int id;
    private final int cell;
    private final List<SwitchRef> switches = new ArrayList<>();
    private boolean state;

    public InputPoint(int id, int cell) {
        this.id = id;
        this.cell = cell;
    }

    public int id() {
        return id;
    }

    /** Index of the input cell inside the circuit. */
    public int cell() {
        return cell;
    }

    public boolean state() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public List<SwitchRef> switches() {
        return Collections.unmodifiableList(switches);
    }

    public void addSwitch(SwitchRef ref) {
        switches.add(ref);
    }
}
