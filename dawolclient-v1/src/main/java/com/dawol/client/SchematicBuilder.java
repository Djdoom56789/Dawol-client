package com.dawol.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block a schematic wants placed, and the order the builder visits them in.
 *
 * <p>The order matters more than it looks. Placing top-down leaves blocks hanging with
 * nothing under them, and a server that validates support rejects the interaction — so the
 * build stalls and looks broken rather than merely looking wrong. Bottom-up by layer is the
 * only order that always places onto something.
 *
 * <p>The distance tiebreak inside a layer keeps the build from zig-zagging between the two
 * far ends of the same plane, and the final x/z comparison makes the result deterministic
 * whatever order the file's own palette sweep happened to produce.
 *
 * <p>This is a pure function of the placement list, so the ordering rule is verifiable
 * without a world or a GL context.
 */
public final class SchematicBuilder {

    /** One block that the schematic wants placed. */
    public record Placement(BlockPos pos, BlockState state) {
    }

    private SchematicBuilder() {
    }

    /**
     * Orders placements bottom-up, then by distance from {@code eye}, then by position.
     *
     * @param eyeX eye position at plan time; only affects the order within a layer
     */
    public static List<SchematicBuilder.Placement> order(
            List<SchematicBuilder.Placement> placements, double eyeX, double eyeY, double eyeZ) {
        List<SchematicBuilder.Placement> out = new ArrayList<>(placements);
        out.sort(Comparator
                .comparingInt((SchematicBuilder.Placement p) -> p.pos().getY())
                .thenComparingDouble(p -> distanceSquared(p.pos(), eyeX, eyeY, eyeZ))
                .thenComparingInt(p -> p.pos().getX())
                .thenComparingInt(p -> p.pos().getZ()));
        return out;
    }

    private static double distanceSquared(BlockPos pos, double x, double y, double z) {
        double dx = pos.getX() + 0.5D - x;
        double dy = pos.getY() + 0.5D - y;
        double dz = pos.getZ() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
