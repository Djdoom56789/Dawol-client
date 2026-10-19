package com.dawol.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Krypton-style module overlay: dark sidebar on the left, scrollable module list
 * in the centre, description panel at the bottom.
 *
 * <ul>
 *   <li>Left sidebar (90px): client name header + one row per category. Clicking a
 *       row filters the module list to that category (or "All" to show every module).</li>
 *   <li>Centre: vertical list of module rows for the selected category, each showing
 *       a green left-border pip when active.</li>
 *   <li>Bottom bar: search field left, description of the hovered module right.</li>
 * </ul>
 *
 * <p>Flat fills + 1px borders only. No textures, no rounded corners.
 */
public final class ModuleScreen extends Screen {

    // Layout constants
    private static final int SIDEBAR_W   = 90;
    private static final int TITLE_H     = 20;
    private static final int CAT_ROW_H   = 14;
    private static final int ROW_H       = 13;
    private static final int BOTTOM_H    = 26;
    private static final int SEARCH_W    = 110;
    private static final int PADDING     = 4;
    private static final int PIP_W       = 3;

    private final Screen parent;

    /** Selected category; null = show all. */
    private Module.Category selectedCategory = null;

    /** The module under the cursor, shown in the description panel. */
    private Module hovered;

    private EditBox search;
    private String filter = "";

    /** Rows built by rebuildRows(). */
    private final List<ModRow> rows = new ArrayList<>();

    public ModuleScreen(Screen parent) {
        super(Component.literal("DawolClient"));
        this.parent = parent;
    }

