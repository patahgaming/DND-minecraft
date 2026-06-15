# DND-Minecraft-Plugin

Dungeons & Dragons AI System — Minecraft-native Paper plugin dengan DeepSeek AI sebagai narator dan NPC.

## Stack
- **Paper API 26.1.2** (Minecraft 1.21.11)
- **Java 21+**
- **Maven** build
- **DeepSeek API** via HTTP (OpenAI-compatible `/chat/completions`)
- **YAML + PDC** data (PersistentDataContainer runtime, YAML persistence)

## Build
```bash
mvn clean package -DskipTests
# Output: target/dndplugin-1.0.0-SNAPSHOT.jar
```

## Run
1. Setup Paper server (1.21.11)
2. Copy JAR ke `plugins/`
3. Set `deepseek.api-key` di `plugins/DndPlugin/config.yml`
4. Start server

## Architecture
See [SKILL.md](SKILL.md) — primary design document.
See [CLAUDE.md](CLAUDE.md) — guidance for AI assistants working on this repo.

## License
Private project — patahgaming
