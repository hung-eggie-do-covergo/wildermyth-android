![Wildermyth on Android](docs/banner.png)

**v0.1** · Unofficial Android launcher for [Wildermyth](https://wildermyth.com) by Worldwalker Games.
Not affiliated with Worldwalker Games or Valve. You need your own copy of the game.

**Get Wildermyth:** [Steam](https://store.steampowered.com/app/763890/Wildermyth/) ·
[GOG](https://www.gog.com/en/game/wildermyth) · [Epic Games Store](https://store.epicgames.com/en-US/p/wildermyth-593344).
The Steam version gets the most from this app: in-app download, Steam Cloud saves, achievements and DLC.
Copies from other stores can be loaded with "Use my game files", but that path is untested.

## TL;DR

We love the [AYN Thor](https://www.ayntec.com) and we love Wildermyth, and there was no proper way to play one
on the other: no Android version, no Linux ARM build, and streaming from a PC is not the same as carrying the
campfire in your pocket. So this app runs the real desktop game on the Thor, with your Steam saves,
achievements and DLC, and a controller that just works.

## Pictures

| First run | Steam sign-in |
|---|---|
| ![First run: use your own game files or download them with Steam](docs/welcome.jpeg) | ![Sign in by scanning a QR code with the Steam app](docs/qr-login.jpeg) |

## What it does

- **One APK, no terminal.** Install, then either point it at your game files or sign in to Steam and let it
  download the game (about 3 GB, a few minutes on good Wi-Fi). The download keeps going with the screen off.
- **Steam sign-in by QR code**, scanned with the Steam app. No password is typed or stored; the app keeps
  only Steam's sign-in token, in its private storage.
- **Steam Cloud saves.** Saves are pulled before you play and pushed when you quit, so the Thor and your PC
  share one legacy. If both changed, the app asks which to keep and backs up the other.
- **Steam achievements** earned in the game are sent to Steam after each session.
- **DLC** you own on Steam is unlocked in the game; DLC you don't own stays locked.
- **Controller, sound and graphics**: the handheld's pad through SDL, the game's FMOD audio, and OpenGL on
  Vulkan (Zink on Turnip).

Tested on the AYN Thor (Snapdragon 8 Gen 2, Android 13). Other arm64 devices with a Turnip-capable Adreno
GPU may work, but are untested.

## How it works

Wildermyth is a Java 8 / libGDX / LWJGL game. The app runs the game's own desktop jar, unmodified, on a
bundled Java 8 runtime, swapping in Android builds of the native pieces the game expects.

| Directory | What it is |
|---|---|
| `amethyst/` | The app (submodule): a fork of Amethyst-Android that boots Wildermyth instead of Minecraft, plus the setup screen, sync flow and download service. |
| `wmcloud/` | Steam in Kotlin: QR sign-in, Steam Cloud pull/push with conflict and mass-delete guards, achievements, DLC ownership, and the game download. |
| `jamepad-android/` | Android build of Jamepad, the game's controller library, over the app's SDL. |
| `fmod-android/` | Android build of the game's FMOD Java bridge, attaching FMOD's threads to ART. |
| `dlcagent/` | A Java agent that answers the game's DLC checks from Steam-verified ownership. |

## Status and limits

- Download speed dips during the long stretch of tiny files (latency to Steam's CDN, not bandwidth).
- Built and tested on one device. Expect rough edges elsewhere.

## Building

Clone with `--recurse-submodules`. Needs the Android SDK and NDK, JDK 17, and your own FMOD Studio API
1.10.12 for Android. Build the bridges with `jamepad-android/build.sh` and `fmod-android/build.sh`, then from
`amethyst/`:

```sh
./gradlew :app_pojavlauncher:assembleRelease
```

Release signing reads `~/.config/wildermyth/signing.properties`; without it, build `assembleDebug`.

## AI disclosure

This project was built with an AI coding assistant (Anthropic's Claude, in Claude Code). The assistant
wrote most of the code, build scripts and this README under human direction; a human chose what to build,
tested each step on the device, and reviewed the results. Treat it like any hobby project: read the code
before trusting it with things you care about, and keep backups of your saves.

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
