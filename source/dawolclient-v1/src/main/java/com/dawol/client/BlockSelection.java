package com.dawol.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * The user's per-block ESP selection.
 *
 * <p>Backed by a set of {@link Block} rather than a name list so hot-path lookups are a
 * set membership test. Names are used only for persistence.
 */
public final class BlockSelection {

    private static final Set<Block> SELECTED = new LinkedHashSet<>();

    private BlockSelection() {
    }

    public static boolean isSelected(Block block) {
        return SELECTED.contains(block);
    }

    public static void toggle(Block block) {
        if (!SELECTED.remove(block)) {
            SELECTED.add(block);
        }
    }

    public static void select(Block block) {
        SELECTED.add(block);
    }

    public static void clear() {
        SELECTED.clear();
    }

    public static int size() {
        return SELECTED.size();
    }

    public static Set<Block> all() {
        return Collections.unmodifiableSet(SELECTED);
    }

    /**
     * Every block in the registry, filtered by a case-insensitive substring of its id.
     * The registry is read live so a datapack adding blocks is reflected.
     */
    public static List<Block> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<Block> out = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == null) {
                continue;
            }
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            if (needle.isEmpty() || id.toString().toLowerCase().contains(needle)) {
                out.add(block);
            }
        }

        return out;
    }

    /** Stable id for a block, used as the persistence key. */
    public static String idOf(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : id.toString();
    }

    /** Resolves a persisted id back to a block, or {@code null} if it no longer exists. */
    public static Block byId(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
    }

    /** Ids of everything selected, for writing to disk. */
    public static List<String> selectedIds() {
        List<String> ids = new ArrayList<>(SELECTED.size());
        for (Block block : SELECTED) {
            String id = idOf(block);
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        Collections.sort(ids);
        return ids;
    }

    /** Human-readable name for display, e.g. {@code deepslate_diamond_ore}. */
    public static String displayName(Block block) {
        String id = idOf(block);
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.replace('_', ' ');
    }

    /**
     * A stable colour per block, derived from its registry id so the same block always
     * draws the same colour across sessions. A hue ramp over the id hash keeps every
     * selected block visually distinguishable at high saturation and value.
     */
    public static int colorOf(Block block) {
        int hash = idOf(block).hashCode();
        float hue = ((hash % 360) + 360) % 360;
        return hsvToRgb(hue, 0.72F, 1.0F);
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float c = value * saturation;
        float h = hue / 60.0F;
        float x = c * (1.0F - Math.abs(h % 2.0F - 1.0F));
        float m = value - c;

        float r;
        float g;
        float b;
        if (h < 1.0F) {
            r = c; g = x; b = 0.0F;
        } else if (h < 2.0F) {
            r = x; g = c; b = 0.0F;
        } else if (h < 3.0F) {
            r = 0.0F; g = c; b = x;
        } else if (h < 4.0F) {
            r = 0.0F; g = x; b = c;
        } else if (h < 5.0F) {
            r = x; g = 0.0F; b = c;
        } else {
            r = c; g = 0.0F; b = x;
        }

        int ri = Math.round((r + m) * 255.0F);
        int gi = Math.round((g + m) * 255.0F);
        int bi = Math.round((b + m) * 255.0F);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
