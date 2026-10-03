#!/bin/sh
# Builds the game agent (Java 8) against your Wildermyth install, into the app's assets.
set -e
cd "$(dirname "$0")/.."
# WILDERMYTH_DIR: a game install (wildermyth.jar and lib/); the jars are only compiled against, never shipped.
G=${WILDERMYTH_DIR:?set WILDERMYTH_DIR to a Wildermyth install}
C=amethyst/app_pojavlauncher/src/main/assets/components/wildermyth
O=$(mktemp -d)
javac --release 8 -nowarn -d "$O" -cp "$G/wildermyth.jar:$G/lib/*" \
  gameagent/src/wildermyth/*.java
jar cfm gameagent/wm-gameagent.jar gameagent/manifest.txt -C "$O" .
rm -rf "$O"
cp gameagent/wm-gameagent.jar $C/
(cd $C && cat libgdx.so libgdx-freetype.so libjamepad.so libfmodJNI.so wm-fmodloader.jar wm-gameagent.jar | shasum | cut -c1-12 > version)
