package com.dawol.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Stash finder: locates container clusters and unusually rich chunks in blocks the client
 * has already received.
 *
 * <p>Modelled on the idea Meteor's StashFinder is built around — a stash is not one chest,
 * it is a <b>concentration</b> of storage, usually underground and usually accompanied by
 * manufactured building materials. So each chunk is scored on two things:
 *
 * <ul>
 *   <li><b>Storage density</b> — how many containers sit in the chunk. A handful of chests
 *       scattered through a cave is terrain generation; a wall of them is a base.</li>
 *   <li><b>Manufactured-block density</b> — player-placed materials. This is what separates
 *       a natural dungeon chest from somebody's storage room.</li>
 * </ul>
 *
 * <p>Hits are persistent per session and sorted so the most promising come first. Only
 * already-received chunk data is read; nothing is requested from the server.
 */
public final class StashFinder {

    /** One chunk's accumulated evidence. */
    public static final class Hit {
        public final int chunkX;
        public final int chunkZ;
        public int containers;
        public int oreCount;
        public int manufactured;
        public BlockPos anchor;
        public long firstSeenMillis;

        Hit(int chunkX, int chunkZ, long firstSeenMillis) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.firstSeenMillis = firstSeenMillis;
        }

        public BlockPos center() {
            return anchor != null ? anchor : new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8);
        }

        /**
         * Confidence that this is a stash rather than terrain.
         *
         * <p>Weighted towards containers because that is the actual target, with manufactured
         * blocks as corroboration. A single chest in a cave does not clear the bar.
         */
        public double score() {
            // Containers dominate by an order of magnitude. Manufactured blocks are capped
            // low enough that a large stone build can never reach the threshold without
            // storage in it — storage is the target, building material is only corroboration.
            return containers * 10.0D
                    + Math.min(manufactured, 400) * 0.05D
                    + Math.min(oreCount, 60) * 0.1D;
        }

        public boolean isStash() {
            return containers >= Toggles.stashMinContainers
                    && score() >= Toggles.stashMinScore;
        }
    }

    /** Player-placed materials that suggest a build rather than natural terrain. */
    private static final java.util.Set<Block> MANUFACTURED = java.util.Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
            Blocks.SHULKER_BOX, Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER,
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
            Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
            Blocks.ENCHANTING_TABLE, Blocks.BREWING_STAND, Blocks.CAULDRON,
            Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE,
            Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
            Blocks.BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS,
            Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
            Blocks.IRON_BARS, Blocks.IRON_CHAIN, Blocks.LANTERN, Blocks.SOUL_LANTERN,
            Blocks.BOOKSHELF, Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_LAMP,
            Blocks.BEACON, Blocks.CONDUIT, Blocks.LODESTONE, Blocks.RESPAWN_ANCHOR,
            Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN,
            Blocks.NETHERITE_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
            Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.LAPIS_BLOCK);

    private static final Map<Long, Hit> HITS = new LinkedHashMap<>();

    /**
     * Derived snapshots. The renderers and the HUD ask for the sorted hit list and the
     * summary every frame, but the underlying hits only change when a chunk is scanned, so
     * the work is done once per scan instead of sixty times a second.
     */
    private static List<Hit> allCache;
    private static List<Hit> stashCache;
    private static String summaryCache;

    private StashFinder() {
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Drops the derived snapshots; called whenever the underlying hits change. */
    private static void invalidate() {
        allCache = null;
        stashCache = null;
        summaryCache = null;
    }

    public static void reset() {
        HITS.clear();
        invalidate();
    }

    public static Hit note(int chunkX, int chunkZ) {
        return HITS.computeIfAbsent(key(chunkX, chunkZ),
                k -> new Hit(chunkX, chunkZ, System.currentTimeMillis()));
    }

    public static Hit get(int chunkX, int chunkZ) {
        return HITS.get(key(chunkX, chunkZ));
    }

    public static int trackedChunks() {
        return HITS.size();
    }

    /**
     * Every chunk that currently meets the stash threshold, best first.
     *
     * <p>Backed by the per-scan snapshot: do not modify the returned list.
     */
    public static List<Hit> stashes() {
        if (stashCache == null) {
            List<Hit> out = new ArrayList<>();
            for (Hit hit : all()) {
                if (hit.isStash()) {
                    out.add(hit);
                }
            }
            stashCache = out;
        }
        return stashCache;
    }

    /**
     * All tracked chunks, best first.
     *
     * <p>Backed by the per-scan snapshot: do not modify the returned list.
     */
    public static List<Hit> all() {
        if (allCache == null) {
            List<Hit> out = new ArrayList<>(HITS.values());
            out.sort(Comparator.comparingDouble(Hit::score).reversed());
            allCache = out;
        }
        return allCache;
    }

    /** A one-line summary for the HUD, rebuilt only when the hits change. */
    public static String summary() {
        if (summaryCache == null) {
            int containers = 0;
            for (Hit hit : HITS.values()) {
                containers += hit.containers;
            }
            summaryCache = stashes().size() + " stash, " + containers + " container, "
                    + HITS.size() + " chunks";
        }
        return summaryCache;
    }

    /**
     * Scans a chunk, recording storage, ore and manufactured-block evidence.
     *
     * <p>Reads only the chunk's already-received block states. It records the position of the
     * first container found so the marker lands on real storage rather than the chunk centre.
     *
     * <p>A scan is a full census, so it replaces the chunk's previous counts instead of
     * adding to them — the same chunk is scanned again by the re-scan key and whenever it is
     * unloaded and reloaded, and accumulating there would inflate every hit into a false one.
     */
    public static void scan(Level level, LevelChunk chunk) {
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        Hit hit = note(chunkX, chunkZ);

        hit.containers = 0;
        hit.manufactured = 0;
        hit.oreCount = 0;
        hit.anchor = null;

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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
                        if (isContainer(block)) {
                            hit.containers++;
                            if (hit.anchor == null) {
                                hit.anchor = new BlockPos(baseX + x, worldY, baseZ + z);
                            }
                        }
                        if (MANUFACTURED.contains(block)) {
                            hit.manufactured++;
                        }
                        if (OreClassifier.isOre(block)) {
                            hit.oreCount++;
                        }
                    }
                }
            }
        }

        invalidate();
    }

    /** Storage blocks worth counting as a stash signal. */
    public static boolean isContainer(Block block) {
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                || block == Blocks.BARREL || block == Blocks.ENDER_CHEST
                || block == Blocks.HOPPER) {
            return true;
        }
        // Dyed shulker boxes are a colour collection in 26.x, so match on type.
        return block.getClass().getSimpleName().contains("ShulkerBox");
    }

    /** Chunk coordinates for a hit, for the renderer. */
    public static ChunkPos chunkPosOf(Hit hit) {
        return new ChunkPos(hit.chunkX, hit.chunkZ);
    }
}