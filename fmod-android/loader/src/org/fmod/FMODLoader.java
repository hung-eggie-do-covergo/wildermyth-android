package org.fmod;

/**
 * Stands in for fmod-jni's loader, which extracts desktop builds from its jar. On Android the launcher
 * has already loaded FMOD itself under the app's VM, so only the JNI bridge is loaded here.
 */
public class FMODLoader {
    public static String error;

    public static boolean loadNatives() {
        try {
            System.loadLibrary("fmodJNI");
            return true;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }
}
