package rauk.com.block.hotel;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import rauk.com.hotel.HotelFurniture;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.NightLedger;

/**
 * The safe behind the reception desk (puzzle 1, hotel design 4.1). A click on one of its four
 * brass dials turns it a figure on, sneaking a figure back, with a click Rauk hears from ten
 * blocks off; the figure shows in the little window over it. Turned to the night clerk's four
 * figures, the tumblers drop and its door swings open, a clunk heard from twenty-four; a click on
 * the open safe takes what he left in it.
 *
 * <p>Its looks: shut, open with the things in it, open and empty.
 */
public class SafeBlock extends FurnitureBlock implements EntityBlock {
	public static final int SHUT = 0, FULL = 1, EMPTY = 2;
	public static BlockEntityType<SafeEntity> entityType;

	public static SafeBlock create(Piece piece, Properties properties) {
		return build(piece, () -> new SafeBlock(piece, properties));
	}

	private SafeBlock(Piece piece, Properties properties) {
		super(piece, properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return partOf(state) == 0 ? new SafeEntity(pos, state) : null;
	}

	/** Which look it's in: shut, full or empty. */
	public int state(BlockState s) {
		return lookOf(s).look();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos origin = originOf(pos, state);
		if (!(level.getBlockEntity(origin) instanceof SafeEntity safe)) {
			return InteractionResult.PASS;
		}
		switch (state(state)) {
			case SHUT -> {
				int dial = hit.getDirection() == facingOf(state) ? dialAt(across(pos, state, hit.getLocation())) : -1;
				if (dial < 0) {
					return InteractionResult.PASS;
				}
				safe.turn(dial, player.isSecondaryUseActive());
				level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.45f, 1.7f + level.random.nextFloat() * 0.2f);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				if (java.util.Arrays.equals(safe.dials(), NightLedger.combination(server))) {
					setLook(level, pos, state, FULL);
					level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.6f);
					level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1, 0.5f);
					level.gameEvent(player, GameEvent.BLOCK_OPEN, pos);
					player.displayClientMessage(Component.translatable("block.rauk.reception_safe.opened"), true);
				}
			}
			case FULL -> {
				setLook(level, pos, state, EMPTY);
				level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
				if (player instanceof ServerPlayer p) {
					NightLedger.takeFromSafe(p);
				}
				player.displayClientMessage(Component.translatable("block.rauk.reception_safe.taken"), true);
			}
			default -> player.displayClientMessage(Component.translatable("block.rauk.reception_safe.empty"), true);
		}
		return InteractionResult.SUCCESS;
	}

	/** Which dial a click on its front is on, by how far across it is, or -1. */
	private static int dialAt(double across) {
		for (int i = 0; i < 4; i++) {
			if (Math.abs(across - HotelFurniture.SAFE_DIALS[i]) < 3) {
				return i;
			}
		}
		return -1;
	}

}
