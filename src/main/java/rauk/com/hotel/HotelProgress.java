package rauk.com.hotel;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import rauk.com.block.hotel.HotelDoorBlock;
import rauk.com.registry.RaukItems;

/**
 * How far the players have got through the hotel (hotel design 4), shared by everyone in it and
 * saved with the world ({@link HotelData}): what has been done, by name, and the locked doors
 * opened with their keys (which Rauk's map then counts as open). The things to carry are picked
 * up where they lie, and never go to waste: one dropped, or left in a larder, stays where it is.
 */
public final class HotelProgress {
	/** The crank is on the service lift's winding gear (puzzle 1). */
	public static final String CRANK = "crank_fitted";
	/** Wennberg's key is turned in the service lift's panel: it goes up to 4 (puzzle 4). */
	public static final String PENTHOUSE_KEY = "penthouse_key_turned";
	/** The reception safe opened and emptied (puzzle 1). */
	public static final String NIGHT_LEDGER = "night_ledger_solved";
	/** The last waltz played through, and the bandstand's drawer open (puzzle 3). */
	public static final String WALTZ = "waltz_played";
	/** The catalogue shelved in its numbers, and Wennberg's hidden study open (puzzle 4). */
	public static final String SHELVES = "catalogue_shelved";
	/** The study clock set to 23:47, and the lockdown control room open (puzzle 5). */
	public static final String CLOCK = "clock_set";

	private HotelProgress() {
	}

	public static void init() {
		HotelDoorBlock.unlocked = HotelProgress::unlocked;
		// Things to carry don't despawn: there's one of each, and the way out needs them.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && RaukItems.carried(item.getItem())) {
				item.setUnlimitedLifetime();
			}
		});
	}

	/** A door in the hotel unlocked with its key: remembered, both halves, for Rauk's map. */
	private static void unlocked(Level level, BlockPos lower) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		HotelData data = HotelData.get(server);
		HotelFrame frame = data.frame();
		if (frame == null || !frame.inBuilding(lower)) {
			return;
		}
		int[] at = frame.toPlan(lower);
		data.unlock(HotelPlan.cellKey(at[0], at[1], at[2]));
		data.unlock(HotelPlan.cellKey(at[0], at[1] + 1, at[2]));
	}

	/** Something lying in the hotel picked up: into the player's hands, or at their feet if those are full. */
	public static void pickUp(Level level, BlockPos pos, Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		// Taken from under water (the fuse in the laundry's tank), the water stays.
		boolean wet = level.getFluidState(pos).isSource();
		level.setBlock(pos, wet ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
				: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
	}
}
