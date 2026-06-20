# 🐉 DnD Minecraft Plugin — Rulebook & Guidebook

**Version: v1.0.0.68** | Paper 1.21.11 | Minecraft-native D&D

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
10. [Sistem Dadu (Dice)](#10-sistem-dadu-dice)
11. [Sistem Item](#11-sistem-item)
12. [Story & Dunia (POI / Story Tree)](#12-story--dunia-poi--story-tree)
13. [AI Narator & Story Modes](#13-ai-narator--story-modes)
14. [Scoreboard & Display](#14-scoreboard--display)
15. [Death & Death Saves](#15-death--death-saves)
16. [Untuk Developer / Story Creator](#16-untuk-developer--story-creator)
17. [Tabel Referensi](#17-tabel-referensi)

---

## 1. Ikhtisar

**DnD Minecraft Plugin** adalah plugin Paper server yang membawa Dungeons & Dragons ke dalam Minecraft. Minecraft adalah satu-satunya interface — tidak ada web, tidak ada aplikasi terpisah. AI (DeepSeek) bertindak sebagai narator cerita dan menggerakkan dialog NPC.

### Konsep Inti

| Konsep | Penjelasan |
|--------|-----------|
| **Karakter D&D** | Setiap player punya character sheet: class, ability scores (STR/DEX/CON/INT/WIS/CHA), HP, level, race |
| **1 Party** | Semua player online adalah 1 party. Progress cerita di-share bersama (consensus node paling maju) |
| **Turn-Based Combat** | Combat pakai initiative (d20+DEX), urutan giliran, bebas pilih target |
| **AI Narator** | DeepSeek menarasikan cerita, NPC dialog, dan kejadian dunia |
| **D&D 5e Rules** | Aturan dari SRD 5e: AC, proficiency, resistance/vulnerability, death saves, spell slots |
| **Physical Dice** | Dadu item nyata di hotbar (D4–D20), bisa klik kanan untuk roll |
| **Item System** | Senjata, armor, potion, dan item spesial dengan PDC tagging & YAML template |
| **POI Hierarchy** | Point of Interest bertingkat: desa → rumah → ruangan, dengan 3D bounding box |
| **Bedrock Support** | Geyser + Floodgate — player Bedrock bisa join! |

### Tech Stack

- **Server**: Paper 26.1.2 (Minecraft 1.21.11)
- **Plugin**: Java 21+, Maven
- **AI**: DeepSeek API (`deepseek-v4-flash`)
- **Data**: PDC (runtime) + YAML (persistence)
- **Cross-play**: Geyser-Spigot + Floodgate + ViaVersion

---

## 2. Quick Start untuk Player Baru

### Pertama Kali Join Server

1. **Join server Minecraft** (Java Edition atau Bedrock via Geyser)
2. **Buat karakter** — server akan otomatis kasih kamu **Buku Karakter** (Book & Quill):
   - **Buku Tahap 1** — Pilih Race + Class. Tulis angka pilihanmu, lalu tanda tangani (sign).
   - **Buku Tahap 2** — Pilih 2 skill, background, dan ability scores. Tanda tangani lagi.
   - Selesai! Scoreboard otomatis muncul di sidebar kanan.
3. **Dadu otomatis** — Hotbar kamu akan auto-terisi dadu D4–D20 (dari kanan ke kiri)
4. **Jelajahi dunia!** Kalau masuk area story, narator akan otomatis bercerita

### Command Paling Penting

```
/character info     → Lihat character sheet kamu
/roll stealth       → Cek skill (stealth/perception/dll)
/battle start       → Mulai combat dengan monster terdekat
/atk melee          → Serang musuh saat giliran kamu
/talk <npc> halo    → Bicara dengan NPC — bisa chat langsung!
```

---

## 3. Membuat Karakter

### 3.1 Book & Quill — 2 Tahap

Server memberi kamu **Writable Book** otomatis saat pertama join (atau setelah `/dnd reroll confirm`).

**Tahap 1 — Race & Class:**
| Page | Isi | Cara Isi |
|------|-----|----------|
| Page 1 | Pilih **Race** (1–9) | Tulis angka pilihan di akhir halaman |
| Page 2 | Pilih **Class** (1–12) | Tulis angka pilihan di akhir halaman |

Lalu **tanda tangani (sign)** buku. Nama karakter = judul buku.

**Tahap 2 — Skills, Background, Scores:**
| Page | Isi | Cara Isi |
|------|-----|----------|
| Page 1 | Pilih **2 Skill** (1–10) | Tulis 2 angka, pisah koma: `3,4` |
| Page 2 | Pilih **Background** (1–8) | Tulis angka pilihan |
| Page 3 | **Ability Scores** | Pilih 1 (Standard Array) atau 2 (Roll 4d6) + isi urutan |

Tanda tangani lagi. Karakter jadi!

### 3.2 9 Race Tersedia

| # | Race ID | Display Name | Traits |
|---|---------|-------------|--------|
| 1 | `human` | Human | +1 all stats, versatile |
| 2 | `elf` | Elf | +2 DEX, keen senses, darkvision |
| 3 | `dwarf` | Dwarf | +2 CON, poison resistance, darkvision |
| 4 | `halfling` | Halfling | +2 DEX, lucky, small size |
| 5 | `half_orc` | Half-Orc | +2 STR, +1 CON, relentless endurance |
| 6 | `tiefling` | Tiefling | +2 CHA, fire resistance, darkvision |
| 7 | `dragonborn` | Dragonborn | +2 STR, +1 CHA, breath weapon |
| 8 | `gnome` | Gnome | +2 INT, gnome cunning, small size |
| 9 | `half_elf` | Half-Elf | +2 CHA, +1 two others, darkvision |

### 3.3 12 Class Tersedia

| # | Class ID | Base HP | Primary Ability | Spellcasting |
|---|----------|---------|-----------------|-------------|
| 1 | `fighter` | 10 | STR | — |
| 2 | `rogue` | 8 | DEX | — |
| 3 | `wizard` | 6 | INT | INT |
| 4 | `cleric` | 8 | WIS | WIS |
| 5 | `paladin` | 10 | STR | WIS |
| 6 | `barbarian` | 12 | STR | — |
| 7 | `monk` | 8 | DEX | — |
| 8 | `druid` | 8 | WIS | WIS |
| 9 | `sorcerer` | 6 | CHA | CHA |
| 10 | `warlock` | 8 | CHA | CHA |
| 11 | `ranger` | 10 | DEX | WIS |
| 12 | `bard` | 8 | CHA | CHA |

### 3.4 8 Background Tersedia

| # | Background ID | Display Name |
|---|---------------|-------------|
| 1 | `acolyte` | Acolyte |
| 2 | `criminal` | Criminal |
| 3 | `folk_hero` | Folk Hero |
| 4 | `noble` | Noble |
| 5 | `sage` | Sage |
| 6 | `soldier` | Soldier |
| 7 | `outlander` | Outlander |
| 8 | `charlatan` | Charlatan |

### 3.5 Ability Scores — Dua Metode

**Metode 1 — Standard Array**: `15, 14, 13, 12, 10, 8`
Urutkan sendiri ke (STR,DEX,CON,INT,WIS,CHA). Contoh: `15,10,13,8,12,14` → STR=15, DEX=10, CON=13, INT=8, WIS=12, CHA=14

**Metode 2 — Roll (4d6 drop lowest)**: 6× roll, hasil random. Bisa tinggi, bisa rendah — nasib di tangan dadu!

### 3.6 HP Awal

`HP = classBaseHP + CON modifier`

| Class | Base HP | Hit Dice |
|-------|---------|----------|
| Barbarian | 12 | d12 |
| Fighter, Paladin, Ranger | 10 | d10 |
| Rogue, Cleric, Warlock, Monk, Druid, Bard | 8 | d8 |
| Wizard, Sorcerer | 6 | d6 |

### 3.7 Character Command (Legacy)

Selain Book & Quill, masih ada `/character` command untuk manajemen:

```
/character info                      → Lihat character sheet kamu
/character reset                     → Hapus karakter & mulai ulang
```

### 3.8 Reroll Karakter

```
/dnd reroll confirm   → Reset karakter + dapat Buku Karakter baru
```

---

## 4. Command Reference Lengkap

### `/character` — Character Management

```
/character info                      → Lihat character sheet kamu
/character reset                     → Hapus karakter & mulai ulang
```

### `/atk` — Serangan (Combat)

```
/atk melee [target]    → Serangan jarak dekat (STR + prof vs AC)
/atk ranged [target]   → Serangan jarak jauh (DEX + prof vs AC)
```

- **Hanya bisa saat giliran kamu di battle**
- Bisa target pakai nama mob atau ID mob
- TAB completion untuk daftar target
- Menggunakan weapon dice dari item di tangan (kalau pakai D&D item)

### `/roll` — Dice Roll & Skill Check

```
/roll                   → Auto-detect PendingRoll (saat combat)
/roll <type>            → Free skill check (di luar combat)
```

**14 Roll Types**: `athletics`, `acrobatics`, `stealth`, `perception`, `investigation`, `insight`, `persuasion`, `deception`, `intimidation`, `sleight_of_hand`, `survival`, `attack`, `initiative`, `death_save`

Bisa juga dengan dadu fisik: **klik kanan** sambil pegang dadu di tangan.

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
/talk <npc_id> [pesan]   → Bicara dengan NPC (multi-turn!)
```

- TAB completion untuk daftar NPC terdekat
- **Multi-turn dialog**: chat langsung setelah `/talk` — tidak perlu command lagi
- NPC penting: dialog AI penuh (DeepSeek), ada history & backstory
- NPC side: 1 bubble greeting saja
- Klik kanan NPC juga trigger dialog
- Max 5 turns per sesi dialog

### `/spawnnpc`, `/newnpc`, `/deletenpc` — NPC Management (Admin/Dev)

```
/spawnnpc <template_id>                         → Spawn NPC dari template YAML
/newnpc <id> <display_name> [type] [spawn]      → Buat NPC baru + save template YAML
/deletenpc <npc_id>                              → Hapus NPC: data + YAML + semua entity di world
```

- NPC sekarang **persistent** — tidak akan despawn meskipun spawn egg
- Triple-lock: `setPersistent` + `setRemoveWhenFarAway(false)` + reflection `setPersistenceRequired`

### `/poi` — POI Management (Admin)

```
/poi define <poi_id> <name> [biome] [parent]  → Mulai define POI dengan stick tool
/poi confirm                                    → Simpan POI yang sedang di-define
/poi cancel                                     → Batalkan define
/poi list [parent]                              → List semua POI (filter by parent)
/poi info <poi_id>                              → Detail + visual border 5 detik
/poi tree                                       → Hierarki POI (indented tree)
/poi showall                                    → Tampilkan outline semua POI 10 detik
```

### `/dnd` — Admin Controls

```
/dnd reload         → Hot swap: reload config + semua YAML + SRD tanpa restart
/dnd save           → Force save semua player/NPC/party data ke disk
/dnd version        → Tampilkan versi plugin, API, party node, AI model
/dnd stop           → Pause story tick task
/dnd start          → Resume story tick task
/dnd mode <semi|auto> → Ganti narrator mode: semi (player pilih choices) / auto (AI advance)
/dnd reroll confirm → Reset karakter + kasih Buku Karakter baru
```

---

## 5. Sistem Combat (Serangan)

### 5.1 Player Damage Immunity

**Player HANYA bisa kena damage saat battle aktif!**

- Di luar battle: player **immune** dari serangan mob (melee + projectile)
- Projectile (panah skeleton, snowball, dll) → cek attacker source, tetap di-immunisasi
- Begitu `/battle start`, semua participant jadi bisa damage satu sama lain
- Begitu battle selesai → immunity kembali aktif

### 5.2 Cara Menyerang

Saat battle aktif dan giliran kamu:
```
/atk melee goblin        → Serang goblin dengan pedang (STR-based)
/atk ranged skeleton     → Panah ke skeleton (DEX-based)
```

### 5.3 Attack Resolution (D&D 5e)

```
Attack Roll = d20 + Ability Modifier + Proficiency Bonus

Melee:  d20 + STR mod + prof → vs target AC
Ranged: d20 + DEX mod + prof → vs target AC
```

- **Hit**: attack roll ≥ target AC
- **Miss**: attack roll < target AC (tidak kena)
- **Nat 20**: Auto-hit + **CRITICAL** (damage dice digandakan)
- **Nat 1**: Auto-miss

### 5.4 Armor Class (AC) — First-Campaign Balanced

| Target | AC |
|--------|-----|
| Player | **10 + DEX modifier** |
| Zombie | 6 |
| Skeleton | 11 |
| Spider | 12 |
| Creeper | 10 |
| Enderman | 13 |
| Pillager / Vindicator | 13 |
| Witch / Evoker | 10 |
| **Iron Golem** | **18** |
| Villager | 8 |

### 5.5 Damage Types & Resistance/Vulnerability

**12 Damage Types**: `SLASHING`, `PIERCING`, `BLUDGEONING`, `FIRE`, `COLD`, `LIGHTNING`, `ACID`, `POISON`, `NECROTIC`, `RADIANT`, `FORCE`, `PSYCHIC`

| Mob | Resistance (½ damage) | Vulnerability (2× damage) |
|-----|----------------------|---------------------------|
| **Skeleton** | Piercing | **Bludgeoning** |
| **Zombie** | — | **Radiant** |
| **Enderman** | Fire, Lightning | — |
| **Spider** | — | **Fire** |
| **Iron Golem** | Poison, Psychic (immune) | — |

### 5.6 Proficiency Bonus

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
| `fire_bolt` | Fire Bolt | 1d10 | Attack Roll, Fire | Arcane (Wizard/Warlock) |
| `magic_missile` | Magic Missile | 3d4+3 | **Auto-hit**, Force | Arcane |
| `burning_hands` | Burning Hands | 3d6 | DEX save, Fire (half on save) | Arcane |
| `chill_touch` | Chill Touch | 1d8 | Attack Roll, Necrotic | Arcane |
| `shocking_grasp` | Shocking Grasp | 1d8 | Attack Roll, Lightning | Arcane |
| `ray_of_frost` | Ray of Frost | 1d8 | Attack Roll, Cold | Arcane |
| `sacred_flame` | Sacred Flame | 1d8 | DEX save, Radiant (save negates) | Divine (Cleric/Paladin/Druid) |
| `poison_spray` | Poison Spray | 1d12 | CON save, Poison (save negates) | Semua caster |
| `acid_splash` | Acid Splash | 1d6 | DEX save, Acid (save negates) | Arcane |
| `frostbite` | Frostbite | 1d6 | CON save, Cold (save negates) | Semua caster |
| `eldritch_blast` | Eldritch Blast | 1d10 | Attack Roll, Force | **Warlock only** |
| `word_of_radiance` | Word of Radiance | 1d6 | CON save, Radiant (save negates) | **Cleric only** |

Semua spell sekarang **data-driven dari YAML** (`data/spells/*.yml`) — bisa dikustomisasi tanpa edit code.

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
| Cleric, Druid, Paladin, Ranger | WIS |
| Warlock, Sorcerer, Bard | CHA |

### 6.4 Spell Slots

- Setiap caster punya **2 spell slots** (Level 1)
- Tiap cast mengurangi 1 slot
- Slots ditentukan dari **class YAML** (`data/classes/<class>.yml`)
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

RollCommand otomatis nambah proficiency bonus **kalau skill termasuk 2 skill yang kamu pilih di character creation** (dari Buku Tahap 2 Page 1).

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
4. **Broadcast**: hasil roll di-broadcast ke semua player (angka saja, bukan animasi)

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
- Player **unconscious** otomatis di-skip — dapat prompt death save
- Setelah aksi di-resolve → auto-next turn

**Saat giliran mob (auto-AI):**
1. Unfreeze mob
2. Serang player terdekat (skip unconscious player)
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

Mob yang selamat di-**unfreeze** (AI normal kembali). Player unconscious di-stabilize.

---

## 9. Sistem NPC & Dialog

### 9.1 Dua Tipe NPC

| Tipe | Ciri | Dialog |
|------|------|--------|
| **NPC Penting** (Important) | Nama **§6emas bold**, dari template YAML | Multi-turn AI dialog via DeepSeek, punya backstory & history |
| **NPC Side** | Nama **§fputih bold**, spawn random | 1 bubble greeting statis (cached) |

### 9.2 Multi-Turn Dialog (Fitur Baru!)

Dialog NPC penting sekarang **multi-turn** — kamu bisa chat bolak-balik tanpa command berulang:

```
Player: /talk elder_marek Apa yang terjadi di hutan ini?
NPC:   *Elder Marek menatapmu dengan mata tuanya...* Hutan ini dulunya damai,
       sampai Conduit itu muncul dari kedalaman goa...

Player: (chat langsung) Conduit apa maksudmu?
NPC:   Sebuah kekuatan gelap... artefak kuno yang terbangun setelah
       berabad-abad. Ia memanggil monster dari bayangan...

Player: (chat langsung) Bagaimana cara menghentikannya?
NPC:   Kau butuh Elder Seal. Tapi itu tersembunyi di kedalaman goa...
```

**State machine**: `IDLE → GREETING → CONVERSING → RESOLVED`
- Max **5 turns** per sesi dialog
- Auto-resolve setelah 5 turn atau idle
- History dialog disimpan ke NPC YAML
- Disposition NPC bisa berubah berdasarkan dialog

### 9.3 Disposition NPC

NPC penting punya `disposition`:
- **`neutral`** — NPC biasa, tidak nyerang
- **`friendly`** — NPC ramah (future: bisa kasih quest/item)
- **`agro`** — NPC musuh, nyerang player

Hostile mob (Zombie, Skeleton) hanya nyerang kalau disposition = `agro`. Dicegah via `EntityTargetEvent`.

### 9.4 Persistence (Triple-Lock)

Semua named entity (NPC penting, side NPC, spawn egg mobs) **tidak akan despawn**:
1. `setPersistent(true)`
2. `setRemoveWhenFarAway(false)`
3. Reflection `setPersistenceRequired(true)` — bypass Paper API limitasi

### 9.5 Color Coding

- **NPC Penting**: `§6§l` (gold bold) — contoh: `§6§lElder Marek`
- **NPC Side**: `§f§l` (white bold) — contoh: `§f§lBob the Villager`
- Semua NPC punya `npc_id` dan `npc_name` di PDC

---

## 10. Sistem Dadu (Dice)

### 10.1 Dadu Fisik di Hotbar

Plugin otomatis memberi kamu **6 dadu polyhedral** di hotbar:

| Slot | Dadu | Warna | Item | Sisi |
|------|------|-------|------|------|
| Hotbar 8 (paling kanan) | **D20** | §fPutih | White Wool | 20 |
| Hotbar 7 | **D12** | §5Ungu | Purple Wool | 12 |
| Hotbar 6 | **D10** | §bCyan | Cyan Wool | 10 |
| Hotbar 5 | **D8** | §aLime | Lime Wool | 8 |
| Hotbar 4 | **D6** | §6Orange | Orange Wool | 6 |
| Hotbar 3 | **D4** | §dPink | Pink Wool | 4 |

- Dadu **terkunci** di hotbar — tidak bisa dibuang, di-drop, atau dipindahkan
- Jumlah stack = jumlah sisi dadu (D20 = 20 items, D6 = 6 items, dst)
- Kalau ada dadu hilang, auto-di-replenish saat validasi
- **Adventure mode friendly** — klik kanan blok = roll, bukan place block

### 10.2 Cara Roll dengan Dadu

**Pegang dadu di main hand** → **klik kanan** pada blok:
- Dadu akan menjalankan roll free (seperti `/roll` tanpa argumen)
- Animasi: bounce item + CRIT particle + ENCHANTED_HIT + progress bar
- Hasil di-broadcast ke semua player

**Atau pakai command** (tetap bisa):
```
/roll stealth    → d20 + DEX mod + prof
```

### 10.3 Dadu dalam Combat

Saat battle, pegang dadu yang sesuai di tangan saat `/atk` atau `/roll`:
- Engine deteksi dadu dari item di main hand
- Dice type (D20, D8, dll) diambil dari PDC dadu
- Kalau tidak ada dadu di tangan → fallback ke D20

---

## 11. Sistem Item

### 11.1 Item D&D Standar

Plugin punya **item template YAML** yang bisa di-spawn:

#### Senjata (Weapons)

| Template ID | Nama | Damage | Type |
|-------------|------|--------|------|
| `shortsword_iron` | Iron Shortsword | 1d6+3 slashing | Melee |
| `longsword_iron` | Iron Longsword | 1d8+3 slashing | Melee |
| `dagger` | Dagger | 1d4+2 piercing | Melee/Ranged (finesse) |
| `greataxe` | Greataxe | 1d12+3 slashing | Melee (2H) |
| `warhammer` | Warhammer | 1d8+3 bludgeoning | Melee |
| `quarterstaff` | Quarterstaff | 1d6+2 bludgeoning | Melee |
| `shortbow_wood` | Wood Shortbow | 1d6+2 piercing | Ranged |
| `light_crossbow` | Light Crossbow | 1d8+3 piercing | Ranged |

#### Senjata Spesial (Magic/Rare)

| Template ID | Nama | Damage | Special |
|-------------|------|--------|---------|
| `longsword_plus1` | Longsword +1 | 1d8+4 slashing | +1 magic weapon |
| `flaming_sword` | Flaming Sword | 1d8+3 slashing + 1d6 fire | Extra fire damage |
| `soul_reaper` | Soul Reaper | 1d10+4 necrotic | Legendary — necrotic damage |

#### Armor

| Template ID | Nama | AC Bonus | Type |
|-------------|------|----------|------|
| `leather_vest` | Leather Vest | +1 | Light |
| `studded_leather` | Studded Leather | +2 | Light |
| `chainmail` | Chainmail | +3 | Medium |
| `plate_of_thornhollow` | Plate of Thornhollow | +5 | Heavy (legendary) |
| `shield_wooden` | Wooden Shield | +2 | Shield |

#### Potion & Utility

| Template ID | Nama | Efek |
|-------------|------|------|
| `health_potion` | Health Potion | Restore 2d4+2 HP |
| `greater_health_potion` | Greater Health Potion | Restore 4d4+4 HP |
| `mana_potion` | Mana Potion | Restore 1 spell slot |
| `antidote` | Antidote | Cure poison condition |

#### Aksesoris & Spesial

| Template ID | Nama | Efek |
|-------------|------|------|
| `ring_of_protection` | Ring of Protection | +1 AC & +1 all saves |
| `ancient_key` | Ancient Key | Quest item — membuka sesuatu... |
| `elder_seal` | Elder Seal | Quest item — mengandung kekuatan kuno |

### 11.2 Cara Mendapatkan Item

Item bisa di-spawn via plugin API (dev) atau ditemukan di dunia. Untuk development:
- Template YAML di `data/items/templates/`
- Item di-load dari JAR (`src/main/resources/data/items/templates/`)
- Custom item bisa ditambah di `server/plugins/DndPlugin/data/items/templates/`

### 11.3 Item PDC Tagging

Semua D&D item punya PDC tags:
- `dnd:item_type` — `WEAPON`, `ARMOR`, `POTION`, `UTILITY`, `QUEST`
- `dnd:weapon_damage_dice` — e.g. `1d8`, `2d6`
- `dnd:weapon_damage_bonus` — e.g. `3`
- `dnd:weapon_damage_type` — e.g. `SLASHING`, `FIRE`
- `dnd:special_effect` — e.g. `FLAMING`, `HOLY`, `LIFESTEAL`

---

## 12. Story & Dunia (POI / Story Tree)

### 12.1 Cara Kerja Story

Plugin punya **tick loop** yang tiap interval (configurable) mengecek:

1. **`ALL_PLAYERS_IN_BBOX`** — trigger kalau **semua player** berada dalam bounding box suatu story node
2. **`SINGLE_PLAYER_EVENT`** — trigger kalau 1 player memicu event spesifik

Kalau trigger terpenuhi:
- Narator (DeepSeek) akan merender narasi + pilihan
- Player bisa memilih antara `choices` untuk maju ke node berikutnya

### 12.2 POI (Point of Interest) — 3D Bounding Box

POI adalah lokasi penting di dunia. Sekarang mendukung **3D bounding box** (X, Y, Z):

```yaml
# data/poi/forest_cave.yml
poi_id: forest_cave
name: "Goa Hutan Gelap"
biome: forest
bbox:
  x1: 100
  y1: 50
  z1: 200
  x2: 150
  y2: 80
  z2: 250
```

### 12.3 POI Hierarchy (Parent-Child Tree)

POI bisa punya **parent-child hierarchy**:

```
Desa Thornhollow (bbox seluruh desa)
  ├── Rumah Elder Marek (bbox rumah)
  │     ├── Ruang Tamu (bbox ruangan)
  │     └── Ruang Bawah Tanah (bbox spesifik)
  └── Kedai Obat (bbox kedai)
```

**Aturan**:
- Child POI harus **sepenuhnya di dalam** bbox parent
- Saat player masuk area, **POI paling spesifik (paling dalam)** yang di-return
- Kalau player di pojok ruang bawah tanah → return `ruang_bawah_tanah`, bukan `desa_thornhollow`

### 12.4 POI Creation Tool (Admin In-Game)

```
/poi define cave_entrance "Goa Gelap" forest [parent_id]
```

1. Dapat **stick POI tool** (display name: `§6§l📐 POI Tool`)
2. **Klik kiri** blok = corner 1 (x1, y1, z1)
3. **Klik kanan** blok = corner 2 (x2, y2, z2)
4. Particle **wireframe** muncul di antara kedua corner (12 edges 3D box)
5. `/poi confirm` → validasi + simpan YAML + reload
6. `/poi cancel` → batalkan

**Validasi**: bbox child harus sepenuhnya di dalam bbox parent.

### 12.5 Story Node YAML (3D Bbox)

```yaml
node_id: forest_entrance
trigger: ALL_PLAYERS_IN_BBOX
bbox:
  x1: 100
  y1: 50
  z1: 200
  x2: 150
  y2: 80
  z2: 250
narrative: >
  Party tiba di gerbang hutan tua...
choices:
  - id: enter_cave
    next_node: cave_dialogue
  - id: avoid_cave
    next_node: forest_path_alt
```

### 12.6 Party Progress (Consensus)

- Progress cerita di-track sebagai **1 party**
- `partyCurrentNode` = node paling maju di antara semua player
- Kalau 1 player masuk node baru, party ikut maju

---

## 13. AI Narator & Story Modes

### 13.1 Dua Mode Narator

| Mode | Behavior | Kapan Dipakai |
|------|----------|--------------|
| **SEMI** (default) | AI narasi + tampilkan choices. Player pilih next node. | Standard play |
| **AUTO** | AI narasi + auto-pilih next node. Story jalan sendiri. | Cinematic / auto-pilot |

```
/dnd mode semi    → Player-driven story
/dnd mode auto    → AI-driven story
/dnd mode         → Cek mode sekarang
```

### 13.2 Cara Kerja AI Narator

1. **On-Rail** (player di story node): AI mengikuti template narasi dari YAML + improvisasi kontekstual
2. **Off-Rail** (player di luar story node): AI free-narrate berdasarkan POI terdekat + world context
3. **Batch Narration**: AI bisa generate beberapa NARASI sekaligus untuk efisiensi

### 13.3 System Prompt Guardrails

- AI di-instruct untuk menggunakan **Bahasa Indonesia** yang natural
- **ANGKA bersifat FINAL** — AI tidak boleh ubah damage, HP, atau roll result
- Template narasi dari YAML dijadikan anchor, AI improvisasi di sekitarnya
- SRD rules di-inject sebagai context untuk rule consistency

### 13.4 Party Session

State party di-track di `data/party/session.yml`:
- `narrator_mode`: `semi` atau `auto`
- `current_node`: story node terakhir
- `events`: rolling 10 events terakhir (untuk AI context)

---

## 14. Scoreboard & Display

### 14.1 Scoreboard Sidebar

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

### 14.2 Auto-Refresh

Scoreboard di-refresh:
- **Setiap tick loop** (interval configurable)
- **Instant**: saat kena damage, regen HP, atau level up
- **Saat mode narator berubah** — Node field update

### 14.3 Title Display

Hasil combat & roll muncul sebagai **title** (bukan chat):
- Animasi roll: obfuscated text + note block sounds
- Combat result: HIT/MISS/CRIT dengan damage

### 14.4 HP Bar di Nameplate

Semua entity (NPC + monster + player) punya **HP bar di bawah nama**:
```
§6§lElder Marek
§c❤ 20/20
```

HP bar di-refresh tiap kali entity kena damage atau regen.

---

## 15. Death & Death Saves

### 15.1 Player Mencapai HP ≤ 0

Saat player HP ≤ 0:
- Player menjadi **unconscious** (Blindness + Slowness)
- HP diset ke 0.5 (setengah hati) — mencegah Minecraft death
- Mulai **Death Saving Throws**

### 15.2 Death Saving Throw

Roll d20 (no modifier):

| Roll | Hasil |
|------|-------|
| **Nat 20** | Langsung bangun! HP kembali **75%** dari max HP |
| **Nat 1** | 2 failures sekaligus |
| **10+** | 1 success |
| **2–9** | 1 failure |

- **3 success** → **Stabilized** — HP kembali **50%** dari max HP
- **3 failure** → **DEAD** (mati, HP = 0)

### 15.3 Revival HP (Baru!)

| Scenario | HP Setelah Bangun |
|----------|------------------|
| Nat 20 | **75%** max HP |
| 3 successes | **50%** max HP |
| Stabilized after battle | **50%** max HP |

### 15.4 Unconscious dalam Battle

- Player unconscious **di-skip** dalam turn order (tidak dapat giliran)
- Prompt death save muncul otomatis di chat
- Player unconscious **tidak dihitung sebagai "mati"** — battle belum berakhir
- Kalau semua player unconscious → battle defeat

### 15.5 Mob/NPC Mati

Mob/NPC yang HP-nya ≤ 0 **langsung mati** (tidak ada death saves).

---

## 16. Untuk Developer / Story Creator

Bagian ini untuk kamu yang ingin membuat konten: story node, NPC, POI, item, karakter template.

### 16.1 Struktur Folder Data

```
server/plugins/DndPlugin/
├── config.yml                           # API key + konfigurasi
└── data/
    ├── party/
    │   └── session.yml                  # Party session state (auto)
    ├── story/                           # Story tree nodes
    │   └── forest_entrance.yml
    ├── npcs/                            # NPC templates
    │   └── elder_marek.yml
    ├── poi/                             # Points of Interest
    │   ├── forest_cave.yml
    │   ├── desa_thornhollow.yml
    │   └── rumah_marek.yml
    ├── items/
    │   └── templates/                   # Item templates (custom)
    ├── characters/                      # Character templates (di JAR)
    │   ├── aric_fighter.yml
    │   ├── lyra_rogue.yml
    │   ├── merlin_wizard.yml
    │   └── mira_cleric.yml
    ├── players/                         # Player data (auto-generated)
    │   └── <uuid>.yml
    └── rules/
        └── srd/                         # SRD rules (5esrd.yaml)
```

### 16.2 Data-Driven Resources (di JAR)

```
src/main/resources/data/
├── spells/          # 12 spell YAML definitions
├── items/templates/ # Item templates (weapons, armor, potions, quest)
├── races/           # 9 race YAML definitions
├── classes/         # 12 class YAML definitions
├── backgrounds/     # 8 background YAML definitions
├── story/           # Default story nodes
├── npcs/            # Default NPC templates
├── poi/             # Default POIs
└── characters/      # Starter character templates
```

### 16.3 Membuat Story Node (3D Bbox)

Buat file YAML baru di `data/story/`:

```yaml
# data/story/cave_dialogue.yml
node_id: cave_dialogue
trigger: ALL_PLAYERS_IN_BBOX
bbox:
  x1: 100
  y1: 50
  z1: 200
  x2: 150
  y2: 80
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
| `bbox` | ✅* | Bounding box: `x1, y1, z1, x2, y2, z2` (3D!) |
| `narrative` | ✅ | Template narasi — AI akan isi detail kontekstual |
| `choices` | ✅ | Pilihan player (min 1), masing-masing punya `id` + `next_node` |

> **Catatan**: `y1` dan `y2` sekarang wajib. Old format 2D (hanya x1,z1,x2,z2) auto-default ke y1=0, y2=256.

### 16.4 Membuat NPC Penting

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

Hapus NPC:
```
/deletenpc elder_marek    → Hapus data + YAML + kill semua entity
```

**Tips Backstory**:
- Tulis backstory yang kaya — AI pakai ini sebagai **system prompt** saat NPC dialog
- Sertakan: asal-usul, personality, rahasia, ketakutan, hubungan dengan dunia
- Semakin detail backstory, semakin natural dialog AI (multi-turn)
- History dialog auto-terekam — kamu bisa lihat apa yang sudah dibicarakan party

### 16.5 Membuat POI (3D dengan Hierarchy)

**Via In-Game Tool** (rekomendasi):
```
/poi define thornhollow "Desa Thornhollow" village
→ Klik kiri corner 1, klik kanan corner 2
→ /poi confirm

/poi define rumah_marek "Rumah Elder Marek" building thornhollow
→ Klik kiri, klik kanan di dalam bbox desa
→ /poi confirm

/poi tree    → Lihat hierarki
```

**Via YAML langsung**:
```yaml
# data/poi/desa_thornhollow.yml
poi_id: desa_thornhollow
name: "Desa Thornhollow"
biome: village
parent_poi_id: null          # Top-level POI
bbox:
  x1: 200
  y1: 60
  z1: 300
  x2: 350
  y2: 85
  z2: 450
```

### 16.6 Membuat Item Template

```yaml
# data/items/templates/flaming_sword.yml
item_id: flaming_sword
item_type: WEAPON
display_name: "§6Flaming Sword"
material: IRON_SWORD
lore:
  - "§7A blade wreathed in eternal flame"
  - "§c+1d6 fire damage"
weapon_damage_dice: "1d8"
weapon_damage_bonus: 3
weapon_damage_type: SLASHING
special_effect: FLAMING
extra_damage_dice: "1d6"
extra_damage_type: FIRE
```

### 16.7 Hot Reload

Setelah edit file YAML:
```
/dnd reload
```

Ini akan reload **semua**: config, story tree, NPC, POI, item, spell, race, class, background, SRD rules. **Tanpa restart server.**

### 16.8 Admin Commands Lengkap

| Command | Kegunaan |
|---------|----------|
| `/dnd reload` | Hot swap semua data tanpa restart |
| `/dnd save` | Force save semua player/NPC/party ke disk |
| `/dnd version` | Cek versi plugin + AI model + party node |
| `/dnd stop` | Pause story tick loop |
| `/dnd start` | Resume story tick loop |
| `/dnd mode <semi\|auto>` | Ganti narrator mode |
| `/dnd reroll confirm` | Reset karakter + kasih buku baru |
| `/poi define <id> <name> [biome] [parent]` | Mulai define POI dengan stick tool |
| `/poi confirm` | Simpan POI |
| `/poi cancel` | Batalkan define |
| `/poi list [parent]` | List POI |
| `/poi info <id>` | Detail + visual border |
| `/poi tree` | Hierarki tree |
| `/poi showall` | Visual outline semua POI |
| `/newnpc <id> <name> [type] [spawn]` | Buat NPC baru |
| `/spawnnpc <id>` | Spawn NPC dari template |
| `/deletenpc <id>` | Hapus NPC |

---

## 17. Tabel Referensi

### 17.1 Semua Class D&D

| Class | Base HP | Primary Ability | Spellcasting | Saving Throws |
|-------|---------|-----------------|-------------|---------------|
| **Barbarian** | 12 | STR | — | STR, CON |
| **Fighter** | 10 | STR | — | STR, CON |
| **Ranger** | 10 | DEX | WIS | STR, DEX |
| **Paladin** | 10 | STR | WIS | WIS, CHA |
| **Rogue** | 8 | DEX | — | DEX, INT |
| **Cleric** | 8 | WIS | WIS | WIS, CHA |
| **Warlock** | 8 | CHA | CHA | WIS, CHA |
| **Monk** | 8 | DEX | — | STR, DEX |
| **Druid** | 8 | WIS | WIS | INT, WIS |
| **Bard** | 8 | CHA | CHA | DEX, CHA |
| **Sorcerer** | 6 | CHA | CHA | CON, CHA |
| **Wizard** | 6 | INT | INT | INT, WIS |

### 17.2 Skill → Ability Map

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

### 17.3 Mob AC Table

| Entity Type | AC |
|-------------|-----|
| **Iron Golem** | 18 |
| Enderman | 13 |
| Pillager | 13 |
| Vindicator | 13 |
| Spider | 12 |
| Skeleton | 11 |
| Creeper | 10 |
| Witch | 10 |
| Evoker | 10 |
| Villager | 8 |
| Wandering Trader | 8 |
| **Zombie** | 6 |
| Other | 8 |

### 17.4 Mob Attack Stats (AI)

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

### 17.5 Spell Reference Card

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

### 17.6 Conditions Reference

| Condition | Minecraft Effect | D&D Effect |
|-----------|-----------------|------------|
| **Poisoned** | Poison I + Slowness I | Disadvantage on attacks & ability checks |
| **Stunned** | Slowness V + Weakness II + Blindness I | Cannot act, auto-fail STR/DEX saves |
| **Unconscious** | Blindness I + Slowness VI | Prone, adjacent attacks = crit |
| **Blinded** | Blindness I | Disadvantage on attacks, enemy advantage |

### 17.7 Dadu Reference

| Dadu | Warna | Material | Sisi | Slot Hotbar |
|------|-------|----------|------|-------------|
| D20 | §fPutih | White Wool | 20 | 8 (kanan) |
| D12 | §5Ungu | Purple Wool | 12 | 7 |
| D10 | §bCyan | Cyan Wool | 10 | 6 |
| D8 | §aLime | Lime Wool | 8 | 5 |
| D6 | §6Orange | Orange Wool | 6 | 4 |
| D4 | §dPink | Pink Wool | 4 | 3 |

### 17.8 Standard D&D Items Quick Reference

| Item | Type | Dice | Special |
|------|------|------|---------|
| Dagger | Weapon | 1d4+2 piercing | Finesse, light |
| Greataxe | Weapon | 1d12+3 slashing | Two-handed |
| Warhammer | Weapon | 1d8+3 bludgeoning | — |
| Light Crossbow | Weapon | 1d8+3 piercing | Ranged |
| Quarterstaff | Weapon | 1d6+2 bludgeoning | — |
| Wooden Shield | Armor | +2 AC | Shield |
| Studded Leather | Armor | +2 AC | Light armor |
| Mana Potion | Potion | Restore 1 slot | Spell slot |
| Antidote | Potion | Cure poison | Remove condition |
| Ring of Protection | Ring | +1 AC, +1 saves | Attunement |

---

## Appendix A: Flow Combat Lengkap

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
   ├── Hit → damage applied → cek kill / unconscious
   ├── Unconscious → prompt death save
   └── Next turn (auto)

3. Giliran Mob (auto-AI):
   ├── Unfreeze mob
   ├── Skip kalau mob unconscious/mati
   ├── Cari player terdekat (skip unconscious)
   ├── Attack roll vs player AC
   ├── Hit → damage applied
   ├── Re-freeze mob
   └── Next turn (delay 1.5s)

4. Death Save (saat giliran player unconscious):
   ├── Prompt death save di chat
   ├── Roll d20 (no modifier)
   ├── Nat20 → bangun 75% HP!
   ├── 10+ → 1 success
   ├── 2-9 → 1 failure
   ├── Nat1 → 2 failures
   ├── 3 success → stabilize 50% HP
   └── 3 failure → DEAD

5. Battle End:
   ├── Victory: semua monster mati → unfreeze survivors
   ├── Defeat: semua player dead/unconscious
   └── Flee: player kabur (DEX DC12)
```

## Appendix B: Flow Karakter Creation (Book & Quill)

```
1. Player join pertama kali (atau /dnd reroll confirm)
   ├── Server kasih Buku 1 (Writable Book) — "Tahap 1"
   ├── Page 1: Pilih Race (1-9)
   ├── Page 2: Pilih Class (1-12)
   └── Tanda tangani (sign) buku

2. BookSignListener capture sign:
   ├── Parse race dari Page 1
   ├── Parse class dari Page 2
   ├── Validasi: 1-9 & 1-12
   ├── Simpan sementara ke PDC
   └── Kasih Buku 2 — "Tahap 2"

3. Player isi Buku 2:
   ├── Page 1: Pilih 2 Skill (1-10), pisah koma
   ├── Page 2: Pilih Background (1-8)
   ├── Page 3: Metode ability scores (1=Std Array, 2=Roll)
   │          + urutan scores (6 angka)
   └── Tanda tangani lagi

4. BookSignListener capture sign:
   ├── Parse skills, background, scores
   ├── Roll 4d6×6 (kalau metode roll)
   ├── finalizeCharacter():
   │   ├── Set PDC: level, str, dex, con, int, wis, cha
   │   ├── Set PDC: class, char_name, race, has_character
   │   └── Set HP max = baseHP + CON mod
   ├── Show scoreboard
   ├── Auto-equip dadu ke hotbar
   └── Broadcast: "✨ [name] telah bergabung!"
```

## Appendix C: POI In-Game Creation Flow

```
1. Admin: /poi define <poi_id> <name> [biome] [parent]
   → Dapat stick POI tool (display: §6§l📐 POI Tool)
   → Prompt: "Klik kiri = corner 1, klik kanan = corner 2"

2. Admin klik kiri blok:
   → PoiDefineListener capture LEFT_CLICK_BLOCK
   → Cek main hand ada PDC dnd:poi_tool
   → Simpan corner 1 (x,y,z) ke PDC player
   → Spawn corner marker particle (END_ROD)
   → Message: "Corner 1 set: {x}, {y}, {z}"

3. Admin klik kanan blok:
   → PoiDefineListener capture RIGHT_CLICK_BLOCK
   → Simpan corner 2 (x,y,z) ke PDC player
   → Spawn wireframe particle (12 edges 3D box)
   → Message: "Corner 2 set: {x}, {y}, {z}"
   → Prompt: "Ketik /poi confirm untuk simpan"

4. Admin: /poi confirm
   → Validasi: kedua corner set + world sama
   → Validasi: parent containment (child ⊆ parent bbox)
   → Save YAML via PoiStore
   → Clear PDC + remove stick tool + hapus particles
   → Message: "✅ POI '{name}' disimpan!"
   → Auto-reload PoiStore
```

---

> **Dibuat dengan 🐉 untuk Minecraft D&D Plugin**
> Versi: v1.0.0.68 | Paper 1.21.11 | Java 21 | Geyser/Floodgate
