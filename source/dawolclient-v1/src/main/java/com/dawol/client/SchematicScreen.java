package com.dawol.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;

/**
 * Browser for external Litematica and Sponge schematic files, and the readout for whatever
 * {@link SchematicController} currently holds. Opened with the schematic keybind.
 *
 * <p>Why a screen and not a path setting: Minecraft has no native file dialog, and borrowing
 * the platform one would mean injecting code into the vanilla widget set to get at it. A scan
 * of one known folder plus a list of what it found is the whole job, needs nothing injected
 * into the game, and keeps the choice inside the game's own input handling — which also means
 * the folder is somewhere the player can be told about, rather than a path typed blind.
 *
 * <p>The layout is two columns: files on the left, the loaded schematic on the right. Loading
 * deliberately does not close the screen, so the readout and the material check update in
 * place while the list stays available for switching files.
 *
 * <p>Everything is flat fills, 1px borders and text — no textures, no rounded corners — the
 * same idiom as the rest of the client's screens.
 */
public final class SchematicScreen extends Screen {

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 30;
    private static final int FOOTER_HEIGHT = 40;
    private static final int LINE = 11;
    private static final int LABEL_WIDTH = 46;

    /**
     * Rows built per scan. A scan can return up to {@link SchematicLibrary}'s own cap, and a
     * widget per path is real work for a list too long to read anyway, so the tail is cut and
     * its size reported instead.
     */
    private static final int RESULT_LIMIT = 200;

    /** The material table is a summary, not an inventory dump. */
    private static final int MATERIAL_LIMIT = 10;

    /**
     * Missing-material highlight. {@link Theme#MOB} is the palette's warm accent — the one
     * colour in it that reads as "wrong" beside the green chrome.
     */
    private static final int MISSING = Theme.MOB;

    private final Screen parent;

    private FileList list;
    private Button builderButton;

    private int fileCount;
    private int hiddenByLimit;
    private String error;

    /**
     * Materials of the loaded schematic, most-needed first, refreshed when the queue moves
     * rather than every frame: {@code SchematicController.materials()} walks every placement.
     */
    private List<SchematicController.Material> materials = List.of();
    private int materialsRemaining = -1;
    private int ticks;

    public SchematicScreen(Screen parent) {
        super(Component.literal("Schematics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.list = new FileList(this.minecraft, listWidth(), this.height - LIST_TOP - FOOTER_HEIGHT,
                LIST_TOP, ROW_HEIGHT);
        addRenderableWidget(this.list);

        int buttons = 5;
        int gap = 4;
        int buttonWidth = Math.max(60, Math.min(110, (this.width - 16 - gap * (buttons - 1)) / buttons));
        int x = Math.max(4, (this.width - (buttons * buttonWidth + gap * (buttons - 1))) / 2);
        int y = this.height - 30;

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> rebuildList())
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        addRenderableWidget(Button.builder(Component.literal("Anchor here"), b -> {
            SchematicController.anchorHere(this.minecraft);
            refreshMaterials();
        }).bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        // The label tracks the flag, so the button always says what pressing it will do.
        this.builderButton = addRenderableWidget(Button.builder(builderLabel(), b -> {
            Toggles.schematicBuilder = !Toggles.schematicBuilder;
            this.builderButton.setMessage(builderLabel());
        }).bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            SchematicController.unload();
            this.error = null;
            refreshMaterials();
        }).bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(x, y, buttonWidth, 20).build());

