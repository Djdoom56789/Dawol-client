package com.dawol.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every selected block within range, with a box and a tracer to each.
 *
 * <p><b>Performance.</b> This used to scan every chunk section inside the range on every
 * frame — a full block-by-block pass over loaded chunks, at frame rate, to rediscover
 * positions that had not changed. Scanning now happens once per chunk as it arrives, and
 * results are cached and pruned. The per-frame cost is a walk of a short position list with
 * a distance test, which is a different order of magnitude.
 *
 * <p>Scope, stated plainly: this reads block states out of chunks the client already has. It
 * cannot see into chunks the server has not sent, so it is not a true x-ray — it is a
 * highlighter over legitimately received world data. Nothing here sends a packet.
 *
 * <p>Tracers are unconditional and always originate at the crosshair.
 */
public final class BlockEspRenderer {

    /** Positions further than this from the player are dropped from the cache. */
    private static final double CACHE_RANGE = 288.0D;

    /** Hard cap on markers drawn in one frame, so a wall of ore cannot tank the frame. */
    private static final int MAX_MARKERS = 384;

    /** Labels are only drawn inside this distance; text layout is the expensive part. */
    private static final double LABEL_RANGE = 40.0D;

    /**
     * Cached selected-block positions, keyed by position so that re-scanning a chunk
     * overwrites its entries instead of appending a second copy of every block.
     */
    private static final Map<BlockPos, Entry> CACHE = new LinkedHashMap<>();

    private BlockEspRenderer() {
    }

    /**
     * A cached position with everything the renderer needs already resolved.
     *
     * <p>The colour, display name and gizmo style are computed once at scan time. Deriving
     * them per frame meant a registry lookup, a string build and a style allocation for every
     * marker on every frame.
     */
    private record Entry(BlockPos pos, int color, String name, GizmoStyle style) {
    }

    public static void reset() {
        CACHE.clear();
    }

    public static int cachedCount() {
        return CACHE.size();
    }

    /**
     * Records every selected block in a chunk. Called on chunk load, not per frame.
     *
     * <p>Skipped entirely when block ESP is off, so the feature costs nothing while disabled.
     */
    public static void scanChunk(ClientLevel level, LevelChunk chunk) {
        if (!Toggles.blockEsp || BlockSelection.size() == 0) {
            return;
        }

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            int sectionBottom = minY + (sectionIndex << 4);
            if (sectionBottom > maxY) {
                break;
            }

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < minY || worldY > maxY) {
                    continue;
                }

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) {
                            continue;
                        }

                        Block block = state.getBlock();
                        if (!BlockSelection.isSelected(block)) {
                            continue;
                        }

                        int color = BlockSelection.colorOf(block);
                        BlockPos pos = new BlockPos(baseX + x, worldY, baseZ + z);
                        CACHE.put(pos, new Entry(pos, color, BlockSelection.displayName(block),
                                GizmoStyle.strokeAndFill(color, 2.0F,
                                        (color & 0x00FFFFFF) | 0x2A000000)));
                    }
                }
            }
        }
    }

    /**
     * @param tracerOrigin the shared tracer origin for this frame, computed once by the
     *                     caller, so block tracers converge on the same point as every other.
     */
    public static void render(Minecraft mc, Vec3 tracerOrigin) {
        if (!Toggles.blockEsp || CACHE.isEmpty()) {
            return;
        }

        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        if (self == null || level == null) {
            return;
        }

        Vec3 eye = self.getEyePosition();

        double range = Toggles.blockEspRange;
        double rangeSq = range * range;
        double labelRangeSq = LABEL_RANGE * LABEL_RANGE;

        // Prune anything that has drifted out of cache range while walking the map once.
        double cacheRangeSq = CACHE_RANGE * CACHE_RANGE;
        Iterator<Entry> it = CACHE.values().iterator();
        while (it.hasNext()) {
            if (it.next().pos().distToCenterSqr(eye.x, eye.y, eye.z) > cacheRangeSq) {
                it.remove();
            }
        }

        int drawn = 0;
        for (Entry entry : CACHE.values()) {
            if (drawn >= MAX_MARKERS) {
                break;
            }

            double distSq = entry.pos().distToCenterSqr(eye.x, eye.y, eye.z);
            if (distSq > rangeSq) {
                continue;
            }

            drawMarker(tracerOrigin, entry, distSq <= labelRangeSq);
            drawn++;
        }
    }

    private static void drawMarker(Vec3 tracerOrigin, Entry entry, boolean withLabel) {
        BlockPos pos = entry.pos();
        AABB box = new AABB(pos).inflate(0.06D);

        applyThroughWall(Gizmos.cuboid(box, entry.style()));

        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        applyThroughWall(Gizmos.line(tracerOrigin, center, entry.color(), 1.0F));

        if (withLabel) {
            Gizmos.billboardText(
                    entry.name(),
                    new Vec3(center.x, center.y + 1.0D, center.z),
                    TextGizmo.Style.forColorAndCentered(entry.color()).withScale(0.45F));
        }
    }

    private static void applyThroughWall(GizmoProperties props) {
        if (Toggles.blockEspThroughWalls && props != null) {
            props.setAlwaysOnTop();
        }
    }
}