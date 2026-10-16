package rauk.com.hotel;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import rauk.com.block.hotel.ClockBlock;
import rauk.com.block.hotel.ClockEntity;
import rauk.com.block.hotel.HotelDoorBlock;

/**
 * Puzzle 5, the Stopped Clock (hotel design 4.5). Wennberg's study, up in the penthouse behind its
 * locked door (the study key, puzzle 3), the lift up to it on his penthouse key (puzzle 4). His
 * journal lies on the desk: at 23:47 he threw the lockdown, and he put the key and the code's
 * second half behind the clock, set to open at the minute it all began, and stopped it so no one
 * would set it by chance. The clock stands stopped at twenty past six. Set it to 11:47 and it
 * chimes and slides aside on the panel behind it: the lockdown control room, where he hid, and
 * where he still is. In it: the lockdown key, the code's second half, and the end of his journal.
 */
public final class StoppedClock {
	/** 23:47, on a clock's face. */
	public static final int TIME = 11 * 60 + 47;

	private StoppedClock() {
	}

	public static boolean solved(ServerLevel level) {
		return HotelData.get(level).done(HotelProgress.CLOCK);
	}

	/** The hands at 11:47: the clock chimes, slides aside, and the panel behind it opens. */
	public static void solve(ServerLevel level, BlockPos origin, BlockState state, Player player) {
		HotelData data = HotelData.get(level);
		data.setDone(HotelProgress.CLOCK, true);
		for (int i = 0; i < 4; i++) {
			level.playSound(null, origin.above(2), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7f, 0.5f + i * 0.02f);
		}
		ClockBlock clock = (ClockBlock) state.getBlock();
		Direction facing = clock.facingOf(state);
		// Aside, along the wall, wherever there's room for it.
		for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
			BlockPos to = origin.relative(side);
			boolean room = true;
			for (int y = 0; y < 3; y++) {
				room &= level.getBlockState(to.above(y)).canBeReplaced();
			}
			if (!room) {
				continue;
			}
			int minutes = level.getBlockEntity(origin) instanceof ClockEntity e ? e.minutes() : TIME;
			BlockState[] parts = new BlockState[3];
			for (int y = 0; y < 3; y++) {
				parts[y] = level.getBlockState(origin.above(y));
			}
			int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (int y = 2; y >= 0; y--) {
				level.setBlock(origin.above(y), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), quiet);
			}
			for (int y = 0; y < 3; y++) {
				level.setBlock(to.above(y), parts[y], quiet);
			}
			if (level.getBlockEntity(to) instanceof ClockEntity e) {
				e.set(minutes);
			}
			level.playSound(null, origin, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.9f, 0.5f);
			break;
		}
		HotelFrame frame = data.frame();
		HotelPlan.Mark door = HotelBuilder.plan().marks.get("control_room_door");
		if (frame != null && door != null) {
			BlockPos at = frame.toWorld(door.x(), door.y(), door.z());
			HotelDoorBlock.unlock(level, at);
			HotelDoorBlock.swing(level, at, HotelDoorBlock.FULL, HotelDoorBlock.CAREFUL, null, true);
		}
		level.gameEvent(player, GameEvent.BLOCK_OPEN, origin);
		player.displayClientMessage(Component.translatable("hotel.rauk.clock.opens"), true);
	}

	/** His journal, on the desk in the study: the night, and where he put the key. */
	public static ItemStack journal() {
		return NightLedger.book("Journal", "H. Wennberg", List.of(
				"23 June 1931\n\nThe anniversary. Two hundred guests, the band from Stockholm. No. 7 has been restless all week. Forsberg says the"
						+ " Cabinet's locks hum at night.",
				"23:47\n\nIt is out. I threw the lockdown: every shutter, every door. It is in the house, and so are they, and I have the key.\n\nGod"
						+ " forgive me. It will not get out into the world.",
				"24 June\n\nI had Forsberg pull the fuses. Nobody lifts the lockdown now, not even me. I am in my rooms. I can hear the band. They are"
						+ " still playing.",
				"25 June\n\nThe music stopped in the night.\n\nThe key and the second half are behind the clock. I set it to open at the minute it all"
						+ " began, and stopped it, so no one sets it by chance."));
	}

	/** The end of it, in the control room. */
	public static ItemStack lastPages() {
		return NightLedger.book("Journal, the Last Pages", "H. Wennberg", List.of(
				"27 June\n\nIt came up the lift shaft last night and stood outside the study door until it was light. It knows my voice. It was fed"
						+ " by my voice, once.",
				"I am in here, behind the clock, with the machinery. Water from the tank. No food since the 25th. It does not matter now.",
				"Whoever reads this: the key turns in the panel in the night clerk's office, by the old front doors. The code, both halves. The"
						+ " power on. Then every shutter rises, and every door.",
				"And it will be loose in the house with every door open, and you will have to run for it.\n\nDo it only if you can run.\n\nH.W."));
	}

	/** The card with the code's second half. */
	public static ItemStack codeCard(ServerLevel level) {
		int[] code = LockdownCode.of(level.getSeed());
		return NightLedger.book("A Card", "H. Wennberg", List.of(
				"LOCKDOWN RELEASE\n\nSecond half:\n\n" + code[4] + "  " + code[5] + "  " + code[6] + "  " + code[7]
						+ "\n\nThe panel is in the night clerk's office."));
	}
}
