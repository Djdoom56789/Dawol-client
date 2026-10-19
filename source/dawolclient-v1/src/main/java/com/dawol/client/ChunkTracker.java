package com.dawol.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Tracks when each chunk was first received and counts ore blocks per chunk.
 * Used by ChunkEspRenderer to highlight new and ore-dense chunks.
 */
public final class ChunkTracker {

    private static final int MAX_ENTRIES = 4096;

    private static final Map<Long, ChunkData> DATA = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ChunkData> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private ChunkTracker() {
    }

    public record ChunkData(long firstSeenMs, int oreCount) {
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static void scan(ClientLevel level, LevelChunk chunk) {
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        long k = key(cx, cz);

        long seenAt = DATA.containsKey(k) ? DATA.get(k).firstSeenMs() : System.currentTimeMillis();
        int ores = countOres(chunk);
        DATA.put(k, new ChunkData(seenAt, ores));
    }

    private static int countOres(LevelChunk chunk) {
        int count = 0;
        for (var section : chunk.getSections()) {
            if (section == null || section.hasOnlyAir()) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        var bs = section.getBlockState(x, y, z);
                        if (bs == null) continue;
                        String id = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).getPath();
                        if (id.endsWith("_ore") || id.equals("ancient_debris")) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public static ChunkData get(int cx, int cz) {
        return DATA.get(key(cx, cz));
    }

    public static boolean isNew(int cx, int cz) {
        ChunkData d = get(cx, cz);
        return d != null && (System.currentTimeMillis() - d.firstSeenMs()) < Toggles.chunkNewWindowMs;
    }

    public static boolean isDense(int cx, int cz) {
        ChunkData d = get(cx, cz);
        return d != null && d.oreCount() >= Toggles.chunkDensityThreshold;
    }

    public static void reset() {
        DATA.clear();
    }

    public static int size() {
        return DATA.size();
    }
}
