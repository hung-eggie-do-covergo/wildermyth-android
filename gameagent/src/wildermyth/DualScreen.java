package wildermyth;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.worldwalkergames.legacy.render.componentrenderers.EnvironmentRenderContext;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.worldwalkergames.engine.EID;
import com.worldwalkergames.engine.Entity;
import com.worldwalkergames.legacy.context.LegacyViewDependencies;
import com.worldwalkergames.legacy.game.common.UISelectionState;
import com.worldwalkergames.legacy.game.common.ui.EntityTooltip;
import com.worldwalkergames.legacy.game.mission.ui.BaseBar;
import com.worldwalkergames.legacy.game.mission.ui.PortraitCard;
import com.worldwalkergames.legacy.game.model.Controlled;
import com.worldwalkergames.legacy.game.model.Individual;
import com.worldwalkergames.legacy.options.InterfaceOptions;
import com.worldwalkergames.legacy.ui.detail.AbilitiesDetails;
import com.worldwalkergames.legacy.ui.detail.AspectsDetails;
import com.worldwalkergames.legacy.ui.detail.CharacterSheetPopup;
import com.worldwalkergames.legacy.ui.detail.CombatDetails;
import com.worldwalkergames.legacy.ui.detail.DetailsPanel;
import com.worldwalkergames.legacy.ui.detail.GearDetails;
import com.worldwalkergames.legacy.ui.detail.RelationshipDetails;
import com.worldwalkergames.legacy.ui.detail.StatsDetails;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.ScreenUtils;
import com.worldwalkergames.engine.EntitiesCollection;
import com.worldwalkergames.legacy.game.api.ViewClientAPI;
import com.worldwalkergames.legacy.game.mission.ClientMissionDomain;
import com.worldwalkergames.legacy.LegacyDesktop;
import com.worldwalkergames.ui.popup.PopUpManager;
import com.worldwalkergames.legacy.context.ClientDataContext;
import com.worldwalkergames.legacy.control.ClientContext;
import com.worldwalkergames.legacy.game.campaign.ClientCampaignDomain;
import com.worldwalkergames.legacy.game.campaign.model.Party;
import com.worldwalkergames.legacy.game.campaign.model.Site;
import com.worldwalkergames.legacy.game.campaign.model.Threat;
import com.worldwalkergames.legacy.game.world.model.OverlandTile;
import com.worldwalkergames.ui.AutoSwapDrawable;
import com.worldwalkergames.ui.IMultiDraw;
import com.worldwalkergames.ui.layout.CanvasCell;
import com.worldwalkergames.ui.layout.CanvasGroup;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second-screen bridge: hides campaign HUD widgets in the game, draws them off-screen with the game's own
 * code and streams their pixels to the launcher's panel on another display (-Dwm.ds.port, a loopback
 * socket); taps on the panel come back as clicks. Game state is only touched on the GL thread.
 */
final class DualScreen {
    /** Frames at 30/s, 60/s for a moment after each touch, so drags and taps answer quickly. */
    private static final long TICK_MS = 250, FRAME_MS = 33, FAST_FRAME_MS = 16, FAST_FOR_MS = 600;
    /** Off-screen resolution relative to the window: the panel shows the widgets larger than in the HUD. */
    private static final int SCALE = 2;
    /** Widget ids shared with the panel. */
    private static final int ROSTER = 0, CONSOLE = 1, CONSOLE_TOGGLE = 2, THREATS = 3, SHEET = 4, STATUS = 5,
            PLACE = 6, BAR = 7, UNDO = 8, RETREAT = 9, HOVER = 10, COUNT = 11;
    private static final float ROSTER_MARGIN = 24;
    private static final Pattern TAP = Pattern.compile("\"tap\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)");
    private static final Pattern TOUCH = Pattern.compile("\"touch\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)");
    private static final Pattern SHEET_TAB = Pattern.compile("\"sheetTab\"\\s*:\\s*(\\d+)");
    private static final Pattern SHEET_VIEW = Pattern.compile("\"sheetView\"\\s*:\\s*(\\d+)");
    private static final Pattern MAP_TAP = Pattern.compile("\"mapTap\"\\s*:\\s*(\\d+)");
    private static final Pattern VISIBLE = Pattern.compile("\"visible\"\\s*:\\s*(\\d+)");
    private static final Pattern BAR_SIZE = Pattern.compile("\"barSize\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)");
    private static final Pattern SHEET_SIZE = Pattern.compile("\"sheetSize\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)");
    private static final Pattern SCROLL = Pattern.compile("\"scroll\"\\s*:\\s*\\[\\s*(-?[0-9.]+)");
    private static final Pattern CONSOLE_SIZE = Pattern.compile("\"consoleSize\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)");

    private final int port;
    /** Messages for the panel, length-prefixed on the wire: 'J' + JSON, or 'F' + id + width + height + RGBA. */
    private final BlockingQueue<byte[]> outbox = new ArrayBlockingQueue<>(16);
    private final AtomicBoolean framePending = new AtomicBoolean();
    private volatile boolean connected;
    /** Until when to run at the fast rate; set by touches, which also ask for a frame straight away. */
    private volatile long fastUntil;
    private volatile boolean frameNow;
    /** The console's box on the panel, in panel pixels; 0 until the panel reports it. */
    private volatile int consoleW, consoleH;
    /** The sheet's box on the panel, in panel pixels. */
    private volatile int sheetW, sheetH;
    /** The header's box on the panel, in panel pixels: the game's top bar is drawn at that size. */
    private volatile int barW, barH;
    private String lastError;
    // GL-thread state.
    private String lastState = "";
    private boolean moved;
    private FrameBuffer fbo;
    /** Checksum of the last frame sent per widget (0: an empty one), to skip unchanged frames. */
    private final long[] lastSum = new long[COUNT];
    private final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    /** Bit per widget id the panel is showing; it tells us as overlays open and close. */
    private volatile int visible = -1;
    private GL20 scaledGl, scaledFor;
    /**
     * First actor on the HUD stage, so it draws before everything else each frame: hides the moved widgets
     * even in the frame where the HUD rebuilds them (option changes, the console toggle).
     */
    private final Actor guard = new Actor() {
        @Override
        public void draw(Batch batch, float parentAlpha) {
            Hud hud = connected ? find() : null;
            if (hud == null) return;
            hud.moveOut(consoleW / (float) SCALE, consoleH / (float) SCALE);
            hideSelectionTooltip(hud);
            // A one-frame flag: catch it here, every frame; the sheet rebuilds on the next tick.
            if (UISelectionState.in(hud.game.entities).stateHasChanged) sheet.dirty = true;
        }
    };
    private Sheet sheet;
    private final OverviewMap map = new OverviewMap();

