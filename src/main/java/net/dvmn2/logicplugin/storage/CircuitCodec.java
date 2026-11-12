package net.dvmn2.logicplugin.storage;

import net.dvmn2.logicplugin.model.Cells;
import net.dvmn2.logicplugin.model.Circuit;
import net.dvmn2.logicplugin.model.InputPoint;
import net.dvmn2.logicplugin.model.OutputPoint;
import net.dvmn2.logicplugin.model.Placement;
import net.dvmn2.logicplugin.model.SwitchRef;
import net.dvmn2.logicplugin.storage.dto.CircuitDto;
import net.dvmn2.logicplugin.storage.dto.InputDto;
import net.dvmn2.logicplugin.storage.dto.OutputDto;
import net.dvmn2.logicplugin.storage.dto.SwitchDto;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Converts between {@link Circuit} and its file form. Stateless; {@link #toDto} must run on the main thread
 * because it reads live state, the result is an independent snapshot.
 */
public final class CircuitCodec {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int GATE_BYTES_SLACK = 8;

    private CircuitCodec() {
    }

    /** Takes a snapshot of a circuit for writing. */
    public static CircuitDto toDto(Circuit circuit) {
        CircuitDto dto = new CircuitDto();
        dto.version = FORMAT_VERSION;
        dto.id = circuit.id().toString();
        dto.name = circuit.name();
        dto.owner = circuit.owner().toString();
        Placement placement = circuit.placement();
        dto.world = placement.world().toString();
        dto.x = placement.x();
        dto.y = placement.y();
        dto.z = placement.z();
        dto.boardEntity = placement.boardEntity().toString();
        dto.labelEntity = placement.labelEntity().toString();
        dto.width = circuit.width();
        dto.height = circuit.height();
        dto.allowedMask = circuit.allowedMask();
        dto.nextInputId = circuit.nextInputId();
        dto.nextOutputId = circuit.nextOutputId();
        dto.cells = Base64.getEncoder().encodeToString(circuit.cells());
        dto.gates = Base64.getEncoder().encodeToString(circuit.gateBits());
        for (UUID trusted : circuit.trusted()) {
            dto.trusted.add(trusted.toString());
        }
        for (InputPoint point : circuit.inputs().values()) {
            InputDto input = new InputDto();
            input.id = point.id();
            input.cell = point.cell();
            input.state = point.state();
            for (SwitchRef ref : point.switches()) {
                SwitchDto entry = new SwitchDto();
                entry.interaction = ref.interaction().toString();
                entry.display = ref.display().toString();
                input.switches.add(entry);
            }
            dto.inputs.add(input);
        }
        for (OutputPoint point : circuit.outputs().values()) {
            OutputDto output = new OutputDto();
            output.id = point.id();
            output.cell = point.cell();
            output.last = point.last();
            output.commands.addAll(point.commands());
            dto.outputs.add(output);
        }
        return dto;
    }

    /**
     * Validates a loaded file and builds the circuit.
     *
     * @return the circuit, or {@code null} (after logging the reason) when the file is invalid
     */
    public static @Nullable Circuit fromDto(CircuitDto dto, Logger logger) {
        try {
            if (dto.version != FORMAT_VERSION) {
                logger.warning("Unsupported circuit file version " + dto.version);
                return null;
            }
            if (dto.name == null || !dto.name.matches("[a-z0-9_-]{1," + MAX_NAME_LENGTH + "}")) {
                logger.warning("Invalid circuit name in file");
                return null;
            }
            if (dto.width < 1 || dto.width > Cells.MAX_DIMENSION
                    || dto.height < 1 || dto.height > Cells.MAX_DIMENSION) {
                logger.warning("Circuit " + dto.name + ": invalid size");
                return null;
            }
            int size = dto.width * dto.height;
            if (dto.cells == null || dto.cells.length() > size * 2 + GATE_BYTES_SLACK) {
                logger.warning("Circuit " + dto.name + ": invalid cells");
                return null;
            }
            byte[] cells = Base64.getDecoder().decode(dto.cells);
            if (cells.length != size) {
                logger.warning("Circuit " + dto.name + ": cell count mismatch");
                return null;
            }
            for (byte cell : cells) {
                if (!Cells.isValid(cell)) {
                    logger.warning("Circuit " + dto.name + ": invalid cell value");
                    return null;
                }
            }
            Placement placement = new Placement(UUID.fromString(dto.world), dto.x, dto.y, dto.z,
                    UUID.fromString(dto.boardEntity), UUID.fromString(dto.labelEntity));
            Circuit circuit = new Circuit(UUID.fromString(dto.id), dto.name, UUID.fromString(dto.owner),
                    placement, dto.width, dto.height, cells, dto.allowedMask);

            List<InputPoint> inputs = new ArrayList<>();
            for (InputDto entry : dto.inputs) {
                InputPoint point = new InputPoint(entry.id, entry.cell);
                point.setState(entry.state);
                for (SwitchDto ref : entry.switches) {
                    point.addSwitch(new SwitchRef(UUID.fromString(ref.interaction), UUID.fromString(ref.display)));
                }
                inputs.add(point);
            }
            List<OutputPoint> outputs = new ArrayList<>();
            for (OutputDto entry : dto.outputs) {
                OutputPoint point = new OutputPoint(entry.id, entry.cell);
                point.setLast(entry.last);
                entry.commands.forEach(point::addCommand);
                outputs.add(point);
            }
            List<UUID> trusted = new ArrayList<>();
            for (String raw : dto.trusted) {
                trusted.add(UUID.fromString(raw));
            }
            byte[] gates = dto.gates == null ? new byte[0] : Base64.getDecoder().decode(dto.gates);
            circuit.restore(inputs, outputs, dto.nextInputId, dto.nextOutputId, gates, trusted);
            return circuit;
        } catch (IllegalArgumentException | NullPointerException exception) {
            // Malformed UUID, Base64 or a missing field: the file is corrupt.
            logger.warning("Circuit file is corrupt: " + exception.getMessage());
            return null;
        }
    }
}
