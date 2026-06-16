# 🐉 DnD Minecraft Plugin — Rulebook & Guidebook

**Version: v1.0.0.34** | Paper 1.21.11 | Minecraft-native D&D

---

## Daftar Isi

1. [Ikhtisar](#1-ikhtisar)
2. [Quick Start untuk Player Baru](#2-quick-start-untuk-player-baru)
3. [Membuat Karakter](#3-membuat-karakter)
4. [Command Reference Lengkap](#4-command-reference-lengkap)
5. [Sistem Combat (Serangan)](#5-sistem-combat-serangan)
6. [Sistem Spell (Sihir)](#6-sistem-spell-sihir)
7. [Sistem Roll & Skill Check](#7-sistem-roll--skill-check)
8. [Sistem Battle (Pertarungan Turn-Based)](#8-sistem-battle-pertarungan-turn-based)
9. [Sistem NPC & Dialog](#9-sistem-npc--dialog)
10. [Story & Dunia (POI / Story Tree)](#10-story--dunia-poi--story-tree)
11. [Scoreboard & Display](#11-scoreboard--display)
12. [Death & Death Saves](#12-death--death-saves)
13. [Untuk Developer / Story Creator](#13-untuk-developer--story-creator)
14. [Tabel Referensi](#14-tabel-referensi)

---

## 1. Ikhtisar

**DnD Minecraft Plugin** adalah plugin Paper server yang membawa Dungeons & Dragons ke dalam Minecraft. Minecraft adalah satu-satunya interface — tidak ada web, tidak ada aplikasi terpisah. AI (DeepSeek) bertindak sebagai narator cerita dan menggerakkan dialog NPC.

### Konsep Inti

| Konsep | Penjelasan |
|--------|-----------|
| **Karakter D&D** | Setiap player punya character sheet: class, ability scores (STR/DEX/CON/INT/WIS/CHA), HP, level |
| **1 Party** | Semua player online adalah 1 party. Progress cerita di-share bersama (consensus node paling maju) |
| **Turn-Based Combat** | Combat pakai initiative (d20+DEX), urutan giliran, bebas pilih target |
| **AI Narator** | DeepSeek menarasikan cerita, NPC dialog, dan kejadian dunia |
| **D&D 5e Rules** | Aturan dari SRD 5e: AC, proficiency, resistance/vulnerability, death saves, spell slots |

### Tech Stack

- **Server**: Paper 26.1.2 (Minecraft 1.21.11)
- **Plugin**: Java 21+, Maven
- **AI**: DeepSeek API (`deepseek-v4-flash`)
- **Data**: PDC (runtime) + YAML (persistence)

---

## 2. Quick Start untuk Player Baru

### Pertama Kali Join Server

1. **Join server Minecraft** (Java atau Bedrock via Geyser)
2. **Buat karakter** — server akan otomatis prompt kamu:
   ```
   /character list              → Lihat template karakter siap pakai
   /character pick aric_fighter → Pilih template (rekomendasi pemula)
   /character create FIGHTER    → Atau bikin custom karakter
   ```
3. **Lihat scoreboard** di sidebar kanan — HP, ability scores, level, story node
4. **Jelajahi dunia!** Kalau masuk area story, narator akan otomatis bercerita

### Command Paling Penting

```
/character info     → Lihat character sheet kamu
/roll stealth       → Cek skill (stealth/perception/dll)
/battle start       → Mulai combat dengan monster terdekat
/atk melee          → Serang musuh saat giliran kamu
/talk <npc> halo    → Bicara dengan NPC
```

---

## 3. Membuat Karakter

### 3.1 Pakai Template (Rekomendasi Pemula)

Ada **4 template starter** siap pakai — tinggal pick:

| Template | Class | Style | Backstory |
|----------|-------|-------|-----------|
| `aric_fighter` | Fighter | Melee tank, STR-based | Mantan kapten milisi Thornhollow |
| `lyra_rogue` | Rogue | Stealth, DEX-based | — |
| `merlin_wizard` | Wizard | Spellcaster, INT-based | — |
| `mira_cleric` | Cleric | Healer/support, WIS-based | — |

```
/character list                → Lihat semua template
/character pick aric_fighter   → Pilih Aric the Shieldbearer
```

### 3.2 Bikin Karakter Custom

```
/character create <CLASS> [name] [race]
```

**10 Class tersedia**: `FIGHTER`, `ROGUE`, `WIZARD`, `CLERIC`, `BARBARIAN`, `RANGER`, `PALADIN`, `WARLOCK`, `MONK`, `DRUID`

Contoh:
```
/character create BARBARIAN Grok Half-Orc
/character create PALADIN
```

### 3.3 Standard Array (Auto-Distribusi)

Saat bikin karakter, ability scores auto-didistribusi:

| Ability | Nilai | Keterangan |
|---------|-------|------------|
| Primary (sesuai class) | **15** | STR untuk Fighter, INT untuk Wizard, dll |
| Constitution | **14** | Semua class (HP penting!) |
| Tertiary 1 | 13 | Distributed |
| Tertiary 2 | 12 | Distributed |
| Tertiary 3 | 10 | Distributed |
| Dump stat | 8 | Kemampuan terlemah |

**HP awal** = `classBaseHP + CON modifier`

| Class | Base HP | Hit Dice |
|-------|---------|----------|
| Barbarian | 12 | d12 |
| Fighter, Ranger, Paladin | 10 | d10 |
| Rogue, Cleric, Warlock, Monk, Druid | 8 | d8 |
| Wizard | 6 | d6 |

### 3.4 Reset Karakter

```
/character reset   → Hapus karakter, mulai dari awal
```

---

## 4. Command Reference Lengkap

### `/character` — Character Management

```
/character list                      → Lihat template tersedia
/character pick <template_id>        → Pilih template karakter
/character create <class> [name] [race] → Bikin karakter custom
/character info                      → Lihat character sheet kamu
/character reset                     → Hapus karakter & mulai ulang
```

### `/atk` — Serangan (Combat)

```
/atk melee [target]    → Serangan jarak dekat (STR + prof vs AC)
/atk ranged [target]   → Serangan jarak jauh (DEX + prof vs AC)
```

- **Hanya bisa saat giliran kamu di battle**
- Target: nama mob atau akan auto-target kalau dikosongin
- TAB completion untuk daftar target

### `/roll` — Dice Roll & Skill Check

```
/roll                   → Auto-detect PendingRoll (saat combat)
/roll <type>            → Free skill check (di luar combat)
```

**14 Roll Types**: `athletics`, `acrobatics`, `stealth`, `perception`, `investigation`, `insight`, `persuasion`, `deception`, `intimidation`, `sleight_of_hand`, `survival`, `attack`, `initiative`, `death_save`

### `/cast` — Cast Spell

```
/cast <spell> [target]  → Cast spell ke target
```

**12 Spells**: `fire_bolt`, `magic_missile`, `burning_hands`, `chill_touch`, `shocking_grasp`, `ray_of_frost`, `sacred_flame`, `poison_spray`, `acid_splash`, `frostbite`, `eldritch_blast`, `word_of_radiance`

### `/battle` — Battle Management

```
/battle start    → Scan monster di radius 15 block → mulai battle
/battle status   → Lihat turn order, HP, giliran sekarang
/battle flee     → Kabur dari battle (DEX check DC 12)
```

### `/talk` — Dialog NPC

```
/talk <npc_id> [pesan]   → Bicara dengan NPC
```

- TAB completion untuk daftar NPC terdekat
- NPC penting: dialog AI (DeepSeek), ada history
- NPC side: 1 bubble greeting saja
- Click kanan NPC juga trigger dialog

### `/spawnnpc` & `/newnpc` — NPC Management (Admin/Dev)

```
/spawnnpc <template_id>                         → Spawn NPC dari template YAML
/newnpc <id> <display_name> [type] [spawn]      → Buat NPC baru + template YAML
```

### `/dnd` — Admin Controls

```
/dnd reload     → Hot swap: reload config + semua YAML + SRD tanpa restart
/dnd save       → Force save semua player/NPC data ke disk
/dnd version    → Tampilkan versi plugin, API info
/dnd stop       → Pause story tick task
/dnd start      → Resume story tick task
```

---

## 5. Sistem Combat (Serangan)

### 5.1 Cara Menyerang

Saat battle aktif dan giliran kamu:
```
/atk melee goblin        → Serang goblin dengan pedang (STR-based)
/atk ranged skeleton     → Panah ke skeleton (DEX-based)
```

### 5.2 Attack Resolution (D&D 5e)

```
Attack Roll = d20 + Ability Modifier + Proficiency Bonus

Melee:  d20 + STR mod + prof → vs target AC
Ranged: d20 + DEX mod + prof → vs target AC
```

- **Hit**: attack roll ≥ target AC
- **Miss**: attack roll < target AC (tidak kena)
- **Nat 20**: Auto-hit + **CRITICAL** (damage dice digandakan)
- **Nat 1**: Auto-miss

### 5.3 Armor Class (AC)

| Target | AC |
|--------|-----|
| Player | **10 + DEX modifier** |
| Zombie | 8 |
| Skeleton | 13 |
| Spider | 14 |
| Creeper | 12 |
| Enderman | 15 |
| Pillager / Vindicator | 15 |
| Witch / Evoker | 12 |
| **Iron Golem** | **20** |
| Villager | 10 |

### 5.4 Damage Types & Resistance/Vulnerability

**12 Damage Types**: `SLASHING`, `PIERCING`, `BLUDGEONING`, `FIRE`, `COLD`, `LIGHTNING`, `ACID`, `POISON`, `NECROTIC`, `RADIANT`, `FORCE`, `PSYCHIC`

| Mob | Resistance (½ damage) | Vulnerability (2× damage) |
|-----|----------------------|---------------------------|
| **Skeleton** | Piercing | **Bludgeoning** |
| **Zombie** | — | **Radiant** |
| **Enderman** | Fire, Lightning | — |
| **Spider** | — | **Fire** |
| **Iron Golem** | Poison, Psychic (immune) | — |

### 5.5 Proficiency Bonus

| Level | Bonus |
|-------|-------|
| 1–4 | +2 |
| 5–8 | +3 |
| 9–12 | +4 |
| 13–16 | +5 |
| 17+ | +6 |

---

## 6. Sistem Spell (Sihir)

### 6.1 Spell List

| Spell ID | Nama | Damage | Tipe | Class |
|----------|------|--------|------|-------|
| `fire_bolt` | Fire Bolt | 1d10 | Attack Roll, Fire | Wizard, Warlock |
| `magic_missile` | Magic Missile | 3d4+3 | **Auto-hit**, Force | Wizard, Warlock |
| `burning_hands` | Burning Hands | 3d6 | DEX save, Fire (half on save) | Wizard, Warlock |
| `chill_touch` | Chill Touch | 1d8 | Attack Roll, Necrotic | Wizard, Warlock |
| `shocking_grasp` | Shocking Grasp | 1d8 | Attack Roll, Lightning | Wizard, Warlock |
| `ray_of_frost` | Ray of Frost | 1d8 | Attack Roll, Cold | Wizard, Warlock |
| `sacred_flame` | Sacred Flame | 1d8 | DEX save, Radiant (save negates) | Cleric, Paladin, Druid |
| `poison_spray` | Poison Spray | 1d12 | CON save, Poison (save negates) | Semua caster |
| `acid_splash` | Acid Splash | 1d6 | DEX save, Acid (save negates) | Wizard, Warlock |
| `frostbite` | Frostbite | 1d6 | CON save, Cold (save negates) | Semua caster |
| `eldritch_blast` | Eldritch Blast | 1d10 | Attack Roll, Force | **Warlock only** |
| `word_of_radiance` | Word of Radiance | 1d6 | CON save, Radiant (save negates) | **Cleric only** |

### 6.2 Spell Mechanics

**Attack Roll Spells** (fire_bolt, chill_touch, dll):
```
d20 + spellcasting_modifier + proficiency vs target AC
```

**Saving Throw Spells** (burning_hands, sacred_flame, dll):
```
Target rolls d20 + save_modifier vs DC (8 + spell_mod + prof)
```

- **Half on save**: target kena setengah damage kalau berhasil save (e.g. burning_hands)
- **Save negates**: target kena 0 damage kalau berhasil save (e.g. sacred_flame)

**Magic Missile**: Auto-hit — skip attack roll, langsung damage.

### 6.3 Spellcasting Ability (per Class)

| Class | Spellcasting Ability |
|-------|---------------------|
| Wizard | INT |
| Cleric, Druid | WIS |
| Warlock | CHA |
| Paladin | WIS |

### 6.4 Spell Slots

- Setiap caster punya **2 spell slots**
- Tiap cast mengurangi 1 slot
- Slot pulih saat **istirahat** (fitur future)
- Slot disimpan di PDC, survive server restart

```
/cast fire_bolt zombie    → Cast Fire Bolt ke zombie (pakai 1 slot)
Mana: 1/2 spell slots left
```

---

## 7. Sistem Roll & Skill Check

### 7.1 Free Skill Check (di luar combat)

```
/roll stealth        → d20 + DEX mod (+ prof kalau proficient)
/roll perception     → d20 + WIS mod (+ prof)
/roll athletics      → d20 + STR mod (+ prof)
/roll persuasion     → d20 + CHA mod (+ prof)
```

### 7.2 Ability → Skill Mapping

| Ability | Skills |
|---------|--------|
| **STR** | athletics |
| **DEX** | acrobatics, stealth, sleight_of_hand, initiative |
| **CON** | (no skills, tapi penting buat HP & concentration) |
| **INT** | investigation |
| **WIS** | perception, survival, insight |
| **CHA** | persuasion, deception, intimidation |

### 7.3 Proficiency Auto-Detect

RollCommand otomatis nambah proficiency bonus **kalau skill termasuk 2 skill pertama class kamu**:
- Fighter: athletics, perception
- Rogue: stealth, acrobatics
- Wizard: investigation, insight
- Cleric: insight, persuasion
- dll (lihat tabel class di §14)

### 7.4 Roll Animation

Setiap `/roll` menampilkan animasi:
1. **800ms obfuscated text** (`§k████`) dengan 4 suara note block (ascending pitch)
2. **Final sound**: Bell (nat20), Bass (nat1), Pling (lainnya)
3. **Color-coded result**:
   - 🔴 Nat 1 = Red
   - 🟠 2–7 = Orange
   - 🟡 8–17 = Yellow
   - 🔵 18–19 = Blue
   - 🟢 Nat 20 = Green

### 7.5 PendingRoll (Anti-Cheat)

Saat combat, `/roll` dice diceExpr dari engine — **player tidak bisa cheat**:
```
/roll attack    → Engine menentukan diceExpr = "d20+5"
                 → Player tidak bisa /roll d20+99 (diceExpr dari engine)
```

---

## 8. Sistem Battle (Pertarungan Turn-Based)

### 8.1 Memulai Battle

```
/battle start
```

Plugin akan:
1. Scan **semua monster** dalam radius 15 block
2. Kumpulin semua player online sebagai party
3. Roll **initiative** (d20 + DEX mod) untuk setiap participant
4. **Freeze** semua mob AI — mob hanya bertindak saat giliran
5. Urutkan turn order dari initiative tertinggi → terendah
6. Mulai Round 1

### 8.2 Turn Order & State Machine

```
WAITING_TURN → ACTION_SELECTED → RESOLVING → NEXT_TURN
```

**Saat giliran player:**
- Pilih aksi: `/atk melee`, `/atk ranged`, `/cast <spell>`, atau `/battle flee`
- Setelah aksi di-resolve → auto-next turn

**Saat giliran mob (auto-AI):**
1. Unfreeze mob
2. Serang player terdekat
3. Re-freeze mob
4. Next turn (delay 1.5 detik)

### 8.3 Lihat Status Battle

```
/battle status
```

Output:
```
=== Battle Status ===
Round: 2 | Turn: Zombie [18]
Turn Order:
  1. Prabu (22) [HP:15] 
  2. Zombie (18) [HP:12] ◄ NOW
  3. Skeleton (15) [HP:8]
  4. Aric (12) [HP:10]
```

### 8.4 Kabur dari Battle

```
/battle flee
```

- **DEX check DC 12**
- Nat 20 = auto-success
- Gagal = giliran terbuang
- Berhasil = keluar dari battle, kalau semua player kabur → battle berakhir

### 8.5 Battle Berakhir

Battle berakhir kalau:
- Semua monster mati ✅ **Victory!**
- Semua player mati/unconscious ❌ **Defeat...**
- Semua player berhasil flee
- Tinggal 1 participant

Mob yang selamat di-**unfreeze** (AI normal kembali).

---

## 9. Sistem NPC & Dialog

### 9.1 Dua Tipe NPC

| Tipe | Ciri | Dialog |
|------|------|--------|
| **NPC Penting** (Important) | Nama **§6emas bold**, dari template YAML | AI penuh via DeepSeek, punya backstory & history |
| **NPC Side** | Nama **§fputih bold**, spawn random | 1 bubble greeting statis (cached) |

### 9.2 Interaksi dengan NPC

```
/talk elder_marek Apa yang terjadi di hutan ini?
```

Atau **klik kanan** NPC — otomatis:
1. NPC di-freeze (tidak bergerak)
2. Dialog dimulai
3. Setelah 5 detik idle → NPC unfreeze

### 9.3 Disposition NPC

NPC penting punya `disposition`:
- **`neutral`** — NPC biasa, tidak nyerang
- **`friendly`** — NPC ramah (future: bisa kasih quest/item)
- **`agro`** — NPC musuh, nyerang player

Hostile mob (Zombie, Skeleton) hanya nyerang kalau disposition = `agro`. Dicegah via `EntityTargetEvent`.

### 9.4 Color Coding

- **NPC Penting**: `§6§l` (gold bold) — contoh: `§6§lElder Marek`
- **NPC Side**: `§f§l` (white bold) — contoh: `§f§lBob the Villager`
- Semua NPC punya `npc_id` dan `npc_name` di PDC

---

## 10. Story & Dunia (POI / Story Tree)

### 10.1 Cara Kerja Story

Plugin punya **tick loop** yang tiap interval (configurable) mengecek:

1. **`ALL_PLAYERS_IN_BBOX`** — trigger kalau **semua player** berada dalam bounding box suatu story node
2. **`SINGLE_PLAYER_EVENT`** — trigger kalau 1 player memicu event spesifik

Kalau trigger terpenuhi:
- Narator (DeepSeek) akan merender narasi + pilihan
- Player bisa memilih antara `choices` untuk maju ke node berikutnya

### 10.2 Story Node YAML (Contoh)

```yaml
node_id: forest_entrance
trigger: ALL_PLAYERS_IN_BBOX
bbox:
  x1: 100
  z1: 200
  x2: 150
  z2: 250
narrative: >
  Party tiba di gerbang hutan tua...
choices:
  - id: enter_cave
    next_node: cave_dialogue
  - id: avoid_cave
    next_node: forest_path_alt
```

### 10.3 Party Progress (Consensus)

- Progress cerita di-track sebagai **1 party**
- `partyCurrentNode` = node paling maju di antara semua player
- Kalau 1 player masuk node baru, party ikut maju

---

## 11. Scoreboard & Display

### 11.1 Scoreboard Sidebar

Setiap player punya **scoreboard private** di sidebar kanan, menampilkan:

```
  §6[Character Name]
  Lv1 Fighter
  §cHP: 15/15
  §7--- Abilities ---
  STR 15 (+2)
  DEX 12 (+1)
  CON 14 (+2)
  INT 10 (+0)
  WIS 11 (+0)
  CHA  8 (-1)
  §7--- Story ---
  forest_entrance
```

### 11.2 Auto-Refresh

Scoreboard di-refresh:
- **Setiap tick loop** (interval configurable)
- **Instant**: saat kena damage, regen HP, atau level up

### 11.3 Title Display

Hasil combat & roll muncul sebagai **title** (bukan chat):
- Animasi roll: obfuscated text + note block sounds
- Combat result: HIT/MISS/CRIT dengan damage

### 11.4 HP Bar di Nameplate

Semua entity (NPC + monster + player) punya **HP bar di bawah nama**:
```
§6§lElder Marek
§c❤ 20/20
```

HP bar di-refresh tiap kali entity kena damage atau regen.

---

## 12. Death & Death Saves

### 12.1 Player Mencapai HP 0

Saat player HP = 0:
- Player menjadi **unconscious** (Blindness + Slowness)
- Mulai **Death Saving Throws**

### 12.2 Death Saving Throw

Roll d20 (no modifier):

| Roll | Hasil |
|------|-------|
| **Nat 20** | Langsung bangun dengan 1 HP! |
| **Nat 1** | 2 failures sekaligus |
| **10+** | 1 success |
| **2–9** | 1 failure |

- **3 success** → Stabilized (HP = 1, bangun)
- **3 failure** → **DEAD** (mati)

### 12.3 Mob/NPC Mati

Mob/NPC yang HP-nya 0 **langsung mati** (tidak ada death saves).

---

## 13. Untuk Developer / Story Creator

Bagian ini untuk kamu yang ingin membuat konten: story node, NPC, POI, karakter template.

### 13.1 Struktur Folder Data

```
server/plugins/DndPlugin/
├── config.yml                    # API key + konfigurasi
└── data/
    ├── story/                    # Story tree nodes
    │   └── forest_entrance.yml
    ├── npcs/                     # NPC templates
    │   └── elder_marek.yml
    ├── poi/                      # Points of Interest
    │   └── forest_cave.yml
    ├── characters/               # Character templates (di JAR)
    │   ├── aric_fighter.yml
    │   ├── lyra_rogue.yml
    │   ├── merlin_wizard.yml
    │   └── mira_cleric.yml
    ├── players/                  # Player data (auto-generated)
    │   └── <uuid>.yml
    └── rules/
        └── srd/                  # SRD rules (5esrd.yaml)
```

### 13.2 Membuat Story Node

Buat file YAML baru di `data/story/`:

```yaml
# data/story/cave_dialogue.yml
node_id: cave_dialogue
trigger: ALL_PLAYERS_IN_BBOX
bbox:
  x1: 100
  z1: 200
  x2: 150
  z2: 250
narrative: >
  Party memasuki goa gelap. Udara lembab dan dingin menusuk tulang.
  Dari kejauhan terdengar suara tetesan air... dan sesuatu yang lain.
  Sebuah altar batu kuno berdiri di tengah ruangan, dikelilingi
  oleh tulisan yang berpendar redup.
choices:
  - id: touch_altar
    next_node: altar_event
  - id: explore_deeper
    next_node: deep_cave
  - id: leave_cave
    next_node: forest_path_alt
```

**Field penjelasan**:

| Field | Wajib | Keterangan |
|-------|-------|------------|
| `node_id` | ✅ | ID unik node (snake_case) |
| `trigger` | ✅ | `ALL_PLAYERS_IN_BBOX` atau `SINGLE_PLAYER_EVENT` |
| `bbox` | ✅* | Bounding box: `x1, z1, x2, z2` (untuk trigger bbox) |
| `narrative` | ✅ | Template narasi — AI akan isi detail kontekstual |
| `choices` | ✅ | Pilihan player (min 1), masing-masing punya `id` + `next_node` |

### 13.3 Membuat NPC Penting

Buat file di `data/npcs/`:

```yaml
# data/npcs/elder_marek.yml
npc_id: elder_marek
display_name: "Elder Marek"
disposition: neutral           # neutral / friendly / agro
story_node: forest_entrance
entity_type: VILLAGER          # Bisa mob type apa aja (ZOMBIE, SKELETON, WITCH, dll)
backstory: >
  Marek is the village elder of Thornhollow. He was once a ranger
  who patrolled these woods for 30 years before retiring. His knowledge
  of the forest paths is unmatched. He lost his left eye to a dire wolf
  and has been wary of adventurers ever since. He knows about the old
  cave entrance but refuses to go near it.
history: []                    # Auto-diisi plugin (riwayat dialog dengan party)
```

Kemudian spawn NPC:
```
/newnpc elder_marek "Elder Marek" VILLAGER spawn
```

Atau kalau template sudah ada:
```
/spawnnpc elder_marek
```

**Tips Backstory**:
- Tulis backstory yang kaya — AI pakai ini sebagai **system prompt** saat NPC dialog
- Sertakan: asal-usul, personality, rahasia, ketakutan, hubungan dengan dunia
- Semakin detail backstory, semakin natural dialog AI

### 13.4 Membuat POI (Point of Interest)

```yaml
# data/poi/forest_cave.yml
poi_id: forest_cave
name: "Goa Hutan Gelap"
biome: forest
bbox:
  x1: 100
  z1: 200
  x2: 150
  z2: 250
```

POI digunakan AI narator sebagai **world context** — AI tahu apa yang ada di sekitar player.

### 13.5 Membuat Karakter Template

```yaml
# data/characters/aric_fighter.yml
template_id: aric_fighter
display_name: "Aric the Shieldbearer"
class: FIGHTER
race: Human
backstory: >
  Aric was a captain in the Thornhollow militia before monsters overran
  his village. He carries his family's ancestral longsword and a shield
  bearing the faded emblem of his fallen order. Stoic and protective,
  he seeks to rebuild his honor and find the creatures that destroyed his home.
```

Template karakter harus di `src/main/resources/data/characters/` (dibuild ke JAR).

### 13.6 Hot Reload

Setelah edit file YAML:
```
/dnd reload
```

Ini akan reload **semua**: config, story tree, NPC, POI, karakter template, SRD rules. **Tanpa restart server.**

### 13.7 Admin Commands

| Command | Kegunaan |
|---------|----------|
| `/dnd reload` | Hot swap semua data tanpa restart |
| `/dnd save` | Force save semua player/NPC ke disk |
| `/dnd version` | Cek versi plugin + AI model + party node |
| `/dnd stop` | Pause story tick loop |
| `/dnd start` | Resume story tick loop |

---

## 14. Tabel Referensi

### 14.1 Semua Class D&D

| Class | Base HP | Primary Ability | Saving Throws | Skill Proficiencies (Top 2 auto-proficient) |
|-------|---------|-----------------|---------------|---------------------------------------------|
| **Barbarian** | 12 | STR | STR, CON | athletics, intimidation, survival, perception |
| **Fighter** | 10 | STR | STR, CON | **athletics**, **perception**, survival, intimidation, acrobatics |
| **Ranger** | 10 | DEX | STR, DEX | **stealth**, **survival**, perception, investigation, athletics |
| **Paladin** | 10 | STR | WIS, CHA | **athletics**, **intimidation**, persuasion, insight |
| **Rogue** | 8 | DEX | DEX, INT | **stealth**, **acrobatics**, sleight_of_hand, investigation, perception, deception |
| **Cleric** | 8 | WIS | WIS, CHA | **insight**, **persuasion**, perception, survival |
| **Warlock** | 8 | CHA | WIS, CHA | **deception**, **intimidation**, investigation, persuasion |
| **Monk** | 8 | DEX | STR, DEX | **acrobatics**, **athletics**, stealth, insight |
| **Druid** | 8 | WIS | INT, WIS | **survival**, **perception**, insight, investigation |
| **Wizard** | 6 | INT | INT, WIS | **investigation**, **insight**, perception |

### 14.2 Skill → Ability Map

| Skill | Ability |
|-------|---------|
| Athletics | STR |
| Acrobatics | DEX |
| Stealth | DEX |
| Sleight of Hand | DEX |
| Perception | WIS |
| Survival | WIS |
| Insight | WIS |
| Investigation | INT |
| Persuasion | CHA |
| Deception | CHA |
| Intimidation | CHA |

### 14.3 Mob AC Table

| Entity Type | AC |
|-------------|-----|
| **Iron Golem** | 20 |
| Enderman | 15 |
| Pillager | 15 |
| Vindicator | 15 |
| Spider | 14 |
| Skeleton | 13 |
| Creeper | 12 |
| Witch | 12 |
| Evoker | 12 |
| Villager | 10 |
| Wandering Trader | 10 |
| **Zombie** | 8 |
| Other | 10 |

### 14.4 Mob Attack Stats (AI)

| Mob | Attack Bonus | Damage Dice | Bonus |
|-----|-------------|-------------|-------|
| Iron Golem | +10 | d10 | +5 |
| Enderman | +7 | d8 | +2 |
| Evoker | +6 | spells | +2 |
| Skeleton | +4 | d6 | +2 |
| Spider | +5 | d6 | +2 |
| Pillager/Vindicator | +5 | d6 | +2 |
| Witch | +5 | spells | +2 |
| Zombie | +3 | d6 | +1 |
| Creeper | +3 | explode | +2 |

### 14.5 Spell Reference Card

| Spell | Atk/Save | Damage | Class | Special |
|-------|----------|--------|-------|---------|
| `fire_bolt` | Atk Roll | 1d10 fire | Arcane | — |
| `magic_missile` | **Auto-hit** | 3d4+3 force | Arcane | No roll needed |
| `burning_hands` | DEX save | 3d6 fire | Arcane | Half on save |
| `chill_touch` | Atk Roll | 1d8 necrotic | Arcane | — |
| `shocking_grasp` | Atk Roll | 1d8 lightning | Arcane | — |
| `ray_of_frost` | Atk Roll | 1d8 cold | Arcane | — |
| `sacred_flame` | DEX save | 1d8 radiant | Divine | Save negates |
| `poison_spray` | CON save | 1d12 poison | All | Save negates |
| `acid_splash` | DEX save | 1d6 acid | Arcane | Save negates |
| `frostbite` | CON save | 1d6 cold | All | Save negates |
| `eldritch_blast` | Atk Roll | 1d10 force | Warlock | Warlock only |
| `word_of_radiance` | CON save | 1d6 radiant | Cleric | Cleric only |

### 14.6 Conditions Reference

| Condition | Minecraft Effect | D&D Effect |
|-----------|-----------------|------------|
| **Poisoned** | Poison I + Slowness I | Disadvantage on attacks & ability checks |
| **Stunned** | Slowness V + Weakness II + Blindness I | Cannot act, auto-fail STR/DEX saves |
| **Unconscious** | Blindness I + Slowness VI | Prone, adjacent attacks = crit |
| **Blinded** | Blindness I | Disadvantage on attacks, enemy advantage |

---

## Appendix: Flow Combat Lengkap

```
1. /battle start
   ├── Scan monster di radius 15 block
   ├── Kumpulin semua player online
   ├── Roll initiative (d20 + DEX mod) untuk semua participant
   ├── Sort descending → turn order
   ├── Freeze semua mob AI
   └── Mulai Round 1, Turn 1

2. Giliran Player:
   ├── Pilih aksi: /atk melee|ranged /cast /battle flee
   ├── /roll → engine diceExpr dice → hit/miss vs AC
   ├── Hit → damage applied → cek kill
   └── Next turn (auto)

3. Giliran Mob (auto-AI):
   ├── Unfreeze mob
   ├── Cari player terdekat
   ├── Attack roll vs player AC
   ├── Hit → damage applied
   ├── Re-freeze mob
   └── Next turn (delay 1.5s)

4. Battle End:
   ├── Victory: semua monster mati → unfreeze survivors
   ├── Defeat: semua player mati
   └── Flee: player kabur (DEX DC12)
```

---

> **Dibuat dengan 🐉 untuk Minecraft D&D Plugin**
> Versi: v1.0.0.34 | Paper 1.21.11 | Java 21