    // ---- row record ----
    private record ModRow(Module module, String label, int x, int y, int w) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + ROW_H - 1;
        }
    }

    // ---- category row bounds ----
    private record CatRow(Module.Category cat, int y) {
        boolean hit(double mx, double my, int sbW) {
            return mx >= 0 && mx < sbW && my >= y && my < y + CAT_ROW_H;
        }
    }

    private List<CatRow> catRows = new ArrayList<>();

    @Override
    protected void init() {
        // Search field — bottom left of the content area.
        int searchX = SIDEBAR_W + PADDING;
        int searchY = this.height - BOTTOM_H + (BOTTOM_H - 12) / 2;
        this.search = new EditBox(this.font, searchX, searchY, SEARCH_W, 12,
                Component.literal("Search..."));
        this.search.setMaxLength(40);
        this.search.setResponder(v -> {
            this.filter = v == null ? "" : v;
            rebuildRows();
        });
        this.search.setValue(this.filter);
        addRenderableWidget(this.search);

        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        if (this.minecraft == null) return;

        int contentX = SIDEBAR_W + PADDING;
        int contentY = PADDING;
        int contentW  = this.width - SIDEBAR_W - PADDING * 2;
        int rowW = contentW - 2;
        int y = contentY;

        for (Module mod : Modules.all()) {
            if (selectedCategory != null && mod.category() != selectedCategory) continue;
            if (!matches(mod)) continue;
            if (y + ROW_H > this.height - BOTTOM_H) break;
            rows.add(new ModRow(mod, trim(mod.name(), rowW - PIP_W - 6), contentX + 1, y, rowW));
            y += ROW_H;
        }
    }

    private boolean matches(Module mod) {
        if (filter.isBlank()) return true;
        String n = filter.toLowerCase();
        return mod.name().toLowerCase().contains(n)
                || mod.description().toLowerCase().contains(n);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        int h = this.height;
        int w = this.width;

        // ---- Full background ----
        g.fill(0, 0, w, h, Theme.PANEL_DEEP);

        // ---- Sidebar ----
        g.fill(0, 0, SIDEBAR_W, h, Theme.SIDEBAR);
        // 1px right border in green
        g.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, h, Theme.GREEN_DEEP);

        // Client title
        g.fill(0, 0, SIDEBAR_W, TITLE_H, Theme.CHROME);
        g.fill(0, TITLE_H - 1, SIDEBAR_W, TITLE_H, Theme.GREEN);
        String title = "DawolClient";
        g.text(this.font, title, (SIDEBAR_W - this.font.width(title)) / 2, (TITLE_H - 8) / 2, Theme.GREEN_BRIGHT);

        // Category rows
        catRows.clear();
        int cy = TITLE_H + 2;

        // "All" pseudo-category
        boolean allSel = selectedCategory == null;
        g.fill(0, cy, SIDEBAR_W - 1, cy + CAT_ROW_H,
                allSel ? Theme.SIDEBAR_SELECTED : (isCatHovered(mx, my, cy) ? Theme.ROW_HOVER : 0));
        if (allSel) g.fill(0, cy, PIP_W, cy + CAT_ROW_H, Theme.GREEN);
        g.text(this.font, "All", PADDING + PIP_W + 2, cy + (CAT_ROW_H - 8) / 2, allSel ? Theme.GREEN_BRIGHT : Theme.WHITE);
        catRows.add(new CatRow(null, cy));
        cy += CAT_ROW_H;

        for (Module.Category cat : Module.Category.values()) {
            boolean sel = cat == selectedCategory;
            boolean catHov = isCatHovered(mx, my, cy);
            g.fill(0, cy, SIDEBAR_W - 1, cy + CAT_ROW_H,
                    sel ? Theme.SIDEBAR_SELECTED : (catHov ? Theme.ROW_HOVER : 0));
            int accent = accentFor(cat);
            if (sel) g.fill(0, cy, PIP_W, cy + CAT_ROW_H, accent);
            g.text(this.font, cat.label(), PADDING + PIP_W + 2, cy + (CAT_ROW_H - 8) / 2,
                    sel ? accent : Theme.WHITE);
            catRows.add(new CatRow(cat, cy));
            cy += CAT_ROW_H;
        }

        // ---- Module list (centre) ----
        int contentX = SIDEBAR_W + PADDING;
        int contentW  = w - SIDEBAR_W - PADDING * 2;
        // Column header
        String catLabel = selectedCategory != null ? selectedCategory.label() : "All Modules";
        g.fill(contentX, 0, contentX + contentW, PADDING + 1, Theme.CHROME);
        g.text(this.font, catLabel, contentX + 2, 1, Theme.GREEN);
        g.fill(contentX, PADDING, contentX + contentW, PADDING + 1, Theme.GREEN_DEEP);

        hovered = null;
        for (ModRow row : rows) {
            boolean on  = row.module().isEnabled();
            boolean hov = row.hit(mx, my);
            if (hov) hovered = row.module();

            int bg = hov ? Theme.ROW_HOVER : (on ? Theme.ROW_ON : Theme.ROW_OFF);
            g.fill(row.x(), row.y(), row.x() + row.w(), row.y() + ROW_H - 1, bg);

            // Active pip
            if (on) {
                int accent = accentFor(row.module().category());
                g.fill(row.x(), row.y(), row.x() + PIP_W, row.y() + ROW_H - 1, accent);
            }

            g.text(this.font, row.label(),
                    row.x() + PIP_W + 3, row.y() + (ROW_H - 1 - 8) / 2,
                    on ? Theme.GREEN_BRIGHT : Theme.WHITE);
        }

        // ---- Bottom bar ----
        int barY = h - BOTTOM_H;
        g.fill(SIDEBAR_W, barY, w, h, Theme.CHROME);
        g.fill(SIDEBAR_W, barY, w, barY + 1, Theme.GREEN_DEEP);

        super.extractRenderState(g, mx, my, pt);

        // Description — right side of bottom bar
        if (hovered != null) {
            int descX = SIDEBAR_W + SEARCH_W + PADDING * 3;
            int descY = barY + (BOTTOM_H - 8) / 2 - 4;
            int descW = w - descX - PADDING;
            g.text(this.font, trim(hovered.name(), descW / 2), descX, descY,
                    hovered.isEnabled() ? Theme.GREEN_BRIGHT : Theme.WHITE);
            g.text(this.font, trim(hovered.description(), descW),
                    descX, descY + 10, Theme.GREEN_DIM);
        }
    }

    private boolean isCatHovered(int mx, int my, int rowY) {
        return mx >= 0 && mx < SIDEBAR_W - 1 && my >= rowY && my < rowY + CAT_ROW_H;
    }

    private static int accentFor(Module.Category cat) {
        if (cat == null) return Theme.GREEN;
        return switch (cat) {
            case COMBAT -> Theme.CAT_COMBAT;
            case RENDER -> Theme.CAT_RENDER;
            case WORLD  -> Theme.CAT_WORLD;
            case PLAYER -> Theme.CAT_PLAYER;
            case MISC   -> Theme.CAT_MISC;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x();
        double ey = event.y();

        // Sidebar category click
        if (ex < SIDEBAR_W) {
            for (CatRow cr : catRows) {
                if (cr.hit(ex, ey, SIDEBAR_W)) {
                    selectedCategory = cr.cat();
                    rebuildRows();
                    return true;
                }
            }
        }

        // Module row click
        for (ModRow row : rows) {
            if (row.hit(ex, ey)) {
                row.module().toggle();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    private String trim(String text, int maxW) {
        if (text == null) return "";
        if (this.font.width(text) <= maxW) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (this.font.width(sb + "" + c + ".") > maxW) break;
            sb.append(c);
        }
        return sb + ".";
    }
}
