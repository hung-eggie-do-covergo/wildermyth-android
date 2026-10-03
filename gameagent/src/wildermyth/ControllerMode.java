package wildermyth;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.worldwalkergames.legacy.controller.ControllerBoss;

import java.lang.reflect.Field;

/**
 * Keeps the game in controller mode (its button prompts and controller UI) while a pad is mapped. The game
 * switches to mouse-and-keyboard whenever the cursor moves, and on a handheld every touch moves the cursor;
 * touches still work as clicks, they just no longer flip the whole UI.
 */
final class ControllerMode {
    private static Field mouseMode, prevX, prevY, mapped;

    private ControllerMode() {}

    static void start() {
        try {
            mouseMode = field("mouseMode");
            prevX = field("prevMouseX");
            prevY = field("prevMouseY");
            mapped = field("mappedControllers");
        } catch (ReflectiveOperationException e) {
            System.err.println("ControllerMode: not applied: " + e);
            return;
        }
        Thread t = new Thread(() -> {
            while (Gdx.app == null) sleep(); // the agent runs before the game creates its app
            Gdx.app.postRunnable(ControllerMode::frame);
        }, "wm-controller-mode");
        t.setDaemon(true);
        t.start();
    }

    /** Runs before each frame's update: the boss then sees no cursor movement, and stays in controller mode. */
    private static void frame() {
        try {
            ControllerBoss boss = boss();
            if (boss != null && ((Array<?>) mapped.get(boss)).size > 0) {
                prevX.setInt(boss, Gdx.input.getX());
                prevY.setInt(boss, Gdx.input.getY());
                mouseMode.setBoolean(boss, false);
            }
        } catch (Throwable ignored) {
            // the UI is being rebuilt; next frame
        }
        Gdx.app.postRunnable(ControllerMode::frame);
    }

    private static ControllerBoss boss() throws ReflectiveOperationException {
        Object ui = DualScreen.field(Gdx.app.getApplicationListener(), "ui");
        if (ui == null) return null;
        Object deps = DualScreen.field(ui, "dependencies");
        return deps == null ? null : (ControllerBoss) DualScreen.field(deps, "controllers");
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = ControllerBoss.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
