![Wildermyth on Android](docs/banner.png)

**v0.1** · Unofficial Android launcher for [Wildermyth](https://wildermyth.com) by Worldwalker Games.
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
> Claude wrote most of the code and docs. We decided what to build, tested every step on a real Thor, and
> reviewed the results. Read the code before you trust it, and keep backups of your saves.

**Questions or bugs?** [Open an issue](https://github.com/hung-eggie-do-covergo/wildermyth-android/issues).

[Features](#features) · [Pictures](#pictures) · [How it works](#how-it-works) ·
[Status and limits](#status-and-limits) · [Building](#building) · [Credits](#credits) · [License](#license)

## TL;DR

The [AYN Thor](https://www.ayntec.com) is a great handheld, and Wildermyth would have been perfect on it,
but there's no Android version and the PC version doesn't run on Android as is.

Hence this app: it runs the PC version of the game on the Thor, with your Steam saves, achievements and DLC.

## Pictures

| First run | Steam sign-in |
|---|---|
| ![First run: use your own game files or download them with Steam](docs/welcome.jpeg) | ![Sign in by scanning a QR code with the Steam app](docs/qr-login.jpeg) |

## Features

- **Install the game from the app.** Pick your own game files, or sign in to Steam and download the game
  (about 3 GB). The download keeps going with the screen off.
- **Sign in with a QR code** from the Steam app. You never type your password.
- **Steam Cloud saves.** Saves download before you play and upload when you quit, so you can switch between
  the Thor and your PC. If both changed, the app asks which to keep and backs up the other.
- **Steam achievements** unlock as you play and sync after each session.
- **DLC** you own on Steam is unlocked.
- **Controller and sound** work out of the box.

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
| `dlcagent/` | A Java agent that answers the game's DLC checks from Steam-verified ownership. |

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
jamepad-android/build.sh && fmod-android/build.sh                   # optional: prebuilt copies are in the app
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

This repository's own code (`wmcloud/`, `dlcagent/`, `jamepad-android/`, `fmod-android/` scripts and sources)
is [MIT](LICENSE). The app in `amethyst/` is a fork of Amethyst-Android and stays under the
[LGPL-3.0](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/LICENSE). Bundled third-party
components keep their own licences: FMOD under FMOD's EULA, fmod-jni under Apache-2.0
([notice](fmod-android/NOTICE)), and Alegreya under the SIL OFL 1.1.
