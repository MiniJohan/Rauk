package rauk.com.block.hotel;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lift's controls (hotel design 3.2): the operator's lever in the car (a click sends it up a
 * floor, with sneak held down a floor) or the call button by a landing's gate (brings it here).
 * What happens is the hotel's to say.
 */
public class LiftControlBlock extends HorizontalDirectionalBlock {
	/** What a control does when used: the level, where it is, who, whether it's the lever, whether sneaking. */
	public interface Handler {
		void use(ServerLevel level, BlockPos pos, Player player, boolean lever, boolean down);
	}

	public static Handler handler = (level, pos, player, lever, down) -> {
	};

	/** Something held to a control (the crank, the penthouse key): whether the hotel took it. */
	public interface ItemHandler {
		boolean use(ServerLevel level, BlockPos pos, Player player, boolean lever, ItemStack stack);
	}

	public static ItemHandler itemHandler = (level, pos, player, lever, stack) -> false;

	private final boolean lever;
	private final MapCodec<LiftControlBlock> codec;

	public LiftControlBlock(boolean lever, Properties properties) {
		super(properties);
		this.lever = lever;
		this.codec = simpleCodec(p -> new LiftControlBlock(lever, p));
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return codec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return lever ? HotelShapes.turned(state.getValue(FACING), 4, 2, 12, 12, 14, 16) : HotelShapes.turned(state.getValue(FACING), 6, 5, 14, 10, 11, 16);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (level instanceof ServerLevel server && itemHandler.use(server, pos, player, lever, stack)) {
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			handler.use(server, pos, player, lever, player.isSecondaryUseActive());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
