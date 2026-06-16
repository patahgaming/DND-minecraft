# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Dungeons & Dragons game embedded in Minecraft as a single Paper server plugin. Minecraft is the sole interface — no web frontend, no separate services. AI (DeepSeek) acts as narrator and drives NPC dialogue.

**Current version: v1.0.0.31** — Phases 1–4 complete, Phase 5 in progress. [SKILL.md](SKILL.md) is the primary design document.

## Versioning Rule

**Setiap ada update code, bump digit terakhir version di `pom.xml`.**

Format: `MAJOR.MINOR.PATCH.BUILD` — `1.0.0.31` → `1.0.0.32`

Steps:

1. Bump `<version>` di `pom.xml` (digit terakhir +1)
2. `mvn package -DskipTests`
3. Copy JAR ke `server/plugins/`, hapus versi lama
4. Commit: `"Bump v1.0.0.X — ..."` + push

## Tech Stack

| Layer | Tech |
|-------|------|
| Server | Paper 26.1.2 (Minecraft 1.21.11) |
| Language | Java 21+ |
| Build | Maven (pom.xml) |
| AI | DeepSeek API via HTTP (`deepseek-v4-flash`) |
| Data | YAML files + PDC (PersistentDataContainer) |
| Dependencies | Paper API (provided), Gson (shaded), SnakeYAML (bundled Paper) |

## Commands

| Command | Description |
|---------|-------------|
| `/atk <melee\|ranged> [target]` | Attack with AC check, hit/miss, crit, damage types, resistance |
| `/roll <type>` | Roll d20 + ability mod + proficiency, animated (obfuscated text + note block sounds), color-coded result |
| `/talk <npc_id> [message]` | Dialog with NPC (AI via DeepSeek), side NPCs work too, tab-complete NPC list |
| `/cast <spell> [target]` | Cast spell in combat: 12 cantrips, attack roll/saving throw, spell slots persisted to PDC |
| `/battle start` | Scan nearby mobs → freeze them → roll initiative → begin turn-based combat |
| `/battle flee` | DEX check DC12 to escape, fail = wasted turn |
| `/battle status` | Show turn order, HP, current turn |
| `/spawnnpc <template_id>` | Spawn important NPC from YAML template (any mob type, gold bold name) |
| `/newnpc <id> <name> [type] [spawn]` | Create new NPC template + save YAML + optional spawn |
| `/character list` | List available character templates |
| `/character pick <template>` | Pick pre-made character (aric_fighter, lyra_rogue, merlin_wizard, mira_cleric) |
| `/character create <class> [name] [race]` | Create custom character with standard array stats |
| `/character info` | Show character sheet (class, HP, abilities, proficient skills) |
| `/character reset` | Delete character & start over |
| `/dnd reload` | Hot swap — reload config + all YAML + SRD without restart |
| `/dnd save` | Force save all player/NPC data to disk |
| `/dnd version` | Show version, API, party node, AI model |
| `/dnd stop\|start` | Pause/resume story tick task |

## Architecture

### Package Structure (31 files)