        rebuildList();
        refreshMaterials();
    }

    private void rebuildList() {
        if (this.list == null) {
            return;
        }

        this.list.clearRows();
        this.hiddenByLimit = 0;

        List<Path> files = SchematicLibrary.files();
        this.fileCount = files.size();

        int shown = 0;
        for (Path file : files) {
            if (shown >= RESULT_LIMIT) {
                this.hiddenByLimit = files.size() - shown;
                break;
            }
            this.list.addRow(new FileRow(file));
            shown++;
        }
    }

    /**
     * Loads a file without closing the screen. A failed read is kept on screen rather than
     * thrown away, because the usual cause is the file itself and the player is the one who
     * can fix it.
     */
    private void loadFile(Path file) {
        try {
            SchematicController.load(file, this.minecraft);
            this.error = null;
        } catch (IOException e) {
            // The controller reports what it rejected; fall back to the raw message so an
            // unreported failure is never swallowed silently.
            String reported = SchematicController.error();
            this.error = reported == null || reported.isBlank() ? e.getMessage() : reported;
        }
        refreshMaterials();
    }

    private void refreshMaterials() {
        if (!SchematicController.isLoaded()) {
            this.materials = List.of();
            this.materialsRemaining = -1;
            return;
        }

        // Returned most-needed first and immutable, so it is held as-is; the controller
        // replaces the list rather than mutating it.
        this.materials = SchematicController.materials();
        this.materialsRemaining = SchematicController.remaining();
    }

    @Override
    public void tick() {
        super.tick();

        // The queue drains while this screen is open, so the hotbar counts go stale. Re-read
        // them at most once a second, never per frame — materials() walks every placement.
        this.ticks++;
        if (this.ticks % 20 == 0 && SchematicController.isLoaded()
                && SchematicController.remaining() != this.materialsRemaining) {
            refreshMaterials();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int listWidth = listWidth();

        graphics.centeredText(this.font, this.title, this.width / 2, 6, Theme.GREEN_BRIGHT);
        graphics.text(this.font, fit("folder: " + SchematicLibrary.directory(), listWidth - 12),
                6, 18, Theme.GREEN_DIM);

        if (this.fileCount == 0) {
            drawEmptyState(graphics, listWidth);
        }

        drawReadout(graphics);

        String footer = this.fileCount + (this.fileCount == 1 ? " file" : " files")
                + " \u2022 click one to load it \u2022 Refresh re-scans the folder,"
                + " and 0 files means it is empty";
        if (this.hiddenByLimit > 0) {
            footer += " \u2022 " + this.hiddenByLimit + " more not listed";
        }
        graphics.centeredText(this.font, fit(footer, this.width - 12),
                this.width / 2, this.height - 46, Theme.GREEN_DIM);
    }

    /** Drawn where the list would be, so an empty folder is explained rather than just blank. */
    private void drawEmptyState(GuiGraphicsExtractor graphics, int listWidth) {
        int x = listWidth / 2;
        int y = LIST_TOP + 16;

        graphics.centeredText(this.font, "No schematic files found.", x, y, Theme.WHITE);
        graphics.centeredText(this.font,
                fit("Litematica exports .litematic files \u2014 put them in the folder above,", listWidth - 12),
                x, y + LINE + 6, Theme.GREEN_DIM);
        graphics.centeredText(this.font,
                fit("then press Refresh. .schem files from other tools work too.", listWidth - 12),
                x, y + 2 * (LINE + 6), Theme.GREEN_DIM);
    }

    /** The right column: the loaded schematic, then the materials it needs. */
    private void drawReadout(GuiGraphicsExtractor graphics) {
        int x = panelX();
        int width = panelWidth();
        int top = LIST_TOP;
        int bottom = this.height - FOOTER_HEIGHT;
        int textWidth = width - 12;

        graphics.fill(x, top, x + width, bottom, Theme.PANEL_BODY);
        graphics.outline(x, top, width, bottom - top, Theme.GREEN_DEEP);

        int tx = x + 6;
        int y = top + 6;

        if (SchematicController.isLoaded()) {
            // The title is the schematic's own declared name, not the file's: an exported file
            // is often named for whatever folder it sat in, while the build is named inside
            // Litematica's metadata.
            String name = SchematicController.name();
            if (name == null || name.isBlank()) {
                name = SchematicController.fileName();
            }
            graphics.text(this.font, fit(name, textWidth), tx, y, Theme.GREEN_BRIGHT);
            y += LINE + 2;

            Vec3i size = SchematicController.size();
            y = labelled(graphics, tx, y, "file", SchematicController.fileName(), Theme.WHITE, textWidth);
            y = labelled(graphics, tx, y, "format", SchematicController.format(), Theme.WHITE, textWidth);
            y = labelled(graphics, tx, y, "size",
                    size.getX() + "x" + size.getY() + "x" + size.getZ()
                            + "  \u2022  regions " + SchematicController.regions(),
                    Theme.WHITE, textWidth);
            y = labelled(graphics, tx, y, "blocks",
                    SchematicController.total() + "  \u2022  queued " + SchematicController.queued()
                            + "  \u2022  left " + SchematicController.remaining(),
                    Theme.WHITE, textWidth);
            String stall = SchematicController.state() == SchematicController.State.STALLED
                    ? SchematicController.stallReason() : "";
            String stateText = SchematicController.state().name() + (stall.isBlank() ? "" : "  " + stall);
            y = labelled(graphics, tx, y, "state", stateText, stateColor(), textWidth);
            y = labelled(graphics, tx, y, "anchor", anchorText(), Theme.WHITE, textWidth);

            int truncated = SchematicController.truncated();
            if (truncated > 0) {
                graphics.text(this.font,
                        fit("truncated: " + truncated + " placements were dropped", textWidth),
                        tx, y, MISSING);
                y += LINE;
            }

            // Blocks this client has no registry entry for: the build comes out incomplete,
            // and this is the only place that says so before the queue starts draining.
            int skipped = SchematicController.skipped();
            if (skipped > 0) {
                graphics.text(this.font,
                        fit("skipped: " + skipped + " unsupported blocks", textWidth), tx, y, MISSING);
                y += LINE;
            }

            // Places where the state the file asks for cannot be produced by placing the block
            // here — water logging, a fence connecting to what is beside it. The build is
            // complete and will not match the file exactly, so it is said rather than hidden.
            int approximate = SchematicController.approximate();
            if (approximate > 0) {
                graphics.text(this.font,
                        fit("approximate: " + approximate + " placed in another state", textWidth),
                        tx, y, MISSING);
                y += LINE;
            }
        } else {
            graphics.text(this.font, "No schematic loaded.", tx, y, Theme.GREEN_DIM);
            y += LINE;
            graphics.text(this.font, fit("Click a file in the list to load it.", textWidth), tx, y, Theme.GREEN_DIM);
            y += LINE;
        }

        if (this.error != null) {
            graphics.text(this.font, fit("load failed: " + this.error, textWidth), tx, y, MISSING);
            y += LINE;
        }

        y += 6;
        graphics.text(this.font, "MATERIALS", tx, y, Theme.GREEN_BRIGHT);
        graphics.text(this.font, fit("have 0 stalls the build",
                        textWidth - this.font.width("MATERIALS") - 6),
                tx + this.font.width("MATERIALS") + 6, y, MISSING);
        y += LINE + 2;

        if (!SchematicController.isLoaded()) {
            graphics.text(this.font, "nothing loaded", tx, y, Theme.GREEN_DIM);
            return;
        }

        if (this.materials.isEmpty()) {
            graphics.text(this.font, fit("nothing missing: the schematic needs no blocks you lack", textWidth),
                    tx, y, Theme.GREEN);
            return;
        }

        // The summary above can already use most of a short screen, so the rows are capped by
        // whatever is left of the panel and the overflow is reported.
        int rows = Math.min(Math.min(MATERIAL_LIMIT, this.materials.size()),
                Math.max(0, (bottom - y - LINE) / LINE));
        for (int i = 0; i < rows; i++) {
            SchematicController.Material material = this.materials.get(i);
            boolean missing = material.inHotbar() == 0;
            int color = missing ? MISSING : Theme.WHITE;

            String have = "have " + material.inHotbar();
            int haveWidth = this.font.width(have);
            String needed = "\u00d7" + material.needed();

            graphics.text(this.font, fit(materialName(material), textWidth - haveWidth - this.font.width(needed) - 20),
                    tx, y, color);
            graphics.text(this.font, needed,
                    tx + textWidth - haveWidth - this.font.width(needed) - 8, y, color);
            graphics.text(this.font, have, tx + textWidth - haveWidth, y, missing ? MISSING : Theme.GREEN_DIM);
            y += LINE;
        }

        if (this.materials.size() > rows) {
            graphics.text(this.font, fit("+" + (this.materials.size() - rows) + " more materials", textWidth),
                    tx, y, Theme.GREEN_DIM);
        }
    }

    /** Label in the dim accent, value beside it; returns the next line's y. */
    private int labelled(GuiGraphicsExtractor graphics, int x, int y, String label, String value,
                         int valueColor, int maxWidth) {
        graphics.text(this.font, label, x, y, Theme.GREEN_DIM);
        graphics.text(this.font, fit(value, maxWidth - LABEL_WIDTH), x + LABEL_WIDTH, y, valueColor);
        return y + LINE;
    }

    /**
     * Clips text to a pixel width, marking the cut, so a shortened path or file name cannot be
     * mistaken for the whole one.
     */
    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    private int listWidth() {
        return Math.max(150, this.width / 2 - 6);
    }

    private int panelX() {
        return listWidth() + 8;
    }

    private int panelWidth() {
        return Math.max(120, this.width - panelX() - 6);
    }

    private static Component builderLabel() {
        return Component.literal(Toggles.schematicBuilder ? "Disable builder" : "Enable builder");
    }

    private static String anchorText() {
        BlockPos anchor = SchematicController.anchor();
        return anchor == null
                ? "not set \u2014 press Anchor here"
                : anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ();
    }

    private static int stateColor() {
        return switch (SchematicController.state()) {
            case COMPLETE -> Theme.GREEN;
            case RUNNING -> Theme.GREEN_BRIGHT;
            case WALKING -> Theme.GREEN_BRIGHT;
            case STALLED -> MISSING;
            case NONE, IDLE -> Theme.WHITE;
        };
    }

    /** Item carries no no-arg display name in this version, so its description id is the
     * stable handle to translate into the label the inventory would show. */
    private static String materialName(SchematicController.Material material) {
        return Component.translatable(material.item().getDescriptionId()).getString();
    }

    private static String sizeKb(Path file) {
        try {
            return Math.max(1, (Files.size(file) + 1023) / 1024) + " KB";
        } catch (IOException e) {
            // A file that vanished between the scan and the draw is not worth an error row.
            return "? KB";
        }
    }

    @Override
    public void onClose() {
        // The builder toggle is a persisted setting, so a crash must not lose the flip.
        Config.save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    private static final class FileList extends ContainerObjectSelectionList<FileRow> {

        private final int rowWidth;

        FileList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
            this.rowWidth = Math.max(80, width - 12);
        }

        @Override
        public int getRowWidth() {
            return this.rowWidth;
        }

        /** Passthroughs: {@code addEntry}/{@code clearEntries} are protected on the vanilla list. */
        void addRow(FileRow row) {
            addEntry(row);
        }

        void clearRows() {
            clearEntries();
        }
    }

    /** One schematic file: its name and size. Clicking it loads it. */
    private final class FileRow extends ContainerObjectSelectionList.Entry<FileRow> {

        private final Path file;

        FileRow(Path file) {
            this.file = file;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            if (hovered) {
                graphics.fill(getContentX() - 2, getContentY() - 1,
                        getContentX() + getContentWidth() + 2, getContentY() + ROW_HEIGHT - 3, 0x30FFFFFF);
            }

            String size = sizeKb(this.file);
            int sizeWidth = minecraft.font.width(size);
            graphics.text(minecraft.font, fit(label(), getContentWidth() - sizeWidth - 10),
                    getContentX(), getContentY() + 4, Theme.WHITE);
            graphics.text(minecraft.font, size,
                    getContentX() + getContentWidth() - sizeWidth, getContentY() + 4, Theme.GREEN_DIM);
        }

        /**
         * The scan is recursive, so two folders can hold the same file name; the folder is
         * prefixed whenever the file is not in the root, to keep the rows distinguishable.
         */
        private String label() {
            Path name = this.file.getFileName();
            if (name == null) {
                return this.file.toString();
            }

            Path parent = this.file.getParent();
            Path holder = parent == null ? null : parent.getFileName();
            if (holder == null || parent.equals(SchematicLibrary.directory())) {
                return name.toString();
            }
            return holder + "/" + name;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            loadFile(this.file);
            return true;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return List.of();
        }
    }
}
