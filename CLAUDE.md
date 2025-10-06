# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ChestLogger is a Fabric server-side mod for Minecraft 1.21.8 that tracks player interactions with containers (chests, barrels, shulker boxes, etc.). It logs what items players add or remove from containers with timestamps and positions.

## Build and Development Commands

### Building
- `./gradlew build` - Build the mod JAR (output in `build/libs/`)
- `./gradlew clean build` - Clean build from scratch

### Running
- `./gradlew runServer` - Run Minecraft server with the mod loaded (development environment)

### Testing
- `./gradlew test` - Run tests (if any exist)

### Gradle Setup
- Requires Gradle 8.12+ (wrapper configured for 8.14.1)
- If wrapper scripts are missing: `gradle wrapper --gradle-version 8.14.1`

## Architecture

### Core Components

**Chestlogger** (Main Entry Point)
- Implements `DedicatedServerModInitializer` for server-only execution
- Initializes `ChestLogManager` on server start
- Registers `ChestLogCommands` and `ChestEventHandler`
- Provides static access to the log manager via `getLogManager()`

**ChestLogManager** (Data Management)
- Stores all access logs in memory and persists to `chest_logs.dat` in world directory using NBT format
- Provides query methods: by player name, by position, recent logs, and log count
- Handles automatic saving after each log addition
- Manages log cleanup (clear all or clear old logs by timestamp)

**ChestEventHandler** (Event Tracking)
- Maintains a map of open containers per player UUID
- Takes a "snapshot" of container contents when opened
- Compares snapshots on close to detect item additions/removals
- Respects config settings for which container types to track
- Uses `captureContainerContents()` to read only container slots (not player inventory)

**ServerPlayerEntityMixin** (Event Injection)
- Injects into `ServerPlayerEntity` to capture container open/close events
- Tracks the last opened container position using `@Unique` field
- Calls `ChestEventHandler` methods at appropriate injection points
- Handles screen handler opening and closing lifecycle

**ChestLogCommands** (Command Interface)
- `/chestlog query <player>` - View all logs for a specific player
- `/chestlog recent [count]` - View recent logs (default 10, max 100)
- `/chestlog at <pos>` - View logs for a specific block position
- `/chestlog clear` - Delete all logs
- `/chestlog clearold <days>` - Delete logs older than specified days
- `/chestlog stats` - Show total log count
- All commands require OP level 2+

**Config** (Configuration Management)
- Loads/saves from `config/chestlogger.json` as JSON
- Settings:
  - `logRetentionDays` - Default retention period (default: 30)
  - `trackChests` - Track chest blocks (default: true)
  - `trackBarrels` - Track barrel blocks (default: true)
  - `trackShulkerBoxes` - Track shulker box blocks (default: true)
  - `trackEnderChests` - Track ender chest blocks (default: false)
  - `trackHoppers` - Track hopper blocks (default: false)
- Auto-creates default config if missing

**ChestAccessLog** (Data Model)
- Immutable record of a single container access
- Contains: player name, position, dimension, timestamp, items added/removed
- Implements NBT serialization/deserialization for persistence
- Provides formatted output methods for commands

### Data Flow

1. Player opens container → Mixin intercepts → `ChestEventHandler.onContainerOpen()` → Creates snapshot
2. Player closes container → Mixin intercepts → `ChestEventHandler.onContainerClose()` → Compares snapshot with current state
3. If changes detected → Creates `ChestAccessLog` → `ChestLogManager.addLog()` → Saves to NBT file

### Important Technical Details

- **Mixin Injection Points**: Uses `@Inject` at `TAIL` of `openHandledScreen` and `HEAD` of `closeScreenHandler` and `onScreenHandlerOpened`
- **Container Content Capture**: For `GenericContainerScreenHandler`, only captures container slots (not player inventory) to avoid false positives
- **NBT Storage**: All logs stored in world directory as `chest_logs.dat`, using compressed NBT format
- **Dimension Tracking**: Uses `World.getRegistryKey().getValue().toString()` for dimension identification
- **Thread Safety**: All operations run on server thread; no concurrent access concerns

### Configuration

- Java 21 required (specified in both build.gradle and fabric.mod.json)
- Gradle 8.14.1 (minimum 8.12)
- Fabric Loader 0.17.2
- Fabric API 0.134.0+1.21.8
- Fabric Loom 1.10.1 for build tooling
- Split environment source sets (separate client/server code)

### API Changes in Minecraft 1.21.8

- NBT methods now return `Optional<T>` instead of primitive/object types
- `NbtCompound.getString(key)` → `NbtCompound.getString(key).orElse(defaultValue)`
- `NbtCompound.getInt(key)` → `NbtCompound.getInt(key).orElse(defaultValue)`
- `NbtCompound.getList(key, type)` → `NbtCompound.getList(key)` (no type parameter)
- `NbtList.getString(index)` → `NbtList.getString(index).orElse(defaultValue)`
- `NbtIo.readCompressed(path)` → `NbtIo.readCompressed(path, null)` (requires NbtSizeTracker)
- `LevelStorage.LevelSave.ROOT` constant removed, use `MinecraftServer.getRunDirectory()` instead
- `Path.toPath()` removed (Path is already a Path)
- `ServerPlayerEntity.closeScreenHandler()` method removed - use tick-based detection or alternative approach
- `ServerPlayerEntity.openHandledScreen()` now returns a value - use `CallbackInfoReturnable<?>` instead of `CallbackInfo` in mixins