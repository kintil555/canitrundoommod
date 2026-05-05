# Can It Run Doom? - Minecraft Mod

A Minecraft 1.12.2 Forge mod that adds a **Monitor block** capable of running DOOM inside Minecraft.

## Features

- **Monitor Block** — Craft with 8 Glowstone + 1 Redstone in center
- **In-game DOOM** — Load any DOOM-compatible WAD file and play it on the monitor
- **Multi-block display** — Place in a 4×5 vertical wall for a big screen
- **Input locking** — Shift + Right-click to lock/unlock keyboard to the monitor

## Crafting Recipe

```
G G G
G R G
G G G
```
- `G` = Glowstone
- `R` = Redstone (center)

## How to Use

1. **Place** monitor blocks in a **4 wide × 5 tall** vertical arrangement
2. **Right-click** any block in the wall to power it on
3. A GUI will appear asking for your **DOOM WAD file**
4. Click `[...]` to browse for your WAD, or type the path manually
5. Click **Load** — DOOM will launch!
6. **Shift + Right-click** the monitor to lock your keyboard input to it
7. **Shift + Right-click** again to unlock

## Controls (while locked to monitor)

| Key | DOOM Action |
|-----|-------------|
| W / ↑ | Move Forward |
| S / ↓ | Move Backward |
| A | Strafe Left |
| D | Strafe Right |
| ← → | Turn |
| Ctrl | Fire |
| Space | Use/Open |
| Enter | Confirm |
| Escape | Menu |

## WAD File

You need a DOOM WAD file to play. Legal options:
- **DOOM Shareware** (free) — `doom1.wad` from the free shareware release
- **DOOM** (commercial) — `doom.wad`
- **DOOM II** (commercial) — `doom2.wad`
- **FreeDoom** (free, open source) — https://freedoom.github.io/

Place your WAD in `.minecraft/doom/` for easy access.

## Technical Notes

The mod bundles **doom-generic** (a portable DOOM source port) for Linux, Windows, and macOS.
The engine writes its framebuffer to a temp file which is read each tick and rendered as a texture on the monitor block face.

## Building

Requires JDK 8 and Gradle 4.9.

```bash
./gradlew setupDecompWorkspace
./gradlew build
```

Output: `build/libs/canitrundoom-1.0.0.jar`

## Adding the DOOM Binary

For the mod to actually launch DOOM, you need to place doom-generic binaries:

```
src/main/resources/assets/doommod/bin/doom-linux-x64
src/main/resources/assets/doommod/bin/doom-windows-x64.exe
src/main/resources/assets/doommod/bin/doom-macos-x64
```

Build doom-generic from: https://github.com/ozkl/doomgeneric

The binary must support these args: `-iwad <file> -framebuffer <file> -width <n> -height <n>`
and write RGBA framebuffer data to the specified file, accepting key events on stdin.
