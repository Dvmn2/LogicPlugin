package net.dvmn2.logicplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/** File form of a circuit output. */
public final class OutputDto {
    public int id;
    public int cell;
    public boolean last;
    public List<String> commands = new ArrayList<>();
}
