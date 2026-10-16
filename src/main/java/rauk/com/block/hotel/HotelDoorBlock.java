package rauk.com.block.hotel;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import rauk.com.entity.RaukEntity;
import rauk.com.registry.RaukItems;

/**
 * The hotel's doors (hotel design 4b): a door that swings, rather than snapping open or shut. Its
 * panel turns on its hinges over time ({@link HotelDoorEntity} keeps the angle, and the client
 * draws it there), slowly or fast, all the way or only an inch. It counts as open, for walking
 * through, once it's past halfway.
 *
 * <p>You open one by hand: a click swings it open or shut; with sneak held you handle it with
 * care, quietly and slowly: a closed door opens a crack to look through, a door ajar opens the rest
 * of the way, an open door is eased shut. Rauk opens them as it's going: slowly and creaking while
 * it stalks, flung back against the wall when it charges, or (peeking) drawn open a finger's width.
 * Double doors swing together.
 *
 * <p>Some are locked (hotel design 4): the staff-only doors the night clerk's keyring opens, and
 * Wennberg's study. Locked, a door rattles and stays shut, for Rauk too; its key turns it once and
 * it stays open to everyone after. A secret one (the section of the library's shelves, puzzle 4)
 * has no key and gives nothing away: it is a bookcase until the puzzle opens it.
 */
public class HotelDoorBlock extends DoorBlock implements EntityBlock {
	/** Which key a door is locked to, or none. */
	public enum Lock implements StringRepresentable {
		NONE, STAFF, STUDY, SECRET;

		@Override
		public String getSerializedName() {
			return name().toLowerCase();
		}

		/** The key that opens it, or null for a secret one (only its puzzle opens that). */
		@Nullable
		public Item key() {
			return this == STAFF ? RaukItems.STAFF_KEYRING : this == STUDY ? RaukItems.STUDY_KEY : null;
		}
	}

	public static final EnumProperty<Lock> LOCK = EnumProperty.create("lock", Lock.class);

	/** Fully open, and a crack: degrees from shut. */
	public static final float FULL = 90, AJAR = 22;
	/** How fast a door swings, in degrees a tick: with care, as a door is opened, flung, and a finger's peek. */
	public static final float CAREFUL = 2.2f, NORMAL = 9, SLAM = 32, PEEK = 1.1f;

	/** The block entity type, set when the blocks are registered. */
	public static BlockEntityType<HotelDoorEntity> entityType;
	/**
	 * Whether a lift's gate at a place may be opened by hand: only with the car there (the hotel
	 * says). Rauk forces them whenever it likes.
	 */
	public static BiPredicate<Level, BlockPos> gateOpens = (level, pos) -> true;
	/** Told of a door unlocked (its lower half), so the hotel remembers it. */
	public static BiConsumer<Level, BlockPos> unlocked = (level, pos) -> {
	};

	private final MapCodec<HotelDoorBlock> codec;
	/** A lift's gate, rather than a door. */
	private final boolean gate;

	public HotelDoorBlock(BlockSetType type, boolean gate, Properties properties) {
		super(type, properties);
		this.gate = gate;
		this.codec = simpleCodec(p -> new HotelDoorBlock(type, gate, p));
		registerDefaultState(defaultBlockState().setValue(LOCK, Lock.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LOCK);
	}

	/** Whether a block is a hotel door that is locked. */
	public static boolean locked(BlockState state) {
		return state.getBlock() instanceof HotelDoorBlock && state.getValue(LOCK) != Lock.NONE;
	}

	/** Unlocks a door, and the other of its pair if that is locked too. */
	public static void unlock(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!locked(state)) {
			return;
		}
		BlockPos low = lower(state, pos);
		BlockPos other = partner(level, low);
		for (BlockPos door : other != null && locked(level.getBlockState(other)) ? new BlockPos[] {low, other} : new BlockPos[] {low}) {
			for (BlockPos half : new BlockPos[] {door, door.above()}) {
				BlockState s = level.getBlockState(half);
				if (s.getBlock() instanceof HotelDoorBlock) {
					level.setBlock(half, s.setValue(LOCK, Lock.NONE), UPDATE_CLIENTS);
				}
			}
			unlocked.accept(level, door);
		}
	}

	@Override
	public MapCodec<? extends DoorBlock> codec() {
		return codec;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new HotelDoorEntity(pos, state) : null;
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == entityType ? (BlockEntityTicker<T>) (BlockEntityTicker<HotelDoorEntity>) HotelDoorEntity::tick : null;
	}

