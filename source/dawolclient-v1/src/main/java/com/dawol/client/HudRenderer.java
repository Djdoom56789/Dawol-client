package com.dawol.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * On-screen status panel, themed to the GreenPvP pack palette.
 *
 * <p>Drawn as a HUD element so it is readable without opening a screen. Plain text and
 * fills only; no textures or atlas access are involved.
 */
public final class HudRenderer {

    private HudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        // The element is registered unconditionally, so the module flag is what decides
        // whether the panel is on screen.
        if (!Toggles.statusHud) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        List<Line> lines = buildLines(mc);
        if (lines.isEmpty()) {
            return;
        }

        var font = mc.font;
        int lineHeight = font.lineHeight + 1;

        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, font.width(line.text()));
        }

        int x = 4;
        int y = 4;
        int padding = 4;

        // Panel backing, with a one-pixel accent rule down the left edge.
        graphics.fill(x - padding, y - padding,
                x + width + padding, y + lines.size() * lineHeight + padding,
                Theme.PANEL);
        graphics.fill(x - padding, y - padding,
                x - padding + 1, y + lines.size() * lineHeight + padding,
                Theme.GREEN);

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            graphics.text(font, line.text(), x, y + i * lineHeight, line.color());
        }
    }

    /** One rendered row: text plus its colour. */
    private record Line(String text, int color) {
    }

    private static List<Line> buildLines(Minecraft mc) {
        List<Line> lines = new ArrayList<>();

        lines.add(new Line("dawolclient", Theme.GREEN_BRIGHT));

        StringBuilder esp = new StringBuilder("ESP ");
        esp.append(Toggles.anyEspActive() ? "on" : "off");
        if (Toggles.espPlayers) {
            esp.append(" P");
        }
        if (Toggles.espMobs) {
            esp.append(" M");
        }
        if (Toggles.espItems) {
            esp.append(" I");
        }
        if (Toggles.espThroughWalls) {
            esp.append(" [walls]");
        }
        lines.add(new Line(esp.toString(), Toggles.anyEspActive() ? Theme.GREEN : Theme.GREEN_DIM));

        if (Toggles.stashFinder) {
            lines.add(new Line("Stash  " + StashFinder.summary(), Theme.GREEN));
        } else {
            lines.add(new Line("Stash finder off", Theme.GREEN_DIM));
        }

        // Shown whenever a file is loaded rather than only while the module is on: the point
        // of the line is to say what the build is doing, including "stopped, and why".
        if (SchematicController.isLoaded()) {
            SchematicController.State state = SchematicController.state();
            var size = SchematicController.size();
            String stall = state == SchematicController.State.STALLED
                    ? SchematicController.stallReason() : "";
            lines.add(new Line("Schematic  " + SchematicController.fileName()
                    + "  " + size.getX() + "x" + size.getY() + "x" + size.getZ()
                    + "  " + state + " " + SchematicController.remaining()
                    + "/" + SchematicController.queued()
                    + (stall.isBlank() ? "" : "  " + stall),
                    schematicColor(state)));
        }

        if (Toggles.totemCounter) {
            int offhand = CorzModules.offhandTotems(mc);
            int total = CorzModules.totalTotems(mc);
            lines.add(new Line("Totems  " + offhand + " off / " + total + " total",
                    total > 0 ? Theme.GREEN : Theme.ITEM));
        }

        if (Toggles.blockEsp) {
            lines.add(new Line("Block ESP  " + BlockSelection.size() + " block(s)", Theme.GREEN));
        }

        if (Toggles.combatAssist) {
            lines.add(new Line("Combat on  reach<=" + String.format("%.1f", Toggles.combatMaxReach),
                    Theme.GREEN));
        } else {
            lines.add(new Line("Combat off", Theme.GREEN_DIM));
        }

        // Surface the moment the configuration becomes conspicuous, so the state is never
        // a surprise.
        if (Toggles.anyBlatantFeatureActive()) {
            lines.add(new Line("! visible mode", Theme.ITEM));
        }

        return lines;
    }

    /** Running and complete read as fine; stalled is the one the user has to act on. */
    private static int schematicColor(SchematicController.State state) {
        return switch (state) {
            case RUNNING -> Theme.GREEN;
            case WALKING -> Theme.GREEN;
            case COMPLETE -> Theme.GREEN_BRIGHT;
            case STALLED -> Theme.ITEM;
            default -> Theme.GREEN_DIM;
        };
    }
}
