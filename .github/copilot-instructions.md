# Copilot Instructions for Gamma (Battle Control)

## Architecture Overview

This is a **Java Tower Defense game** (Swing-based GUI) with **refactored modular architecture**. The codebase has shifted from monolithic `Gamma.java` to specialized manager classes handling distinct concerns.

### Core Components

1. **Gamma.java** - Main JPanel entry point; manages game state machine (MAIN_MENU → MAP_SELECT → MODE_SELECT → IN_GAME/PAUSED)
2. **GameManager** - Singleton centralizing all game state: instances (buildings/enemies), resources (power/kromer), wave tracking
3. **GameLoop** - Timing and update logic: delta time calculation, universal timer, wave system updates
4. **RenderSystem** - All rendering: menus, pause screen, game area, UI panel, build previews
5. **InputManager** - Keyboard/mouse event handling: mode toggling (repair/sell/build), pause, UI interactions
6. **BuildingManager** - Build/repair/sell mode state management; build preview data (turret offsets, ranges, buildable cells)
7. **ConfigManager** - Loads and caches JSON configs: buildings, enemies, weapons from `resources/config/`

### Data Flow

```
Input Events → InputManager → BuildingManager/GameLoop → GameManager (state updates)
GameManager (instances) → GameLoop (update routines) → RenderSystem → Screen
ConfigManager ← Gamma.init() (loads at startup)
```

## Key Architectural Patterns

### Singleton with getInstance()
- `GameManager.getInstance()` - ensures single game state instance
- Allows multiple game instances in theory but enforces singularity in practice
- Used for: instances list, resources (power/kromer/wave), config access

### Manager Delegation Pattern
- State moved from `Gamma` static fields → specialized managers (GameManager, GameLoop, etc.)
- `Gamma` retains UI/rendering orchestration and some nested classes (Icon, Turret, etc.)
- BuildingManager still references Gamma for state since Icon/Turret are Gamma inner classes
- **Pattern**: Managers provide `get/set` methods; coordinate via GameManager

### Instance Queue Buffer
```java
GameManager.addInstance() → instanceQueue (buffered)
GameManager.flushInstanceQueue() → instances (actual list)
```
**Why**: Prevents concurrent modification during iteration. Call `flushInstanceQueue()` once per update cycle.

### Range Boost Dynamic Feature
- RadarDish presence checked each update in GameLoop
- If present: `Building.rangeMult = 1.25`, else `1.0`
- Applied to all Building instances each frame

## Project Layout

```
src/
  Gamma.java                 - Main entry, state machine, nested Icon/Turret classes
  GameManager.java           - Central state holder (instances, resources)
  GameLoop.java              - Update logic (timing, wave system, range boost)
  RenderSystem.java          - All rendering (~934 lines)
  InputManager.java          - Event handlers
  BuildingManager.java       - Build/repair/sell modes
  ConfigManager.java         - JSON config loading
  BuildingStats.java         - Data class (from buildings.json)
  EnemyStats.java            - Data class (from enemies.json)
  WeaponStats.java           - Data class (from weapons.json)
  TurretStats.java           - Turret configuration
  Utilities.java             - Image loading, Animation class, helpers

resources/
  config/
    buildings.json           - All building definitions (cost, stats, turrets, weapons)
    enemies.json             - Enemy types and wave patterns
    weapons.json             - Weapon definitions
  images/                    - PNG sprites (.load() helper handles fallback to default.png)
  anims/                     - Animation frames (subfolders per animation: cat_cheese/, explode/, etc.)
  fonts/                     - Custom fonts

lib/
  gson-2.10.1.jar            - Only external dependency (JSON parsing)
```

## Build & Run

- **IDE**: VS Code with Extension Pack for Java
- **Compile**: Automatic via Java Language Server (source path: `src/`, output: `bin/`)
- **Libraries**: `lib/gson-2.10.1.jar` (configured in `.vscode/settings.json`)
- **Entry Point**: `Gamma.main()` (creates JFrame with Gamma JPanel, 60 FPS timer)
- **No build tool** (Maven/Gradle) - direct source compilation

