#include <jni.h>
#include <pthread.h>
#include <stdlib.h>

/* The desktop JVM resolves JNI_OnLoad through dependencies too and would run libfmod's, which must
   only ever see the app's own VM (it was loaded and initialised there first). */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) { return JNI_VERSION_1_6; }

/* FMOD's Android backend calls into the app's (ART) VM from whatever thread calls it, and the game's
   threads belong to the desktop JVM. Attach each calling thread to ART once; detach when it exits,
   as ART aborts on threads that die attached. Amethyst exports ART's JavaVM* as DALVIK_JAVAVM. */
static JavaVM *art_vm;
static pthread_key_t attached_key;
static pthread_once_t once = PTHREAD_ONCE_INIT;

static void detach(void *unused) { (*art_vm)->DetachCurrentThread(art_vm); }

static void init(void) {
    const char *p = getenv("DALVIK_JAVAVM");
    if (p) art_vm = (JavaVM *) (intptr_t) strtoll(p, NULL, 10);
    pthread_key_create(&attached_key, detach);
}

void wm_attach_art(void) {
    pthread_once(&once, init);
    if (!art_vm || pthread_getspecific(attached_key)) return;
    JNIEnv *env;
    if ((*art_vm)->GetEnv(art_vm, (void **) &env, JNI_VERSION_1_6) == JNI_OK) return;
    if ((*art_vm)->AttachCurrentThread(art_vm, &env, NULL) == JNI_OK)
        pthread_setspecific(attached_key, (void *) 1);
}
