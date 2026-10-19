package com.dawol.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;

/**
 * Finds the schematic files the user has on disk.
 *
 * <p>Vanilla has no file-picker API and this mod ships no mixins, so a native open dialog is
 * not available. A directory scan plus a list is the only no-mixin way to choose a file, and
 * it is also what the external tooling does: Litematica writes its exports to
 * {@code schematics/} next to the game directory, so pointing at that directory means the
 * files the user already has Just work, with no path to type.
 */
public final class SchematicLibrary {

    /**
     * Extensions this client can actually parse. Deliberately short: offering a file type in
     * the picker that the reader cannot read would only produce a confusing load error.
     * MCEdit's {@code .schematic} and vanilla's {@code .nbt} structure format are both left
     * out for that reason.
     */
    public static final List<String> EXTENSIONS = List.of(".litematic", ".schem");

    /** Folder name under the game directory, matching the convention the modding tools use. */
    public static final String DIRECTORY_NAME = "schematics";

    /** Depth below the schematics folder to search. Exports are two levels deep at most. */
    private static final int SCAN_DEPTH = 3;

    /** Files listed before the scan stops adding. Keeps a pathological tree off the screen. */
    private static final int MAX_FILES = 500;

    private SchematicLibrary() {
    }

    /**
     * The folder schematics are read from, created if it does not exist.
     *
     * <p>Never returns null: without a live client it falls back to a relative path, so a
     * caller outside a running game still gets something it can print rather than a crash.
     */
    public static Path directory() {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc == null ? null : mc.gameDirectory.toPath();
        if (gameDir == null) {
            return Path.of(DIRECTORY_NAME).toAbsolutePath();
        }

        Path dir = gameDir.resolve(DIRECTORY_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DawolClient.LOGGER.warn("[dawol] could not create {}: {}", dir, e.toString());
        }
        return dir;
    }

    /** Schematic files under {@link #directory()}, sorted by name and capped. */
    public static List<Path> files() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        List<Path> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, SCAN_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(SchematicLibrary::isSchematic)
                    .forEach(found::add);
        } catch (IOException e) {
            DawolClient.LOGGER.warn("[dawol] could not scan {}: {}", dir, e.toString());
            return List.of();
        }

        // Sorted by file name rather than full path so a nested folder does not scatter one
        // build's parts through the list.
        found.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return found.size() > MAX_FILES ? List.copyOf(found.subList(0, MAX_FILES)) : List.copyOf(found);
    }

    /** True when the file's extension is one this client can parse. */
    public static boolean isSchematic(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