	/** The door's lower half, where it keeps its angle. */
	public static BlockPos lower(BlockState state, BlockPos pos) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
	}

	@Nullable
	public static HotelDoorEntity entity(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof HotelDoorBlock)) {
			return null;
		}
		return level.getBlockEntity(lower(state, pos)) instanceof HotelDoorEntity e ? e : null;
	}

	/**
	 * The other door of a pair: beside this one on the side away from its hinges, facing the same
	 * way, hinged on the other side. Null if it stands alone.
	 */
	@Nullable
	public static BlockPos partner(Level level, BlockPos lower) {
		BlockState state = level.getBlockState(lower);
		if (!(state.getBlock() instanceof HotelDoorBlock)) {
			return null;
		}
		Direction facing = state.getValue(FACING);
		boolean left = state.getValue(HINGE) == DoorHingeSide.LEFT;
		BlockPos other = lower.relative(left ? facing.getClockWise() : facing.getCounterClockWise());
		BlockState o = level.getBlockState(other);
		return o.getBlock() instanceof HotelDoorBlock && o.getValue(FACING) == facing && o.getValue(HINGE) != state.getValue(HINGE)
				&& o.getValue(HALF) == DoubleBlockHalf.LOWER ? other : null;
	}

	/** Swings a door (and its pair, if it has one) to an angle at a speed. Returns whether there was a door. */
	public static boolean swing(Level level, BlockPos pos, float target, float speed, @Nullable Entity who, boolean pair) {
		HotelDoorEntity door = entity(level, pos);
		if (door == null || locked(level.getBlockState(door.getBlockPos()))) {
			return false;
		}
		door.swing(target, speed, who);
		BlockPos other = pair ? partner(level, door.getBlockPos()) : null;
		if (other != null && !locked(level.getBlockState(other)) && level.getBlockEntity(other) instanceof HotelDoorEntity e) {
			e.swing(target, speed, who);
		}
		return true;
	}

	/** Rauk at a door it wants to look through: one long finger draws it open a crack. */
	public static boolean peek(Level level, BlockPos pos, RaukEntity rauk) {
		HotelDoorEntity door = entity(level, pos);
		if (door == null || door.target() >= AJAR) {
			return door != null;
		}
		return swing(level, pos, AJAR, PEEK, rauk, false);
	}

	/** How Rauk swings a door as it goes through: as fast as it's going. */
	private static float speedOf(@Nullable Entity who) {
		if (who instanceof RaukEntity rauk) {
			double s = rauk.mover().speed();
			return s <= 2.3 ? CAREFUL : s <= 5.5 ? NORMAL : SLAM;
		}
		return NORMAL;
	}

	@Override
	public boolean isOpen(BlockState state) {
		return state.getValue(OPEN);
	}

	@Override
	public void setOpen(@Nullable Entity who, Level level, BlockState state, BlockPos pos, boolean open) {
		if (!state.is(this) || locked(state)) {
			return;
		}
		HotelDoorEntity door = entity(level, pos);
		if (door != null && door.target() == (open ? FULL : 0)) {
			return;
		}
		float speed = speedOf(who);
		swing(level, pos, open ? FULL : 0, speed, who, speed >= NORMAL);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!type().canOpenByHand()) {
			return InteractionResult.PASS;
		}
		if (locked(state)) {
			if (state.getValue(LOCK) == Lock.SECRET) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide) {
				level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 0.8f + level.random.nextFloat() * 0.2f);
				player.displayClientMessage(Component.translatable("block.rauk.hotel_door.locked." + state.getValue(LOCK).getSerializedName()), true);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (!level.isClientSide) {
			HotelDoorEntity door = entity(level, pos);
			if (gate && door != null && door.target() == 0 && !gateOpens.test(level, lower(state, pos))) {
				// The car isn't here: the gate's locked shut, and behind it the shaft.
				level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHAIN_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.7f);
				player.displayClientMessage(net.minecraft.network.chat.Component.literal("The gate won't open. The car isn't here."), true);
				return InteractionResult.SUCCESS;
			}
			if (door != null) {
				float now = door.target();
				if (player.isSecondaryUseActive()) {
					// With care: a crack, then the rest of the way; or eased shut.
					swing(level, pos, now < AJAR - 1 ? AJAR : now < FULL - 1 ? FULL : 0, CAREFUL, player, false);
				} else {
					swing(level, pos, now < FULL / 2 ? FULL : 0, NORMAL, player, true);
				}
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A locked door and its key: the key turns, and the door stays unlocked. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		Item key = locked(state) ? state.getValue(LOCK).key() : null;
		if (key == null || !stack.is(key)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!level.isClientSide) {
			Lock lock = state.getValue(LOCK);
			unlock(level, pos);
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.7f);
			player.displayClientMessage(Component.translatable("block.rauk.hotel_door.unlocked." + lock.getSerializedName()), true);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
		// Not worked by redstone: these are opened by hand.
	}
}