    /** The selection tooltip (top right in the HUD) opens from the hero's name on the panel now. */
    private static void hideSelectionTooltip(Hud hud) {
        try {
            Object tip = field(hud.game.deps.tooltipManager, "currentTip");
            if (tip instanceof EntityTooltip) ((Actor) tip).setVisible(false);
        } catch (ReflectiveOperationException ignored) {
            // no tooltip manager we know: leave it
        }
    }

    /** The HUD plus our character sheet, which is built once per campaign and lives on the HUD stage. */
    private Hud find() {
        Hud h = Hud.find();
        if (h == null) return null;
        if (sheet == null || sheet.game.deps != h.game.deps || sheet.game.entities != h.game.entities) sheet = new Sheet(h.game);
        h.sheet = sheet.table;
        h.status = sheet.status;
        h.place = sheet.place;
        h.hover = sheet.hoverShown ? sheet.hover : null; // nothing hovered: an empty frame clears the panel's card
        h.bar = sheet.bar;
        return h;
    }

    private DualScreen(int port) {
        this.port = port;
    }

    static void start() {
        int port = Integer.getInteger("wm.ds.port", 0);
        if (port <= 0) return;
        Thread t = new Thread(new DualScreen(port)::run, "wm-dualscreen");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        while (true) {
            try (Socket s = new Socket(InetAddress.getByName("127.0.0.1"), port)) {
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                lastState = "";
                java.util.Arrays.fill(lastSum, -1); // a new panel has none of our frames
                post(map::reset);
                connected = true;
                Thread reader = new Thread(() -> read(s), "wm-dualscreen-in");
                reader.setDaemon(true);
                reader.start();
                long nextState = 0, nextFrame = 0;
                while (!s.isClosed()) {
                    long now = System.currentTimeMillis();
                    if (now >= nextState) {
                        post(this::snapshot);
                        nextState = now + TICK_MS;
                    }
                    // One frame in flight at most: a slow GL thread drops frames instead of queueing them.
                    boolean fast = now < fastUntil;
                    if ((now >= nextFrame || frameNow) && Gdx.app != null && framePending.compareAndSet(false, true)) {
                        frameNow = false;
                        post(this::frame);
                        nextFrame = now + (fast ? FAST_FRAME_MS : FRAME_MS);
                    }
                    byte[] msg = outbox.poll(fast ? 4 : FAST_FRAME_MS, TimeUnit.MILLISECONDS);
                    if (msg == null) continue;
                    out.writeInt(msg.length);
                    out.write(msg);
                    out.flush();
                }
            } catch (Throwable t) {
                // The panel is optional: on any failure, give the HUD back and try again.
                if (!String.valueOf(t).equals(lastError)) System.err.println("DualScreen: " + (lastError = String.valueOf(t)));
            }
            connected = false;
            post(this::snapshot);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void read(Socket s) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("\"tap\"") || line.contains("\"touch\"") || line.contains("\"scroll\"")
                        || line.contains("\"sheet")) {
                    fastUntil = System.currentTimeMillis() + FAST_FOR_MS;
                    frameNow = true;
                }
                Matcher m = TAP.matcher(line);
                if (m.find()) {
                    int id = Integer.parseInt(m.group(1));
                    float x = Float.parseFloat(m.group(2)), y = Float.parseFloat(m.group(3));
                    post(() -> tap(id, x, y));
                }
                m = TOUCH.matcher(line);
                if (m.find()) {
                    int id = Integer.parseInt(m.group(1)), action = Integer.parseInt(m.group(2));
                    float x = Float.parseFloat(m.group(3)), y = Float.parseFloat(m.group(4));
                    post(() -> touch(id, action, x, y));
                }
                m = SHEET_TAB.matcher(line);
                if (m.find()) {
                    int t = Integer.parseInt(m.group(1));
                    post(() -> { if (sheet != null) sheet.select(t); });
                }
                m = SHEET_VIEW.matcher(line);
                if (m.find()) {
                    int v = Integer.parseInt(m.group(1));
                    post(() -> { if (sheet != null) sheet.showDetail(v); });
                }
                m = MAP_TAP.matcher(line);
                if (m.find()) {
                    int tile = Integer.parseInt(m.group(1));
                    post(() -> map.tap(tile));
                }
                m = VISIBLE.matcher(line);
                if (m.find()) {
                    visible = Integer.parseInt(m.group(1));
                    java.util.Arrays.fill(lastSum, -1); // newly shown widgets need a fresh frame
                }
                m = BAR_SIZE.matcher(line);
                if (m.find()) {
                    barW = Integer.parseInt(m.group(1));
                    barH = Integer.parseInt(m.group(2));
                }
                m = SHEET_SIZE.matcher(line);
                if (m.find()) {
                    sheetW = Integer.parseInt(m.group(1));
                    sheetH = Integer.parseInt(m.group(2));
                }
                m = SCROLL.matcher(line);
                if (m.find()) {
                    float dy = Float.parseFloat(m.group(1));
                    post(() -> scrollConsole(dy));
                }
                m = CONSOLE_SIZE.matcher(line);
                if (m.find()) {
                    consoleW = Integer.parseInt(m.group(1));
                    consoleH = Integer.parseInt(m.group(2));
                }
            }
        } catch (Throwable ignored) {
            // Socket closed; run() reconnects.
        }
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    private static void post(Runnable r) {
        if (Gdx.app == null) return;
        Gdx.app.postRunnable(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                System.err.println("DualScreen: " + t);
            }
        });
    }

    /** Keeps the moved widgets out of the HUD while connected, and gives them back when not. */
    private void snapshot() {
        Hud hud = find();
        if (hud != null && moved && !connected) {
            guard.remove();
            hud.restoreEdgePan();
            hud.rebuild(); // the HUD's own layout, as built
        }
        moved = connected && hud != null;
        if (!connected) return;
        if (hud != null) {
            hud.moveOut(consoleW / (float) SCALE, consoleH / (float) SCALE);
            Group root = hud.roster.getStage().getRoot();
            if (!root.getChildren().contains(guard, true)) root.addActorAt(0, guard);
            if (!root.getChildren().contains(sheet.table, true)) root.addActor(sheet.table);
            if (!root.getChildren().contains(sheet.status, true)) root.addActor(sheet.status);
            if (!root.getChildren().contains(sheet.place, true)) root.addActor(sheet.place);
            if (!root.getChildren().contains(sheet.hover, true)) root.addActor(sheet.hover);
            if (!root.getChildren().contains(sheet.bar, true)) root.addActor(sheet.bar);
            // At the HUD's own size, so its pattern is the HUD's; area() crops a piece the header's shape.
            if (hud.topBar != null) sheet.bar.setSize(hud.topBar.getWidth(), hud.topBar.getHeight());
            sheet.fit(sheetW, sheetH);
            sheet.update(hud.game);
            if (hud.game.campaign != null) map.update(hud.game.campaign, this::send, outbox::offer);
        }
        String sheetState = hud == null ? null : sheet.state();
        // A story event or other popup over the map: the bottom screen rests (idle logo) until it closes.
        String state = hud == null || hud.popupOpen() ? "{\"campaign\":false}"
                : "{\"campaign\":true,\"battle\":" + (hud.game.campaign == null) + ",\"console\":" + hud.consoleShown() + (sheetState == null ? "" : ",\"sheet\":" + sheetState)
                + "}";
        if (!state.equals(lastState)) {
            lastState = state;
            send(state);
        }
    }

    /**
     * Draws the hidden widgets with the HUD's own camera and passes into an off-screen buffer at SCALE
     * times the window size, so they come out as the game draws them but sharper; sends their pixels.
     */
    private void frame() {
        framePending.set(false);
        Hud hud = connected ? find() : null;
        Stage stage = hud == null ? null : hud.roster.getStage();
        if (stage == null) return;
        int w = Gdx.graphics.getBackBufferWidth() * SCALE, h = Gdx.graphics.getBackBufferHeight() * SCALE;
        if (fbo == null || fbo.getWidth() != w || fbo.getHeight() != h) {
            if (fbo != null) fbo.dispose();
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        }
        // Clipping (portraits, scroll panes) uses glScissor in window pixels: scale it to the buffer.
        GL20 gl = Gdx.gl, gl20 = Gdx.gl20;
        GL30 gl30 = Gdx.gl30;
        GL20 scaled = scaledScissors(gl);
        Gdx.gl = Gdx.gl20 = scaled;
        if (gl30 != null && scaled instanceof GL30) Gdx.gl30 = (GL30) scaled;
        fbo.begin();
        // Battle portraits render through buffers of their own, whose end() rebinds "the screen":
        // make that our buffer while we capture, or the rest of the portrait lands on the real screen.
        int[] screens = redirectScreen(fbo.getFramebufferHandle());
        try {
            Actor[] widgets = hud.widgets();
            int shown = visible;
            for (int id = 0; id < widgets.length; id++) {
                if ((shown & (1 << id)) == 0) continue; // off the panel right now: not worth a capture
                int[] r = widgets[id] == null || (id == CONSOLE && !hud.consoleShown()) ? new int[4]
                        : pixelBounds(area(widgets[id], id), stage.getCamera(), w, h);
                if (r[2] <= 0 || r[3] <= 0) { // nothing to show: an empty frame clears the panel's copy
                    if (lastSum[id] == 0) continue;
                    lastSum[id] = 0;
                    byte[] msg = new byte[10];
                    msg[0] = 'F';
                    msg[1] = (byte) id;
                    outbox.offer(msg);
                    continue;
                }
                Gdx.gl.glClearColor(0, 0, 0, 0);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                float alpha = widgets[id].getColor().a; // battle widgets are kept transparent in the HUD
                widgets[id].getColor().a = 1;
                if (id == ROSTER && hud.portraitBackdrops != null) draw(hud.portraitBackdrops, stage); // selection sparkle
                draw(widgets[id], stage);
                widgets[id].getColor().a = alpha;
                byte[] px = ScreenUtils.getFrameBufferPixels(r[0], r[1], r[2], r[3], true);
                crc.reset();
                crc.update(px, 0, px.length);
                long sum = crc.getValue() ^ ((long) r[2] << 32) ^ ((long) r[3] << 48) | 1;
                if (sum == lastSum[id]) continue; // unchanged: the panel already has it
                lastSum[id] = sum;
                byte[] msg = new byte[10 + px.length];
                msg[0] = 'F';
                msg[1] = (byte) id;
                putInt(msg, 2, r[2]);
                putInt(msg, 6, r[3]);
                System.arraycopy(px, 0, msg, 10, px.length);
                outbox.offer(msg); // frames are droppable
            }
        } finally {
            restoreScreen(screens);
            fbo.end();
            Gdx.gl = gl;
            Gdx.gl20 = gl20;
            Gdx.gl30 = gl30;
        }
    }

    /** The framebuffer classes whose end() binds a static "default" framebuffer: the game's and libGDX's. */
    private static final String[] SCREEN_HOLDERS = {"com.worldwalkergames.legacy.render.NiceFrameBuffer",
            "com.badlogic.gdx.graphics.glutils.GLFrameBuffer"};

    /** Points each holder's default framebuffer at {@code handle}; returns the old values (-1: not found). */
    private static int[] redirectScreen(int handle) {
        int[] old = new int[SCREEN_HOLDERS.length];
        for (int i = 0; i < SCREEN_HOLDERS.length; i++) {
            try {
                Field f = Class.forName(SCREEN_HOLDERS[i]).getDeclaredField("defaultFramebufferHandle");
                f.setAccessible(true);
                old[i] = f.getInt(null);
                f.setInt(null, handle);
            } catch (ReflectiveOperationException | RuntimeException e) {
                old[i] = -1;
            }
        }
        return old;
    }

    private static void restoreScreen(int[] old) {
        for (int i = 0; i < SCREEN_HOLDERS.length; i++) {
            if (old[i] < 0) continue;
            try {
                Field f = Class.forName(SCREEN_HOLDERS[i]).getDeclaredField("defaultFramebufferHandle");
                f.setAccessible(true);
                f.setInt(null, old[i]);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // nothing to restore
            }
        }
    }

    /** One widget the way its parent would draw it: the HUD's multi-pass order, or plain scene2d. */
    private static void draw(Actor a, Stage stage) {
        Batch batch = stage.getBatch();
        ShaderProgram shader = batch.getShader();
        batch.setShader(null); // a story screen can leave its own (e.g. distance-field text) shader on the shared batch
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.setColor(1, 1, 1, 1);
        batch.begin();
        if (a instanceof IMultiDraw) {
            IMultiDraw d = (IMultiDraw) a;
            d.draw_step1_background(batch, 1);
            d.draw_step2_misc(batch, 1);
            d.draw_step3_text(batch, 1);
        } else { // a plain actor draws at its parent's coordinates: put it where it is on the stage
            Vector2 p = a.getParent() == null ? new Vector2(a.getX(), a.getY())
                    : a.getParent().localToStageCoordinates(new Vector2(a.getX(), a.getY()));
            float x = a.getX(), y = a.getY();
            a.setPosition(p.x, p.y);
            a.draw(batch, 1);
            a.setPosition(x, y);
        }
        batch.end();
        batch.setShader(shader);
    }

    /** {@code gl} with glScissor's rectangle multiplied by SCALE; every other call passes straight through. */
    private GL20 scaledScissors(GL20 gl) {
        if (scaledFor != gl) {
            Set<Class<?>> types = new LinkedHashSet<>();
            for (Class<?> c = gl.getClass(); c != null; c = c.getSuperclass()) types.addAll(Arrays.asList(c.getInterfaces()));
            scaledGl = (GL20) Proxy.newProxyInstance(gl.getClass().getClassLoader(), types.toArray(new Class<?>[0]), (proxy, m, args) -> {
                if (m.getName().equals("glScissor"))
                    for (int i = 0; i < 4; i++) args[i] = (Integer) args[i] * SCALE;
                try {
                    return m.invoke(gl, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
            scaledFor = gl;
        }
        return scaledGl;
    }

    /**
     * What we capture of a widget, in stage coordinates {x, y, width, height}: its bounds; for the roster,
     * its cards plus a margin, since badges and out-of-action cards stick out past their edges.
     */
    private float[] area(Actor a, int id) {
        if (id == BAR && barW > 0) { // the left end of the bar, in the header's proportions
            Vector2 lo = a.localToStageCoordinates(new Vector2(0, 0));
            return new float[]{lo.x, lo.y, Math.min(a.getWidth(), a.getHeight() * barW / barH), a.getHeight()};
        }
        if (id == SHEET) { // one column of the sheet's panel
            sheet.table.validate(); // a newly opened tab has no layout yet: its column would measure 0 x 0
            Actor c = sheet.column();
            Vector2 lo = c.localToStageCoordinates(new Vector2(0, 0));
            return new float[]{lo.x, lo.y, c.getWidth(), c.getHeight()};
        }
        float x0 = 0, y0 = 0, x1 = a.getWidth(), y1 = a.getHeight();
        if (id == ROSTER || id == THREATS) { // the cards, not the full-height column they are laid out in
            x0 = y0 = Float.MAX_VALUE;
            x1 = y1 = -Float.MAX_VALUE;
            Array<Actor> children = ((Group) a).getChildren();
            for (int i = 0; i < children.size; i++) {
                Actor c = children.get(i);
                if (!c.isVisible()) continue;
                x0 = Math.min(x0, c.getX());
                y0 = Math.min(y0, c.getY());
                x1 = Math.max(x1, c.getX() + c.getWidth());
                y1 = Math.max(y1, c.getY() + c.getHeight());
            }
            if (x0 > x1) return new float[4];
            x0 -= ROSTER_MARGIN;
            y0 -= ROSTER_MARGIN;
            x1 += ROSTER_MARGIN;
            y1 += ROSTER_MARGIN;
        }
        Vector2 lo = a.localToStageCoordinates(new Vector2(x0, y0));
        return new float[]{lo.x, lo.y, x1 - x0, y1 - y0};
    }

    /** A stage rectangle in buffer pixels (origin bottom-left, as glReadPixels wants), clamped. */
    private static int[] pixelBounds(float[] r, Camera cam, int w, int h) {
        Vector3 p0 = cam.project(new Vector3(r[0], r[1], 0), 0, 0, w, h);
        Vector3 p1 = cam.project(new Vector3(r[0] + r[2], r[1] + r[3], 0), 0, 0, w, h);
        int x0 = Math.max(0, (int) Math.floor(Math.min(p0.x, p1.x))), y0 = Math.max(0, (int) Math.floor(Math.min(p0.y, p1.y)));
        int x1 = Math.min(w, (int) Math.ceil(Math.max(p0.x, p1.x))), y1 = Math.min(h, (int) Math.ceil(Math.max(p0.y, p1.y)));
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    /** A tap on widget {@code id}, as fractions of its image (y down), replayed as a click on that widget. */
    private void tap(int id, float fx, float fy) {
        Hud hud = find();
        Stage stage = hud == null ? null : hud.roster.getStage();
        Actor a = stage == null || id < 0 || id >= COUNT ? null : hud.widgets()[id];
        if (a == null || hud.popupOpen()) return;
        if (id == CONSOLE_TOGGLE) {
            hud.toggleConsole();
            return;
        }
        float[] r = area(a, id);
        float sx = r[0] + fx * r[2], sy = r[1] + (1 - fy) * r[3];
        // Straight to the widget's own actor under the finger: asking the stage would hand the click to
        // whatever covers that spot in the HUD instead.
        a.setVisible(true); // hidden actors are skipped by hit tests; the guard hides it again
        Vector2 local = a.stageToLocalCoordinates(new Vector2(sx, sy));
        Actor target = a.hit(local.x, local.y, true);
        if (id == THREATS) { // the HUD ignores clicks on threats; Ctrl+F1..F4 select them: do that
            for (Actor c = target; c != null && c != a; c = c.getParent())
                if (c instanceof PortraitCard) {
                    UISelectionState.in(hud.game.entities).selectEntity(((PortraitCard) c).getEntityId());
                    hud.game.api.stopTime();
                    break;
                }
        } else if (target != null) {
            fire(target, stage, InputEvent.Type.touchDown, sx, sy);
            fire(target, stage, InputEvent.Type.touchUp, sx, sy);
        }
        a.setVisible(false);
    }

    private static void fire(Actor target, Stage stage, InputEvent.Type type, float sx, float sy) {
        InputEvent e = Pools.obtain(InputEvent.class);
        e.setType(type);
        e.setStage(stage);
        e.setStageX(sx);
        e.setStageY(sy);
        e.setPointer(0);
        e.setButton(Input.Buttons.LEFT);
        target.fire(e);
        Pools.free(e);
    }

    /** A drag on the panel's log, as a fraction of its height (down = positive), scrolls the game's log. */
    private void scrollConsole(float dy) {
        Hud hud = find();
        if (hud == null) return;
        try {
            ScrollPane sp = (ScrollPane) field(hud.console, "textScroll");
            sp.setScrollY(sp.getScrollY() - dy * hud.console.getHeight()); // finger down: back to older lines
            sp.updateVisualScroll();
        } catch (ReflectiveOperationException ignored) {
            // not a console we know; nothing to scroll
        }
    }

    private void send(String json) {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[b.length + 1];
        msg[0] = 'J';
        System.arraycopy(b, 0, msg, 1, b.length);
        if (!outbox.offer(msg)) { // a stalled panel resyncs instead of lagging behind
            outbox.clear();
            lastState = "";
            map.reset();
        }
    }

    /**
     * A finger on widget {@code id} (0 down, 1 move, 2 up, as fractions of its image), replayed as the same
     * pointer on the stage, so scroll panes drag and buttons press as they would under a mouse.
     */
    private void touch(int id, int action, float fx, float fy) {
        Hud hud = find();
        Stage stage = hud == null ? null : hud.roster.getStage();
        Actor a = stage == null || id < 0 || id >= COUNT ? null : hud.widgets()[id];
        if (a == null || hud.popupOpen()) return;
        float[] r = area(a, id);
        float sx = r[0] + fx * r[2], sy = r[1] + (1 - fy) * r[3];
        // Straight to the widget, like tap(): moving the stage's pointer would unhover the HUD's
        // controller-mode default action and make its prompt flicker.
        a.setVisible(true); // hidden actors are skipped by hit tests; the guard hides it again
        if (action == 0) {
            Vector2 local = a.stageToLocalCoordinates(new Vector2(sx, sy));
            touchTarget = a.hit(local.x, local.y, true);
        }
        if (touchTarget != null)
            fire(touchTarget, stage, action == 0 ? InputEvent.Type.touchDown
                    : action == 1 ? InputEvent.Type.touchDragged : InputEvent.Type.touchUp, sx, sy);
        if (action == 2) touchTarget = null;
        a.setVisible(false);
    }

    /** The actor a finger went down on; its drags and lift go there too, as with the stage's touch focus. */
    private Actor touchTarget;


    /**
     * The game's character-sheet panels for the selected hero (or the last one, while a tile is selected).
     * CharacterSheetPopup itself is a full-screen modal; its panels are not. The panel draws the name,
     * tabs and Back itself, large for touch; we stream one column at a time: the list, or its detail.
     */
    private static final class Sheet {
        private static final String[] TABS = {"characterSheet.abilitiesTab", "characterSheet.gearTab",
                "characterSheet.statsTab", "characterSheet.combatTab", "characterSheet.relationshipsTab",
                "characterSheet.aspectsTab"};
        /** Width of the two-column panel in stage units; each column is about half. */
        private static final float WIDTH = 1060;
        final Game game;
        final Table table;
        private final LegacyViewDependencies deps;
        private final DetailsPanel[] panels;
        /** The game's selection tooltip for the hero (the HUD's top-right card); the panel opens it on demand. */
        final EntityTooltip status;
        /** While a tile, site or threat is selected, its card (the same tooltip) replaces the sheet. */
        final EntityTooltip place;
        /** In battle, what the cursor points at (the HUD's hover tooltip), shown on the panel instead. */
        final EntityTooltip hover;
        private EID hoverOf;
        boolean hoverShown;
        /** The game's own top-bar art (behind "Chapter Three ..." in the HUD), for the panel's header. */
        final BaseBar bar;
        private EID placeOf;
        private boolean placeMode;
        private final Cell<Actor> panelCell;
        private int tab;
        /** 0: the list column, 1: the detail column. */
        volatile int view;
        private EID hero;
        boolean dirty;
        private long lastRebuild;

        Sheet(Game game) {
            this.game = game;
            deps = game.deps;
            panels = new DetailsPanel[]{new AbilitiesDetails(deps), new GearDetails(deps), new StatsDetails(deps, false),
                    new CombatDetails(deps), new RelationshipDetails(deps), new AspectsDetails(deps)};
            EntityTooltip.Mode mode = game.campaign == null ? EntityTooltip.Mode.mission : EntityTooltip.Mode.overland;
            status = new EntityTooltip(deps, mode);
            place = new EntityTooltip(deps, mode);
            place.setVisible(false);
            hover = new EntityTooltip(deps, mode);
            hover.setVisible(false);
            bar = new BaseBar(deps, false);
            bar.setVisible(false);
            table = new Table(deps.skin);
            table.setVisible(false);
            table.setSize(WIDTH, WIDTH * 0.5f);
            @SuppressWarnings("unchecked")
            Cell<Actor> cell = (Cell<Actor>) (Cell<?>) table.add(panels[0]).grow();
            status.setVisible(false);
            panelCell = cell;
        }

        /** The panel's box is w x h pixels: size the panel so one column fills it at our render scale. */
        void fit(int w, int h) {
            if (w > 0 && h > 0) {
                table.setSize(WIDTH, WIDTH * 0.5f * h / w);
                aspect = h / (float) w;
            }
        }

        /** Height over width of the panel's box. */
        private float aspect;

        /**
         * Wider than a sheet column: the card's fonts are larger styles, and the less the panel magnifies
         * the game's bitmap glyphs, the sharper they look.
         */
        private final float cardWidth = 800;

        /**
         * A tooltip card at a sheet column's width, so the game wraps its text instead of one long line,
         * and at least the box's height, so its parchment fills the whole box.
         */
        private final Actor placeFiller = new Actor(), statusFiller = new Actor(), hoverFiller = new Actor();

        /** The hero's card is a dropdown under the name: narrower, and only as tall as its content. */
        private static final float DROPDOWN_WIDTH = 440;

        private void layOut(EntityTooltip card) {
            boolean dropdown = card == status;
            float width = dropdown ? DROPDOWN_WIDTH : cardWidth;
            try { // the parchment is on an inner panel sized to its content: let it fill, content on top
                Table body = (Table) field(card, "tooltipBody");
                Cell<?> cell = card.getCell(body);
                if (cell != null) cell.grow();
                // An empty last row takes the extra height, so the game's rows stay packed at the top.
                Actor filler = card == place ? placeFiller : card == hover ? hoverFiller : statusFiller;
                if (!dropdown && !body.getChildren().contains(filler, true)) {
                    body.row();
                    body.add(filler);
                }
                for (Cell<?> c : body.getCells()) c.expand(c.getExpandX() != 0, c.getActor() == filler);
                card.top();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // a tooltip laid out differently: keep the game's own sizing
            }
            card.setWidth(width);
            card.invalidate();
            card.validate();
            // Never taller than the window, or the capture loses its top: widen until it fits.
            float limit = Gdx.graphics.getHeight() * 0.95f;
            while (card.getPrefHeight() > limit && width < Gdx.graphics.getWidth() * 0.95f) {
                width += 120;
                card.setWidth(width);
                card.invalidate();
                card.validate();
            }
            card.setHeight(dropdown ? card.getPrefHeight() : Math.max(card.getPrefHeight(), width * aspect));
            card.validate();
        }

        void select(int t) {
            if (t < 0 || t >= panels.length) return;
            tab = t;
            view = 0;
            panelCell.setActor(panels[t]);
            panels[t].setTarget(entity(), null, null);
            panels[t].stateChanged();
        }

        void showDetail(int v) {
            view = v;
        }

        /** The column on show, or the whole panel when it has no detail column. */
        Actor column() {
            try {
                Actor right = (Actor) field(panels[tab], "rightScroll");
                if (right instanceof ScrollPane && !lightened.contains(right)) { // dark parchment, dark text: lighten it
                    ScrollPane sp = (ScrollPane) right;
                    sp.setStyle(deps.skin.get("lightDialogPanel", ScrollPane.ScrollPaneStyle.class));
                    lightened.add(right);
                }
                return view == 1 && right != null ? right : (Actor) field(panels[tab], "leftScroll");
            } catch (ReflectiveOperationException e) {
                return table;
            }
        }

        private final java.util.Set<Actor> lightened = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

        private Entity entity() {
            return hero == null ? null : game.entities.entity(hero);
        }

        /** What a selected non-hero is, for the header's small caps line. */
        private static String kind(Entity e) {
            if (e == null) return "Selected";
            if (e.contains(Threat.class)) return "Threat";
            if (e.contains(Site.class)) return "Site";
            if (e.contains(Party.class)) return "Party";
            if (e.contains(OverlandTile.class)) return "Tile";
            if (e.contains(Individual.class)) return Controlled.isControlledByHuman(e.parentCollection, e) ? "Hero" : "Foe";
            return "Selected";
        }

        /** For the panel's header and tab row; null with no hero to show. */
        String state() {
            if (placeMode) return "{\"place\":" + quote(kind(game.entities.entity(placeOf)))
                    + ",\"placeName\":" + quote(deps.gameStrings.bestName(game.entities, placeOf)) + "}";
            if (hero == null) return null;
            StringBuilder b = new StringBuilder("{\"name\":").append(quote(deps.gameStrings.bestName(game.entities, hero)))
                    .append(",\"tab\":").append(tab).append(",\"view\":").append(view).append(",\"tabs\":[");
            for (int i = 0; i < TABS.length; i++) b.append(i > 0 ? "," : "").append(quote(deps.gameStrings.ui(TABS[i])));
            return b.append("]}").toString();
        }

        /** Follows the selection; rebuilds the open tab when the game says state changed (at most ~3/s). */
        void update(Game d) {
            UISelectionState sel = UISelectionState.in(d.entities);
            EID h = game.campaign == null ? sel.selectionHover() : null;
            Entity he = h == null ? null : d.entities.entity(h);
            hoverShown |= he != null; // keeps the last card once the cursor moves off
            if (he != null && (!h.equals(hoverOf) || dirty)) {
                hover.setCharacter(he);
                hoverOf = h;
            }
            if (hoverShown) layOut(hover);
            EID id = sel.selectedEntity();
            Entity e = id == null ? null : d.entities.entity(id);
            boolean isHero = e != null && e.contains(Individual.class) && CharacterSheetPopup.canView(deps, e);
            // A card for places on the map, or anything but a hero in a battle (a foe, a tile); story events
            // and the like keep the hero's sheet up.
            placeMode = e != null && !isHero && (game.campaign == null || e.contains(OverlandTile.class)
                    || e.contains(Site.class) || e.contains(Threat.class) || e.contains(Party.class));
            if (placeMode && (!id.equals(placeOf) || dirty)) {
                place.setCharacter(e);
                placeOf = id;
            }
            layOut(place); // cheap, and the box size may have just arrived
            layOut(status);
            if (!isHero) id = sel.recentHero;
            if (id != null && !id.equals(hero)) {
                hero = id;
                view = 0;
                panels[tab].setTarget(entity(), null, null);
                status.setCharacter(entity());
                layOut(status);
                dirty = false;
            } else if (dirty && System.currentTimeMillis() - lastRebuild > 300) {
                panels[tab].stateChanged();
                status.setCharacter(entity());
                layOut(status);
                lastRebuild = System.currentTimeMillis();
                dirty = false;
            }
        }
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    /** What the HUD widgets read from, on the campaign map or in a battle. */
    static final class Game {
        final LegacyViewDependencies deps;
        final EntitiesCollection entities;
        final ViewClientAPI api;
        /** The campaign, for the overview map; null in a battle. */
        final ClientCampaignDomain campaign;

        Game(LegacyViewDependencies deps, EntitiesCollection entities, ViewClientAPI api, ClientCampaignDomain campaign) {
            this.deps = deps;
            this.entities = entities;
            this.api = api;
            this.campaign = campaign;
        }
    }

    /** The campaign HUD widgets we move, resolved fresh each time because the HUD rebuilds them. */
    private static final class Hud {
        Game game;
        Object gameHud;
        /** The particle layer used only for portrait backdrops (CampaignHud and MissionHud both have one). */
        Actor portraitBackdrops;
        /** The UI root's popups: a battle's dependencies are a copy with their own, often unused, manager. */
        PopUpManager rootPopups;

        /** A story event, foe cards, a menu or any other popup is over the game. */
        boolean popupOpen() {
            return !game.deps.popUpManager.isEmpty() || rootPopups != null && !rootPopups.isEmpty();
        }
        CanvasGroup canvas;
        Actor roster, console, consoleToggle, threats, sheet, status, place, bar, undo, retreat, hover;
        /** The HUD's own top bar, to size ours like it. */
        Actor topBar;
        /** Per process, not per Hud: Hud objects are rebuilt every frame. */
        static boolean edgePanWasOn;
        CanvasCell consoleCell;

        /** Indexed by widget id. */
        Actor[] widgets() {
            return new Actor[]{roster, console, consoleToggle, threats, sheet, status, place, bar, undo, retreat, hover};
        }


        /** The game's own "show message log" option, toggled by its console button. */
        boolean consoleShown() {
            return game.deps.context.interfaceOptions.showGameConsole;
        }

        /** Hides the widgets in the HUD and sizes the console to its box on the panel. */
        void moveOut(float consoleWidth, float consoleHeight) {
            // Edge panning needs a real mouse; here a cursor left on the top edge (a stray touch) pans for ever.
            InterfaceOptions options = InterfaceOptions.in(game.entities);
            if (options != null && options.panCameraAtEdgeOfScreen) {
                options.panCameraAtEdgeOfScreen = false; // in memory only: the saved option is untouched
                edgePanWasOn = true;
            }
            Actor[] widgets = widgets();
            for (Actor a : widgets) if (a != null) a.setVisible(false);
            // The portraits' backdrops (sparkles) live on their own particle layer: hide it too.
            if (portraitBackdrops != null) portraitBackdrops.setVisible(false);
            unmaskBattle();
            oneColumn(roster);
            if (consoleCell != null && consoleShown() && consoleWidth > 0
                    && (consoleCell.explicitWidth != consoleWidth || consoleCell.explicitHeight != consoleHeight)) {
                consoleCell.explicitWidth = consoleWidth;
                consoleCell.explicitHeight = consoleHeight;
                canvas.invalidate();
            }
        }

        /**
         * What the HUD's console button does, minus its HUD rebuild (which replays the portrait columns'
         * slide-in): flip and save the option, and give the button the art the rebuild would.
         */
        void toggleConsole() {
            ClientContext context = game.deps.context;
            context.interfaceOptions.showGameConsole = !context.interfaceOptions.showGameConsole;
            context.saveAllOptions();
            if (consoleToggle == null) return;
            AutoSwapDrawable icon = new AutoSwapDrawable(game.deps.skin.getSisterSkin(ClientDataContext.Skins.SCALE_UI));
            String art = context.interfaceOptions.showGameConsole ? "mainMenu_promoBarX_up" : "icon_dropdown";
            icon.addOption(art);
            icon.addOption(art + "2x");
            setIcon(consoleToggle, icon);
        }

        private static void setIcon(Actor a, AutoSwapDrawable icon) {
            if (a instanceof Image) ((Image) a).setDrawable(icon);
            else if (a instanceof Group) for (Actor c : ((Group) a).getChildren()) setIcon(c, icon);
        }

        /**
         * The HUD wraps heroes that don't fit into a second column; the panel scrolls one column instead.
         * The layout fits minSlots cards per column, so ask for as many as there are heroes.
         */
        private static void oneColumn(Actor roster) {
            try {
                Field slots = roster.getClass().getDeclaredField("minSlots");
                slots.setAccessible(true);
                int cards = ((Group) roster).getChildren().size;
                Float base = baseSlots.computeIfAbsent(roster, r -> {
                    try {
                        return slots.getFloat(r);
                    } catch (IllegalAccessException e) {
                        return 5f;
                    }
                });
                float want = Math.max(base, cards);
                if (slots.getFloat(roster) != want) {
                    slots.setFloat(roster, want);
                    ((com.badlogic.gdx.scenes.scene2d.utils.Layout) roster).invalidate();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // a roster laid out differently: leave it to the game
            }
        }

        /** Each roster's own slot count, before we raised it. */
        private static final java.util.Map<Actor, Float> baseSlots = new java.util.WeakHashMap<>();

        /**
         * In battle the world isn't drawn under portrait cards or the log (a stencil the HUD and each card
         * register). Transparent cards opt out by the game's own rule; the HUD's own mask we replace with just
         * its top and bottom bars, since the log has moved off.
         */
        private void unmaskBattle() {
            if (game.campaign != null) return;
            for (Actor a : new Actor[]{roster, threats}) if (a != null) a.getColor().a = 0;
            EnvironmentRenderContext ctx = game.deps.environmentRenderContext;
            if (ctx == null || ctx == unmasked) return;
            unmasked = ctx;
            ctx.renderStencilSignal.remove(gameHud);
            LegacyViewDependencies.ScreenInfo screen = game.deps.screenInfo;
            ctx.renderStencilSignal.add(BARS_MASK, (ShapeRenderer shapes) -> {
                if (shapes == null) return;
                float bottom = screen.scale(80), top = screen.scale(36);
                shapes.rect(0, 0, screen.width, bottom);
                shapes.rect(0, screen.height - top, screen.width, top);
            });
        }

        /** The battle whose stencil we replaced (one per battle). */
        private static EnvironmentRenderContext unmasked;
        private static final Object BARS_MASK = new Object();

        /** Gives edge panning back if we turned it off; the panel is gone. */
        void restoreEdgePan() {
            InterfaceOptions options = InterfaceOptions.in(game.entities);
            if (options != null && edgePanWasOn) options.panCameraAtEdgeOfScreen = true;
            edgePanWasOn = false;
        }

        void rebuild() {
            try {
                java.lang.reflect.Method m = gameHud.getClass().getDeclaredMethod("invalidateBuild");
                m.setAccessible(true);
                m.invoke(gameHud);
            } catch (ReflectiveOperationException e) {
                roster.setVisible(true); // at least give the roster back
            }
        }

        static Hud find() {
            try {
                if (!(Gdx.app.getApplicationListener() instanceof LegacyDesktop)) return null;
                Object ui = field(Gdx.app.getApplicationListener(), "ui");
                ClientContext control = (ClientContext) field(ui, "control");
                if (control == null || control.instances == null) return null;
                Hud h = new Hud();
                Object screen = field(ui, "content");
                String screenName = screen == null ? "" : screen.getClass().getSimpleName();
                if (control.viewState == ClientContext.ViewState.campaign && control.instances.campaign != null
                        && screenName.equals("CampaignScreen")) {
                    ClientCampaignDomain d = control.instances.campaign;
                    h.game = new Game(d.dependencies, d.entities, d.api, d);
                } else if (control.viewState == ClientContext.ViewState.mission && control.instances.mission != null
                        && screenName.equals("MissionScreen")) {
                    ClientMissionDomain d = control.instances.mission;
                    h.game = new Game(d.dependencies, d.entities, d.api, null);
                } else {
                    return null;
                }
                if (!h.game.api.hasBeenWelcomed) return null;
                Object rootDeps = field(ui, "dependencies");
                if (rootDeps instanceof LegacyViewDependencies) h.rootPopups = ((LegacyViewDependencies) rootDeps).popUpManager;
                h.gameHud = field(screen, "hud");
                Object portraits = field(h.gameHud, "unitPortraits");
                h.console = (Actor) field(h.gameHud, "gameConsole");
                h.canvas = (CanvasGroup) field(h.gameHud, "canvas");
                if (portraits == null || h.console == null || h.canvas == null) return null;
                h.roster = (Actor) field(field(portraits, "friendlyPortraitMapper"), "verticalGroup");
                if (h.roster == null) return null;
                @SuppressWarnings("unchecked")
                Array<CanvasCell> cells = (Array<CanvasCell>) field(h.canvas, "cells");
                for (CanvasCell c : cells) if (c.actor == h.console) h.consoleCell = c;
                h.consoleToggle = consoleToggle(h.canvas, h.console);
                Array<Actor> all = h.canvas.getChildren(); // index loop: libgdx reuses Array iterators
                for (int i = 0; i < all.size; i++) // the top bar is the BaseBar not pinned at y 0
                    if (all.get(i) instanceof BaseBar && all.get(i).getY() > 0) h.topBar = all.get(i);
                h.threats = (Actor) field(field(portraits, "enemyPortraitMapper"), "verticalGroup");
                h.portraitBackdrops = (Actor) field(h.gameHud, "bottomParticleStageUI");
                if (h.game.campaign == null) { // a battle's Undo and Retreat buttons go to the panel's bar
                    h.undo = (Actor) field(h.gameHud, "undoButton");
                    // The HUD forgets its retreat button after the first frame; it sits right after Undo.
                    Group row = h.undo == null ? null : h.undo.getParent();
                    int at = row == null ? -1 : row.getChildren().indexOf(h.undo, true);
                    if (at >= 0 && at + 1 < row.getChildren().size) h.retreat = row.getChildren().get(at + 1);
                }
                return h;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null; // mid-rebuild or a screen we don't handle
            }
        }

        /** The console's show/hide button: CampaignHud adds it to the canvas right after the console. */
        private static Actor consoleToggle(Group canvas, Actor console) {
            Array<Actor> children = canvas.getChildren();
            for (int i = children.indexOf(console, true) + 1; i > 0 && i < children.size; i++)
                if (children.get(i).getClass().getSimpleName().equals("NiceButton")) return children.get(i);
            return null;
        }
    }

    static Object field(Object o, String name) throws ReflectiveOperationException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
