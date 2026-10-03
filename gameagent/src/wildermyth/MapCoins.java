package wildermyth;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;
import com.worldwalkergames.engine.Entity;
import com.worldwalkergames.legacy.context.ClientDataContext;
import com.worldwalkergames.legacy.context.LegacyViewDependencies;
import com.worldwalkergames.legacy.game.campaign.model.Threat;
import com.worldwalkergames.legacy.game.campaign.render.CoinRenderer;
import com.worldwalkergames.legacy.game.model.Controlled;
import com.worldwalkergames.legacy.game.model.Faction;
import com.worldwalkergames.legacy.game.model.status.Status;
import com.worldwalkergames.legacy.server.context.ClassLevelData;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The map's coins, drawn once each with the game's own sprites (background, faction icon, class edge) and
 * sent to the panel as small images it caches by name. GL thread only.
 */
final class MapCoins {
    private static final int SIZE = 96;
    private final Set<String> sent = new HashSet<>();
    private FrameBuffer fbo;
    private SpriteBatch batch;

    /** The coin for a threat: its faction's icon on a background in the faction's colour. */
    String threat(LegacyViewDependencies deps, Threat t, Predicate<byte[]> send) {
        String key = "threat:" + t.flavor;
        if (!sent.contains(key)) {
            Skin skin = deps.dataContext.getSkin(ClientDataContext.Skins.SCALE);
            Color tint = Color.WHITE;
            Controlled c = Controlled.any(t);
            Faction f = c == null ? null : Faction.byId(deps.entities, c.controllerFaction);
            if (f != null) tint = f.color;
            if (send.test(render(key, skin.getRegion("coin_bg_other"), tint, CoinRenderer.getIconForThreat(t, skin),
                    skin.getRegion("coin_edge_other1")))) sent.add(key); // else: try again next time
        }
        return key;
    }

    /** The coin for a party: the first hero's class background and edge; the panel adds the head count. */
    String hero(LegacyViewDependencies deps, Entity hero, Predicate<byte[]> send) {
        String type = "other";
        ClassLevelData.ClassAndLevel cl = ClassLevelData.getClassAndLevel(Status.any(hero));
        if (cl != null && cl.archetype != null) {
            String a = cl.archetype.name();
            if (a.equals("hunter") || a.equals("warrior") || a.equals("mystic")) type = a;
        }
        String key = "hero:" + type;
        if (!sent.contains(key)) {
            Skin skin = deps.dataContext.getSkin(ClientDataContext.Skins.SCALE);
            if (send.test(render(key, skin.getRegion("coin_bg_" + type), Color.WHITE, null, skin.getRegion("coin_edge_" + type + "1"))))
                sent.add(key);
        }
        return key;
    }

    /** A panel that just connected has none of them. */
    void reset() {
        sent.clear();
    }

    /** 'I' + name length + name + width + height + RGBA rows, top first. */
    private byte[] render(String name, TextureRegion bg, Color tint, TextureRegion icon, TextureRegion edge) {
        if (fbo == null) {
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, false);
            batch = new SpriteBatch(8);
            batch.getProjectionMatrix().setToOrtho2D(0, 0, SIZE, SIZE);
            // Coverage, not colour, for alpha: the panel composites these over its map.
            batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        fbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        if (bg != null) {
            batch.setColor(tint);
            batch.draw(bg, 0, 0, SIZE, SIZE);
        }
        batch.setColor(Color.WHITE);
        if (icon != null) batch.draw(icon, SIZE * 0.18f, SIZE * 0.18f, SIZE * 0.64f, SIZE * 0.64f);
        if (edge != null) batch.draw(edge, 0, 0, SIZE, SIZE);
        batch.end();
        byte[] px = ScreenUtils.getFrameBufferPixels(0, 0, SIZE, SIZE, true);
        fbo.end();
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[2 + n.length + 8 + px.length];
        msg[0] = 'I';
        msg[1] = (byte) n.length;
        System.arraycopy(n, 0, msg, 2, n.length);
        int at = 2 + n.length;
        for (int v : new int[]{SIZE, SIZE}) {
            msg[at++] = (byte) (v >>> 24);
            msg[at++] = (byte) (v >>> 16);
            msg[at++] = (byte) (v >>> 8);
            msg[at++] = (byte) v;
        }
        System.arraycopy(px, 0, msg, at, px.length);
        return msg;
    }
}
