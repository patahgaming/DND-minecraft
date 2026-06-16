---
name: dnd-ai-system
description: >
  Sistem game Dungeons & Dragons berbasis AI agent, full Java, native di dalam
  Minecraft (Paper plugin) sebagai satu-satunya interface — server-side only,
  kompatibel Java & Bedrock via Geyser/Floodgate. Gunakan skill ini setiap kali
  bekerja pada komponen apapun: Paper plugin listener/command, HTTP client ke
  DeepSeek API, story tree YAML branching, POI bbox tick checker, NPC penting/side
  (auto-naming, freeze-on-interact, history per-NPC shared party), character sheet
  via scoreboard sidebar + PDC, combat turn-based custom (initiative d20+DEX),
  SRD rules YAML, atau sleight-of-hand mechanic. Aktifkan saat user menyebut
  Paper plugin, NPC, POI, story node, scoreboard, initiative, /atk /roll /talk,
  PDC, YAML rules, atau komponen DnD-in-Minecraft apapun.
---

# DnD AI System (Minecraft-Native, v4)

Game DnD dengan Minecraft sebagai satu-satunya interface. AI (DeepSeek) berperan
ganda: **narator** (mengisi cerita di sepanjang story tree atau membuat cabang
baru saat keluar jalur) dan **NPC penting** (dialog interaktif, bisa agro/teman/damai/
sleight-of-hand). Pure Java, pure server-side, tanpa Qdrant/Node/NestJS/fog-of-war/CI-CD.

## Stack

| Layer | Teknologi | Keputusan |
|-------|-----------|-----------|
| Interface | Minecraft (Paper server) | Single world, 5000x5000 block, dibuat manual |
| Cross-play | Geyser + Floodgate | Sudah pernah setup — Java & Bedrock bisa join |
| Plugin | Java (Paper API) | Satu-satunya codebase, tidak ada layer terpisah |
| AI | HTTP langsung ke DeepSeek API | Tanpa LangChain kecuali dibutuhkan tooling kompleks |
| Rules | YAML (format dnd-5e-srd) | Filter section by keyword sebelum di-stuff ke prompt |
| Story | YAML branching tree | On-rail (ikuti node) vs off-rail (AI generate baru) |
| Data | YAML (PDC sebagai source of truth runtime) | Tidak ada DB eksternal |
| Hosting | Localhost, on-demand | Hanya jalan saat sesi main |

**Eksplisit di-drop:** Qdrant, Three.js/web frontend, NestJS/Node, fog of war, CI/CD,
automasi POI export (manual untuk sekarang — nanti bisa export dari folder world).

---

## Engine Tick Loop

Tiap **2 detik**, plugin scan semua POI bbox terdaftar di story tree:

- **`ALL_PLAYERS_IN_BBOX`** — trigger hanya jika SEMUA player aktif berada di dalam bbox POI
- **`SINGLE_PLAYER_EVENT`** — trigger jika 1 player memenuhi kondisi event spesifik (tergantung narasi/story node)

Hanya jika trigger terpenuhi, plugin assemble context dan panggil DeepSeek.
Tidak ada panggilan AI di luar trigger ini, command, atau hasil battle.

### Trigger AI dipanggil saat:
1. POI bbox trigger terpenuhi (tick check di atas)
2. Player menjalankan command (`/atk`, `/roll`, `/talk`, dll)
3. Battle selesai (win/lose)
4. Player interact dengan NPC penting (dialog)

---

## Character Sheet — Sync Minecraft ↔ Sheet

Source of truth: **PersistentDataContainer (PDC)** pada player + file YAML per player
sebagai backup/persistence. Display layer: **scoreboard sidebar private** per player
(bukan item GUI/chest — hindari pemain harus pindah-pindah tampilan).

| Aspek | Representasi Minecraft |
|-------|------------------------|
| HP | HP Minecraft = HP karakter (1:1) |
| Level | Custom counter di PDC, mirror ke XP bar/level Minecraft |
| STR/DEX/INT/WIS/CHA/CON | PDC tags pada player (bukan item kertas — final decision) |
| Equipment/Spell | Hotbar slot, item NBT-tagged menentukan efek |
| Stat display | Scoreboard sidebar, private per player, real-time update |

