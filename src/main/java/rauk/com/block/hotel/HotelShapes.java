package rauk.com.block.hotel;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shapes of the hotel's own blocks (hotel design 11.3) that aren't plain cubes: columns and
 * pilasters in base, shaft and capital pieces, cornices, balustrades, ornate iron, the corridor
 * runner, pipes and finials. Their models are written by the asset tool from the same list.
 */
public final class HotelShapes {
	private HotelShapes() {
	}

	/** Which piece of a column: the foot, the run of it, or the head. */
	public enum Part implements StringRepresentable {
		BASE("base"), SHAFT("shaft"), CAPITAL("capital");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Which piece of a runner: its middle, or the edge with the gold border. */
	public enum RunnerPart implements StringRepresentable {
		CENTER("center"), EDGE("edge");

		private final String name;

		RunnerPart(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	public static final EnumProperty<RunnerPart> RUNNER_PART = EnumProperty.create("part", RunnerPart.class);

	/** A box as the model has it, turned to face a direction (it is drawn facing north). */
	static VoxelShape turned(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
		return switch (facing) {
			case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
			case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.box(x0, y0, z0, x1, y1, z1);
		};
	}

	/** A full block with a carved front: a keystone. */
	public static class Facing extends HorizontalDirectionalBlock {
		public static final MapCodec<Facing> CODEC = simpleCodec(Facing::new);

		public Facing(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}
	}

	/**
	 * A square column: base, shaft and capital pieces, each a full block so that Rauk can climb
	 * it (design 3.4). A placed piece takes its part from what's above and below it.
	 */
	public static class Column extends Block {
		public static final MapCodec<Column> CODEC = simpleCodec(Column::new);

		public Column(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(PART, Part.SHAFT));
		}

		@Override
		protected MapCodec<? extends Block> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(PART);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			boolean below = context.getLevel().getBlockState(context.getClickedPos().below()).is(this);
			return defaultBlockState().setValue(PART, below ? Part.SHAFT : Part.BASE);
		}
	}

	/** A flat column against a wall, standing a little proud of it. */
	public static class Pilaster extends HorizontalDirectionalBlock {
		public static final MapCodec<Pilaster> CODEC = simpleCodec(Pilaster::new);

