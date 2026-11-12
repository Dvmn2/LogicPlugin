package net.dvmn2.logicplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * File form of a circuit ({@code circuits/<uuid>.json}). Cells and gate values are Base64.
 * Version 1.
 */
public final class CircuitDto {
    public int version;
    public String id;
    public String name;
    public String owner;
    public String world;
    public double x;
    public double y;
    public double z;
    public String boardEntity;
    public String labelEntity;
    public int width;
    public int height;
    public int allowedMask;
    public int nextInputId;
    public int nextOutputId;
    public String cells;
    public String gates;
    public List<String> trusted = new ArrayList<>();
    public List<InputDto> inputs = new ArrayList<>();
    public List<OutputDto> outputs = new ArrayList<>();
}
