# LogicPlugin

Paper plugin that stores logic circuits, simulates them and runs commands when an output turns on. Circuits are edited on a client-side grid by the companion Fabric mod `Logic`.

## Requirements
- Minecraft 1.21.11, Paper, Java 21
- Companion client mod `Logic` 1.0.0 (required for editing; everything else works without it)

## Build and run
```bash
./gradlew build      # build/libs/LogicPlugin-<version>.jar
./gradlew runServer  # Paper 1.21.11 test server
```

## Architecture
| Class | Responsibility |
|---|---|
| `LogicPlugin` | composition root: wiring, enable/disable order |
| `LogicManager` | runtime state: circuits, editor sessions, simulation, entities, persistence; main thread only |
| `LogicListener` | board and switch clicks, editor lock release on quit, orphan cleanup on `EntitiesLoadEvent` |
| `LogicSettings` | immutable snapshot of `config.yml` |
| `Lang`, `Permissions`, `PluginKeys` | localisation, permission constants, PDC keys |
| `LogicApiImpl` | implementation of `LogicApi` |
| `command.LogicCommand` | the `/logic` Brigadier tree |
| `gui.EditorNetwork` | plugin-message transport of the client editor, format in its Javadoc |
| `model.Circuit` | one circuit: cells, inputs, outputs, access list, simulation hook |
| `model.Simulator` | pure simulation, no Bukkit types |
| `model.CellType`, `model.Cells` | cell kinds and the one-byte cell packing |
| `model.InputPoint`, `model.OutputPoint`, `model.SwitchRef`, `model.Placement` | circuit parts |
| `storage.CircuitStore` | `circuits/<uuid>.json` reading and ordered writing |
| `storage.CircuitCodec` | conversion between `Circuit` and `storage.dto.CircuitDto` |
| `util.Wire` | VarInt, string, UUID and byte-array helpers of the wire format |

Threading: all Bukkit API calls and all circuit state are on the main thread. Plugin messages are decoded where they arrive and handed over with `runTask`. Disk writes run on one dedicated thread owned by `CircuitStore`.

Lifecycle: `onEnable` saves the default config, reads settings, creates store, network and manager, loads circuits, removes orphaned entities, registers listener, command and API, then starts the periodic tasks. `onDisable` cancels the tasks, closes open editors, unregisters channels, queues a snapshot of every circuit and waits until the writer finished.

### Simulation rules
- Cells with a direction emit only towards their front. Splitters emit forward, left and right. Bridges pass a signal straight through on both axes without mixing them.
- Wires, splitters and bridges are instant. The signal is found by flooding from true sources, so a loop of wires cannot hold a value.
- A gate reads the neighbours on its three non-front sides and shows the result on the next step. Every gate therefore adds one step of delay, and feedback loops through gates (latches, clocks) work.
- A gate with no connected input is false. NOT, like NOR, is true when no connected input is true.
- A step runs every `settings.tick-interval-ticks`. Circuits whose gates did not change and whose inputs did not change are skipped. Circuits in unloaded chunks are not simulated.
- An output runs its commands on the false-to-true edge.

## Public API
Obtain the service from the main thread:

```java
LogicApi api = Bukkit.getServicesManager().load(LogicApi.class);
api.setInput("door", 1, true);   // input i1 of circuit "door"
```

| Method | Description |
|---|---|
| `hasMod(Player)` | true if the client announced the editor channel |
| `circuitNames()` | sorted names of all circuits |
| `setInput(String, int, boolean)` | sets an input; returns `SUCCESS`, `UNKNOWN_CIRCUIT` or `UNKNOWN_INPUT` |
| `isOutputOn(String, int)` | last known output value; false for unknown outputs |

Event `LogicOutputTriggerEvent` (cancellable, main thread) is called on every rising edge of an output that has commands, before they run. Cancelling skips the commands of that trigger.

## Protocol
| Channel | Direction | Protocol | Format |
|---|---|---|---|
| `dvmn2:lg_open` | S2C | 1 | `EditorNetwork` Javadoc |
| `dvmn2:lg_signals` | S2C | 1 | `EditorNetwork` Javadoc |
| `dvmn2:lg_close` | S2C | 1 | `EditorNetwork` Javadoc |
| `dvmn2:lg_save` | C2S | 1 | `EditorNetwork` Javadoc |

Any format change increases `EditorNetwork.PROTOCOL` and the constant of the mod. A packet with another protocol version is ignored. Plugin messages are limited to about 32 KB, one cell is one byte, so a circuit side is limited to 128 cells (`Cells.MAX_DIMENSION`).

The server never trusts the editor: a save is accepted only from the player who holds the editor session, the size must match, every cell must be valid, locked cells must be unchanged, and in normal mode only allowed cell types may appear.

## Data and files
`plugins/LogicPlugin/circuits/<uuid>.json`, one file per circuit, format version 1, DTOs in `storage.dto`. Cells and gate values are Base64. Writes are atomic (temporary file, then move). A file that fails validation is reported and skipped.

Gate values are saved so a restart does not produce false rising edges. Output commands never run during loading.

## Conventions and constraints
- Board and switch entities are persistent and stay in the world on shutdown. STYLE §4.7 asks to remove helper entities on shutdown, but these are placed objects. They are tied to circuits by PDC keys and an entity registry; marked entities that no circuit owns are removed on startup and on `EntitiesLoadEvent`.
- Disk writes use one dedicated thread instead of the Bukkit async pool, because writes of the same file must stay ordered and must be able to finish during `onDisable`.
- Cells are stored as Base64 inside JSON (at most about 22 KB per circuit) instead of a separate gzip file.
- Output commands run as the console. They can be set only with `logic.command` by the owner or `logic.admin`. The placeholders `<player>`, `<circuit>` and `<output>` are replaced by plain text; `<player>` is the last player who changed an input, empty for the console and API.
- Input and output ids are stable per cell and are never reused; moving an input cell creates a new id.

## License
All rights reserved.
