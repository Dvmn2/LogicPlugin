# LogicPlugin

Paper plugin for building logic circuits with gates on a grid, switching their inputs with levers or commands, and running server commands when an output turns on.

## Features
- Circuits of up to 128x128 cells with wires, splitters, bridges and the gates AND, OR, NOT, XOR, NAND, NOR and XNOR.
- Fixed inputs, outputs and locked cells set up by the creator; players with access only place the allowed elements.
- Editing on a large grid in the client with live signal display (needs the companion mod).
- Inputs driven by switch entities in the world or by `/logic input`.
- Each output runs any number of console commands when it turns on.
- Safe saving: invalid or forbidden edits are rejected by the server.
- Gate feedback loops work, so latches and clocks can be built.

## Requirements
- Paper 1.21.11, Java 21
- The Logic client mod for players who edit circuits. Switching inputs does not need the mod.

## Installation
1. Put `LogicPlugin-<version>.jar` into the `plugins` folder.
2. Start the server; `plugins/LogicPlugin/config.yml` is created.
3. Give editors the Logic mod.
4. Stand where the board should be and run `/logic create <name> <width> <height>`.
5. Sneak and right-click the board to set it up: place inputs, outputs and locked cells.

## Commands
| Command | Permission | Description |
|---|---|---|
| `/logic create <name> <width> <height>` | `logic.create` | Create a circuit and its board at your position |
| `/logic delete <circuit>` | owner or `logic.admin` | Delete a circuit with its entities |
| `/logic list` | `logic.use` | List the circuits you may edit |
| `/logic info <circuit>` | `logic.use` | Show owner, location, inputs, outputs and allowed cells |
| `/logic allow <circuit> <type> <true\|false>` | owner or `logic.admin` | Allow or forbid a cell type for editors |
| `/logic trust <circuit> add\|remove <player>` | owner or `logic.admin` | Manage players who may edit the circuit |
| `/logic input <circuit> <input> true\|false\|toggle` | owner, trusted or `logic.admin` | Set an input |
| `/logic spawn-input <circuit> <input>` | owner or `logic.admin` | Create a switch for an input at your position |
| `/logic output <circuit> <output> command add <command>` | `logic.command` | Add a command to an output |
| `/logic output <circuit> <output> command remove <number>` | `logic.command` | Remove a command |
| `/logic output <circuit> <output> command list` | `logic.command` | List the commands of an output |
| `/logic reload` | `logic.reload` | Reload `config.yml` |

Inputs are named `i1`, `i2`, ... and outputs `o1`, `o2`, ... in the order they were created. In output commands `<player>` is the last player who changed an input, `<circuit>` the circuit name and `<output>` the output id. Commands run as the console on every false-to-true change of the output.

Right-click the board to edit; sneak and right-click as the owner or `logic.admin` to set the circuit up. Right-click a switch to flip its input.

## Permissions
| Permission | Default | Description |
|---|---|---|
| `logic.use` | everyone | Use `/logic`, flip switches, edit circuits you may access |
| `logic.create` | op | Create circuits |
| `logic.command` | op | Manage commands run by outputs |
| `logic.reload` | op | Use `/logic reload` |
| `logic.admin` | op | All administrative permissions; full access to every circuit |

## Configuration
File: `plugins/LogicPlugin/config.yml`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `settings.language` | string | `auto` | `ru`, `en`, `auto` | Message language; `auto` follows the player's client language |
| `settings.max-width-cells` | int | `64` | `1..128` | Maximum circuit width |
| `settings.max-height-cells` | int | `64` | `1..128` | Maximum circuit height |
| `settings.tick-interval-ticks` | int | `4` | `1..100` | Simulation step period; every gate adds one step of delay |
| `settings.save-interval-seconds` | int | `30` | `5..3600` | How often modified circuits are written |
| `settings.max-circuits-per-player` | int | `5` | `1..1000` | Circuits one player may own; `logic.admin` is not limited |
| `settings.max-commands-per-output` | int | `8` | `1..64` | Commands per output |
| `settings.max-command-length-chars` | int | `256` | `1..1024` | Length of one command |
| `settings.editor-timeout-seconds` | int | `900` | `30..86400` | An editor session is closed after this time |

```yaml
settings:
  language: en
  max-width-cells: 96
  max-height-cells: 96
  tick-interval-ticks: 2
```

## Data
Circuits are stored in `plugins/LogicPlugin/circuits/`, one `<uuid>.json` file per circuit. Back up the folder while the server is stopped or copy it as is. Deleting a file removes that circuit after the next restart; its board and switches are cleaned up automatically. Deleting the folder resets all circuits.

## Companion mod
The `Logic` client mod adds the grid editor: a large zoomable field with a palette of elements, direction control and live signal display. Without the mod players can still use switches and commands, but cannot edit.

## Troubleshooting
- **"Editing requires the Logic client mod"**: the player has no mod, or an older one with another protocol version. Install the matching mod version.
- **"Another player is editing this circuit"**: only one editor per circuit; the lock is released when the editor is closed, the player leaves or `settings.editor-timeout-seconds` passes.
- **Changes were rejected**: the circuit has locked cells or the creator did not allow an element. Use `/logic allow` or set the circuit up again.
- **An output command does not run**: check `/logic output <circuit> <output> command list` and that the output actually goes from off to on.
- **Board or switch is missing after `/kill`**: delete the circuit with `/logic delete` and create it again.

## License
All rights reserved.
