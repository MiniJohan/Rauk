package rauk.com.hotel;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.entity.RaukEntity;
import rauk.com.registry.RaukEntities;
import rauk.com.registry.RaukItems;

/**
 * Puzzle 2, the Generator (hotel design 4.2). The engineer pulled the three fuses on the night, on
 * Wennberg's orders, so nobody could lift the lockdown; one went into the till at the bar, one with
 * the night porter into the cold room, one into the laundry's soaking tank. With all three in the
 * fuse board on B2 the engine has its spark again, and it starts the way his log says:
 *
 * <ol>
 * <li>prime the fuel pump, three strokes;</li>
 * <li>open the choke;</li>
 * <li>swing the crank till she fires;</li>
 * <li>as she catches, close the choke: not before, not after.</li>
 * </ol>
 *
 * Crank her unprimed or with the choke shut and she coughs, a bang Rauk hears from far off, and
 * you start again; leave the choke open as she catches and she races, coughs and dies. When she
 * runs the hotel has power: the lights come on, the lifts work, and Rauk, wherever it is in the
 * building, knows where that came from.
 */
public final class Generator {
	/** The fuse board is full (the engine has its spark). */
	public static final String FUSES_IN = "fuses_in";
	/** The engine runs: the hotel has power (puzzle 2 done). */
	public static final String RUNNING = "generator_running";
	/** How long she catches for, in ticks: the moment to close the choke. */
	private static final int CATCH = 50;

	// The start under way. One hotel per world, so kept here, and forgotten on a restart.
	private static int primes, cranks, catching;
	private static BlockPos engine;

