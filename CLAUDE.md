# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Dungeons & Dragons game embedded in Minecraft as a single Paper server plugin. Minecraft is the sole interface — no web frontend, no separate services. AI (DeepSeek) acts as narrator and drives NPC dialogue.

**Current version: v1.0.0.20** — Phases 1–4 complete, Phase 5 in progress. [SKILL.md](SKILL.md) is the primary design document.

## Versioning Rule

**Setiap ada update code, bump digit terakhir version di `pom.xml`.**

Format: `MAJOR.MINOR.PATCH.BUILD` — `1.0.0.20` → `1.0.0.21`

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
| `/atk <melee\|ranged> [target]` | Attack with AC check, hit/miss, crit, damage types |
| `/roll <type>` | Roll d20 + ability mod (14 types with tab-complete) |
| `/talk <npc_id> [message]` | Dialog with NPC (AI via DeepSeek), side NPCs work too |
| `/battle start` | Scan nearby mobs → roll initiative → begin combat |
| `/battle flee` | DEX check DC12 to escape, fail = wasted turn |
| `/battle status` | Show turn order, HP, current turn |
| `/spawnnpc <template_id>` | Spawn important NPC from YAML template |
| `/newnpc <id> <name> [type] [spawn]` | Create new NPC template + optional spawn |
| `/dnd reload` | Hot swap — reload config + all YAML without restart |
| `/dnd save` | Force save all player/NPC data to disk |
| `/dnd version` | Show version, API, party node, AI model |
| `/dnd stop\|start` | Pause/resume story tick task |

## Architecture

### Package Structure (25+ files)

```
src/main/java/id/patahgaming/dndplugin/
├── DndPlugin.java                    # Entry point + DI container
├── ai/
│   ├── DeepSeekClient.java           # HTTP POST /chat/completions (async-only)
│   ├── NarratorService.java          # On-rail/off-rail AI narration
│   └── NpcDialogueService.java       # NPC dialogue + disposition + backstory
├── command/
│   ├── AtkCommand.java               # /atk (melee/ranged + AC + TabCompleter)
│   ├── BattleCommand.java            # /battle (start/flee/status)
│   ├── DndCommand.java               # /dnd (reload/save/version/stop/start)
│   ├── NewNpcCommand.java            # /newnpc (create template + any mob type)
│   ├── RollCommand.java              # /roll (d20 + ability mod + 14 types)
│   ├── SpawnNpcCommand.java          # /spawnnpc (spawn from template)
│   └── TalkCommand.java              # /talk (AI dialog + PDC lookup + TabCompleter)
├── data/
│   ├── NpcDataStore.java             # NPC records (auto-save on change)
│   ├── PlayerDataStore.java          # PDC + YAML player data
│   ├── PoiStore.java                 # Points of interest (bbox lookup)
│   ├── SrdRulesStore.java            # 1.4MB SRD keyword search engine
│   └── StoryTreeStore.java           # Branching story nodes (trigger types, bbox)
├── engine/
│   ├── PoiTickTask.java              # 2s tick loop (configurable interval)
│   ├── dice/DiceRoller.java          # d20, dN, NdS+mod
│   └── combat/
│       ├── CombatEngine.java         # AC, hit/miss, damage types, resistance
│       ├── Conditions.java           # Poisoned/stunned/unconscious/blinded + death saves
│       ├── InitiativeRoller.java     # d20+DEX initiative order
│       └── TurnManager.java          # Turn state machine + auto-mob AI
├── listener/
│   ├── NpcInteractListener.java      # Right-click NPC → freeze + dialog
│   ├── NpcSpawnListener.java         # Auto-name NPCs + EntityTargetEvent guard
│   └── PlayerJoinListener.java       # Init char sheet + HP/level/node sync
└── ui/
    ├── ScoreboardManager.java        # Private sidebar (6 abilities + HP + node)
    └── TitleDisplay.java             # /title for roll & combat results
```

### Key Design Rules

1. **Async AI only** — DeepSeekClient must NEVER be called from main thread. Always wrap in `BukkitScheduler.runTaskAsynchronously`.
2. **PDC is runtime source of truth** — YAML is backup/persistence. Read from PDC first.
3. **NPC hostility controlled by disposition** — Hostile mobs (Zombie, Skeleton) are passive unless `disposition=agro`. Enforced via `EntityTargetEvent` cancel.
4. **NPC color coding** — Important NPCs = `§6§l` gold bold, Side NPCs = `§f§l` white bold.
5. **Scoreboard auto-refresh** — Tick loop refreshes all player scoreboards every interval (HP/max HP sync). Listener catches damage/regen/level change for instant refresh.
6. **Max health via Attribute API** — `player.getAttribute(Attribute.MAX_HEALTH).getValue()` — respects `/attribute` command.
7. **Party progress = consensus** — `partyCurrentNode` uses most-advanced node across all players. Volatile for thread safety.
8. **API key NOT in git** — `config.yml` template has placeholder. Runtime config at `server/plugins/DndPlugin/config.yml`.

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
| `data/players/<uuid>.yml` | Player character sheets (runtime, generated) | Stats, HP, inventory, story progress |
| `data/rules/srd/5esrd.yaml` | D&D 5e SRD (1.4MB, compiled from dnd-5e-srd) | Full rules reference |
| `config.yml` | Plugin config | DeepSeek API key/model, tick interval, world size |

## SRD Rules (SrdRulesStore)

- Loaded from `data/rules/srd/5esrd.yaml` (1.4MB, 9 top-level sections)
- `search("keyword")` → filtered rule text (max 3500 chars, cached)
- `searchMultiple("stealth", "grapple")` → combined results
- Used by NarratorService and NpcDialogueService for AI context
- Index built at startup (async, non-blocking)
