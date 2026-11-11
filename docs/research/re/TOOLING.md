# RE tooling and device inventory

Status: Task 1 (2026-11-12). Read-only observation only.

## Tooling actually available

| Tool | Source | Used for |
| --- | --- | --- |
| REA MCP (`mcp__rea__*`) | MCP server `rea`, reconnected via `/mcp` | advertised: adb, APK decode, class/method search, native analysis, Frida. Only adb works here (see gaps below) |
| adb 37.0.1 | `C:\platform-tools\adb.exe` | fallback for anything REA does not wrap |
| Claude-in-Chrome, Playwright | plugins | fetching upstream repos/docs for research |

### Verified gaps (Task 3, 2026-11-12)

| REA capability | Result on this Windows machine |
| --- | --- |
| adb: device, package inspect, `pull_adb_package`, logcat, file listing | works |
| `inspect_android_package`, class/method search (JADX) | unavailable: `REA_JADX_MCP_JAR` not set |
| `decode_android_resources` (apktool) | unavailable: apktool not on PATH, `REA_APKTOOL_COMMAND` not set |
| Native analysis of ARM64 ELF (Ghidra) | unavailable: `GHIDRA_INSTALL_DIR` not set, and the Windows Ghidra profile only accepts PE targets |
| `inspect_binary_layout` (pwntools) | unavailable: Linux x64 only |
| Hopper / IDA / Kuna | unavailable (unsupported host or not configured) |
| Frida | not tested |

Workarounds in use, all stdlib or plain CLI: `unzip` for APK inventory, `tools/axml_dump.py` to decode the binary manifest, Python string scans of `.so` and `.dex` files, `dumpsys package`, `uiautomator dump`, `screencap`. Limit: no decompilation or disassembly, so findings stay at manifest, symbol-name and string level. If a task needs method-level understanding, the owner must supply JADX (`jadx-headless-mcp` 0.7.1 jar) or Ghidra 12.1.x; ask before relying on it.

Windows note: adb is a Windows binary, so under Git Bash set `MSYS_NO_PATHCONV=1` for device paths like `/sdcard/...`, and pass `adb pull` destinations as relative paths.

Rules in force (CLAUDE.md §2.2, §5.2): read-only adb (`pm list`, `dumpsys`, `logcat`, `pull` of the app's own APK); back up into `.re-workspace/backups/` before anything that could change data; never uninstall/clear the emulator apps; no committing decompiled code, `.so`, `.dex` or assets.

## Device

| Field | Value |
| --- | --- |
| Serial | ZA222JM8Z6 |
| Model | motorola moto g34 5G (`fogos`) |
| Android | 15 (SDK 35), security patch 2026-09-01 |
| ABIs | arm64-v8a, armeabi-v7a, armeabi |
| Build | user / release-keys, `ro.debuggable=0` (no root, no run-as on other apps) |

Consequence of `ro.debuggable=0` and an unrooted device: other apps' private data (`/data/data/<pkg>`) is not readable. RE of on-disk layout is limited to shared storage (`/sdcard/...`, `Android/data` as far as Android 15 allows through adb shell) and to the APKs themselves.

## Target packages (installed on the device)

| App | Package | Version (code) | Installer | Perms | Flags |
| --- | --- | --- | --- | --- | --- |
| John GBA Lite | `com.johnemulators.johngbalite` | v4.15 (4150) | Play Store | 14 | ALLOW_CLEAR_USER_DATA, LARGE_HEAP |
| MelonDS (Android) | `me.magnum.melonds` | 2.0.1 PS (40) | Play Store | 15 | ALLOW_CLEAR_USER_DATA, ALLOW_BACKUP |
| JoiPlay (main) | `cyou.joiplay.joiplay` | 1.20.410-patreon (120410) | package installer (sideload) | 18 | ALLOW_BACKUP, LARGE_HEAP |
| JoiPlay Ren'Py plugin | `cyou.joiplay.renpy` | 8.1.3_7.6.3_001-patreon (813001) | package installer (sideload) | 10 | ALLOW_BACKUP, LARGE_HEAP |
| JoiPlay RPG Maker plugin | `cyou.joiplay.rpgm` | 1.20.53-patreon (12053) | package installer (sideload) | 9 | ALLOW_BACKUP, LARGE_HEAP |

Extra JoiPlay component found: `cyou.joiplay.runtime.ruffle` (Flash runtime plugin). It shows JoiPlay's plugin model extends beyond the two plugins in scope; note for Task 7 and for the Ruffle line in Section 5.1.

Notes:

- The `-patreon` JoiPlay builds are supporter builds. We observe them as installed; the RE work does not touch billing or licence checks.
- The MelonDS build `2.0.1 PS` (package `me.magnum.melonds`) is the melonDS-Android fork family. "PS" is the `playStore` product flavor (confirmed from source in Task 4).
- Full permission lists, components and intent filters come in the per-app tasks (Tasks 3-7).

## Test fixtures

`DemoGames/` (gitignored, 2.7 GB): GBA Mythic Legends (+ John GBA `.jg0/.js0/.sav`), NDS Sacred Gold (+ melonDS `.ml0/.sav`), Infinite Fusion (RPG Maker XP, `.rxdata`, Android zip). The `.jg0/.js0/.ml0` files are real examples of the state formats Section 7.4 migration must read.