	private Generator() {
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (catching > 0 && level.dimension() == Level.OVERWORLD) {
				catching(level);
			}
		});
	}

	private static FurnitureBlock block(BlockState state) {
		return (FurnitureBlock) state.getBlock();
	}

	private static int look(BlockState state) {
		return block(state).lookOf(state).look();
	}

	/** One of the generator's controls, or the engine itself, clicked. */
	static InteractionResult use(ServerLevel level, BlockPos pos, BlockState state, Player player, String control) {
		HotelData data = HotelData.get(level);
		boolean running = data.done(RUNNING);
		engine = pos;
		switch (control) {
			case "primer" -> {
				if (running) {
					say(player, "primer_running");
					break;
				}
				primes++;
				level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35f, 1.3f + Math.min(primes, 4) * 0.1f);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				say(player, primes < 3 ? "primer" : primes == 3 ? "primer_full" : "primer_over");
			}
			case "choke" -> {
				boolean open = look(state) == 0;
				block(state).setLook(level, pos, state, open ? 1 : 0);
				level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, open ? 0.7f : 0.6f);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				if (catching > 0 && !open) {
					start(level, pos, player);
				} else if (!running) {
					say(player, open ? "choke_open" : "choke_shut");
				}
			}
			case "crank" -> {
				if (running || catching > 0) {
					say(player, running ? "running" : "catching");
					break;
				}
				level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, 0.55f);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				if (!data.done(FUSES_IN)) {
					say(player, "no_spark");
				} else if (primes < 3 || !chokeOpen(level, pos)) {
					cough(level, pos, player);
				} else if (++cranks >= 3) {
					catching = CATCH;
					cranks = 0;
					say(player, "catches");
				} else {
					say(player, "cranks");
				}
			}
			default -> say(player, running ? "running" : data.done(FUSES_IN) ? "cold" : "cold_no_spark");
		}
		return InteractionResult.SUCCESS;
	}

	/** Whether the choke near the engine is open (it's among the controls on its front). */
	private static boolean chokeOpen(ServerLevel level, BlockPos near) {
		for (BlockPos p : BlockPos.betweenClosed(near.offset(-3, -2, -3), near.offset(3, 2, 3))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof FurnitureBlock f && f.piece().id.equals("gen_choke")) {
				return look(s) == 1;
			}
		}
		return false;
	}

	/** She catches: sputtering, while there's time to close the choke; if not, she races and dies. */
	private static void catching(ServerLevel level) {
		catching--;
		if (engine == null) {
			catching = 0;
			return;
		}
		if (catching % 6 == 0) {
			level.playSound(null, engine, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 0.5f + level.random.nextFloat() * 0.3f);
			level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, engine);
		}
		if (catching == 0) {
			cough(level, engine, null);
			for (Player p : level.players()) {
				if (p.distanceToSqr(engine.getX(), engine.getY(), engine.getZ()) < 16 * 16) {
					say(p, "raced");
				}
			}
		}
	}

	/** A backfire: a bang and a puff of black smoke, heard a long way off; and back to the start. */
	private static void cough(ServerLevel level, BlockPos pos, Player player) {
		primes = 0;
		cranks = 0;
		catching = 0;
		level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.7f, 1.6f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.6, 0.4, 0.6, 0.02);
		level.gameEvent(player, GameEvent.EXPLODE, pos);
		if (player != null) {
			say(player, "cough");
		}
	}

	/** She runs: power to the whole hotel, and the whole hotel hears it. */
	private static void start(ServerLevel level, BlockPos pos, Player player) {
		catching = 0;
		primes = 0;
		HotelData.get(level).setDone(RUNNING, true);
		HotelLights.setPower(level, true);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.5f);
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1, 0.4f);
		level.gameEvent(player, GameEvent.EXPLODE, pos);
		for (RaukEntity rauk : level.getEntities(RaukEntities.RAUK, r -> true)) {
			rauk.mind().hearFar(pos.getCenter(), "generator");
		}
		say(player, "started");
	}

	/** The fuse board, clicked with a fuse in hand or without. */
	static ItemInteractionResult board(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
		int in = look(state);
		if (!stack.is(RaukItems.FUSE)) {
			say(player, "board_" + in);
			return ItemInteractionResult.SUCCESS;
		}
		if (in >= 3) {
			say(player, "board_3");
			return ItemInteractionResult.SUCCESS;
		}
		block(state).setLook(level, pos, state, in + 1);
		stack.consume(1, player);
		level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8f, 1.5f);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		if (in + 1 == 3) {
			HotelData.get(level).setDone(FUSES_IN, true);
			level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1, 0.6f);
			say(player, "board_full");
		} else {
			say(player, "board_" + (in + 1));
		}
		return ItemInteractionResult.SUCCESS;
	}

	/** The bar's till: its drawer rings out, and in it, among the coins, a fuse. */
	static InteractionResult till(ServerLevel level, BlockPos pos, BlockState state, Player player) {
		if (look(state) == 1) {
			say(player, "till_empty");
			return InteractionResult.SUCCESS;
		}
		block(state).setLook(level, pos, state, 1);
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1, 1.6f);
		level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, 1.4f);
		level.gameEvent(player, GameEvent.BLOCK_OPEN, pos);
		ItemStack fuse = new ItemStack(RaukItems.FUSE);
		if (!player.getInventory().add(fuse)) {
			player.drop(fuse, false);
		}
		say(player, "till");
		return InteractionResult.SUCCESS;
	}

	private static void say(Player player, String what) {
		if (player != null) {
			player.displayClientMessage(Component.translatable("hotel.rauk.generator." + what), true);
		}
	}

	/** The engineer's log, on the table in the boiler room. */
	public static ItemStack log() {
		return NightLedger.book("Engine Room Log", "A. Forsberg", List.of(
				"ENGINE ROOM LOG\n\nThe Raukvik Grand\n\nA. Forsberg, engineer",
				"The plant: a gas engine of 140 horsepower and a dynamo of 90 kilowatts. Far too much for a hotel this size. When I asked,"
						+ " Mr. Wennberg said the house had needs beyond its lamps.",
				"I know them now. The lockdown shutters. The locks on the Cabinet. And the feeding hatch, on a circuit of its own that is"
						+ " never to be switched off. I do not ask what it feeds.",
				"TO START HER\n\n1. Prime the fuel pump: three strokes.\n2. Open the choke.\n3. Swing the crank till she fires.\n4. As she"
						+ " catches, close the choke.\n\nNot before. Not after. Or she coughs and dies.",
				"Midsummer, past midnight. Mr. Wennberg came down himself. Pull the fuses, he said. Nobody lifts the lockdown. Not even me."
						+ "\n\nI pulled them.",
				"I could not keep them together. One went in the till at the bar. The porter took one with him to the cold room, the fool."
						+ " The third I dropped in the laundry's soaking tank, in the dark, and could not find again.",
				"If you have all three: the fuse board is in the fuse room, along the passage. Then the engine.\n\nIt will hear her start."
						+ " Everything in this house will.\n\nA.F."));
	}
}