```yaml
# data/players/<uuid>.yml
player_id: <uuid>
name: "Aric"
class: Fighter
level: 3
stats:
  hp: { current: 28, max: 35 }
  str: 16
  dex: 12
  con: 14
  int: 10
  wis: 11
  cha: 8
inventory_hotbar:
  - slot: 0
    item_id: longsword_plus1
    effect: "melee +1"
story_progress:
  current_node: forest_entrance
```

---

## Story Tree (YAML Branching)

```yaml
# data/story/forest_entrance.yml
node_id: forest_entrance
trigger: ALL_PLAYERS_IN_BBOX
bbox: { x1: 100, z1: 200, x2: 150, z2: 250 }
narrative: >
  Deskripsi dasar situasi — AI mengisi detail kontekstual (nama player,
  posisi, waktu) di atas template ini.
choices:
  - id: enter_cave
    next_node: cave_dialogue
  - id: avoid_cave
    next_node: forest_path_alt
```

### Mode AI Narator
- **On-rail**: player masih mengikuti story tree → AI render `narrative` + isi
  detail kontekstual, tawarkan `choices`
- **Off-rail**: player sudah keluar jauh dari tree (tidak ada node yang match)
  → AI generate cerita baru, tetap constrained oleh world POI/lore yang ada di YAML
  (tanpa Qdrant — context langsung dari file POI + SRD relevan)

Plugin men-track `current_node` per player di file YAML player (treat semua player
sebagai **1 party** — story progress shared, bukan per-individu).

---

## NPC System

### Spawn & Tipe
- NPC = Villager (peaceful) atau hostile mob vanilla, spawn normal, AI pathfinding
  vanilla aktif sampai diinteraksi
- Auto-generate nama unik saat spawn → simpan di PDC: `npc_id`, `npc_name`

**Dua tipe:**

1. **NPC Penting (story-critical)**
   - Terdaftar di YAML dengan `npc_id` fixed, terkait story node tertentu
   - Player interact → NPC **freeze** (disable pathfinding) → masuk dialog
   - Tiap dialog → panggil DeepSeek dengan context: disposition, history, story node
   - AI decide real-time: agro / jadi teman / damai / sleight-of-hand
   - History **per-NPC, shared untuk seluruh party** (bukan per-player)

   ```yaml
   # data/npcs/elder_marek.yml
   npc_id: elder_marek
   display_name: "Elder Marek"
   disposition: neutral
   story_node: forest_entrance
   history:
     - timestamp: ...
       summary: "Party meminta info soal hutan, Marek curiga tapi memberi petunjuk"
   ```

2. **NPC Side (flavor only)**
   - Spawn random, dapat nama unik, dialog 1 bubble (di-generate sekali saat spawn,
     di-cache) — tidak ada panggilan AI berulang
   - Tidak ada gameplay impact, hanya keramaian dunia

### Sleight of Hand (bonus event)
- Hanya berlaku ke player (item dicuri dari inventory player oleh AI-NPC)
- Item random dari hotbar/inventory yang "stealable" (bukan equipped/quest item)
- Trigger: DEX (Sleight of Hand) check vs NPC Perception saat dialog

---

## Combat — Custom Turn-Based

**Inisiatif:** semua participant (player + NPC/mob) roll d20 + DEX modifier saat
battle mulai → urutkan descending → jadi turn order.

**State machine:**
```
WAITING_TURN → ACTION_SELECTED → RESOLVING → NEXT_TURN
```

- `/atk <mob_id>` dan `/roll <jenis>` hanya valid saat giliran player aktif
- Hasil tiap aksi → tampil via `/title` (bukan chat biasa, untuk custom display
  seperti hasil roll)
- HP update → scoreboard sidebar

### Conditions (default dari SRD, bisa di-override YAML)
| Kondisi | Efek |
|---------|------|
| `poisoned` | Disadvantage attack + ability check |
| `stunned` | Tidak bisa action, auto-fail STR/DEX save |
| `unconscious` | Prone, attack dari adjacent = critical |
| `blinded` | Disadvantage attack, enemy advantage |

