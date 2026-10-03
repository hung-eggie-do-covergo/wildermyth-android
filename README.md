![Wildermyth on Android](docs/banner.png)

**v0.5** · Unofficial Android launcher for [Wildermyth](https://wildermyth.com) by Worldwalker Games.
Not affiliated with Worldwalker Games or Valve.

> [!IMPORTANT]
> **Bring your own game.** You need your own copy of Wildermyth:
> [Steam](https://store.steampowered.com/app/763890/Wildermyth/),
> [GOG](https://www.gog.com/en/game/wildermyth) or
> [Epic Games Store](https://store.epicgames.com/en-US/p/wildermyth-593344). No game files ship with the app.
> The Steam version gets the most out of it: in-app download, cloud saves, achievements and DLC. Copies from
> other stores load through "Use my game files", which is untested.
>
> **Download and play.** Get the APK from the
> [Releases page](https://github.com/hung-eggie-do-covergo/wildermyth-android/releases) and install it on your
> Thor. On first launch, pick your game files or sign in to Steam to download the game.
>
> **AI disclosure:** This project was built with Claude (Anthropic's AI coding assistant, via Claude Code).
> Claude wrote most of the code and docs. I decided what to build, tested every step on a real Thor, and
> reviewed the results. Read the code before you trust it, and keep backups of your saves.

**Questions or bugs?** [Open an issue](https://github.com/hung-eggie-do-covergo/wildermyth-android/issues).

[Features](#features) · [Pictures](#pictures) · [Dual-Screen](#dual-screen) · [How it works](#how-it-works) ·
[Status and limits](#status-and-limits) · [Building](#building) · [Credits](#credits) · [License](#license)

## TL;DR

Wildermyth is one of my favorite games, and the [AYN Thor](https://www.ayntec.com) is a great handheld.
The game would have been perfect on it, but there's no Android version and the PC version doesn't run on
Android as is.

Hence this app: it runs the PC version of the game on the Thor, with your Steam saves, achievements and DLC.

To bring this amazing game to more people, the app is free and open source. If you haven't played Wildermyth
yet, give it a try: it's on [Steam](https://store.steampowered.com/app/763890/Wildermyth/),
[GOG](https://www.gog.com/en/game/wildermyth) and
[Epic](https://store.epicgames.com/en-US/p/wildermyth-593344).

## Pictures

| First run | Steam sign-in |
|---|---|
| ![First run: use your own game files or download them with Steam](docs/welcome.jpeg) | ![Sign in by scanning a QR code with the Steam app](docs/qr-login.jpeg) |
| **The game, driven by the controller** | **Saves synced after a session** |
| ![Wildermyth's main menu running on the Thor, with the controller's A prompt](docs/main-menu.jpeg) | ![All synced: Play again or close](docs/synced.jpeg) |
| **Both sides changed: you choose** | **Battle, with controller prompts** |
| ![Saves differ: keep this device or use Steam Cloud; the other copy is backed up](docs/conflict.jpeg) | ![A tactical battle on the Thor, with the game's controller hints](docs/battle.jpeg) |

## Dual-Screen

On a dual-screen handheld like the Thor, turn on "Dual screen" on the setup screen (top right). The map
uses the whole top screen, and the hero panels move to the bottom screen. The controller still controls the
game; the bottom screen is for touch.

| Hero sheet | Hero card (tap the name) | Selected tile |
|---|---|---|
| ![The selected hero's abilities on the bottom screen, the map on top](docs/ds-sheet.jpeg) | ![The hero's card dropping down from their name](docs/ds-herocard.jpeg) | ![A selected tile's card: its site and who occupies it](docs/ds-tile.jpeg) |
| **Overview map** | **Threats** | **Message log** |
| ![The campaign map: terrain, fog, threat coins, your parties and the camera frame](docs/ds-map.jpeg) | ![The threats column shown over the sheet](docs/ds-threats.jpeg) | ![The game's message log over the bottom screen](docs/ds-log.jpeg) |

- Hero sheet: abilities, gear, stats, combat, relationships and aspects. Tap an entry for details.
- Overview map: terrain, fog of war, threats and your heroes. Pinch to zoom, drag to pan, tap a tile to
  move the camera there.
- Threats and the message log open with the buttons at the top.

In battle, the bottom screen shows the card for whatever the cursor points at, or the selected hero's sheet
("Sheet"). Undo and Retreat sit above the heroes.

| Info | Sheet | Threats |
|---|---|---|
| ![The card for what the cursor points at, full width on the bottom screen](docs/ds-battle-info.jpeg) | ![The hero sheet during a battle](docs/ds-battle-sheet.jpeg) | ![The foes' column shown over the card](docs/ds-battle-threats.jpeg) |

## Features

- **Install the game from the app.** Pick your own game files, or sign in to Steam and download the game
  (about 3 GB). The download keeps going with the screen off.
- **Sign in with a QR code** from the Steam app. You never type your password.
- **Steam Cloud saves.** Saves download before you play and upload when you quit, so you can switch between
  the Thor and your PC. If both changed, the app asks which to keep and backs up the other.
- **Steam achievements** unlock as you play and sync after each session.
- **DLC** you own on Steam is unlocked.
- **Controller and sound** work out of the box.
- **Dual screen** on handhelds with two screens. See [Dual-Screen](#dual-screen).
- **Touch and controller** both work. Tapping doesn't switch the game to keyboard prompts.
- **Updates.** The app offers new releases when you open it. Turn off with "Updates" on the setup screen.

Tested on the AYN Thor (Snapdragon 8 Gen 2, Android 13). Other Android handhelds with a Snapdragon chip may
work, but haven't been tested.

## How it works

Wildermyth is a Java 8 / libGDX / LWJGL game. The app runs the game's own desktop jar, unmodified, on a
bundled Java 8 runtime, swapping in Android builds of the game's native libraries (graphics, controller and
sound).

| Directory | What it is |
|---|---|
| `amethyst/` | The app (submodule): a fork of Amethyst-Android that boots Wildermyth instead of Minecraft, plus the setup screen, sync flow and download service. |
| `wmcloud/` | Steam in Kotlin: QR sign-in, Steam Cloud pull/push with conflict and mass-delete guards, achievements, DLC ownership, and the game download. |
| `jamepad-android/` | Android build of Jamepad, the game's controller library, over the app's SDL. |
| `fmod-android/` | Android build of the game's FMOD Java bridge, attaching FMOD's threads to ART. |
| `gameagent/` | A Java agent loaded into the game: owned DLC, controller mode with touch, and the second screen's panels and map. |

## Status and limits

- Downloads slow down during the long stretch of small files. That's latency to Steam's servers, not your
  connection.
- Only tested on one device.

## Building

Clone with `--recurse-submodules`. You need the Android SDK, NDK r27 and JDK 17. To rebuild the controller
and sound bridges you also need [Jamepad](https://github.com/libgdx/Jamepad) cloned into `jamepad/`,
[fmod-jni](https://github.com/NateAustin/fmod-jni) cloned into `fmod-jni/`, and the FMOD Studio API 1.10.12
for Android from [fmod.com](https://www.fmod.com/download).

```sh
export ANDROID_NDK_HOME=~/Library/Android/sdk/ndk/27.3.13750724   # your NDK r27
export FMOD_SDK=~/fmodstudioapi11012android/api                     # the SDK's api/ directory
export WILDERMYTH_DIR=~/wildermyth                                  # a game install, for gameagent to compile against
jamepad-android/build.sh && fmod-android/build.sh && gameagent/build.sh  # optional: prebuilt copies are in the app
cd amethyst && ./gradlew :app_pojavlauncher:assembleRelease
```

Release signing reads `~/.config/wildermyth/signing.properties`; without it, build `assembleDebug`.

## Credits

This stands on other people's work. Thank you.

- **[Worldwalker Games](https://worldwalkergames.com)** for Wildermyth. Buy it; this launcher is useless without it.
- **[Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android)** (AngelAuraMC), and
  **[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)** before it: the Java runtime, LWJGL,
  input and renderer plumbing this app is built on (LGPL-3.0).
- **[SilksongAndroid-Achievements](https://github.com/OmegaUwUr/SilksongAndroid-Achievements)** by OmegaUwUr,
  which showed a PC game with its Steam features could feel at home on an Android handheld.
- **[JavaSteam](https://github.com/Longi94/JavaSteam)** for sign-in, Steam Cloud, achievements and the depot download.
- **[fmod-jni](https://github.com/NateAustin/fmod-jni)** by Nate Austin (Apache-2.0), the game's FMOD bridge, rebuilt here for Android; changes are listed in `fmod-android/NOTICE`.
- **[Jamepad](https://github.com/libgdx/Jamepad)**, **[libGDX](https://libgdx.com)**, **[LWJGL](https://www.lwjgl.org)**
  and **[SDL](https://libsdl.org)**.
- **[Mesa](https://mesa3d.org)** for Zink and Turnip.
- **FMOD Studio** by Firelight Technologies Pty Ltd for the game's audio. The APK bundles the FMOD
  Studio API 1.10.12 Android runtime under FMOD's non-commercial licence; this project is free and never
  monetised.
- **[Alegreya](https://github.com/huertatipografica/Alegreya)** by Huerta Tipográfica (SIL OFL 1.1), the setup screen's font.
- **[OkHttp](https://square.github.io/okhttp/)**, **[Bouncy Castle](https://www.bouncycastle.org)**,
  **[ZXing](https://github.com/zxing/zxing)**, **[XZ for Java](https://tukaani.org/xz/java.html)**,
  **[zstd-jni](https://github.com/luben/zstd-jni)**, **[Gson](https://github.com/google/gson)** and
  **[Protocol Buffers](https://protobuf.dev)**.

Wildermyth and its art belong to Worldwalker Games. None of the game's files ship in this app.

## License

This repository's own code (`wmcloud/`, `gameagent/`, `jamepad-android/`, `fmod-android/` scripts and sources)
is [MIT](LICENSE). The app in `amethyst/` is a fork of Amethyst-Android and stays under the
[LGPL-3.0](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/LICENSE). Bundled third-party
components keep their own licences: FMOD under FMOD's EULA, fmod-jni under Apache-2.0
([notice](fmod-android/NOTICE)), and Alegreya under the SIL OFL 1.1.
