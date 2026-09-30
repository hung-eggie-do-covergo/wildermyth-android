#!/bin/sh
# Builds fmod-jni's SWIG bridge for Android arm64 against FMOD 1.10.12, into the app's assets.
set -e
cd "$(dirname "$0")"
N=~/Library/Android/sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/darwin-x86_64/bin
F=/Volumes/NAS/scratch/fmod/x/fmodstudioapi11012android/api
C=../amethyst/app_pojavlauncher/src/main/assets/components/wildermyth
# Every JNI entry point attaches its thread to ART before touching FMOD.
sed -E 's/^(SWIGEXPORT .*JNICALL Java_org_fmod_jni_FMODJNI_.*\{)$/\1 wm_attach_art();/' \
  ../fmod-jni/nativeSwigWrapperLibraries/c_jni_wrapper/fmod_wrap.c | sed '1i\
void wm_attach_art(void);' > fmod_wrap_android.c
# FMOD 1.10's .so files are too old for lld; link against same-named stubs, the real ones load at runtime.
mkdir -p stubs
for l in fmod fmodstudio; do
  $N/llvm-nm -D --defined-only lib$l.so | awk '$2=="T"{print "void "$3"(void){}"}' | grep -vE ' (_init|_fini|JNI_OnLoad)\(' > stubs/$l.c
  $N/aarch64-linux-android21-clang -shared -fPIC -w stubs/$l.c -Wl,-soname,lib$l.so -o stubs/lib$l.so
done
$N/aarch64-linux-android21-clang -shared -fPIC -O2 -w -I $F/lowlevel/inc -I $F/studio/inc \
  fmod_wrap_android.c onload.c -Lstubs -lfmod -lfmodstudio -o libfmodJNI.so
cp libfmodJNI.so $C/
(cd $C && cat libgdx.so libgdx-freetype.so libjamepad.so libfmodJNI.so wm-fmodloader.jar | shasum | cut -c1-12 > version)
