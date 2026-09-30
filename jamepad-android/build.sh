#!/bin/sh
# Builds Jamepad 2.0.20.0's JNI for Android arm64 against the app's SDL2, into the app's assets.
set -e
cd "$(dirname "$0")/.."
N=~/Library/Android/sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/darwin-x86_64/bin
L=amethyst/app_pojavlauncher/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a
C=amethyst/app_pojavlauncher/src/main/assets/components/wildermyth
python3 jamepad-android/jnigen_lite.py jamepad/src/main/java/com/studiohartman/jamepad > jamepad-android/jamepad.cpp
$N/aarch64-linux-android21-clang++ -shared -fPIC -O2 -std=c++11 -fvisibility=hidden -I jamepad/SDL/include \
  jamepad-android/jamepad.cpp -L$L -lSDL2 -llog -o jamepad-android/libjamepad.so
cp jamepad-android/libjamepad.so $C/
(cd $C && cat libgdx.so libgdx-freetype.so libjamepad.so libfmodJNI.so wm-fmodloader.jar wm-dlcagent.jar | shasum | cut -c1-12 > version)
