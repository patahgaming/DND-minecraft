# DnD Minecraft — Test Server Setup

## Platform

| Layer | Tech |
|-------|------|
| Server | Paper 1.21.11 |
| Java | OpenJDK 25 (Temurin) |
| RAM | 2 GB |
| GC | ZGC Generational |

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| **DnDPlugin** | 1.0.0.58 | Game system — combat, spells, NPCs, dice, items, POI |
| **Geyser-Spigot** | Latest | Bedrock/Pocket Edition cross-play (UDP 19132) |
| **Floodgate-Spigot** | Latest | Auto-auth for Bedrock players (no Java account needed) |
| **Spark** | Latest | Performance profiler (`/spark health`, `/spark profiler`) |

## Cara Mulai

```bash
cd server
start-test.bat
```

- **Java Edition**: Connect ke `localhost:25565`
- **Bedrock/Pocket Edition**: Connect ke `localhost:19132`

## Port

| Port | Protocol | Purpose |
|------|----------|---------|
| 25565 | TCP | Java Edition |
| 19132 | UDP | Bedrock Edition (Geyser) |

## Fabric Mods — Tidak Kompatibel

Fabric dan Paper adalah platform server berbeda — **tidak bisa jalan bareng**.
Paper sudah punya optimasi built-in yang ekuivalen dengan Fabric mods:

| Fabric Mod | Paper Equivalent |
|------------|-----------------|
| Lithium | Paper built-in optimizations |
| Phosphor/Starlight | Paper lighting engine |
| FerriteCore | Paper memory optimizations |
| Krypton | Paper networking |
| Carpet | Spark profiler |
| No Chat Reports | Paper config |
| Chunky | Not installed (optional) |

## Java 25 Flags

```bash
-Xmx2G -Xms1G
-XX:+UseZGC -XX:+ZGenerational
-XX:+AlwaysPreTouch
-XX:+ParallelRefProcEnabled
-XX:+UseStringDeduplication
-XX:MaxGCPauseMillis=200
--enable-native-access=ALL-UNNAMED
```

## Konfigurasi Geyser

File `plugins/Geyser-Spigot/config.yml` auto-generated saat first run.
Setting penting:
- `bedrock.port: 19132`
- `remote.auth-type: floodgate`
