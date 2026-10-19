package com.dawol.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Base finder: detects player-built structures by structural block density.
 *
 * <p>Different from StashFinder, which targets container clusters. A base is a
 * <em>built structure</em> — high density of building materials (planks, bricks,
 * obsidian, concrete) combined with signs of habitation (beds, workbenches,
 * furnaces). One chest in a cave is a stash; fifty stone bricks surrounding it
 * is a base.
 */
public final class BaseFinder {

    public static final class Hit {
        public final int chunkX;
        public final int chunkZ;
        public int structural;
        public int habitation;
        public int obsidian;
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

        public double score() {
            return structural * 0.08D
                    + habitation * 4.0D
                    + Math.min(obsidian, 200) * 0.5D;
        }

        public boolean isBase() {
            return score() >= Toggles.baseMinScore;
        }
    }

    private static final Map<Long, Hit> HITS = new LinkedHashMap<>();
    private static List<Hit> allCache;
    private static List<Hit> baseCache;
    private static String summaryCache;

    private BaseFinder() {
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static void invalidate() {
        allCache = null;
        baseCache = null;
        summaryCache = null;
    }

    public static void reset() {
        HITS.clear();
        invalidate();
    }

    public static List<Hit> bases() {
        if (baseCache == null) {
            List<Hit> out = new ArrayList<>();
            for (Hit h : all()) {
                if (h.isBase()) out.add(h);
            }
            baseCache = out;
        }
        return baseCache;
    }

    public static List<Hit> all() {
        if (allCache == null) {
            List<Hit> out = new ArrayList<>(HITS.values());
            out.sort(Comparator.comparingDouble(Hit::score).reversed());
            allCache = out;
        }
        return allCache;
    }

    public static String summary() {
        if (summaryCache == null) {
            summaryCache = bases().size() + " bases, " + HITS.size() + " chunks";
        }
        return summaryCache;
    }

    public static void scan(Level level, LevelChunk chunk) {
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        Hit hit = HITS.computeIfAbsent(key(chunkX, chunkZ),
                k -> new Hit(chunkX, chunkZ, System.currentTimeMillis()));

        hit.structural = 0;
        hit.habitation = 0;
        hit.obsidian = 0;
        hit.anchor = null;

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) continue;
            int sectionBottom = minY + (si << 4);
            if (sectionBottom > maxY) break;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < minY || worldY > maxY) continue;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        Block block = state.getBlock();

                        if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) {
                            hit.obsidian++;
                            if (hit.anchor == null) {
                                hit.anchor = new BlockPos(baseX + x, worldY, baseZ + z);
                            }
                        } else if (isHabitation(block)) {
                            hit.habitation++;
                            if (hit.anchor == null) {
                                hit.anchor = new BlockPos(baseX + x, worldY, baseZ + z);
                            }
                        } else if (isStructural(block)) {
                            hit.structural++;
                            if (hit.anchor == null && hit.habitation == 0) {
                                hit.anchor = new BlockPos(baseX + x, worldY, baseZ + z);
                            }
                        }
                    }
                }
            }
        }

        invalidate();
    }

    private static boolean isStructural(Block block) {
        String id = block.getDescriptionId();
        return id.contains("_planks") || id.contains("_brick")
                || id.contains("concrete") || id.contains("polished")
                || id.contains("smooth_") || id.contains("_tiles")
                || id.contains("terracotta") || id.contains("wool")
                || id.contains("glass") || id.contains("quartz")
                || id.contains("nether") || id.contains("prismarine")
                || id.contains("purpur") || id.contains("end_stone")
                || id.contains("chiseled") || id.contains("cut_")
                || block == Blocks.IRON_BARS || block == Blocks.IRON_CHAIN
                || block == Blocks.NETHERITE_BLOCK || block == Blocks.DIAMOND_BLOCK;
    }

    private static boolean isHabitation(Block block) {
        return block == Blocks.CRAFTING_TABLE
                || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL || block == Blocks.BREWING_STAND
                || block == Blocks.RESPAWN_ANCHOR || block == Blocks.LODESTONE
                || block == Blocks.BEACON || block == Blocks.CONDUIT
                || block.getDescriptionId().contains("_bed");
    }
}
