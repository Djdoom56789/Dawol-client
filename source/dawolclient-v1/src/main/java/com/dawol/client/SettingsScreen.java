package com.dawol.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The RSHIFT overlay: every setting as a clickable row.
 *
 * <p>Left-click advances a setting, right-click reverses it. Booleans flip. Numbers step
 * by their configured increment. The layout is generic over {@link Setting}, so new
 * settings appear here automatically.
 */
public final class SettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_TOP = 32;

    private final Screen parent;
    private SettingList list;

    public SettingsScreen(Screen parent) {
        super(Component.literal("DawolClient Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listHeight = this.height - LIST_TOP - 44;

        this.list = new SettingList(this.minecraft, this.width, listHeight, LIST_TOP, ROW_HEIGHT);
        for (Setting setting : Settings.all()) {
            this.list.addRow(new SettingRow(setting));
        }
        addRenderableWidget(this.list);

        int buttonY = this.height - 34;
        int buttonWidth = 150;

        addRenderableWidget(Button.builder(Component.literal("Block ESP picker"), b ->
                        this.minecraft.setScreenAndShow(new BlockPickerScreen(this)))
                .bounds(this.width / 2 - buttonWidth - 4, buttonY, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 + 4, buttonY, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 12, Theme.GREEN_BRIGHT);

        int selected = BlockSelection.size();
        String footer = "Left-click to change \u2022 right-click to reverse \u2022 "
                + selected + " block" + (selected == 1 ? "" : "s") + " selected";
        graphics.centeredText(this.font, footer, this.width / 2, this.height - 46, Theme.GREEN_DIM);
    }

    @Override
    public void onClose() {
        // Persist on exit so a crash cannot lose the session's changes.
        Config.save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    /** Scrollable list of setting rows. */
    private static final class SettingList extends ContainerObjectSelectionList<SettingRow> {
        SettingList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        /** Passthrough: {@code addEntry} is protected on the vanilla list. */
        void addRow(SettingRow row) {
            addEntry(row);
        }
    }

    /** One setting: label on the left, current value on the right. */
    private final class SettingRow extends ContainerObjectSelectionList.Entry<SettingRow> {

        private final Setting setting;

        SettingRow(Setting setting) {
            this.setting = setting;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            boolean active = this.setting.isActive();
            int labelColor = active ? Theme.WHITE : Theme.GREEN_DIM;
            int valueColor = active ? Theme.GREEN : Theme.GREEN_DIM;

            if (hovered) {
                graphics.fill(getContentX() - 2, getContentY() - 2,
                        getContentX() + getContentWidth() + 2, getContentY() + ROW_HEIGHT - 2,
                        0x30FFFFFF);
            }

            graphics.text(minecraft.font, this.setting.label(),
                    getContentX(), getContentY() + 4, labelColor);

            String value = this.setting.valueText();
            int valueWidth = minecraft.font.width(value);
            graphics.text(minecraft.font, value,
                    getContentX() + getContentWidth() - valueWidth, getContentY() + 4, valueColor);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            // Button 1 is the right mouse button; anything else counts as a forward click.
            boolean backwards = event.button() == 1;
            this.setting.click(backwards);
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
