# MH Wilds Loadout Optimizer

An optimizer for *Monster Hunter Wilds* loadouts. Given a set of required skills, it searches
armor, decoration, amulet, and weapon combinations and returns the highest-scoring builds.

Game data is from [mhdb-wilds-data](https://github.com/LartTyler/mhdb-wilds-data).

## Requirements

Java 17+, Maven 3.9+.

## CLI

```bash
./run.sh                          # uses default config and data
./run.sh data config.json         # custom data dir and config
./run.sh data config.json --top 5 --show 3 --output results.txt
```

| Option | Description |
|---|---|
| `--top N` | Number of builds to compute (default: 1000) |
| `--show N` | Number of computed builds to display (default: all computed) |
| `--ranking <name>` | `free_slots` (default) or `free_equipment_slots` |
| `--equipment-bonus N` | Points per omitted equipment slot (used by `free_equipment_slots`) |
| `--output <file>` | Write to file instead of stdout |

## GUI

```bash
./gui.sh
```

Same solver behind a Swing window: set skills, rarity bounds, ranking, click Solve,
and browse the ranked results. Configs can be saved and loaded from the File menu.
