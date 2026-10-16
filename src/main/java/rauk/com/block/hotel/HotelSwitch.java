package rauk.com.block.hotel;

import java.util.function.BiConsumer;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The hotel's light switches (hotel design 6.3): a brass plate of two push buttons by the door of
 * each room. Underneath it's a lever, so Rauk works them as it works levers (its design 4.6); when
 * one is pulled the hotel switches the lights it is wired to, if there is power.
 */
public class HotelSwitch extends LeverBlock {
	public static final MapCodec<HotelSwitch> CODEC = simpleCodec(HotelSwitch::new);

	/** Told when a switch is pulled, with whether it is now on: the hotel's wiring. */
	public static BiConsumer<ServerLevel, BlockPos> wiring = (level, pos) -> {
	};

	public HotelSwitch(Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("unchecked")
	public MapCodec<LeverBlock> codec() {
		return (MapCodec<LeverBlock>) (MapCodec<?>) CODEC;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isVertical()) {
			return null;
		}
		return defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, face).setValue(POWERED, false);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide) {
			pull(state, level, pos, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void pull(BlockState state, Level level, BlockPos pos, Player player) {
		super.pull(state, level, pos, player);
		if (level instanceof ServerLevel server) {
			wiring.accept(server, pos);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// No redstone sparks: it's a switch.
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return HotelShapes.turned(state.getValue(FACING), 5.5, 4, 14, 10.5, 12, 16);
	}
}