```
src/main/java/id/patahgaming/dndplugin/
├── DndPlugin.java                    # Entry point + DI container + lifecycle
├── ai/
│   ├── DeepSeekClient.java           # HTTP POST /chat/completions (synchronous, async-only wrapper)
│   ├── NarratorService.java          # On-rail/off-rail AI narration + SRD rules lookup
│   └── NpcDialogueService.java       # NPC dialogue + disposition decision + backstory + SRD
├── command/
│   ├── AtkCommand.java               # /atk melee/ranged: d20+mod+prof vs AC, hit/miss/crit, TabCompleter
│   ├── BattleCommand.java            # /battle start/flee/status: scan mobs, flee DEX check
│   ├── CastCommand.java              # /cast <spell>: 12 spells, attack roll/save, class filter, slots
│   ├── CharacterCommand.java         # /character list/pick/create/info/reset: full creation flow
│   ├── DndCommand.java               # /dnd reload/save/version/stop/start: admin controls
│   ├── NewNpcCommand.java            # /newnpc: create template YAML, any mob type, backstory
│   ├── RollCommand.java              # /roll: d20 + ability + proficiency, 14 types + TabCompleter
│   ├── SpawnNpcCommand.java          # /spawnnpc: spawn from template, any entity type
│   └── TalkCommand.java              # /talk: AI dialog + PDC lookup + side NPC greeting + TabCompleter
├── data/
│   ├── CharacterClass.java           # 10 D&D classes enum: HP, primary ability, skills, saving throws
│   ├── NpcDataStore.java             # NPC records: auto-save YAML on history/disposition change
│   ├── PlayerDataStore.java          # PDC + YAML: char creation, consensus party node, reset
│   ├── PoiStore.java                 # Points of interest: bbox lookup, YAML loading
│   ├── SrdRulesStore.java            # 1.4MB SRD keyword search engine + keyword index
│   └── StoryTreeStore.java           # Branching story nodes: trigger types, bbox, choices
├── engine/
│   ├── PoiTickTask.java              # Configurable tick loop: bbox check + trigger dedup + auto-refresh
│   ├── dice/DiceRoller.java          # d20, dN, NdS+mod (ThreadLocalRandom)
│   └── combat/
│       ├── CombatEngine.java         # AC calc, hit/miss, damage types, resistance/vuln, prof bonus
│       ├── Conditions.java           # Poisoned/stunned/unconscious/blinded + death saves (d20)
│       ├── InitiativeRoller.java     # d20+DEX initiative: sort descending + tiebreaker
│       └── TurnManager.java          # State machine + auto-mob AI + freeze/unfreeze cycle + recursion guard
├── listener/
│   ├── CombatListener.java           # Damage sound/animation + HP bar below names + title cooldown
│   ├── NpcInteractListener.java      # Right-click NPC → freeze + AI dialog + auto-unfreeze 5s
│   ├── NpcSpawnListener.java         # Auto-name NPCs + EntityTargetEvent guard + color coding
│   └── PlayerJoinListener.java       # Init character flow + scoreboard + HP/level auto-refresh
└── ui/
    ├── ScoreboardManager.java        # Private per-player sidebar: name, class, HP, 6 abilities, node
    └── TitleDisplay.java             # /title display: roll animation (obfuscated text + note block sounds)
```

### Key Design Rules

1. **Async AI only** — DeepSeekClient must NEVER be called from main thread. Always wrap in `BukkitScheduler.runTaskAsynchronously`.
2. **PDC is runtime source of truth** — YAML is backup/persistence. Read from PDC first.
3. **NPC hostility controlled by disposition** — Hostile mobs (Zombie, Skeleton) are passive unless `disposition=agro`. Enforced via `EntityTargetEvent` cancel. Mobs freeze on battle start, unfreeze→attack→re-freeze per turn.
4. **NPC color coding** — Important NPCs = `§6§l` gold bold, Side NPCs = `§f§l` white bold.
5. **Scoreboard auto-refresh** — Tick loop refreshes all player scoreboards every interval (HP/max HP sync). Listener catches damage/regen/level change for instant refresh.
6. **Max health via Attribute API** — `player.getAttribute(Attribute.MAX_HEALTH).getValue()` — respects `/attribute` command.
7. **Party progress = consensus** — `partyCurrentNode` uses most-advanced node across all players. Volatile for thread safety.
8. **API key NOT in git** — `config.yml` template has placeholder. Runtime config at `server/plugins/DndPlugin/config.yml`.
9. **HP bar on entities** — CombatListener generates fresh HP bar via `LegacyComponentSerializer`, pulls base name from PDC for NPCs, never stacks.
10. **Spell slots persisted** — Stored in PDC (`spell_slots` key), survive server restart.
11. **Combat damage feedback** — `applyDamage()` manually calls `CombatListener.updateHpBar()` since `setHealth()` doesn't fire `EntityDamageEvent`.
12. **Battle freeze cycle** — start: freeze all mobs → per mob turn: unfreeze→attack→re-freeze → end: unfreeze survivors.

## Build & Run

```bash
# Build
mvn package -DskipTests
# Output: target/dndplugin-<version>.jar

# Deploy
cp target/dndplugin-*.jar server/plugins/

# Run server (dari folder server/)
cd server
java -Xmx2G -Xms1G --enable-native-access=ALL-UNNAMED -jar paper.jar nogui
```

## Data Files

