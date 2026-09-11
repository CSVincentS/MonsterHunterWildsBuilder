# MH Wilds Loadout Optimizer

Finds the best armor, decorations, talisman, and weapon for your hunt in *Monster Hunter Wilds* based on the skills you actually want.

You tell it what skills you need (for example, Constitution 5 and Stamina Surge 3), and it searches all possible combinations of armor, deco jewels, talisman, and weapon to build the best loadout it can.

## Quick start

You need Java 17+ and Maven 3.9+.

```bash
mvn compile     # compile
mvn test        # run the tests
```

Run the command-line tool:

```bash
./run.sh
```

Or against your own file with options:

```bash
./run.sh data config.json --top 5 --output results.txt
```

`run.sh` takes the data folder and config file as arguments. Most options have sensible defaults: `--top N` controls how many builds to show, `--output <file>` writes results to a file instead of the screen.

## GUI

```bash
./gui.sh
```

A simple desktop app that does the same job without the terminal:

- Add the skills you want (with minimum levels)
- Optionally limit armor/decoration/talisman/weapon rarity
- Click **Solve** and browse the ranked builds
- **File → Save Config…** saves everything to a config file you can reuse later

## Telling it what you want

Once you learn the skill names, creating a config is easy. The default one looks like:

```json
{
  "required_skills": {
    "Constitution": 5,
    "Stamina Surge": 3
  },
  "weapon_type": "bow"
}
```

- `required_skills`: the skills your build must have, and the minimum level. Names match with or without capital letters.
- `weapon_type`: which weapon class to optimize (defaults to `bow`).

Optional settings let you exclude specific sets, skills, decorations, or talismans you don't want in the pool, or restrict rarity. See `src/main/resources/default-config.json` for a complete example.

## Where to find more

- `data/` include the game data this runs on (read-only, do not edit)
- The config files in `configs/` include example configs to copy and tweak

Game data is from the [mhdb-wilds-data](https://github.com/LartTyler/mhdb-wilds-data) project.