		public Pilaster(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.SHAFT));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, PART);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			boolean shaft = state.getValue(PART) == Part.SHAFT;
			return shaft ? turned(state.getValue(FACING), 3, 0, 11, 13, 16, 16) : turned(state.getValue(FACING), 1, 0, 10, 15, 16, 16);
		}
	}

	/** A moulded cornice standing out from a wall, stepping out to an overhanging top. */
	public static class Cornice extends HorizontalDirectionalBlock {
		public static final MapCodec<Cornice> CODEC = simpleCodec(Cornice::new);

		public Cornice(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			Direction f = state.getValue(FACING);
			return Shapes.or(turned(f, 0, 0, 10, 16, 5, 16), turned(f, 0, 5, 5, 16, 11, 16), turned(f, 0, 11, 0, 16, 16, 16));
		}
	}

	/** Stone balusters under a rail, along x or z. */
	public static class Balustrade extends Block {
		public static final MapCodec<Balustrade> CODEC = simpleCodec(Balustrade::new);
		private static final VoxelShape ALONG_X = Block.box(0, 0, 2, 16, 16, 14), ALONG_Z = Block.box(2, 0, 0, 14, 16, 16);

		public Balustrade(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X));
		}

		@Override
		protected MapCodec<? extends Block> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(BlockStateProperties.HORIZONTAL_AXIS);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, context.getHorizontalDirection().getClockWise().getAxis());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
		}
	}

	/** Ornate iron that joins up like iron bars: railings, the lift cage's lattice. */
	public static class Railing extends IronBarsBlock {
		public static final MapCodec<Railing> CODEC = simpleCodec(Railing::new);

		public Railing(Properties properties) {
			super(properties);
		}

		@Override
		public MapCodec<? extends IronBarsBlock> codec() {
			return CODEC;
		}
	}

	/** The corridor runner: a carpet, its middle or an edge with the border on the facing side. */
	public static class Runner extends CarpetBlock {
		public static final MapCodec<Runner> CODEC = simpleCodec(Runner::new);

		public Runner(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(RUNNER_PART, RunnerPart.CENTER)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		}

		@Override
		public MapCodec<? extends CarpetBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(RUNNER_PART, BlockStateProperties.HORIZONTAL_FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection());
		}
	}

	/** An iron pipe along x, y or z. */
	public static class Pipe extends RotatedPillarBlock {
		public static final MapCodec<Pipe> CODEC = simpleCodec(Pipe::new);
		private static final VoxelShape X = Block.box(0, 5, 5, 16, 11, 11), Y = Block.box(5, 0, 5, 11, 16, 11), Z = Block.box(5, 5, 0, 11, 11, 16);

		public Pipe(Properties properties) {
			super(properties);
		}

		@Override
		public MapCodec<? extends RotatedPillarBlock> codec() {
			return CODEC;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(AXIS)) {
				case X -> X;
				case Z -> Z;
				default -> Y;
			};
		}
	}

	/** A brass finial: a spire on a roof. */
	public static class Finial extends Block {
		public static final MapCodec<Finial> CODEC = simpleCodec(Finial::new);
		private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 3, 12), Block.box(5, 3, 5, 11, 13, 11), Block.box(7, 13, 7, 9, 16, 9));

		public Finial(Properties properties) {
			super(properties);
		}

		@Override
		protected MapCodec<? extends Block> codec() {
			return CODEC;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}
	}
	/**
	 * A face at 45 degrees across the block with the half behind it filled (hotel design 14). Drawn
	 * facing north: the face looks north-west and the filled half is the south-east one. Set in the
	 * steps of a slanted wall, a row of them makes it flat; what stands against it stops at a few
	 * small steps just behind the face.
	 */
	public static class Slant extends HorizontalDirectionalBlock {
		public static final MapCodec<Slant> CODEC = simpleCodec(Slant::new);

		public Slant(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			Direction f = state.getValue(FACING);
			return Shapes.or(turned(f, 4, 0, 12, 8, 16, 16), turned(f, 8, 0, 8, 12, 16, 16), turned(f, 12, 0, 4, 16, 16, 16));
		}
	}

	/** A slender post, in base, shaft and capital pieces; the capital branches out under what it holds. */
	public static class Post extends Block {
		public static final MapCodec<Post> CODEC = simpleCodec(Post::new);
		private static final VoxelShape SHAFT = Block.box(6, 0, 6, 10, 16, 10), BASE = Shapes.or(Block.box(4, 0, 4, 12, 3, 12), SHAFT);

		public Post(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(PART, Part.SHAFT));
		}

		@Override
		protected MapCodec<? extends Block> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(PART);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(PART) == Part.BASE ? BASE : SHAFT;
		}
	}

	/** The letters of the hotel's name. */
	public enum Letter implements StringRepresentable {
		A, D, G, I, K, N, R, U, V;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Letter> LETTER = EnumProperty.create("letter", Letter.class);

	/** A brass letter standing on its edge in the middle of the block, its face to the facing. */
	public static class LetterBlock extends HorizontalDirectionalBlock {
		public static final MapCodec<LetterBlock> CODEC = simpleCodec(LetterBlock::new);

		public LetterBlock(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LETTER, Letter.R));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, LETTER);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return turned(state.getValue(FACING), 2, 0, 7, 14, 15, 9);
		}
	}

	/** A wire of bulbs along x or z, hanging under whatever it is strung from; lit, or dead. */
	public static class Festoon extends Block {
		public static final MapCodec<Festoon> CODEC = simpleCodec(Festoon::new);
		private static final VoxelShape X = Block.box(0, 10, 6, 16, 16, 10), Z = Block.box(6, 10, 0, 10, 16, 16);

		public Festoon(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X)
					.setValue(BlockStateProperties.LIT, false));
		}

		@Override
		protected MapCodec<? extends Block> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.LIT);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, context.getHorizontalDirection().getClockWise().getAxis());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? X : Z;
		}
	}
}