### HP = 0
- **Player**: `unconscious`, Death Saving Throws (roll d20/turn, 10+ = success,
  3 success = stabil HP=1, 3 fail = mati)
- **NPC/mob**: mati langsung, trigger loot + XP

---

## Komunikasi In-Game

- **Whisper** (`/msg`) — narasi private per player
- **Public chat** — narasi grup / story-wide events
- **`/title`** — hasil roll, hasil combat, custom display events

---

## Command System

| Command | Fungsi |
|---------|--------|
| `/atk <mob_id>` | Serangan saat giliran combat aktif |
| `/roll <jenis_tindakan>` | Roll dice untuk tindakan yang butuh check — dengan suggestion (Brigadier) |
| `/talk <npc_id>` | Mulai dialog dengan NPC (interact juga bisa trigger ini) |

Semua command didaftarkan via **Brigadier API** (Paper) dengan suggestion provider
untuk auto-complete.

---

## Rules (SRD YAML)

Format mengikuti [dnd-5e-srd](https://github.com/BTMorton/dnd-5e-srd) (YAML per kategori,
contoh: `02 classes.yaml`). Saat AI butuh rules lookup (misal skill check sleight of
hand), plugin **filter section relevan by keyword** dari file SRD sebelum di-embed ke
prompt — jangan kirim seluruh SRD sekaligus.

---

## POI (Point of Interest)

```yaml
# data/poi/forest_cave.yml
poi_id: forest_cave
name: "Goa Hutan Gelap"
biome: forest
bbox: { x1: 100, z1: 200, x2: 150, z2: 250 }
```

Untuk sekarang: dibuat **manual**. Nanti: export otomatis dari folder world
(format/cara export belum ditentukan — structure block / armor stand tag / sign,
masih open).

AI narator hanya tahu: list POI (dengan bbox + biome), character party, dan
posisi exact (x,y,z) tiap player. Dari ini AI menyimpulkan apa yang ada di
hadapan player.

---

## Struktur Project (Paper Plugin)

```
/dnd-plugin
├── pom.xml
├── src/main/java/.../dndplugin/
│   ├── DndPlugin.java                 # entry point
│   ├── listener/
│   │   ├── NpcSpawnListener.java      # auto-naming saat villager/hostile spawn
│   │   ├── NpcInteractListener.java   # freeze + trigger dialog
│   │   └── PlayerJoinListener.java    # load/create char sheet
│   ├── command/
│   │   ├── AtkCommand.java            # Brigadier
│   │   ├── RollCommand.java           # Brigadier + suggestion
│   │   └── TalkCommand.java
│   ├── engine/
│   │   ├── PoiTickTask.java           # tick loop 2 detik, cek bbox trigger
│   │   ├── combat/
│   │   │   ├── InitiativeRoller.java
│   │   │   ├── TurnManager.java       # state machine
│   │   │   └── Conditions.java
│   │   └── dice/DiceRoller.java
│   ├── ai/
│   │   ├── DeepSeekClient.java        # HTTP client ke api.deepseek.com
│   │   ├── NarratorService.java       # on-rail/off-rail narrator
│   │   └── NpcDialogueService.java    # NPC penting dialog + sleight of hand
│   ├── data/
│   │   ├── PlayerDataStore.java       # PDC + YAML sync
│   │   ├── NpcDataStore.java          # history per-NPC
│   │   ├── StoryTreeStore.java
│   │   ├── PoiStore.java
│   │   └── SrdRulesStore.java         # filter by keyword
│   └── ui/
│       ├── ScoreboardManager.java     # sidebar char sheet
│       └── TitleDisplay.java          # /title untuk roll & combat
└── src/main/resources/
    ├── plugin.yml
    ├── data/story/*.yml
    ├── data/npcs/*.yml
    ├── data/poi/*.yml
    ├── data/players/*.yml             # runtime, generated
    └── data/rules/srd/*.yml           # SRD format dnd-5e-srd
```

---

## Belum Diputuskan (Open Decisions)