## Game Modes

- **normal** - Standard mode (start with 1000 kromer)
- **survival** - Wave-based (start with 1000 kromer)
- **challenge** - Custom difficulty (start with 1000 kromer)
- **sandbox** - Unlimited resources (start with 999999 kromer)
  - **Pattern**: Mode-specific initialization in `GameManager.initializeKromerForMode(String mode)`

## Critical Conventions & Patterns

### Instance Management
- All game objects (buildings, enemies) inherit from `Instance` (assumed base class)
- Instance lifecycle: `addInstance() → stored in instanceQueue → flushInstanceQueue() → active list`
- Dead instances removed via `GameManager.removeDeadInstances()` (calls `instance.isAlive()`)
- Turrets and Weapons stored as lists in `Instance.turrets` and `Instance.weapons`

### Building Placement & Occupancy
- Grid-based placement checked against `Location.occupancy` (HashSet of Points)
- `GameManager.canPlaceBuilding()` validates bounds and cell occupancy
- Turret offsets stored per building type (loaded from config, used in build preview)

### Resource Management
- **Kromer** (currency) - spent on building placement, gained from selling buildings
- **Power** - generated by PowerPlant, consumed by other buildings (game over if insufficient)
- Both tracked in `GameManager` and updated via `addKromer()`, `addPower()` methods

### Animation System
- `Utilities.Animation` class: frame-based (60 FPS frame duration), loop support
- Animations attached to `Instance.anims` list
- Frame files: `resources/anims/{animName}/{animName}001.png`, `{animName}002.png`, etc.
- Non-existent frame → animation dies and stops

### Config Loading Pattern
```java
ConfigManager cfg = GameManager.getInstance().getConfigManager();
Map<String, BuildingStats> stats = cfg.getAllBuildingStats();
```
- Lazy-loaded once at game startup (idempotent via `loaded` flag)
- All stats parsed via GSON and cached in HashMaps

### Mode Mutual Exclusivity
- Build, repair, and sell modes are mutually exclusive
- `BuildingManager.setRepairMode(true)` → disables sell mode and clears build mode
- Pattern: Always check other modes before enabling

### UI Panel Layout
- Game area: 1560px wide (13/16 of 1920)
- UI panel: 360px wide (3/16 of 1920), right side, shows stats and building list
- Height: 1080px
- UI midpoint constant: `uiMid = 1740` (1560 + 180)

## Common Workflows

### Adding a New Building Type
1. Add entry to `resources/config/buildings.json` (name, cost, width, height, turrets, weapons)
2. Create corresponding Java class extending `Instance` (if special behavior needed)
3. Add to `Gamma.init()` buildingOrder array for UI display order
4. Create Icon in Gamma.init() → automatically added to productive/offensive lists

### Fixing a Build Preview Bug
- Check `RenderSystem.renderBuildMode()` for overlay drawing logic
- Verify turret offsets loaded from `BuildingStats.turretOffsets` in config
- Ensure buildable/unbuildable cells populated in `GameLoop` or input handler

### Debugging Instance Updates
- Game updates happen in `GameLoop.updateGameObjects()` each frame
- Instances call: `update(deltaTime) → routine(deltaTime) → turret.update() → weapon.update() → anim.update()`
- Add logging in `GameManager.flushInstanceQueue()` to catch missing instances

## Known Refactoring Notes

- **Partial Refactoring**: BuildingManager still references Gamma's public fields (buildMode, iconToBuild, etc.) because Icon/Turret are Gamma inner classes
- **Future Work**: Consider extracting Icon/Turret as top-level classes to fully decouple BuildingManager
- **Unused Code**: `unused/` folder contains deprecated MapManager and PlainMap (ignore for now)
- **Wave System**: Delegated to `WaveManager` (not fully examined here, check its update logic)

## Testing & Debugging

- No automated tests present; manual testing via running Gamma.main()
- Debug info available: press **H** to toggle hitbox rendering
- Press **ESC** to pause/resume
- Press **Z** to toggle repair mode; **X** for sell mode
