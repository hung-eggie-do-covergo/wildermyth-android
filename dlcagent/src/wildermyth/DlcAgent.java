package wildermyth;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tells the game which DLC Steam says the account owns. The game asks the Steam client, which does not
 * exist on Android, and caches the answer in a static Boolean; pre-filling that cache with the app's
 * Steam-verified answer (-Dwm.ownedDlc=id,id) makes it skip its own check. Unowned DLC is left alone.
 */
public final class DlcAgent {
    private static final String CONTEXT = "com.worldwalkergames.legacy.server.context.ServerDataContext";

    public static void premain(String args) {
        List<String> owned = Arrays.asList(System.getProperty("wm.ownedDlc", "").split(","));
        try {
            Class<?> c = Class.forName(CONTEXT, true, ClassLoader.getSystemClassLoader());
            if (owned.contains("2935580")) set(c, "isDLCInstalledOmenroad");
            if (owned.contains("2139130")) set(c, "isDLCInstalledArmorsAndSkins");
        } catch (Throwable t) {
            System.err.println("DlcAgent: could not apply owned DLC: " + t);
        }
        threadDumpOnRequest();
    }

    /**
     * Android release builds allow no jstack or SIGQUIT, so a hang can't be diagnosed from outside.
     * Creating wm-dump-threads in the game folder makes this write every thread's stack to wm-threads.txt.
     */
    private static void threadDumpOnRequest() {
        File trigger = new File("wm-dump-threads"), out = new File("wm-threads.txt");
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    if (!trigger.delete()) continue;
                    try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
                        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                            Thread th = e.getKey();
                            w.println("\"" + th.getName() + "\" " + th.getState() + (th.isDaemon() ? " daemon" : ""));
                            for (StackTraceElement f : e.getValue()) w.println("    at " + f);
                            w.println();
                        }
                    }
                } catch (Throwable ignored) {
                    // Diagnostics must never take the game down.
                }
            }
        }, "wm-thread-dump");
        t.setDaemon(true);
        t.start();
    }

    private static void set(Class<?> c, String field) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, Boolean.TRUE);
        System.out.println("DlcAgent: " + field + " = true (owned on Steam)");
    }
}
