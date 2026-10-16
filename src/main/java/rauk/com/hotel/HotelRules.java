package rauk.com.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import rauk.com.Rauk;
import rauk.com.hotel.HotelPlan.Mark;

/**
 * The hotel's rules (hotel design 6.1): inside it you're in adventure mode, so you can open doors
 * and pull levers but not build or dig; nobody but a creative player breaks its blocks from
 * outside either. The coal chute gives way behind the first one in, and quietly clears again once
 * the hotel has been empty a while. A generated hotel is gone over once, the first time anyone
 * comes near, to put right anything world generation grew into it.
 *
 * <p>With friends (multiplayer): whoever comes later finds the rubble loose, and goes down with it
 * into the coal store (nobody inside gets out that way); and anyone who dies in the hotel wakes up
 * in the staff dormitory (hotel design 6.2), not out at the world spawn.
 */
public final class HotelRules {
	/** Set on a survival player while the hotel has them in adventure mode. */
	private static final String TAG = "rauk_in_hotel";
	private static final long REOPEN_AFTER = 6000;
	public static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	private HotelRules() {
	}

	public static void tick(ServerLevel level) {
		if (level.getGameTime() % 10 != 0) {
			return;
		}
		HotelData data = HotelData.get(level);
		HotelFrame frame = data.frame();
		if (frame == null) {
			return;
		}
		boolean anyone = false;
		for (ServerPlayer player : level.players()) {
			boolean inside = frame.inBuilding(player.blockPosition());
			GameType mode = player.gameMode.getGameModeForPlayer();
			if (inside && mode == GameType.SURVIVAL) {
				player.setGameMode(GameType.ADVENTURE);
				player.addTag(TAG);
			} else if (!inside && player.getTags().contains(TAG)) {
				if (mode == GameType.ADVENTURE) {
					player.setGameMode(GameType.SURVIVAL);
				}
				player.removeTag(TAG);
			}
			boolean prey = mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
			if (prey && data.chuteDown() && onRubble(frame, player)) {
				slideIn(level, frame, player);
				continue;
			}
			if (inside && prey) {
				anyone = true;
				wakeInDormitory(frame, player);
				// The chute falls in once they're through the wall, not while they're still in it.
				int[] at = frame.toPlan(player.blockPosition());
				if (!data.chuteDown() && HotelPlan.inCore(at[0], at[1], at[2])) {
					collapseChute(level, frame);
					data.setChuteDown(true);
				}
			}
			if (data.needsRepair() && player.blockPosition().distManhattan(frame.center()) < 160 && loaded(level, frame)) {
				int fixed = HotelWriter.write(level, frame, null, QUIET, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
				data.repaired();
				Rauk.LOGGER.info("Went over the hotel: {} blocks put right", fixed);
			}
		}
		if (anyone) {
			data.occupied(level.getGameTime());
		} else if (data.chuteDown() && level.getGameTime() - data.lastOccupied() > REOPEN_AFTER) {
			HotelWriter.write(level, frame, chute(frame), QUIET, -9, 0, false);
			data.setChuteDown(false);
		}
	}

	/** Whether a player outside is standing on the fallen-in top of the chute. */
	private static boolean onRubble(HotelFrame frame, ServerPlayer player) {
		Mark top = HotelBuilder.plan().marks.get("chute_top");
		int[] at = frame.toPlan(player.blockPosition());
		return Math.abs(at[0] - top.x()) <= 1 && Math.abs(at[2] - top.z()) <= 1 && at[1] >= top.y() - 1 && at[1] <= top.y() + 2;
	}

	/**
	 * A late arrival: the rubble in the chute gives way under them and they go down with it, out
	 * through the fallen-in passage into the coal store. It's a way in only: the chute stays full.
	 */
	private static void slideIn(ServerLevel level, HotelFrame frame, ServerPlayer player) {
		Mark bottom = HotelBuilder.plan().marks.get("chute_bottom");
		int[] spot = standingNear(bottom.x() + 5, bottom.y(), bottom.z(), 3);
		if (spot == null) {
			return;
		}
		BlockPos from = player.blockPosition(), to = frame.toWorld(spot[0], spot[1], spot[2]);
		for (BlockPos at : new BlockPos[] {from, to}) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), at.getX() + 0.5, at.getY() + 0.8,
					at.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.1);
			level.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.4f, 0.5f);
		}
		player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), player.getXRot());
		player.displayClientMessage(net.minecraft.network.chat.Component.literal("The rubble gives way under you, and you go down with it."), true);
	}

	/**
	 * Inside, they wake in the staff dormitory if it gets them (hotel design 6.2): their respawn
	 * point is set to the floor by the bunks, without a bed.
	 */
	private static void wakeInDormitory(HotelFrame frame, ServerPlayer player) {
		Mark bunk = HotelBuilder.plan().marks.get("dormitory_bunk");
		int[] spot = standingNear(bunk.x(), bunk.y(), bunk.z(), 5);
		if (spot == null) {
			return;
		}
		BlockPos wake = frame.toWorld(spot[0], spot[1], spot[2]);
		if (!wake.equals(player.getRespawnPosition()) || player.getRespawnDimension() != Level.OVERWORLD) {
			player.setRespawnPosition(Level.OVERWORLD, wake, player.getYRot(), true, false);
		}
	}

	/** The plan cell nearest a place where someone can stand: two clear blocks on a solid one. */
	private static int[] standingNear(int x, int y, int z, int reach) {
		HotelPlan plan = HotelBuilder.plan();
		for (int r = 0; r <= reach; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r && plan.isAir(x + dx, y, z + dz) && plan.isAir(x + dx, y + 1, z + dz)
							&& !plan.isAir(x + dx, y - 1, z + dz)) {
						return new int[] {x + dx, y, z + dz};
					}
				}
			}
		}
		return null;
	}

	/** The chute and its passage, as a world box. */
	private static BoundingBox chute(HotelFrame frame) {
		Mark top = HotelBuilder.plan().marks.get("chute_top");
		return frame.box(top.x() - 1, -9, top.z() - 1, -1, 0, top.z() + 1);
	}

	/**
	 * The coal chute falls in: rubble down the shaft and through the passage, behind whoever just
	 * came through, with the noise and dust of it.
	 */
	private static void collapseChute(ServerLevel level, HotelFrame frame) {
		Mark top = HotelBuilder.plan().marks.get("chute_top"), bottom = HotelBuilder.plan().marks.get("chute_bottom");
		BlockState[] rubble = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
				Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
		int i = 0;
		for (int y = bottom.y(); y <= top.y(); y++) {
			level.setBlock(frame.toWorld(top.x(), y, top.z()), rubble[i++ % rubble.length], Block.UPDATE_ALL);
		}
		for (int x = top.x() + 1; x < 0; x++) {
			for (int y = bottom.y(); y <= bottom.y() + 1; y++) {
				BlockPos at = frame.toWorld(x, y, top.z());
				level.setBlock(at, rubble[i++ % rubble.length], Block.UPDATE_ALL);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), at.getX() + 0.5,
						at.getY() + 0.5, at.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.1);
			}
		}
		BlockPos mouth = frame.toWorld(-1, bottom.y(), top.z());
		level.playSound(null, mouth, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5f, 0.5f);
		level.playSound(null, mouth, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
		level.playSound(null, mouth, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.5f);
	}

	/** Whether every chunk of the hotel is loaded, so going over it touches no unloaded ground. */
	private static boolean loaded(ServerLevel level, HotelFrame frame) {
		BoundingBox box = frame.bounds();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether a player is kept from breaking a block: the hotel's own, unless they're creative. */
	public static boolean protects(Level level, Player player, BlockPos pos) {
		if (player.isCreative() || !(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		HotelFrame frame = HotelData.get(server).frame();
		return frame != null && frame.inBuilding(pos);
	}
}
