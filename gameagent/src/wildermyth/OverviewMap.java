package wildermyth;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.worldwalkergames.engine.EID;
import com.worldwalkergames.engine.EntitiesCollection;
import com.worldwalkergames.legacy.game.campaign.ClientCampaignDomain;
import com.worldwalkergames.legacy.game.campaign.components.WorldMapCamera;
import com.worldwalkergames.legacy.game.common.UISelectionState;
import com.worldwalkergames.legacy.game.world.model.OverlandTile;
import com.worldwalkergames.legacy.game.world.model.TileContents;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The campaign map as data for the panel to draw: every tile's outline and colour once, then what the
 * player can see and what is selected whenever that changes. GL thread only.
 */
final class OverviewMap {
    private ClientCampaignDomain domain;
    /** Tiles in the order the panel numbers them. */
    private final Array<OverlandTile> tiles = new Array<>();
    private String lastState = "";

    /** Sends the geometry for a new campaign, then the state when it changes. */
    void update(ClientCampaignDomain d, Consumer<String> send) {
        if (d != domain) {
            domain = d;
            lastState = "";
            send.accept(geometry(d.entities));
        }
        String state = state(d.entities);
        if (!state.equals(lastState)) {
            lastState = state;
            send.accept(state);
        }
    }

    /** Forces a resend, e.g. for a panel that just connected. */
    void reset() {
        domain = null;
    }

    private String geometry(EntitiesCollection entities) {
        tiles.clear();
        Array<OverlandTile> all = entities.getComponentsArray(OverlandTile.class);
        StringBuilder b = new StringBuilder("{\"map\":{\"tiles\":[");
        for (int i = 0; i < all.size; i++) {
            OverlandTile t = all.get(i);
            if (!t.inPlay || t.originalPolygon == null || t.originalPolygon.size < 3) continue;
            if (tiles.size > 0) b.append(',');
            tiles.add(t);
            b.append("{\"p\":[");
            for (int k = 0; k < t.originalPolygon.size; k++) {
                Vector2 v = t.originalPolygon.get(k);
                if (k > 0) b.append(',');
                b.append(String.format(Locale.US, "%.2f,%.2f", v.x, v.y));
            }
            b.append("],\"c\":").append(color(t.biome)).append('}');
        }
        return b.append("]}}").toString();
    }

    /** Terrain colours sampled from the game's own map art; a tile's own colour is only a tint. */
    private static int color(OverlandTile.Biome biome) {
        switch (biome) {
            case forestDeciduous: return 0x6E8F3C;
            case forestConiferous: return 0x4E6E3A;
            case grassland: return 0xA9B35A;
            case swamp: return 0x5E6B4A;
            case hills: return 0xC8A85E;
            case mountains: return 0x8A8274;
            case lake: return 0x6C93A6;
            case ocean: return 0x4F6F86;
            case netherflare: return 0x8A4A6A;
            default: return 0x9A8A6A;
        }
    }

    /** One letter per tile: h hidden, p seen before, v visible now; and the selected tile's index. */
    private String state(EntitiesCollection entities) {
        StringBuilder vis = new StringBuilder(tiles.size);
        for (int i = 0; i < tiles.size; i++) {
            TileContents.TileVisibility v = tiles.get(i).getVisibilityState(null);
            vis.append(v == TileContents.TileVisibility.visible ? 'v' : v == TileContents.TileVisibility.partial ? 'p' : 'h');
        }
        EID sel = UISelectionState.in(entities).selectedEntity();
        int selected = -1;
        for (int i = 0; sel != null && i < tiles.size; i++) if (sel.equals(tiles.get(i).id())) selected = i;
        return "{\"mapState\":{\"vis\":\"" + vis + "\",\"sel\":" + selected + "}}";
    }

    /** A tap on tile {@code i}: select it and fly the main camera there, as a click on the map would. */
    void tap(int i) {
        if (domain == null || i < 0 || i >= tiles.size) return;
        OverlandTile t = tiles.get(i);
        if (t.getVisibilityState(null) == TileContents.TileVisibility.hidden) return;
        UISelectionState.in(domain.entities).selectEntity(t.id());
        domain.api.stopTime();
        Vector2 c = new Vector2();
        for (int k = 0; k < t.originalPolygon.size; k++) c.add(t.originalPolygon.get(k));
        c.scl(1f / t.originalPolygon.size);
        WorldMapCamera camera = WorldMapCamera.any(domain.entities);
        if (camera != null) camera.showPoint(c.x, c.y);
    }
}
