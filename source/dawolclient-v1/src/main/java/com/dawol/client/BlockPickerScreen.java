package com.dawol.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Searchable picker over every block in the registry. Clicking a row toggles that block's
 * ESP on or off.
 *
 * <p>Results are capped: the registry holds well over a thousand blocks, and building a
 * widget per entry for all of them at once is wasteful when a search box can narrow it.
 * A cap of {@link #RESULT_LIMIT} is applied per query and the overflow is reported.
 */
public final class BlockPickerScreen extends Screen {

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 56;
    private static final int RESULT_LIMIT = 400;

    /** Handy starting point, offered as a quick-add. */
    private static final String[] COMMON_ORES = {
            "diamond_ore", "deepslate_diamond_ore", "ancient_debris", "emerald_ore",
            "deepslate_emerald_ore", "gold_ore", "deepslate_gold_ore", "nether_gold_ore",
            "iron_ore", "deepslate_iron_ore", "redstone_ore", "deepslate_redstone_ore",
            "lapis_ore", "deepslate_lapis_ore", "copper_ore", "deepslate_copper_ore",
            "coal_ore", "deepslate_coal_ore", "nether_quartz_ore"
    };

    private final Screen parent;
    private EditBox search;
    private BlockList list;
    private String filter = "";
    private int hiddenByLimit = 0;

    public BlockPickerScreen(Screen parent) {
        super(Component.literal("Block ESP"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.search = new EditBox(this.font, this.width / 2 - 150, 26, 300, 18,
                Component.literal("Search blocks"));
        this.search.setResponder(value -> {
            this.filter = value == null ? "" : value;
            rebuildList();
        });
        this.search.setValue(this.filter);
        addRenderableWidget(this.search);

        addRenderableWidget(Button.builder(Component.literal("Add common ores"), b -> {
            for (String path : COMMON_ORES) {
                Block block = BlockSelection.byId("minecraft:" + path);
                if (block != null) {
                    BlockSelection.select(block);
                }
            }
            rebuildList();
        }).bounds(this.width / 2 - 235, 26, 80, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> {
            BlockSelection.clear();
            rebuildList();
        }).bounds(this.width / 2 + 155, 26, 80, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());

        int listHeight = this.height - LIST_TOP - 42;
        this.list = new BlockList(this.minecraft, this.width, listHeight, LIST_TOP, ROW_HEIGHT);
        addRenderableWidget(this.list);

        rebuildList();
    }

    private void rebuildList() {
        if (this.list == null) {
            return;
        }

        this.list.clearRows();
        this.hiddenByLimit = 0;

        List<Block> matches = BlockSelection.search(this.filter);
        int shown = 0;
        for (Block block : matches) {
            if (shown >= RESULT_LIMIT) {
                this.hiddenByLimit = matches.size() - shown;
                break;
            }
            this.list.addRow(new BlockRow(block));
            shown++;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 10, Theme.GREEN_BRIGHT);

        String status = BlockSelection.size() + " selected";
        if (this.hiddenByLimit > 0) {
            status += "  \u2022  " + this.hiddenByLimit + " more matches, refine the search";
        }
        graphics.centeredText(this.font, status, this.width / 2, this.height - 42, Theme.GREEN_DIM);
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    private static final class BlockList extends ContainerObjectSelectionList<BlockRow> {
        BlockList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 380;
        }

        /** Passthroughs: {@code addEntry}/{@code clearEntries} are protected on the vanilla list. */
        void addRow(BlockRow row) {
            addEntry(row);
        }

        void clearRows() {
            clearEntries();
        }
    }

    /** One block: a tick box, the display name, and the full registry id. */
    private final class BlockRow extends ContainerObjectSelectionList.Entry<BlockRow> {

        private final Block block;

        BlockRow(Block block) {
            this.block = block;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            boolean selected = BlockSelection.isSelected(this.block);
            int color = BlockSelection.colorOf(this.block);

            int x = getContentX();
            int y = getContentY();

            if (hovered) {
                graphics.fill(x - 2, y - 1, x + 320, y + ROW_HEIGHT - 3, 0x30FFFFFF);
            }

            // Tick indicator, drawn as a filled swatch in the block's own colour.
            if (selected) {
                graphics.fill(x, y + 3, x + 8, y + 11, color);
            } else {
                graphics.fill(x, y + 3, x + 8, y + 4, Theme.GREEN_DIM);
                graphics.fill(x, y + 10, x + 8, y + 11, Theme.GREEN_DIM);
                graphics.fill(x, y + 3, x + 1, y + 11, Theme.GREEN_DIM);
                graphics.fill(x + 7, y + 3, x + 8, y + 11, Theme.GREEN_DIM);
            }

            int labelColor = selected ? color : Theme.WHITE;
            graphics.text(minecraft.font, BlockSelection.displayName(this.block),
                    x + 14, y + 4, labelColor);

            String id = BlockSelection.idOf(this.block);
            int idWidth = minecraft.font.width(id);
            graphics.text(minecraft.font, id, x + 320 - idWidth, y + 4, Theme.GREEN_DIM);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            boolean wasSelected = BlockSelection.isSelected(this.block);

            // Left-click toggles. Right-click selects without toggling off.
            if (event.button() == 1) {
                BlockSelection.select(this.block);
            } else if (wasSelected) {
                BlockSelection.toggle(this.block);
            } else {
                BlockSelection.select(this.block);
            }
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
