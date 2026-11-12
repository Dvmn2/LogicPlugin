package net.dvmn2.logicplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/** File form of a circuit input. */
public final class InputDto {
    public int id;
    public int cell;
    public boolean state;
    public List<SwitchDto> switches = new ArrayList<>();
}
