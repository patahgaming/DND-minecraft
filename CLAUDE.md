# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Dungeons & Dragons game embedded in Minecraft as a single Paper server plugin. Minecraft is the sole interface — no web frontend, no separate services. AI (DeepSeek) acts as narrator and drives NPC dialogue. Cross-play via Geyser/Floodgate (Java + Bedrock).

## Reference: SKILL.md

[SKILL.md](SKILL.md) is the primary design document. Read it before any significant work — it defines every subsystem, the data model, and all architectural decisions. This CLAUDE.md summarizes the essentials; SKILL.md is the source of truth.

## Tech Stack

- **Language:** Java (Paper API — Minecraft server plugin)
- **Build:** Maven (`pom.xml`)
- **AI:** HTTP calls to DeepSeek API (no LangChain unless complex tooling needed)
- **Data:** YAML files + PDC (PersistentDataContainer) as runtime source of truth
- **Cross-play:** Geyser + Floodgate
- **Hosting:** Localhost, on-demand only

## Architecture

### Core Loop

Every **2 seconds**, the plugin ticks a POI (Point of Interest) bbox checker. If trigger conditions are met (all players in bbox, or single-player event), it assembles context and calls DeepSeek. AI is **never** called outside: tick triggers, player commands, combat results, or NPC interactions.

### Key Subsystems

| Subsystem | Responsibility |
|-----------|---------------|
| **Character Sheet** | PDC tags on player + YAML backup. HP = Minecraft HP (1:1). Stats (STR/DEX/etc.) in PDC. Displayed via private per-player scoreboard sidebar. |
| **Story Tree** | YAML branching narrative. On-rail (follow nodes) vs off-rail (AI generates new branches constrained by world POI/lore). Shared party progress, tracked per-player in YAML. |
| **NPC System** | Two types: **Important** (story-critical, freeze on interact, AI-driven dialogue with history) and **Side** (flavor only, one cached dialog bubble). Auto-named on spawn. |
| **Combat** | Custom turn-based: d20 + DEX for initiative, state machine (WAITING_TURN → ACTION_SELECTED → RESOLVING → NEXT_TURN). Results via `/title`. HP=0 triggers death saves for players, instant death for mobs. |
| **AI Services** | `DeepSeekClient` (HTTP), `NarratorService` (story rendering), `NpcDialogueService` (NPC disposition decisions, sleight-of-hand). |
| **SRD Rules** | YAML format from dnd-5e-srd. Filtered by keyword before embedding in AI prompts — never send full SRD. |

### Data Flow

```
World events (tick/command/interact) → Trigger check → Context assembly
  → DeepSeek API (with filtered SRD + story node + NPC history)
  → Response parsed → Game action (narrative, combat, NPC disposition)
  → State written to PDC + YAML → Scoreboard updated
```

### Planned Package Structure

```
src/main/java/.../dndplugin/
├── DndPlugin.java              # entry point
├── listener/                   # Minecraft event listeners
├── command/                    # Brigadier commands (/atk, /roll, /talk)
├── engine/                     # Tick loop, combat state machine, dice
├── ai/                         # DeepSeek HTTP client, narrator, NPC dialogue
├── data/                       # PDC+YAML stores (player, NPC, story, POI, SRD)
└── ui/                         # Scoreboard sidebar, /title display
```

## Development Phases

Per SKILL.md, development follows 5 phases:

1. **Foundation** — pom.xml, plugin skeleton, PlayerDataStore, ScoreboardManager, DiceRoller
2. **World & Story** — POI loading, tick loop, story tree, narrator AI
3. **NPC** — Spawn/interact listeners, NPC data store, dialogue AI, side NPCs
4. **Combat** — Initiative, turn manager, commands, conditions, death saves
5. **Rules & Polish** — SRD filtering, full dialog flow, chat routing, testing

The project is currently pre-Phase 1 (design only, no source code).

## Open Decisions

Two design questions remain unresolved (documented in SKILL.md):
1. **POI auto-export** — how to mark POI regions in-world (structure blocks, armor stand tags, signs, or region scan) is undecided; manual for now
2. **Sleight-of-hand item filter** — exact criteria for "stealable" items beyond "not equipped/quest item" needs clarification
