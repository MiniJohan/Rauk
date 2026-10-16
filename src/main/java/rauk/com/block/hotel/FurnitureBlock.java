package rauk.com.block.hotel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import rauk.com.Rauk;
import rauk.com.hotel.HotelFurniture;
import rauk.com.hotel.HotelFurniture.Form;
import rauk.com.hotel.HotelFurniture.Light;
import rauk.com.hotel.HotelFurniture.Look;
import rauk.com.hotel.HotelFurniture.Mount;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.HotelBooks;
import rauk.com.hotel.HotelPuzzles;
import rauk.com.hotel.HotelProgress;

/**
 * A piece of the hotel's furniture (hotel design 12), as its {@link Piece} describes it: its block
 * states, outline and light come from there. Rows (a sofa, a counter) and stacks (a curtain) join
 * up with their neighbours; tables join on every side; a piece of several blocks is placed and
 * broken whole. Candles and fires can be put out and lit again; the hotel's electric lamps switch
 * on and off when there is power.
 *
 * <p>Some pieces are more than furniture, by their {@code kind}: things to read ("read:"), things
 * to pick up ("pickup:", which can lie under water), and the puzzles' working parts ("puzzle:",
 * worked by {@link HotelPuzzles}).
 */
public class FurnitureBlock extends Block {
	/** Which block of a row: alone, or its low end, middle or high end. */
	public enum Run implements StringRepresentable {
		SINGLE, LEFT, MIDDLE, RIGHT;

		@Override
		public String getSerializedName() {
			return name().toLowerCase();
		}
	}

	/** Which block of a stack: alone, or its bottom, middle or top. */
	public enum Stack implements StringRepresentable {
		SINGLE, BOTTOM, MIDDLE, TOP;

		@Override
		public String getSerializedName() {
			return name().toLowerCase();
		}
	}

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Run> RUN = EnumProperty.create("run", Run.class);
	public static final EnumProperty<Stack> STACK = EnumProperty.create("stack", Stack.class);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** North, east, south, west: the order the furniture's joins are in. */
	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	private static final BooleanProperty[] JOINS = {BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
			BlockStateProperties.WEST};

	/**
	 * Whether an electric light at a place may be switched: the hotel's lamps only when it has
	 * power (set by the hotel; everywhere else, always).
	 */
	public static BiPredicate<Level, BlockPos> power = (level, pos) -> true;
	/** Whether the hotel the player is in has power, on the client (for the running generator's smoke and noise). */
	public static java.util.function.BooleanSupplier clientPower = () -> false;

	private static final ThreadLocal<Piece> BUILDING = new ThreadLocal<>();

	private final Piece piece;
	private final IntegerProperty part, look;
	private final Map<BlockState, VoxelShape> shapes = new HashMap<>();
	private final MapCodec<FurnitureBlock> codec;

	/** Makes the block for a piece. (Its block states depend on the piece, so it is made this way.) */
	public static FurnitureBlock create(Piece piece, Properties properties) {
		return build(piece, () -> new FurnitureBlock(piece, properties));
	}

	/** Makes a piece's block of some kind of furniture block (the safe): the piece is known while its states are. */
	protected static <T extends FurnitureBlock> T build(Piece piece, java.util.function.Supplier<T> make) {
		BUILDING.set(piece);
		try {
			return make.get();
		} finally {
			BUILDING.remove();
		}
	}

