package com.dawol.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chooses which neighbour to click, and where on it, so a block lands in the state the
 * schematic asks for.
 *
 * <p>Vanilla derives a block's orientation from two things the builder controls: the face it
 * is clicked against, and where the player is standing and looking. Getting either wrong
 * produces a build that is structurally right and visually wrong — stairs facing away, logs
 * lying on their side, torches on the wrong wall. That is a worse failure than a block that
 * simply did not place, because it looks finished.
 *
 * <p>Rather than reimplementing vanilla's placement rules per block (stair, trapdoor, door,
 * wall torch, glazed terracotta, chain, ...), the search asks the block itself:
 * {@code Block.getStateForPlacement} is public, so the state a given click would produce can
 * be computed without touching the world. Prediction is exact by construction and cannot
 * drift from the game's own rules the way a table of special cases would.
 *
 * <p>The prediction reads the player's facing, so it is made with the rotation the click
 * would be aimed at, and the player's own rotation is restored afterwards. Candidates come
 * back ranked best-first, because the caller is the one that knows which of them it can
 * actually see and reach.
 *
 * <p>A candidate that scores below {@link #PERFECT} is still usable — some states cannot be
 * produced by placement at all (a fence connects to whatever is beside it, water logs itself)
 * — so the ranking is advice, not a filter.
 */
public final class SchematicPlacer {

    /**
     * Faces to try, floor first.
     *
     * <p>This is the order a person builds in: off the ground where possible, off a wall when
     * there is nothing under it, off the ceiling as a last resort.
     */
    public static final Direction[] FACE_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

    /**
     * Where on a face to click, as the fraction of the way down it.
     *
     * <p>The centre is the natural target. The two off-centre points exist because half the
     * blocks in a build — stairs, trapdoors, doors — take their half from where on the face
     * the click landed, and the centre of a side face is exactly the case where vanilla has
     * to guess.
     */
    private static final double[] FACE_FRACTIONS = {0.5D, 0.25D, 0.75D};

    /**
     * Score of a prediction that matches the wanted state exactly.
     *
     * <p>Higher than any partial score can reach, so an exact candidate always outranks an
     * approximate one regardless of how many properties the block has.
     */
    public static final int PERFECT = 1000;

    /** How far from a target the builder stands when it has to walk to a particular side. */
    private static final double STAND_DISTANCE = 3.0D;

    /**
     * One way to place the block: click {@code click}, on the {@code face} of {@code support}.
     *
     * @param predicted the state vanilla would produce, straight from the block's own rules
     * @param score     how well {@code predicted} matches the wanted state; -1 if the click
     *                  cannot place this block at all
     */
    public record Choice(BlockPos support, Direction face, Vec3 click, BlockState predicted, int score) {

        public boolean perfect() {
            return score >= PERFECT;
        }

        /** The same thing in the form the executor acts on. */
        public SchematicExecutor.Surface surface() {
            return new SchematicExecutor.Surface(support, face, click, support);
        }
    }

    private SchematicPlacer() {
    }

    // --- candidate search ---

    /**
     * Every click that could place {@code required} at {@code target}, best first.
     *
     * <p>Ties keep the order they were generated in, which is {@link #FACE_ORDER} and then the
     * face fractions, so an unremarkable block is still placed off the floor first.
     *
     * @param solid which neighbouring positions hold something to click against
     * @param eye   the position the click will be aimed from; a hypothetical stand point can
     *              be passed, which is how the builder decides where it needs to walk to
     */
    public static List<Choice> rank(BlockState required, Player player, ItemStack stack,
                                    BlockPos target, Vec3 eye, Predicate<BlockPos> solid) {
        List<Choice> out = new ArrayList<>();
        if (required == null || target == null || solid == null) {
            return out;
        }

        boolean onAWall = wallMounted(required);
        for (Direction face : FACE_ORDER) {
            if (onAWall && face.getAxis().isVertical()) {
                // A wall-mounted block needs a wall clicked, whatever the prediction says.
                continue;
            }
            BlockPos support = target.relative(face);
            if (!solid.test(support)) {
                continue;
            }

            // The face of the support that touches the target, which is the one clicked.
            Direction clicked = face.getOpposite();
            for (Vec3 click : clickPoints(support, clicked)) {
                BlockState predicted = predict(required, player, stack, support, clicked, click, eye);
                int score = score(predicted, required);
                if (score >= 0) {
                    out.add(new Choice(support, clicked, click, predicted, score));
                }
            }
        }

        // Stable, so equal scores stay in generation order.
        out.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return out;
    }

    /**
     * The state vanilla would place, for a click at {@code click} on {@code clicked}.
     *
     * <p>The player's rotation is set to the aim the click implies and restored afterwards:
     * {@code getStateForPlacement} takes the horizontal facing and the look direction from the
     * player, so a prediction made with stale rotation would describe a block nobody is about
     * to place.
     *
     * @return the predicted state, or null when the block refuses to be placed there
     */
    private static BlockState predict(BlockState required, Player player, ItemStack stack,
                                      BlockPos support, Direction clicked, Vec3 click, Vec3 eye) {
        float savedYaw = player.getYRot();
        float savedPitch = player.getXRot();
        try {
            player.setYRot(yawTo(eye, click));
            player.setXRot(pitchTo(eye, click));
            BlockHitResult hit = new BlockHitResult(click, clicked, support, false);
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
            return required.getBlock().getStateForPlacement(context);
        } finally {
            player.setYRot(savedYaw);
            player.setXRot(savedPitch);
        }
    }

    // --- geometry ---

    /**
     * Points to click on a face, in the order to try them.
     *
     * <p>A face whose normal is vertical pins the block's half on its own, so its centre is
     * the only candidate that means anything. A side face leaves the vertical position free,
     * and that is the lever for half-height blocks.
     */
    public static List<Vec3> clickPoints(BlockPos support, Direction face) {
        if (face.getAxis().isVertical()) {
            return List.of(clickPoint(support, face, 0.5D, 0.5D));
        }
        return List.of(
                clickPoint(support, face, 0.5D, 0.5D),
                clickPoint(support, face, 0.5D, 0.25D),
                clickPoint(support, face, 0.5D, 0.75D));
    }

    /**
     * A point on the given face of {@code support}.
     *
     * <p>At {@code u = v = 0.5} this is the centre of the face, on its surface: the natural
     * click, and the one a person makes. The parameters run across the face (for a vertical
     * normal) or across its horizontal extent (for a horizontal one).
     */
    public static Vec3 clickPoint(BlockPos support, Direction face, double u, double v) {
        double onFaceX = support.getX() + 0.5D + face.getStepX() * 0.5D;
        double onFaceY = support.getY() + 0.5D + face.getStepY() * 0.5D;
        double onFaceZ = support.getZ() + 0.5D + face.getStepZ() * 0.5D;
        return switch (face.getAxis()) {
            case X -> new Vec3(onFaceX, support.getY() + v, support.getZ() + u);
            case Y -> new Vec3(support.getX() + u, onFaceY, support.getZ() + v);
            case Z -> new Vec3(support.getX() + u, support.getY() + v, onFaceZ);
        };
    }

    /** Yaw that looks from {@code eye} at {@code point}, in the game's convention. */
    public static float yawTo(Vec3 eye, Vec3 point) {
        double dx = point.x - eye.x;
        double dz = point.z - eye.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    /** Pitch that looks from {@code eye} at {@code point}, positive downward. */
    public static float pitchTo(Vec3 eye, Vec3 point) {
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    // --- scoring ---

    /**
     * How closely a predicted state matches the wanted one.
     *
     * <p>Property count rather than a yes/no, so that among several wrong candidates the one
     * that is wrong in fewer ways wins. A block mismatch scores nothing at all: placing the
     * wrong block is not a near miss, it is a different mistake with its own recovery path.
     *
     * @return 0..{@link #PERFECT}, or -1 when {@code predicted} is null or a different block
     */
    public static int score(BlockState predicted, BlockState required) {
        if (predicted == null || required == null || predicted.getBlock() != required.getBlock()) {
            return -1;
        }
        if (predicted == required) {
            return PERFECT;
        }

        int matched = 0;
        int total = 0;
        for (Property<?> property : required.getProperties()) {
            total++;
            if (sameValue(property, predicted, required)) {
                matched++;
            }
        }
        if (total == 0) {
            return PERFECT;
        }
        // Capped below PERFECT: matching every property is what equality means, and that case
        // returned above, so a partial score must never be able to claim a perfect one.
        return matched * (PERFECT - 1) / total;
    }

    /**
     * Which side of the block the player must stand on for it to take its wanted orientation,
     * or null when the wanted state does not care.
     *
     * <p>Every horizontal facing in the game is "the way the block looks", and vanilla points
     * it at whoever placed it: a stair ascends toward the player, a chest opens toward them, a
     * wall torch points at them. Standing on the facing side is therefore both what produces
     * the state and what a person doing it by hand would do.
     */
    public static Direction preferredStand(BlockState required) {
        if (required == null) {
            return null;
        }
        for (Property<?> property : required.getProperties()) {
            Direction facing = facingOf(property, required);
            if (facing != null) {
                return facing.getAxis().isHorizontal() ? facing : null;
            }
        }
        return null;
    }

    /**
     * Where to stand to build at {@code target}.
     *
     * <p>A block with an orientation to respect gets its facing side, because that is the only
     * side the wanted state can be produced from. Anything else is approached from wherever
     * the player already is, which keeps the walk as short as the build allows.
     */
    public static Vec3 standGoal(BlockState required, BlockPos target, Vec3 eye) {
        Vec3 centre = new Vec3(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);

        Direction facing = preferredStand(required);
        if (facing != null) {
            return centre.add(facing.getStepX() * STAND_DISTANCE, 0.0D, facing.getStepZ() * STAND_DISTANCE);
        }

        double dx = eye.x - centre.x;
        double dz = eye.z - centre.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return centre.add(STAND_DISTANCE, 0.0D, 0.0D);
        }
        return centre.add(dx / length * STAND_DISTANCE, 0.0D, dz / length * STAND_DISTANCE);
    }

    /**
     * The item that places this block.
     *
     * <p>For most blocks that is the block's own item. The wall and attached variants are the
     * exception: a wall torch, a wall sign, a wall banner and an attached stem share the item
     * of their upright form and have no item of their own, so
     * {@code state.getBlock().asItem()} answers {@code air} for them and a builder that trusts
     * it concludes those blocks cannot be placed at all. Falls back to the upright form by
     * name, which is a rename rather than a table of every variant the game has.
     */
    public static Item itemFor(BlockState state) {
        if (state == null) {
            return Items.AIR;
        }
        Item item = state.getBlock().asItem();
        if (item != Items.AIR) {
            return item;
        }

        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String upright = path.replace("_wall_", "_").replace("wall_", "").replace("attached_", "");
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(upright));
        return block == null ? Items.AIR : block.asItem();
    }

    /**
     * Whether a block mounts on a wall rather than standing on the ground.
     *
     * <p>The wall-mounted blocks — wall torches, wall signs, wall banners — share the item of
     * their upright form, and it is the item that decides which of the two gets placed: click
     * the floor with a torch and a torch stands up, click a wall and a wall torch appears. The
     * block's own placement rules cannot see that, so a floor click can be predicted correctly
     * and still put the wrong block in the world. Only walls are offered for these.
     *
     * <p>"wall_" rather than "wall": a cobblestone wall has the word in its name and none of
     * the behaviour.
     */
    public static boolean wallMounted(BlockState required) {
        if (required == null) {
            return false;
        }
        return BuiltInRegistries.BLOCK.getKey(required.getBlock()).getPath().contains("wall_");
    }

    // --- property reading ---

    /**
     * The value of a property named {@code facing}, when it holds a direction.
     *
     * <p>Matched by name rather than by holding a reference to
     * {@code HorizontalDirectionalBlock.FACING}, because the same property is shared by every
     * one of the dozens of blocks that face somewhere and they do not all expose it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Direction facingOf(Property<?> property, BlockState state) {
        if (!"facing".equals(property.getName())) {
            return null;
        }
        Object value = state.getValue((Property) property);
        return value instanceof Direction direction ? direction : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean sameValue(Property<?> property, BlockState a, BlockState b) {
        return a.getValue((Property) property).equals(b.getValue((Property) property));
    }
}
