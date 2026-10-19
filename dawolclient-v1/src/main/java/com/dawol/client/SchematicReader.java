package com.dawol.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads schematic files into placements.
 *
 * <p>Supports the two formats Corz advertises:
 * <ul>
 *   <li><b>Sponge schematic</b> ({@code .schem}) — a paletted flat block array, with the
 *       origin at one corner and {@code Offset} giving the placement anchor.</li>
 *   <li><b>Litematica</b> ({@code .litematic}) — one paletted array per sub-region, each
 *       with its own origin and size, stitched into world positions.</li>
 * </ul>
 *
 * <p>No third-party library is involved: 26.3 already exposes {@code NbtIo.readCompressed}
 * and the tag accessors, so this needs no mixin and no extra mod. Unknown block ids are
 * skipped rather than failing the load, since a schematic may reference modded blocks.
 *
 * <p>Positions come back relative to the schematic's own origin, not to the world: the
 * caller anchors them. A real Litematica export routinely has negative relative positions
 * (a region whose first corner sits on the positive side of the second), and the reader
 * preserves that rather than normalising it away.
 *
 * <p>Every load is capped. A hand-written file can claim a region of 2000x600x2000 and
 * either wrap an int or exhaust the heap while the parser is still unpacking it; the cap
 * bounds the work and the drop count is reported so an incomplete build is never a
 * surprise.
 */
public final class SchematicReader {

    /**
     * Largest palette index accepted from a file. A byte-array sponge palette can only ever
     * reference 0-255, so anything past this is corrupt or hostile and is rejected rather
     * than used to size a list.
     */
    private static final int MAX_PALETTE_SIZE = 65536;

    /**
     * Largest litematica region accepted. This is a sanity bound, not a practical one: it
     * catches the wrapped-integer and memory-exhausting cases before anything is allocated.
     * The real limit on a build is {@link #MAX_PLACEMENTS}, since a large region is usually
     * mostly air and an air cell costs nothing to sweep.
     */
    private static final long MAX_REGION_VOLUME = 1L << 30;

    /** Placements a single load returns before it starts counting what it left out. */
    public static final int MAX_PLACEMENTS = 2_000_000;

    private SchematicReader() {
    }

    /**
     * Result of a load, including what could not be resolved.
     *
     * @param placements blocks to place, air already filtered out, in the file's own order
     * @param skipped    entries whose palette slot held a block this client does not have
     * @param format     human-readable format name, for the UI
     * @param name       the file's own {@code Metadata.Name} when present, else the file name
     * @param size       bounding box of {@code placements}, in blocks
     * @param regions    sub-regions parsed; sponge is always 1
     * @param dropped    blocks left out because the placement cap was reached
     */
    public record Result(List<SchematicBuilder.Placement> placements, int skipped, String format,
                         String name, Vec3i size, int regions, int dropped) {

        /** True when the file held more blocks than the cap allowed. */
        public boolean truncated() {
            return dropped > 0;
        }
    }

    /** Loads a schematic with the default placement cap. */
    public static Result load(Path file) throws IOException {
        return load(file, MAX_PLACEMENTS);
    }

    /**
     * Loads a schematic, detecting the format from its contents rather than its extension.
     *
     * @param placementCap maximum placements to keep; the rest are counted as dropped
     * @throws IOException if the file is missing, unreadable, or neither format
     */
    public static Result load(Path file, int placementCap) throws IOException {
        if (placementCap <= 0) {
            throw new IOException("placement cap must be positive, got " + placementCap);
        }

        CompoundTag root = readNbt(file);

        // Sponge uses "Schematic" as a version marker inside a "Schematic" wrapper in v3,
        // while older exports put the markers at the top level. Handle both.
        CompoundTag body = root.getCompoundOrEmpty("Schematic");
        boolean sponge = root.contains("Blocks") || body.contains("Blocks");
        boolean litematica = root.contains("Regions");

        if (!sponge && !litematica) {
            throw new IOException("unrecognised schematic: no Blocks (sponge) or Regions (litematica) tag");
        }

        String name = displayName(root, file);
        Collector collector = new Collector(placementCap);

        return litematica
                ? loadLitematica(root, name, collector)
                : loadSponge(root.contains("Blocks") ? root : body, name, collector);
    }

    private static CompoundTag readNbt(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("no such file: " + file);
        }

