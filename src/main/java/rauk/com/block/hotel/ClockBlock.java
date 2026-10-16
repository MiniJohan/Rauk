package rauk.com.block.hotel;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.StoppedClock;

/**
 * The grandfather clock in Wennberg's study (puzzle 5, hotel design 4.5), stopped, its hands where
 * he left them. A click moves the minute hand on a minute; sneaking, the hour hand on an hour. Set
 * to the minute he threw the lockdown, it chimes, and slides aside on the panel behind it.
 */
public class ClockBlock extends FurnitureBlock implements EntityBlock {
	public static BlockEntityType<ClockEntity> entityType;

	public static ClockBlock create(Piece piece, Properties properties) {
		return build(piece, () -> new ClockBlock(piece, properties));
	}

	private ClockBlock(Piece piece, Properties properties) {
		super(piece, properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return partOf(state) == 0 ? new ClockEntity(pos, state) : null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos origin = originOf(pos, state);
		if (!(level.getBlockEntity(origin) instanceof ClockEntity clock) || StoppedClock.solved(server)) {
			return InteractionResult.PASS;
		}
		boolean hour = player.isSecondaryUseActive();
		clock.turn(hour);
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.4f, hour ? 0.9f : 1.6f);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		player.displayClientMessage(Component.translatable(hour ? "hotel.rauk.clock.hour" : "hotel.rauk.clock.minute"), true);
		if (clock.minutes() == StoppedClock.TIME) {
			StoppedClock.solve(server, origin, state, player);
		}
		return InteractionResult.SUCCESS;
	}
}