1. **POI export otomatis** — format marking di world (structure block / armor stand
   tag / sign / region scan) belum ditentukan, manual dulu
2. **Sleight of hand item filter** — kriteria persis "stealable" (selain bukan
   equipped/quest item) belum detail

---

## Urutan Development

```
Phase 1 — Foundation                                    [██████████] 4/4 DONE
  [x] pom.xml + plugin.yml + DndPlugin.java skeleton
  [x] PlayerDataStore (PDC + YAML sync) — char sheet baca/tulis
  [x] ScoreboardManager — sidebar display dasar + 6 ability scores + auto-refresh HP/level
  [x] DiceRoller (d20+modifier, unit-testable)

Phase 2 — World & Story                                [██████████] 4/4 DONE
  [x] PoiStore — load YAML POI manual
  [x] PoiTickTask — configurable interval, ALL_PLAYERS_IN_BBOX & SINGLE_PLAYER_EVENT, dedup trigger
  [x] StoryTreeStore — load/parse story node YAML (trigger type, bbox, narrative, choices)
  [x] NarratorService — on-rail render + off-rail generate (DeepSeek HTTP async)

Phase 3 — NPC                                          [██████████] 5/5 DONE
  [x] NpcSpawnListener — auto-naming villager/hostile + PDC tagging + color coding
  [x] NpcInteractListener — freeze on interact + auto-unfreeze + EquipmentSlot guard
  [x] NpcDataStore — history per-NPC shared party + thread-safe + auto-save YAML
  [x] NpcDialogueService — disposition decision via DeepSeek async + backstory context
  [x] Side NPC — 1-bubble cached dialog (pool statis 10 greetings)
  [+] /newnpc + /spawnnpc — create + spawn NPC dari semua mob type + backstory field

Phase 4 — Combat                                       [██████████] 7/7 DONE
  [x] InitiativeRoller (d20+DEX, sort descending + tiebreaker)
  [x] TurnManager — state machine + auto-mob AI + mob attack resolution
  [x] RollCommand (Brigadier + suggestion) — 14 roll types + ability mapping
  [x] AtkCommand — melee/ranged, d20+mod+prof vs AC, hit/miss, crit, damage types
  [x] CombatEngine — AC calculation, resistance/vulnerability, proficiency bonus
  [x] Conditions.java (poisoned/stunned/unconscious/blinded via PotionEffects)
  [x] Death saving throws (d20, nat1=2fail, nat20=1HP, 3success=stabil, 3fail=mati)
  [x] TitleDisplay untuk hasil roll/combat
  [+] /battle start|flee|status — full battle initiation + flee mechanic

Phase 5 — Rules & Polish                              [████████░░] 2/4
  [x] SrdRulesStore — load 1.4MB 5esrd.yaml + keyword index + search API
  [x] TalkCommand — full dialog flow + player message + tab-complete NPC search
  [ ] Whisper/public chat routing untuk narasi
  [ ] Testing end-to-end (localhost, on-demand)

Phase 6 — Infrastructure (beyond original plan)        [██████████] DONE
  [+] /dnd reload — hot swap config + YAML tanpa restart server
  [+] /dnd save|version|stop|start — admin controls
  [+] EntityTargetEvent guard — hostile NPCs passive unless disposition=agro
  [+] Scoreboard auto-refresh via tick loop (HP/max HP sync)
  [+] Attribute API max_health — respect /attribute command
  [+] Versioning rule: MAJOR.MINOR.PATCH.BUILD (1.0.0.20)
  [+] Git repo + .gitignore + README.md + CLAUDE.md
```

---

## File Referensi

Baca saat relevan — jangan baca semua sekaligus:

| File | Baca saat |
|------|-----------|
| `data/story/*.yml` | Kerja di story tree / narrator |
| `data/npcs/*.yml` | Kerja di NPC dialog / history |
| `data/poi/*.yml` | Kerja di tick checker / world context |
| `data/players/*.yml` | Kerja di char sheet sync |
| `data/rules/srd/*.yml` | Rules lookup / skill check |
| `ai/DeepSeekClient.java` | Setup HTTP call ke DeepSeek |
