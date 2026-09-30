![Wildermyth on Android](docs/banner.png)

Unofficial Android launcher for [Wildermyth](https://wildermyth.com) (Worldwalker Games). Not affiliated with
Worldwalker or Valve. You need your own copy of the game: point the app at your files, or download them with
your Steam account.

- `amethyst/` — the app: a fork of [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) (LGPL-3.0)
  that runs the game's desktop build on Java 8, LWJGL, Zink and Turnip.
- `wmcloud/` — Steam sign-in, Steam Cloud saves, achievements, DLC ownership, game download (JavaSteam).
- `fmod-android/`, `jamepad-android/` — Android builds of the game's sound and controller bridges.
- `dlcagent/` — hands Steam-verified DLC ownership to the game.

FMOD's libraries come from your own FMOD SDK download and must not be redistributed.