        NbtAccounter quota = NbtAccounter.create(64L * 1024L * 1024L);

        // Both formats are gzip-compressed, and NbtIo.readCompressed already decompresses,
        // so the stream must NOT be wrapped in a GZIPInputStream first.
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in, quota);
        } catch (IOException compressedFailed) {
            // Tolerate an uncompressed export before giving up.
            try (java.io.DataInputStream raw =
                         new java.io.DataInputStream(Files.newInputStream(file))) {
                return NbtIo.read(raw, quota);
            } catch (IOException rawFailed) {
                throw new IOException("could not read NBT from " + file.getFileName()
                        + " (tried gzip and raw): " + compressedFailed.getMessage(), rawFailed);
            }
        }
    }

    // --- Sponge (.schem) ---

    private static Result loadSponge(CompoundTag tag, String name, Collector collector) throws IOException {
        byte[] blocks = tag.getByteArray("Blocks")
                .orElseThrow(() -> new IOException("sponge schematic has no Blocks array"));

        int width = tag.getInt("Width").orElse(0);
        int height = tag.getInt("Height").orElse(0);
        int length = tag.getInt("Length").orElse(0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("sponge schematic has invalid dimensions");
        }

        // Palette maps "namespace:block[state]" or "namespace:block" to an index.
        List<String> palette = readPalette(tag.getCompoundOrEmpty("Palette"));

        // Offset is where the schematic's minimum corner sits relative to the anchor.
        Vec3i offset = readVec(tag.getCompoundOrEmpty("Offset"));

        // The array is y-major, then z, then x.
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    if (index >= blocks.length) {
                        continue;
                    }
                    int paletteId = blocks[index] & 0xFF;
                    BlockState state = resolve(palette, paletteId);
                    if (state == null) {
                        // A slot that names nothing this client has. Air resolves fine, so
                        // a null here is always a genuinely unresolvable entry, not filler.
                        collector.skip();
                        continue;
                    }
                    // WorldEdit exports palette-map minecraft:air, so every air cell would
                    // otherwise become a placement the planner throws away. Skipping it here
                    // keeps this path agreeing with the litematica path.
                    if (state.isAir()) {
                        continue;
                    }
                    collector.add(new BlockPos(x + offset.getX(), y + offset.getY(), z + offset.getZ()), state);
                }
            }
        }

        // A file that parses but yields nothing is almost always the wrong variant of the
        // format rather than an empty build — MCEdit's .schematic, for example, stores numeric
        // block ids in the same "Blocks" tag. Failing loudly beats building nothing silently.
        if (collector.placements().isEmpty()) {
            throw new IOException("sponge schematic produced no placements "
                    + "(unsupported variant, or an empty selection)");
        }

        return collector.result("sponge .schem", name, bounds(collector.placements()), 1);
    }

    // --- Litematica (.litematic) ---

    private static Result loadLitematica(CompoundTag root, String name, Collector collector) throws IOException {
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.keySet().isEmpty()) {
            throw new IOException("litematica file has no regions");
        }

        int parsedRegions = 0;

        for (String regionName : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(regionName);

            // Litematica packs palette indices into a long array, not a byte array.
            long[] packed = region.getLongArray("BlockStates")
                    .orElseThrow(() -> new IOException(
                            "litematica region '" + regionName + "' has no BlockStates"));

            // Litematica writes a negative component when the selection's second corner sits
            // on the negative side of the first. The sign is kept: discarding it mirrors the
            // region and pushes it outside its own box. The magnitude is the dimension.
            CompoundTag sizeTag = region.getCompoundOrEmpty("Size");
            int sizeX = sizeTag.getInt("x").orElse(0);
            int sizeY = sizeTag.getInt("y").orElse(0);
            int sizeZ = sizeTag.getInt("z").orElse(0);
            int widthX = Math.abs(sizeX);
            int widthY = Math.abs(sizeY);
            int widthZ = Math.abs(sizeZ);
            if (widthX <= 0 || widthY <= 0 || widthZ <= 0) {
                continue;
            }

            CompoundTag posTag = region.getCompoundOrEmpty("Position");
            int posX = posTag.getInt("x").orElse(0);
            int posY = posTag.getInt("y").orElse(0);
            int posZ = posTag.getInt("z").orElse(0);

            // With a negative size the blocks occupy [size+1 .. 0] from Position, so the
            // region's minimum corner is Position + size + 1; the index sweep still starts at
            // that corner, so only the origin needs shifting.
            int originX = sizeX < 0 ? posX + sizeX + 1 : posX;
            int originY = sizeY < 0 ? posY + sizeY + 1 : posY;
            int originZ = sizeZ < 0 ? posZ + sizeZ + 1 : posZ;

            List<BlockState> palette = readStatePalette(region.getListOrEmpty("BlockStatePalette"));
            int bits = Math.max(2, bitsNeeded(Math.max(1, palette.size())));

            // Computed in long: a file-supplied 2000x600x2000 wraps an int negative, the loop
            // body then never runs and the region silently contributes nothing. Absurd
            // volumes are rejected before anything is allocated.
            long volume = (long) widthX * widthY * widthZ;
            if (volume > MAX_REGION_VOLUME) {
                throw new IOException("litematica region '" + regionName + "' is too large: "
                        + widthX + "x" + widthY + "x" + widthZ);
            }

            parsedRegions++;

            for (int index = 0; index < volume; index++) {
                int paletteId = readPacked(packed, bits, index);
                if (paletteId < 0 || paletteId >= palette.size()) {
                    collector.skip();
                    continue;
                }
                BlockState state = palette.get(paletteId);
                if (state == null) {
                    // A slot the palette declares but this client cannot resolve: a modded
                    // block, or one removed since the file was written. Counted, not fatal.
                    collector.skip();
                    continue;
                }
                if (state.isAir()) {
                    continue;
                }

                // Same y-major, then z, then x ordering as sponge, using the magnitudes so a
                // signed size cannot produce a negative modulus.
                int x = index % widthX;
                int z = (index / widthX) % widthZ;
                int y = index / (widthX * widthZ);

                collector.add(new BlockPos(originX + x, originY + y, originZ + z), state);
            }
        }

        if (collector.placements().isEmpty()) {
            throw new IOException("litematica file produced no placements");
        }
        return collector.result("litematica .litematic", name,
                bounds(collector.placements()), parsedRegions);
    }

    // --- helpers ---

    /** The schematic's own name when it carries one, else the file name without extension. */
    private static String displayName(CompoundTag root, Path file) {
        String declared = root.getCompoundOrEmpty("Metadata").getStringOr("Name", "");
        if (declared != null && !declared.isBlank()) {
            return declared;
        }
        // Sponge files keep their metadata beside the body, so check there too before
        // falling back to the file name.
        String wrapped = root.getCompoundOrEmpty("Schematic").getCompoundOrEmpty("Metadata")
                .getStringOr("Name", "");
        if (wrapped != null && !wrapped.isBlank()) {
            return wrapped;
        }

        String fileName = file.getFileName() == null ? "schematic" : file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Bounding box of the placements, in blocks. Zero when there is nothing to place. */
    private static Vec3i bounds(List<SchematicBuilder.Placement> placements) {
        if (placements.isEmpty()) {
            return Vec3i.ZERO;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (SchematicBuilder.Placement placement : placements) {
            BlockPos pos = placement.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private static List<String> readPalette(CompoundTag paletteTag) throws IOException {
        // Sponge palettes are name -> index, so invert into an index-ordered list. The index
        // is file-supplied, so it is validated before it is ever used as a length: a single
        // "minecraft:stone": 2147483647 would otherwise allocate two billion entries from a
        // handful of bytes and exhaust the heap.
        Map<Integer, String> byIndex = new HashMap<>();
        int size = 0;
        for (String key : paletteTag.keySet()) {
            int id = paletteTag.getInt(key).orElse(-1);
            if (id < 0) {
                continue;
            }
            if (id >= MAX_PALETTE_SIZE) {
                throw new IOException("sponge schematic palette index " + id
                        + " is implausible (max " + (MAX_PALETTE_SIZE - 1) + ")");
            }
            byIndex.put(id, key);
            size = Math.max(size, id + 1);
        }

        // Sized once, after validation, instead of growing element by element.
        List<String> palette = new ArrayList<>(Collections.nCopies(size, null));
        for (Map.Entry<Integer, String> entry : byIndex.entrySet()) {
            palette.set(entry.getKey(), entry.getValue());
        }
        return palette;
    }

    private static List<BlockState> readStatePalette(ListTag list) {
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i).orElse(null);
            if (entry == null) {
                palette.add(null);
                continue;
            }
            palette.add(parseState(entry.getStringOr("Name", ""), entry.getCompoundOrEmpty("Properties")));
        }
        return palette;
    }

    private static Vec3i readVec(CompoundTag tag) {
        return new Vec3i(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0));
    }

    private static int bitsNeeded(int paletteSize) {
        int bits = 0;
        while ((1 << bits) < paletteSize) {
            bits++;
        }
        return Math.max(1, bits);
    }

    /** Reads a palette index out of a packed long array, LSB-first, spans across longs. */
    private static int readPacked(long[] data, int bits, int index) {
        long bitIndex = (long) index * bits;
        int startLong = (int) (bitIndex / 64);
        int startOffset = (int) (bitIndex % 64);
        if (startLong >= data.length) {
            return -1;
        }

        long value = data[startLong] >>> startOffset;
        int bitsTaken = 64 - startOffset;
        if (bitsTaken < bits) {
            if (startLong + 1 < data.length) {
                value |= data[startLong + 1] << bitsTaken;
            }
        }
        return (int) (value & ((1L << bits) - 1));
    }

    private static BlockState resolve(List<String> palette, int id) {
        if (id < 0 || id >= palette.size()) {
            return null;
        }
        String entry = palette.get(id);
        if (entry == null || entry.isBlank()) {
            return null;
        }
        return parseState(entry, null);
    }

    /** Parses "minecraft:oak_stairs[facing=north]" or a plain id, tolerating modded blocks. */
    private static BlockState parseState(String name, CompoundTag properties) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String idPart = name;
        CompoundTag inlineProps = properties;

        int bracket = name.indexOf('[');
        if (bracket >= 0 && name.endsWith("]")) {
            idPart = name.substring(0, bracket);
            if (inlineProps == null || inlineProps.keySet().isEmpty()) {
                inlineProps = parseInlineProperties(name.substring(bracket + 1, name.length() - 1));
            }
        }

        Identifier id = Identifier.tryParse(idPart.contains(":") ? idPart : "minecraft:" + idPart);
        if (id == null) {
            return null;
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return null;
        }

        BlockState state = block.defaultBlockState();
        if (inlineProps != null) {
            state = applyProperties(state, inlineProps);
        }
        return state;
    }

    private static CompoundTag parseInlineProperties(String body) {
        CompoundTag tag = new CompoundTag();
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                tag.putString(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return tag;
    }

    /** Applies NBT property strings onto a state, ignoring any property the block lacks. */
    private static BlockState applyProperties(BlockState state, CompoundTag properties) {
        BlockState result = state;
        for (String key : properties.keySet()) {
            String value = properties.getStringOr(key, null);
            if (value == null) {
                continue;
            }
            for (var property : result.getProperties()) {
                if (!property.getName().equals(key)) {
                    continue;
                }
                result = setProperty(result, property, value);
                break;
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        // Property.getValue(String) yields Optional<T>; StateHolder.setValue takes the value
        // directly, so no unchecked cast is needed.
        return property.getValue(value)
                .map(v -> state.setValue(property, v))
                .orElse(state);
    }

    /**
     * Accumulates placements up to the cap, counting what it leaves out.
     *
     * <p>Once the cap is reached the sweep keeps walking but stops allocating. Stopping it
     * early would be faster, but it would make the reported drop count mean "there is more
     * in the file" instead of "this is how much of your build is missing", and that is the
     * number the user needs in order to decide whether to split the job up.
     */
    private static final class Collector {

        private final List<SchematicBuilder.Placement> placements = new ArrayList<>();
        private final int cap;
        private int skipped;
        private int dropped;

        Collector(int cap) {
            this.cap = cap;
        }

        void add(BlockPos pos, BlockState state) {
            if (placements.size() >= cap) {
                dropped++;
                return;
            }
            placements.add(new SchematicBuilder.Placement(pos, state));
        }

        void skip() {
            skipped++;
        }

        List<SchematicBuilder.Placement> placements() {
            return placements;
        }

        Result result(String format, String name, Vec3i size, int regions) {
            return new Result(List.copyOf(placements), skipped, format, name, size, regions, dropped);
        }
    }
}