| Path | Purpose | Format |
|------|---------|--------|
| `data/story/*.yml` | Story tree nodes | `node_id`, `trigger`, `bbox`, `narrative`, `choices` |
| `data/npcs/*.yml` | NPC templates | `npc_id`, `display_name`, `disposition`, `entity_type`, `backstory`, `history` |
| `data/poi/*.yml` | Points of Interest | `poi_id`, `name`, `biome`, `bbox` |
| `data/characters/*.yml` | Character templates | `template_id`, `display_name`, `class`, `race`, `backstory` |
| `data/players/<uuid>.yml` | Player character sheets (runtime, generated) | Stats, HP, class, char_name, race, story progress |
| `data/rules/srd/5esrd.yaml` | D&D 5e SRD (1.4MB, compiled from dnd-5e-srd) | Full rules reference |
| `config.yml` | Plugin config | DeepSeek API key/model, tick interval, world size |

## SRD Rules (SrdRulesStore)

- Loaded from `data/rules/srd/5esrd.yaml` (1.4MB, 9 top-level sections)
- `search("keyword")` → filtered rule text (max 3500 chars, cached)
- `searchMultiple("stealth", "grapple")` → combined results
- Used by NarratorService and NpcDialogueService for AI context
- Index built at startup (async, non-blocking)

## Character System

- **10 classes**: Fighter, Rogue, Wizard, Cleric, Barbarian, Ranger, Paladin, Warlock, Monk, Druid
- **Standard array**: Primary=15, Secondary=CON(14), rest=13,12,10,8
- **HP**: `classBaseHP + CON modifier` (Fighter=10, Barbarian=12, Wizard=6)
- **Proficiency**: `floor((level-1)/4) + 2` → applied to first 2 class skills in `/roll`
- **4 starter templates**: aric_fighter, lyra_rogue, merlin_wizard, mira_cleric (with backstory)
- **PDC keys**: `level`, `str/dex/con/int/wis/cha`, `class`, `char_name`, `race`, `has_character`

## Combat & Battle System

- **AC**: Player=10+DEX, Mobs=natural armor (Zombie:8, Skeleton:13, Enderman:15, Iron Golem:20, etc.)
- **Attack**: d20 + ability + proficiency vs AC, nat20=crit (double dice), nat1=auto miss
- **Damage types**: 12 types (slashing/piercing/bludgeoning/fire/cold/lightning/acid/poison/necrotic/radiant/force/psychic)
- **Resistance/Vulnerability**: Skeleton (piercing ½, bludgeon 2×), Zombie (radiant 2×), Enderman (fire/lightning ½), Spider (fire 2×)
- **TurnManager state**: WAITING_TURN → ACTION_SELECTED → RESOLVING → NEXT_TURN
- **Auto-mob AI**: unfreeze→attack nearest player→re-freeze, 1.5s delay
- **Flee**: DEX check DC12, nat20 auto-success
- **Death saves**: d20, 10+ = success, nat1 = 2 failures, nat20 = regain 1 HP, 3 success = stable, 3 fail = dead

## Spell System (CastCommand)

- **12 spells**: fire_bolt, magic_missile, burning_hands, chill_touch, shocking_grasp, ray_of_frost, sacred_flame, poison_spray, acid_splash, frostbite, eldritch_blast, word_of_radiance
- **Class filter**: Arcane (Wizard/Warlock), Divine (Cleric/Paladin/Druid), Warlock-only (eldritch_blast)
- **Spell slots**: 2 slots, persisted in PDC (`spell_slots` key)
- **Attack roll spells**: d20 + spellcasting mod + prof vs AC
- **Save spells**: target saves vs DC (8+mod+prof), half-on-save (burning_hands) vs save-negates (sacred_flame)
- **Spellcasting ability**: determined by class primary (Wizard=INT, Cleric=WIS, Warlock=CHA)

## Roll Animation

- **Animation**: 800ms obfuscated text (`§k████`) + 4 note block hat sounds (ascending pitch)
- **Color map**: Nat1=Red, 2-7=Orange, 8-17=Yellow, 18-19=Blue, Nat20=Green
- **Final sound**: Bell (nat20), Bass (nat1), Pling (others, pitch varies)
- **Chat log**: `[Roll] 14 + 3 = 17 — stealth`
