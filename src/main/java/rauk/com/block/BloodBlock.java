package rauk.com.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import rauk.com.registry.RaukBlocks;

/**
 * Blood on the floor (design doc 9): drops from a wound, the smear left by dragging a kill, the
 * pool where it fed. A stain, not a block you bump into: nothing collides with it and anything
 * placed there replaces it. It dries and fades over a few minutes (pools take longer), then is
 * gone.
 */
public class BloodBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BloodBlock> CODEC = simpleCodec(BloodBlock::new);
	public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
	private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, 0.5, 16);

	public enum Shape implements StringRepresentable {
		/** A few drops from a bleeding wound. */
		DROPS("drops", 0.5f),
		/** A smeared streak, from something dragged across the floor. */
		SMEAR("smear", 0.3f),
		/** A wide pool where something was killed or eaten. */
		POOL("pool", 0.12f);

		private final String name;
		/** The chance on each random tick that it dries one stage further. */
		final float drying;

		Shape(String name, float drying) {
			this.name = name;
			this.drying = drying;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public BloodBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.DROPS).setValue(AGE, 0));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SHAPE, AGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
			BlockPos neighborPos) {
		return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextFloat() >= state.getValue(SHAPE).drying) {
			return;
		}
		int age = state.getValue(AGE);
		if (age >= 3) {
			level.removeBlock(pos, false);
		} else {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Leaves blood on the floor at or below a point (up to four blocks down, so drops from a wall
	 * or ceiling land on the floor). A heavier stain replaces a lighter one; the same kind is
	 * freshened. Returns where it went, or null if there was nowhere for it.
	 */
	@Nullable
	public static BlockPos stain(ServerLevel level, Vec3 at, Shape shape, Direction facing) {
		BlockPos p = BlockPos.containing(at);
		if (!room(level.getBlockState(p))) {
			// Just inside the floor: the stain goes on top of it.
			p = p.above();
		}
		BlockState state = RaukBlocks.BLOOD.defaultBlockState().setValue(FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH)
				.setValue(SHAPE, shape);
		for (int i = 0; i < 5; i++, p = p.below()) {
			BlockState here = level.getBlockState(p);
			if (!room(here)) {
				return null;
			}
			if (!state.canSurvive(level, p)) {
				continue;
			}
			if (here.is(RaukBlocks.BLOOD) && here.getValue(SHAPE).ordinal() > shape.ordinal()) {
				// Don't turn a pool into drops: just freshen it.
				level.setBlock(p, here.setValue(AGE, 0), Block.UPDATE_CLIENTS);
			} else {
				level.setBlock(p, state, Block.UPDATE_ALL);
			}
			return p.immutable();
		}
		return null;
	}

	/** Empty, or something a stain can go over (grass, another stain), and dry. */
	private static boolean room(BlockState state) {
		return (state.is(RaukBlocks.BLOOD) || state.canBeReplaced()) && state.getFluidState().isEmpty();
	}
}
