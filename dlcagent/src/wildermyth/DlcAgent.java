package wildermyth;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

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
    }

    private static void set(Class<?> c, String field) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, Boolean.TRUE);
        System.out.println("DlcAgent: " + field + " = true (owned on Steam)");
    }
}
