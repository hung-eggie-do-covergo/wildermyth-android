package wildermyth;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.worldwalkergames.legacy.context.LegacyViewDependencies;

import java.io.File;

/**
 * Mutes the game while the app is in the background (power button, home). The app marks that with a
 * wm-background file in the game folder; GLFW focus never reaches the game on Android.
 */
final class FocusBridge {
    private static final File FLAG = new File("wm-background");

    private FocusBridge() {}

    static void start() {
        FLAG.delete(); // left over from a crash: start audible
        Thread t = new Thread(() -> {
            boolean background = false;
            while (true) {
                try {
                    Thread.sleep(500);
                    boolean now = FLAG.exists();
                    if (now == background || Gdx.app == null) continue;
                    background = now;
                    Gdx.app.postRunnable(() -> apply(now));
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                    // never take the game down
                }
            }
        }, "wm-focus-bridge");
        t.setDaemon(true);
        t.start();
    }

    /** The game's own lost/regained focus handling, plus its mute: the PC-minded option may say not to. */
    private static void apply(boolean background) {
        try {
            ApplicationListener game = Gdx.app.getApplicationListener();
            if (!background) {
                game.resume(); // ends the "window up" snapshots, the mute with them
                return;
            }
            game.pause();
            LegacyViewDependencies deps = (LegacyViewDependencies) DualScreen.field(DualScreen.field(game, "ui"), "dependencies");
            deps.audioManager.beginSnapshot("UIwindowUpMute", 1.0f, null);
        } catch (Throwable ignored) {
            // not up yet; the next change applies
        }
    }
}