	protected FurnitureBlock(Piece piece, Properties properties) {
		super(properties);
		this.piece = piece;
		this.part = (IntegerProperty) stateDefinition.getProperty("part");
		this.look = (IntegerProperty) stateDefinition.getProperty("look");
		BlockState state = stateDefinition.any();
		if (piece.facing) {
			state = state.setValue(FACING, Direction.NORTH);
		}
		if (state.hasProperty(LIT)) {
			state = state.setValue(LIT, piece.light == Light.FLAME);
		}
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, false);
		}
		registerDefaultState(state);
		for (BlockState s : stateDefinition.getPossibleStates()) {
			shapes.put(s, outline(s));
		}
		this.codec = simpleCodec(p -> create(piece, p));
	}

	public Piece piece() {
		return piece;
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return codec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		Piece p = BUILDING.get();
		if (p.facing) {
			builder.add(FACING);
		}
		switch (p.form) {
			case RUN -> builder.add(RUN);
			case STACK -> builder.add(STACK);
			case TABLE -> builder.add(JOINS);
			case MULTI -> builder.add(IntegerProperty.create("part", 0, p.parts() - 1));
			default -> {
			}
		}
		if (p.looks > 1) {
			builder.add(IntegerProperty.create("look", 0, p.looks - 1));
		}
		if (p.light != Light.NONE) {
			builder.add(LIT);
		}
		if (p.kind.startsWith("pickup:")) {
			builder.add(BlockStateProperties.WATERLOGGED);
		}
	}

	@Override
	protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
				? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	/** The light a state gives out: read from its values, as it's asked before the block is done. */
	public static int light(Piece piece, BlockState state) {
		boolean lit = false;
		int part = 0;
		for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
			if (e.getKey().getName().equals("lit")) {
				lit = (Boolean) e.getValue();
			} else if (e.getKey().getName().equals("part")) {
				part = (Integer) e.getValue();
			}
		}
		return lit && piece.glows(part) ? piece.glow : 0;
	}

	// --- what state it's in ------------------------------------------------------------------------

	public Look lookOf(BlockState s) {
		String run = piece.form == Form.RUN ? s.getValue(RUN).getSerializedName()
				: piece.form == Form.STACK ? s.getValue(STACK).getSerializedName() : "single";
		int joins = 0;
		if (piece.form == Form.TABLE) {
			for (int i = 0; i < 4; i++) {
				if (s.getValue(JOINS[i])) {
					joins |= HotelFurniture.JOIN[i];
				}
			}
		}
		return new Look(run, joins, look != null ? s.getValue(look) : 0, s.hasProperty(LIT) && s.getValue(LIT));
	}

	/** The state in another of its looks. */
	public BlockState withLook(BlockState s, int l) {
		return look == null ? s : s.setValue(look, l);
	}

	/** Puts every part of the piece a block belongs to into a look. */
	public void setLook(Level level, BlockPos pos, BlockState state, int l) {
		if (piece.form != Form.MULTI) {
			level.setBlock(pos, withLook(state, l), UPDATE_CLIENTS);
			return;
		}
		BlockPos origin = originOf(pos, state);
		Direction f = facingOf(state);
		for (int p = 0; p < piece.parts(); p++) {
			BlockPos at = partPos(origin, f, p);
			BlockState s = level.getBlockState(at);
			if (s.is(this) && partOf(s) == p) {
				level.setBlock(at, withLook(s, l), UPDATE_CLIENTS);
			}
		}
	}

	public BlockState withPart(BlockState s, int p) {
		return part == null ? s : s.setValue(part, p);
	}

	public int partOf(BlockState s) {
		return part != null ? s.getValue(part) : 0;
	}

	public Direction facingOf(BlockState s) {
		return piece.facing ? s.getValue(FACING) : Direction.NORTH;
	}

	private VoxelShape outline(BlockState s) {
		Direction f = facingOf(s);
		VoxelShape shape = Shapes.empty();
		for (double[] b : HotelFurniture.outline(piece, lookOf(s), partOf(s))) {
			shape = Shapes.or(shape, HotelShapes.turned(f, b[0], b[1], b[2], b[3], b[4], b[5]));
		}
		return shape.optimize();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state);
	}

	// --- where its cells are -------------------------------------------------------------------------

	/** A step in the piece's own space (x across, z back from its front) turned to the world's way. */
	private static int[] turn(int x, int z, Direction facing) {
		return switch (facing) {
			case EAST -> new int[] {-z, x};
			case SOUTH -> new int[] {-x, -z};
			case WEST -> new int[] {z, -x};
			default -> new int[] {x, z};
		};
	}

	/** Where a part of the piece is, from where its first part (front, left, bottom) is. */
	public BlockPos partPos(BlockPos origin, Direction facing, int part) {
		int[] c = piece.cellOf(part);
		int[] t = turn(c[0], c[2], facing);
		return origin.offset(t[0], c[1], t[1]);
	}

	/**
	 * How far across the piece a point is, in sixteenths from its own left edge (low x as it is
	 * drawn), whichever block of it the point is on: which dial of the safe, which key of the piano.
	 * Someone in front of the piece has its high x on their left.
	 */
	public double across(BlockPos pos, BlockState state, net.minecraft.world.phys.Vec3 at) {
		BlockPos origin = originOf(pos, state);
		net.minecraft.world.phys.Vec3 rel = at.subtract(origin.getX(), origin.getY(), origin.getZ());
		return 16 * switch (facingOf(state)) {
			case SOUTH -> 1 - rel.x;
			case EAST -> rel.z;
			case WEST -> 1 - rel.z;
			default -> rel.x;
		};
	}

	public BlockPos originOf(BlockPos pos, BlockState state) {
		int[] c = piece.cellOf(partOf(state));
		int[] t = turn(c[0], c[2], facingOf(state));
		return pos.offset(-t[0], -c[1], -t[1]);
	}

	// --- placing, joining, breaking ----------------------------------------------------------------

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState s = defaultBlockState();
		Direction facing = context.getHorizontalDirection().getOpposite();
		if (piece.mount == Mount.WALL && context.getClickedFace().getAxis().isHorizontal()) {
			facing = context.getClickedFace();
		}
		if (piece.facing) {
			s = s.setValue(FACING, facing);
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (piece.form == Form.MULTI) {
			for (int p = 1; p < piece.parts(); p++) {
				BlockPos at = partPos(pos, facingOf(s), p);
				if (!level.isInWorldBounds(at) || !level.getBlockState(at).canBeReplaced(context)) {
					return null;
				}
			}
		}
		return joined(s, level, pos);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		if (piece.form == Form.MULTI) {
			for (int p = 1; p < piece.parts(); p++) {
				level.setBlock(partPos(pos, facingOf(state), p), state.setValue(part, p), UPDATE_ALL);
			}
		}
	}

	/** The state with its joins to its neighbours worked out. */
	public BlockState joined(BlockState s, LevelAccessor level, BlockPos pos) {
		switch (piece.form) {
			case RUN -> {
				Direction f = facingOf(s);
				boolean low = sameFacing(s, level.getBlockState(pos.relative(f.getCounterClockWise())));
				boolean high = sameFacing(s, level.getBlockState(pos.relative(f.getClockWise())));
				return s.setValue(RUN, low && high ? Run.MIDDLE : low ? Run.RIGHT : high ? Run.LEFT : Run.SINGLE);
			}
			case STACK -> {
				boolean below = sameFacing(s, level.getBlockState(pos.below()));
				boolean above = sameFacing(s, level.getBlockState(pos.above()));
				return s.setValue(STACK, below && above ? Stack.MIDDLE : below ? Stack.TOP : above ? Stack.BOTTOM : Stack.SINGLE);
			}
			case TABLE -> {
				for (int i = 0; i < 4; i++) {
					s = s.setValue(JOINS[i], level.getBlockState(pos.relative(SIDES[i])).is(this));
				}
				return s;
			}
			default -> {
				return s;
			}
		}
	}

	private boolean sameFacing(BlockState s, BlockState other) {
		return other.is(this) && (!piece.facing || other.getValue(FACING) == s.getValue(FACING));
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos,
			BlockPos neighbourPos) {
		if (piece.form == Form.MULTI) {
			// A part whose neighbouring part has gone goes too.
			int[] c = piece.cellOf(partOf(state));
			Direction f = facingOf(state);
			for (int axis = 0; axis < 3; axis++) {
				for (int step : new int[] {-1, 1}) {
					int[] n = c.clone();
					n[axis] += step;
					if (n[0] < 0 || n[1] < 0 || n[2] < 0 || n[0] >= piece.w || n[1] >= piece.h || n[2] >= piece.d) {
						continue;
					}
					int other = piece.partAt(n[0], n[1], n[2]);
					if (partPos(originOf(pos, state), f, other).equals(neighbourPos)
							&& !(neighbour.is(this) && partOf(neighbour) == other && facingOf(neighbour) == f)) {
						return Blocks.AIR.defaultBlockState();
					}
				}
			}
			return state;
		}
		return joined(state, level, pos);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (piece.facing) {
			state = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
		}
		if (piece.form == Form.TABLE) {
			BlockState out = state;
			for (int i = 0; i < 4; i++) {
				Direction to = rotation.rotate(SIDES[i]);
				out = out.setValue(JOINS[List.of(SIDES).indexOf(to)], state.getValue(JOINS[i]));
			}
			state = out;
		}
		return state;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (piece.facing) {
			state = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
		}
		if (piece.form == Form.TABLE) {
			BlockState out = state;
			for (int i = 0; i < 4; i++) {
				Direction to = mirror.mirror(SIDES[i]);
				out = out.setValue(JOINS[List.of(SIDES).indexOf(to)], state.getValue(JOINS[i]));
			}
			state = out;
		}
		return state;
	}

	// --- light ---------------------------------------------------------------------------------------

	/** Lights (or puts out) every part of the piece that a block belongs to. */
	public void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
		if (!state.hasProperty(LIT)) {
			return;
		}
		if (piece.form != Form.MULTI) {
			level.setBlock(pos, state.setValue(LIT, lit), UPDATE_ALL);
			return;
		}
		BlockPos origin = originOf(pos, state);
		Direction f = facingOf(state);
		for (int p = 0; p < piece.parts(); p++) {
			BlockPos at = partPos(origin, f, p);
			BlockState s = level.getBlockState(at);
			if (s.is(this) && partOf(s) == p) {
				level.setBlock(at, s.setValue(LIT, lit), UPDATE_CLIENTS);
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (piece.kind.startsWith("puzzle:")) {
			return HotelPuzzles.use(level, pos, state, player, piece.kind.substring("puzzle:".length()), hit);
		}
		if (piece.kind.startsWith("read:")) {
			// Something to read where it lies (the night ledger): opened as a book.
			if (player instanceof net.minecraft.server.level.ServerPlayer reader) {
				HotelBooks.read(reader, piece.kind.substring("read:".length()));
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (piece.kind.startsWith("pickup:")) {
			// Something to carry, lying here: picked up.
			if (!level.isClientSide) {
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.3f);
				String what = piece.kind.substring("pickup:".length());
				HotelProgress.pickUp(level, pos, player, what.startsWith("volume:")
						? rauk.com.hotel.CollectorsShelves.volume(Integer.parseInt(what.substring("volume:".length())))
						: new ItemStack(BuiltInRegistries.ITEM.get(Rauk.id(what))));
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (piece.light == Light.FLAME && state.getValue(LIT)) {
			// Pinched out, or blown.
			if (!level.isClientSide) {
				setLit(level, pos, state, false);
				level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1, 1);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (piece.light == Light.ELECTRIC) {
			if (!level.isClientSide) {
				boolean powered = power.test(level, pos);
				if (powered) {
					setLit(level, pos, state, !state.getValue(LIT));
				}
				level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, powered ? 1.4f : 0.8f);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (piece.kind.startsWith("puzzle:")) {
			return HotelPuzzles.useItem(stack, level, pos, state, player, piece.kind.substring("puzzle:".length()));
		}
		boolean fire = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
		if (piece.light == Light.FLAME && fire && !state.getValue(LIT)) {
			if (!level.isClientSide) {
				setLit(level, pos, state, true);
				level.playSound(null, pos, stack.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE,
						SoundSource.BLOCKS, 1, 1);
				if (stack.is(Items.FLINT_AND_STEEL)) {
					stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
				} else if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (piece.id.equals("generator") && clientPower.getAsBoolean()) {
			// The generator running (puzzle 2): its thrum, and smoke where the exhaust leaves it. The
			// game picks blocks at random for this, so every block of it takes a turn.
			BlockPos origin = originOf(pos, state);
			if (random.nextInt(3) == 0) {
				level.playLocalSound(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, rauk.com.sound.HotelSounds.GENERATOR,
						SoundSource.BLOCKS, 0.9f, 0.95f + random.nextFloat() * 0.1f, false);
			}
			BlockPos exhaust = partPos(origin, facingOf(state), piece.partAt(1, 1, 1));
			level.addParticle(ParticleTypes.SMOKE, exhaust.getX() + 0.3 + random.nextDouble() * 0.4, exhaust.getY() + 0.9,
					exhaust.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
		}
		if (piece.light == Light.ELECTRIC && state.getValue(LIT) && random.nextFloat() < 0.04f) {
			// A bulb that has been on its own for ninety years: it buzzes, and now and then flickers.
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rauk.com.sound.HotelSounds.LAMP, SoundSource.BLOCKS,
					1, 0.9f + random.nextFloat() * 0.2f, false);
		}
		if (piece.light != Light.FLAME || !state.getValue(LIT)) {
			return;
		}
		if (piece.flames.stream().noneMatch(f -> f[3] == 1) && random.nextFloat() < 0.06f) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS,
					0.6f + random.nextFloat() * 0.4f, 0.8f + random.nextFloat() * 0.4f, false);
		}
		int[] c = piece.cellOf(partOf(state));
		Direction f = facingOf(state);
		for (double[] flame : piece.flames) {
			double x = flame[0] - c[0] * 16, y = flame[1] - c[1] * 16, z = flame[2] - c[2] * 16;
			if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
				continue;
			}
			double[] w = switch (f) {
				case EAST -> new double[] {16 - z, x};
				case SOUTH -> new double[] {16 - x, 16 - z};
				case WEST -> new double[] {z, 16 - x};
				default -> new double[] {x, z};
			};
			double px = pos.getX() + w[0] / 16, py = pos.getY() + y / 16, pz = pos.getZ() + w[1] / 16;
			if (flame[3] == 0) {
				if (random.nextFloat() < 0.17f) {
					level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0, 0);
				}
				level.addParticle(ParticleTypes.SMALL_FLAME, px, py, pz, 0, 0, 0);
			} else {
				if (random.nextFloat() < 0.5f) {
					level.addParticle(ParticleTypes.FLAME, px + random.nextGaussian() * 0.15, py, pz + random.nextGaussian() * 0.1, 0, 0.01, 0);
				}
				if (random.nextFloat() < 0.3f) {
					level.addParticle(ParticleTypes.SMOKE, px, py + 0.4, pz, 0, 0.03, 0);
				}
				if (random.nextFloat() < 0.04f) {
					level.playLocalSound(px, py, pz, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6f + random.nextFloat() * 0.4f,
							0.6f + random.nextFloat() * 0.6f, false);
				}
			}
		}
	}
}
