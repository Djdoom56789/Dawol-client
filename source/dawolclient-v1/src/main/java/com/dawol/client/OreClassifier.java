package com.dawol.client;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Classifies a block as an ore worth counting.
 *
 * <p>Deliberately tiny. Ore classification used to be buried in a scanner that also wrote
 * to a trace store, ran a base-detection flood fill and drove its own renderer; all of that
 * is now the stash finder's job. This is the one piece still shared, so it is its own class.
 *
 * <p>Ore count per chunk is a supporting signal for stash detection, not a feature in its
 * own right: a chunk that is unusually rich in ore is worth a second look because players
 * mine and store in the same places.
 */
public final class OreClassifier {

    private OreClassifier() {
    }

    /** True when the block is an ore we care about. */
    public static boolean isOre(Block block) {
        return block == Blocks.DIAMOND_ORE
                || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.EMERALD_ORE
                || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.LAPIS_ORE
                || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.COPPER_ORE
                || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.COAL_ORE
                || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.NETHER_QUARTZ_ORE;
    }

    /** Human-readable name, for logs and the description panel. */
    public static String nameOf(Block block) {
        String path = block.getClass().getSimpleName();
        return path.isEmpty() ? block.toString() : path;
    }
}